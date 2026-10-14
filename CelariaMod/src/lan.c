// Protection: the mod is only active during a multiplayer game on a server of the local network (LAN).
//
// When the game connects to a server, the TCP connections of this process to the port of the server are read from the
// Windows TCP table. The mod becomes active only if such a connection exists and every one of them goes to a local
// address: private / loopback / link-local / shared (100.64/10, Tailscale) ranges, or an address inside the subnet of an
// active network adapter of this computer (virtual LANs like Radmin VPN 26.0.0.0/8, Hamachi 25.0.0.0/8, ZeroTier).
// Otherwise (single player, menus, servers on the Internet, any error) the mod is passive: every hook calls the original
// function, nothing is sent, displayed or blocked.
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <iphlpapi.h>
#include <stdlib.h>
#include "hook.h"
#include "mod.h"

#define OBJ_NETWORK_HANDLER 0x58
#define VAR_SERVER_PORT 1239
#define OBJ_CONTROLLER 0x50
#define VAR_MULTIPLAYER 965

int g_lan_session;

static int private_ipv4(DWORD addressNetworkOrder) {
	unsigned a = addressNetworkOrder & 0xff, b = (addressNetworkOrder >> 8) & 0xff;
	return a == 127 || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254)
		|| (a == 100 && b >= 64 && b <= 127);
}

// address inside the subnet of an active adapter (prefix of at least 8 bits for IPv4, 16 for IPv6)
static int on_local_subnet(int family, const void *address) {
	ULONG size = 16 * 1024;
	IP_ADAPTER_ADDRESSES *list = NULL;
	ULONG ret = ERROR_BUFFER_OVERFLOW;
	for (int tries = 0; tries < 3 && ret == ERROR_BUFFER_OVERFLOW; tries++) {
		free(list);
		list = malloc(size);
		if (list == NULL) return 0;
		ret = GetAdaptersAddresses(family, GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER, NULL, list, &size);
	}
	int found = 0;
	if (ret == NO_ERROR) {
		for (IP_ADAPTER_ADDRESSES *ad = list; ad && found == 0; ad = ad->Next) {
			if (ad->OperStatus != IfOperStatusUp || ad->IfType == IF_TYPE_SOFTWARE_LOOPBACK) continue;
			for (IP_ADAPTER_UNICAST_ADDRESS *u = ad->FirstUnicastAddress; u && found == 0; u = u->Next) {
				const struct sockaddr *sa = u->Address.lpSockaddr;
				int prefix = u->OnLinkPrefixLength;
				if (sa == NULL || sa->sa_family != family) continue;
				if (family == AF_INET) {
					if (prefix < 8 || prefix > 32) continue;
					uint32_t mask = prefix == 32 ? 0xffffffffu : ~(0xffffffffu >> prefix);
					uint32_t local = ntohl(((const struct sockaddr_in *)sa)->sin_addr.s_addr);
					uint32_t remote = ntohl(*(const DWORD *)address);
					if ((local & mask) == (remote & mask)) {
						found = 1;
						mod_log("server on the subnet of the adapter %ls", ad->FriendlyName);
					}
				} else {
					if (prefix < 16 || prefix > 128) continue;
					const UCHAR *l = ((const struct sockaddr_in6 *)sa)->sin6_addr.s6_addr, *r = address;
					int bytes = prefix / 8, bits = prefix % 8;
					if (memcmp(l, r, bytes) == 0 && (bits == 0 || ((l[bytes] ^ r[bytes]) & (0xff << (8 - bits))) == 0)) {
						found = 1;
						mod_log("server on the subnet of the adapter %ls", ad->FriendlyName);
					}
				}
			}
		}
	}
	free(list);
	return found;
}

static int private_ipv6(const UCHAR *a) {
	static const UCHAR loopback[16] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
	if (memcmp(a, loopback, 16) == 0) return 1;
	if (a[0] == 0xfe && (a[1] & 0xc0) == 0x80) return 1;// fe80::/10 link-local
	if ((a[0] & 0xfe) == 0xfc) return 1;// fc00::/7 unique local
	static const UCHAR mapped[12] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff };
	if (memcmp(a, mapped, 12) == 0) return private_ipv4(*(const DWORD *)(a + 12));// ::ffff:a.b.c.d
	return 0;
}

// 1 = at least one connection to the port and all of them are private, 0 = otherwise
static int connections_private(int port) {
	DWORD pid = GetCurrentProcessId();
	int found = 0, publicFound = 0;

	DWORD size = 0;
	GetExtendedTcpTable(NULL, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_CONNECTIONS, 0);
	MIB_TCPTABLE_OWNER_PID *t4 = malloc(size);
	if (t4 && GetExtendedTcpTable(t4, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_CONNECTIONS, 0) == NO_ERROR) {
		for (DWORD i = 0; i < t4->dwNumEntries; i++) {
			MIB_TCPROW_OWNER_PID *r = &t4->table[i];
			if (r->dwOwningPid != pid || r->dwState != MIB_TCP_STATE_ESTAB || ntohs((u_short)r->dwRemotePort) != port) continue;
			found = 1;
			if (private_ipv4(r->dwRemoteAddr) == 0 && on_local_subnet(AF_INET, &r->dwRemoteAddr) == 0) publicFound = 1;
		}
	} else {
		publicFound = 1;// fail closed
	}
	free(t4);

	size = 0;
	GetExtendedTcpTable(NULL, &size, FALSE, AF_INET6, TCP_TABLE_OWNER_PID_CONNECTIONS, 0);
	MIB_TCP6TABLE_OWNER_PID *t6 = malloc(size);
	if (t6 && GetExtendedTcpTable(t6, &size, FALSE, AF_INET6, TCP_TABLE_OWNER_PID_CONNECTIONS, 0) == NO_ERROR) {
		for (DWORD i = 0; i < t6->dwNumEntries; i++) {
			MIB_TCP6ROW_OWNER_PID *r = &t6->table[i];
			if (r->dwOwningPid != pid || r->dwState != MIB_TCP_STATE_ESTAB || ntohs((u_short)r->dwRemotePort) != port) continue;
			found = 1;
			if (private_ipv6(r->ucRemoteAddr) == 0 && on_local_subnet(AF_INET6, r->ucRemoteAddr) == 0) publicFound = 1;
		}
	}
	free(t6);
	return found && publicFound == 0;
}

// called when the connection to a server succeeded
void lan_check_session(void) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_NETWORK_HANDLER, VAR_SERVER_PORT, ARRAY_NONE, &v);
	int port = (int)yy_to_real(&v);
	yy_clear(&v);
	g_lan_session = port > 0 && port < 65536 && connections_private(port);
	mod_log(g_lan_session ? "LAN server (port %d): mod active" : "server not on the local network (port %d): mod inactive", port);
}

// 1 = connected to a LAN server and in a multiplayer game: every feature of the mod may run
int mod_active(void) {
	if (g_lan_session == 0) return 0;
	RValue v = { 0 };
	YY_VAR_GET(OBJ_CONTROLLER, VAR_MULTIPLAYER, ARRAY_NONE, &v);
	int mp = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	return mp;
}

void lan_end_session(void) {
	if (g_lan_session) mod_log("disconnected: mod inactive");
	g_lan_session = 0;
}
