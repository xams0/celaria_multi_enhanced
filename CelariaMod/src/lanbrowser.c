// Server browser LAN: the multiplayer menu gets "Server Browser Online" (the original browser) and "Server Browser LAN".
//
// The LAN browser is the server browser of the game: the request to the master server (getServerList.php) is NOT sent,
// the mod broadcasts "CCPD" on every local network of the computer instead (LAN, Radmin VPN, Hamachi, ZeroTier: broadcast
// address of every active adapter, 255.255.255.255 and 127.0.0.1) to UDP port 26510. The cup servers answer
// "CSPD|<port>|<players>|<max players>|<protocol>|<password 0/1>|<name>". After a moment, the answers are given to the
// HTTP event of the browser (obj_http_serverList) as the JSON answer of the master server: the list, the selection and
// the connection are the ones of the game. Nothing is sent to the Internet.
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <iphlpapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define GML_SCR_CREATE_BUTTON_ICON_MENU 0x004d3920// (x, y, w, h, text, icon sprite, behaviour script, id, icon scale, text scale)
#define ICON_SCALE_TWO_LINES 0.42// scales of the icon and of the text for a text of two lines
#define TEXT_SCALE_TWO_LINES 0.58
#define GML_BEV_MULTIPLAYER_SERVERBROWSER 0x0057d430
#define GML_BEV_GUIE_TEST 0x006a1760// unused test script of the editor: behaviour of the "Server Browser LAN" button
#define SCRIPT_BEV_GUIE_TEST 850
#define GML_OBJ_HTTP_SERVERLIST_HTTP 0x00a8ea60// HTTP event of obj_http_serverList (answer of the master server)
#define OBJ_HTTP_SERVERLIST 160
#define GML_CHTTP_IS_RESPONDING 0x008bd8e0
#define GML_CHTTP_GET_RESPONSE_STATUS 0x008bdf00
#define GML_CHTTP_GET_RESPONSE 0x008bc710
#define GML_CHTTP_MESSAGE_IS_OK 0x008b9c50
#define SLOT_FN_HTTP_POST_STRING 0x00f38e14
#define FUNCTION_TABLE 0x031b3104
#define DISCOVERY_PORT 26510
#define DISCOVERY_TIME_MS 800

typedef void(__cdecl *NativeFn)(RValue *result, void *self, void *other, int argc, RValue *args);
typedef void(__cdecl *GmlEvent)(void *self, void *other);
typedef int(__cdecl *FnWithNew)(void *iterator, void **pSelf, void **pOther, int object);
typedef char(__cdecl *FnWithNext)(void *iterator, void **pSelf, void **pOther);
typedef void(__cdecl *FnWithDelete)(void *iterator, void **pSelf, void **pOther);
#define YY_WITH_NEW ((FnWithNew)ADDR(0x00b9d720))
#define YY_WITH_NEXT ((FnWithNext)ADDR(0x00b8fd20))
#define YY_WITH_DELETE ((FnWithDelete)ADDR(0x00b8fd50))

static GmlScript orig_create_button_icon_menu;
static GmlScript orig_serverbrowser;
static GmlScript orig_guie_test;
static GmlScript orig_is_responding, orig_get_response_status, orig_get_response, orig_message_is_ok;
static NativeFn orig_http_post_string;

static int lan_browser;// the server browser shows the LAN servers
static int fake_answer;// the HTTP event of the browser is called by the mod
static char answer_json[8192];

// ---------------------------------------------------------------- multiplayer menu

static int button_saved;
static int lan_button_created;// the behaviour of the test script is only replaced after the creation of the button
static double browser_values[10];// numeric arguments of the "Server-Browser" button (position)
static int browser_kinds[10];

static RValue *__cdecl hook_create_button_icon_menu(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc != 10) return orig_create_button_icon_menu(self, other, result, argc, args);
	const char *text = yy_string(args[7]);
	if (text && strcmp(text, "Server-Browser") == 0) {
		for (int i = 0; i < 10; i++) {
			browser_kinds[i] = args[i]->kind & 0xffffff;
			browser_values[i] = browser_kinds[i] == KIND_REAL ? args[i]->real : 0;
		}
		button_saved = 1;
		if (yy_string(args[4])) yy_set_string(args[4], "Server Browser#Online");
		if ((args[8]->kind & 0xffffff) == KIND_REAL) args[8]->real = ICON_SCALE_TWO_LINES;
		if ((args[9]->kind & 0xffffff) == KIND_REAL) args[9]->real = TEXT_SCALE_TWO_LINES;
		return orig_create_button_icon_menu(self, other, result, argc, args);
	}
	if (text && strcmp(text, "Direct-IP") == 0 && button_saved) {
		// "Server Browser LAN" at the place of "Direct-IP", "Direct-IP" one place lower
		RValue a[10] = { { { 0 } } }, r = { 0 };
		RValue *pa[10];
		for (int i = 0; i < 10; i++) {
			pa[i] = &a[i];
			const char *s = yy_string(args[i]);
			if (s) YY_CREATE_STRING(&a[i], strcmp(s, "Direct-IP") == 0 ? (i == 4 ? "Server Browser#LAN" : "Server-Browser-LAN") : s);
			else a[i].real = yy_to_real(args[i]);
		}
		a[5].real = browser_values[5];// icon of the server browser
		a[6].real = SCRIPT_BEV_GUIE_TEST;
		a[8].real = ICON_SCALE_TWO_LINES;
		a[9].real = TEXT_SCALE_TWO_LINES;
		lan_button_created = 1;
		orig_create_button_icon_menu(self, other, &r, 10, pa);
		yy_clear(&r);
		for (int i = 0; i < 10; i++) yy_clear(&a[i]);
		for (int i = 0; i < 10; i++) {
			if (i == 5 || i == 6 || i >= 8 || browser_kinds[i] != KIND_REAL || (args[i]->kind & 0xffffff) != KIND_REAL) continue;
			double delta = args[i]->real - browser_values[i];
			if (delta != 0) args[i]->real += delta;
		}
		button_saved = 0;
	}
	return orig_create_button_icon_menu(self, other, result, argc, args);
}

static RValue *__cdecl hook_serverbrowser(void *self, void *other, RValue *result, int argc, RValue **args) {
	lan_browser = 0;// "Server Browser Online"
	return orig_serverbrowser(self, other, result, argc, args);
}

static RValue *__cdecl hook_guie_test(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (lan_button_created == 0) return orig_guie_test(self, other, result, argc, args);
	lan_browser = 1;
	mod_log("server browser LAN");
	return orig_serverbrowser(self, other, result, argc, args);
}

// ---------------------------------------------------------------- discovery

typedef struct {
	char ip[64];
	char name[96];
	int port, players, max_players, version, password;
} LanServer;

#define MAX_SERVERS 32
static LanServer servers[MAX_SERVERS];
static int server_count;
static SOCKET discovery_socket = INVALID_SOCKET;
static double discovery_end;

static void send_discovery(SOCKET s, uint32_t address) {
	struct sockaddr_in to = { 0 };
	to.sin_family = AF_INET;
	to.sin_port = htons(DISCOVERY_PORT);
	to.sin_addr.s_addr = address;
	sendto(s, "CCPD", 4, 0, (struct sockaddr *)&to, sizeof to);
}

static void discovery_start(void) {
	static int wsa;
	if (wsa == 0) {
		WSADATA data;
		WSAStartup(MAKEWORD(2, 2), &data);
		wsa = 1;
	}
	if (discovery_socket != INVALID_SOCKET) closesocket(discovery_socket);
	server_count = 0;
	discovery_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	if (discovery_socket == INVALID_SOCKET) return;
	BOOL on = TRUE;
	setsockopt(discovery_socket, SOL_SOCKET, SO_BROADCAST, (const char *)&on, sizeof on);
	u_long nonblocking = 1;
	ioctlsocket(discovery_socket, FIONBIO, &nonblocking);
	// broadcast address of every active network adapter (LAN, Radmin VPN, Hamachi...), everything, this computer
	ULONG size = 16 * 1024;
	IP_ADAPTER_ADDRESSES *list = malloc(size);
	if (list && GetAdaptersAddresses(AF_INET, GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER, NULL, list, &size) == NO_ERROR) {
		for (IP_ADAPTER_ADDRESSES *ad = list; ad; ad = ad->Next) {
			if (ad->OperStatus != IfOperStatusUp || ad->IfType == IF_TYPE_SOFTWARE_LOOPBACK) continue;
			for (IP_ADAPTER_UNICAST_ADDRESS *u = ad->FirstUnicastAddress; u; u = u->Next) {
				if (u->Address.lpSockaddr->sa_family != AF_INET || u->OnLinkPrefixLength < 8 || u->OnLinkPrefixLength > 30) continue;
				uint32_t ip = ntohl(((struct sockaddr_in *)u->Address.lpSockaddr)->sin_addr.s_addr);
				uint32_t mask = ~(0xffffffffu >> u->OnLinkPrefixLength);
				send_discovery(discovery_socket, htonl(ip | ~mask));
			}
		}
	}
	free(list);
	send_discovery(discovery_socket, htonl(INADDR_BROADCAST));
	send_discovery(discovery_socket, htonl(INADDR_LOOPBACK));
	discovery_end = clock_local_ms() + DISCOVERY_TIME_MS;
	mod_log("LAN discovery started");
}

static void discovery_receive(void) {
	char buffer[512];
	struct sockaddr_in from;
	int from_size = sizeof from;
	int n;
	while ((n = recvfrom(discovery_socket, buffer, sizeof buffer - 1, 0, (struct sockaddr *)&from, &from_size)) > 0) {
		buffer[n] = 0;
		LanServer s = { 0 };
		int name_at = 0;
		if (strncmp(buffer, "CSPD|", 5) != 0 || sscanf(buffer + 5, "%d|%d|%d|%d|%d|%n", &s.port, &s.players, &s.max_players, &s.version, &s.password, &name_at) < 5) continue;
		snprintf(s.name, sizeof s.name, "%s", name_at > 0 ? buffer + 5 + name_at : "Celaria server");
		inet_ntop(AF_INET, &from.sin_addr, s.ip, sizeof s.ip);
		int known = 0;// one answer per server (several networks can reach the same server)
		for (int i = 0; i < server_count; i++)
			if (servers[i].port == s.port && strcmp(servers[i].name, s.name) == 0) {
				known = 1;
				// the local address is preferred to a VPN address
				if (strncmp(s.ip, "127.", 4) == 0 || strncmp(s.ip, "192.168.", 8) == 0) snprintf(servers[i].ip, sizeof servers[i].ip, "%s", s.ip);
			}
		if (known == 0 && server_count < MAX_SERVERS) servers[server_count++] = s;
		from_size = sizeof from;
	}
}

static void json_string(char *out, size_t size, const char *s) {
	size_t o = 0;
	for (; *s && o + 3 < size; s++) {
		if (*s == '"' || *s == '\\') out[o++] = '\\';
		out[o++] = (unsigned char)*s < 32 ? ' ' : *s;
	}
	out[o] = 0;
}

static void build_answer(void) {
	int o = snprintf(answer_json, sizeof answer_json, "{\"version\":1,\"totalServers\":%d,\"servers\":[", server_count);
	for (int i = 0; i < server_count && o < (int)sizeof answer_json - 300; i++) {
		char name[200];
		json_string(name, sizeof name, servers[i].name);
		o += snprintf(answer_json + o, sizeof answer_json - o, "%s[\"%s\",\"%s\",%d,%d,%d,%d,%d,0]", i ? "," : "", servers[i].ip, name,
			servers[i].port, servers[i].players, servers[i].max_players, servers[i].version, servers[i].password);
	}
	snprintf(answer_json + o, sizeof answer_json - o, "]}");
}

// called every frame
void lanbrowser_update(void *self, void *other) {
	if (discovery_socket == INVALID_SOCKET) return;
	discovery_receive();
	if (clock_local_ms() < discovery_end) return;
	closesocket(discovery_socket);
	discovery_socket = INVALID_SOCKET;
	build_answer();
	mod_log("LAN discovery: %d server(s)", server_count);
	// answer given to the browser (if it is still open)
	uint8_t iterator[1024];
	void *s = self, *o = other;
	void *browser = NULL;
	if (YY_WITH_NEW(iterator, &s, &o, OBJ_HTTP_SERVERLIST) > 0) browser = s;
	YY_WITH_DELETE(iterator, &s, &o);
	if (browser) {
		fake_answer = 1;
		((GmlEvent)ADDR(GML_OBJ_HTTP_SERVERLIST_HTTP))(browser, browser);
		fake_answer = 0;
	}
}

// ---------------------------------------------------------------- HTTP

// http_post_string(url, data): the request of the LAN browser to the master server is replaced by the LAN discovery
static void __cdecl hook_http_post_string(RValue *result, void *self, void *other, int argc, RValue *args) {
	const char *url = argc >= 1 ? yy_string(&args[0]) : NULL;
	if (lan_browser && url && strstr(url, "getServerList")) {
		result->kind = KIND_REAL;
		result->real = -1;
		discovery_start();
		return;
	}
	orig_http_post_string(result, self, other, argc, args);
}

static RValue *__cdecl hook_is_responding(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (fake_answer == 0) return orig_is_responding(self, other, result, argc, args);
	yy_set_real(result, 1);
	return result;
}

static RValue *__cdecl hook_get_response_status(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (fake_answer == 0) return orig_get_response_status(self, other, result, argc, args);
	yy_set_real(result, 0);
	return result;
}

static RValue *__cdecl hook_get_response(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (fake_answer == 0) return orig_get_response(self, other, result, argc, args);
	yy_set_string(result, answer_json);
	return result;
}

static RValue *__cdecl hook_message_is_ok(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (fake_answer == 0) return orig_message_is_ok(self, other, result, argc, args);
	yy_set_real(result, 1);
	return result;
}

void lanbrowser_install(void) {
	orig_create_button_icon_menu = (GmlScript)hook_install(ADDR(GML_SCR_CREATE_BUTTON_ICON_MENU), PROLOGUE, sizeof PROLOGUE, hook_create_button_icon_menu);
	orig_serverbrowser = (GmlScript)hook_install(ADDR(GML_BEV_MULTIPLAYER_SERVERBROWSER), PROLOGUE, sizeof PROLOGUE, hook_serverbrowser);
	orig_guie_test = (GmlScript)hook_install(ADDR(GML_BEV_GUIE_TEST), PROLOGUE, sizeof PROLOGUE, hook_guie_test);
	orig_is_responding = (GmlScript)hook_install(ADDR(GML_CHTTP_IS_RESPONDING), PROLOGUE, sizeof PROLOGUE, hook_is_responding);
	orig_get_response_status = (GmlScript)hook_install(ADDR(GML_CHTTP_GET_RESPONSE_STATUS), PROLOGUE, sizeof PROLOGUE, hook_get_response_status);
	orig_get_response = (GmlScript)hook_install(ADDR(GML_CHTTP_GET_RESPONSE), PROLOGUE, sizeof PROLOGUE, hook_get_response);
	orig_message_is_ok = (GmlScript)hook_install(ADDR(GML_CHTTP_MESSAGE_IS_OK), PROLOGUE, sizeof PROLOGUE, hook_message_is_ok);
}

// native function http_post_string: its entry in the function table is replaced (once the table exists)
void lanbrowser_wrap_natives(void) {
	if (orig_http_post_string) return;
	uint8_t *table = *(uint8_t **)ADDR(FUNCTION_TABLE);
	int index = *(int *)ADDR(SLOT_FN_HTTP_POST_STRING);
	if (table && index > 0 && index < 10000) {
		NativeFn *entry = (NativeFn *)(table + index * 0x50 + 0x40);
		orig_http_post_string = *entry;
		*entry = hook_http_post_string;
	}
}
