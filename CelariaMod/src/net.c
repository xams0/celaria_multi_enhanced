// Network helpers: extra packets understood by the cup server (ignored by the official servers).
//   client -> server  204 u32 : precise goal time in milliseconds (sent just before the goal packet 203)
//   client -> server  205 u8  : the client runs the mod (version)
//   server -> client  chat message starting with "CUPMOD" : the server supports the mod (hidden, not displayed)
//   server -> client  chat message "CUPMS <ticks> <ms> <name>" : milliseconds of a leaderboard entry (hidden), sent just
//                     before the leaderboard packet; the leaderboard entry (ticks) is displayed with the milliseconds
//
// On a server supporting the mod (cup server) nothing is sent to Steam: no leaderboard score, no multiplayer experience.
// Official servers and single player are not affected.
#include <windows.h>
#include <string.h>
#include <stdio.h>
#include "hook.h"
#include "mod.h"

int g_server_supports_mod;

static GmlScript orig_send_metadata;
static GmlScript orig_add_chat;
static GmlScript orig_init_mp;
static GmlScript orig_reset_mp;
static GmlScript orig_upload;
static GmlScript orig_upload_retry;
static GmlScript orig_add_experience;

static GmlScript orig_add_entry;

// milliseconds of the leaderboard entries (by player name)
#define MAX_TIMES 64
static struct {
	char name[128];
	double ticks, ms;
} times[MAX_TIMES];
static int time_count;

static void store_time(const char *text) {
	double ticks, ms;
	int offset = 0;
	if (sscanf(text, "%lf %lf %n", &ticks, &ms, &offset) < 2 || offset <= 0) return;
	const char *name = text + offset;
	int slot = -1;
	for (int i = 0; i < time_count; i++)
		if (strcmp(times[i].name, name) == 0) slot = i;
	if (slot < 0) {
		slot = time_count < MAX_TIMES ? time_count++ : 0;
		snprintf(times[slot].name, sizeof times[slot].name, "%s", name);
	}
	times[slot].ticks = ticks;
	times[slot].ms = ms;
}

// number held by an RValue (real, int32 or int64)
static int rvalue_number(const RValue *v, double *out) {
	switch (v->kind & 0xffffff) {
	case KIND_REAL: *out = v->real; return 1;
	case 7: *out = v->i32; return 1;
	case 10: *out = (double)v->i64; return 1;
	}
	return 0;
}

// scr_menu_leaderboard_addEntry(...): the name is "<leaderboard name> [<player id>]", the time an integer (ticks).
// The time argument is replaced by the precise time (ticks with fraction), displayed by scr_time_format in milliseconds.
static RValue *__cdecl hook_add_entry(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (g_server_supports_mod && mod_active()) {
		const char *name = NULL;
		int nameArg = -1;
		for (int i = 0; i < argc && name == NULL; i++) {
			name = yy_string(args[i]);
			nameArg = i;
		}
		// server records have no player id (255): remove the " [255]" added by the client
		if (name) {
			size_t len = strlen(name);
			if (len > 6 && strcmp(name + len - 6, " [255]") == 0) {
				char clean[256];
				snprintf(clean, sizeof clean, "%.*s", (int)(len - 6), name);
				yy_set_string(args[nameArg], clean);
				name = yy_string(args[nameArg]);
			}
		}
		for (int t = 0; name && t < time_count; t++) {
			size_t len = strlen(times[t].name);
			if (strncmp(name, times[t].name, len) != 0 || (name[len] != 0 && name[len] != ' ')) continue;
			for (int i = 0; i < argc; i++) {
				double v;
				if (rvalue_number(args[i], &v) && v == times[t].ticks) {
					args[i]->kind = KIND_REAL;
					args[i]->flags = 0;
					args[i]->real = times[t].ms / 10.0;
					break;
				}
			}
			break;
		}
	}
	return orig_add_entry(self, other, result, argc, args);
}

static RValue *return_noone(RValue *result) {
	yy_set_real(result, -4);
	return result;
}

static int packet_size(int bufferType) { return bufferType == BUFFER_U32 ? 4 : 1; }

void net_send_tcp(void *self, void *other, int packetId, int bufferType, RValue *value) {
	RValue buffer = { 0 }, server = { 0 }, r = { 0 };
	YY_VAR_GET(OBJ_NETWORK, VAR_TCP_OUT_BUFFER, ARRAY_NONE, &buffer);
	YY_VAR_GET(OBJ_NETWORK, VAR_SERVER, ARRAY_NONE, &server);

	RValue *argBuffer[1] = { &buffer };
	((GmlScript)ADDR(GML_BUFFER_RESET))(self, other, &r, 1, argBuffer);
	yy_clear(&r);

	RValue id = { 0 }, size = { 0 };
	id.real = packetId;
	size.real = packet_size(bufferType);
	RValue *argHeader[3] = { &buffer, &id, &size };
	((GmlScript)ADDR(GML_BUFFER_WRITE_CLIENT_HEADER_TCP))(self, other, &r, 3, argHeader);
	yy_clear(&r);

	YY_BUFFER_WRITE((int)yy_to_real(&buffer), bufferType, value);

	RValue tell = { 0 };
	YY_CALL_BUILTIN(self, other, &tell, 1, *(void **)ADDR(SLOT_FN_BUFFER_TELL), argBuffer);
	RValue *argSend[3] = { &server, &buffer, &tell };
	YY_CALL_BUILTIN(self, other, &r, 3, *(void **)ADDR(SLOT_FN_NETWORK_SEND_RAW), argSend);
	yy_clear(&r);
	yy_clear(&tell);
	yy_clear(&buffer);
	yy_clear(&server);
}

// the metadata packet is sent once after joining a server: announce the mod
static RValue *__cdecl hook_send_metadata(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_send_metadata(self, other, result, argc, args);
	lan_check_session();
	if (mod_active() == 0) return result;// server on the Internet: nothing is sent
	RValue version = { 0 };
	version.real = MOD_VERSION;
	net_send_tcp(self, other, 205, BUFFER_U8, &version);
	mod_log("mod announced to the server");
	return result;
}

static RValue *__cdecl hook_add_chat(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_active() == 0) return orig_add_chat(self, other, result, argc, args);
	for (int i = 0; i < argc; i++) {
		const char *s = yy_string(args[i]);
		if (s && s[0] == 1 && (clock_handle_message(s) || prediction_handle_message(s) || cupstate_handle_message(s))) return return_noone(result);
		if (s && s[0] == 1 && strncmp(s + 1, "CUPMS ", 6) == 0) {
			store_time(s + 7);
			return return_noone(result);
		}
		if (s && s[0] == 1 && strncmp(s + 1, "CUPMOD", 6) == 0) {
			g_server_supports_mod = 1;
			mod_log("server supports the mod: Steam uploads disabled for this session");
			return return_noone(result);
		}
	}
	return orig_add_chat(self, other, result, argc, args);
}

static RValue *__cdecl hook_init_mp(void *self, void *other, RValue *result, int argc, RValue **args) {
		g_server_supports_mod = 0;
	lan_end_session();
	time_count = 0;
	clock_reset();
	prediction_reset();
	cupstate_reset();
	return orig_init_mp(self, other, result, argc, args);
}

static RValue *__cdecl hook_reset_mp(void *self, void *other, RValue *result, int argc, RValue **args) {
		g_server_supports_mod = 0;
	lan_end_session();
	clock_reset();
	prediction_reset();
	cupstate_reset();
	return orig_reset_mp(self, other, result, argc, args);
}

static RValue *__cdecl hook_upload(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (g_server_supports_mod && mod_active()) {
		mod_log("Steam leaderboard upload blocked (cup server)");
		return return_noone(result);
	}
	return orig_upload(self, other, result, argc, args);
}

static RValue *__cdecl hook_upload_retry(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (g_server_supports_mod && mod_active()) return return_noone(result);
	return orig_upload_retry(self, other, result, argc, args);
}

static RValue *__cdecl hook_add_experience(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (g_server_supports_mod && mod_active()) {
		mod_log("Steam multiplayer experience blocked (cup server)");
		return return_noone(result);
	}
	return orig_add_experience(self, other, result, argc, args);
}

void net_install(void) {
	orig_add_entry = (GmlScript)hook_install(ADDR(GML_SCR_MENU_LEADERBOARD_ADD_ENTRY), PROLOGUE, sizeof PROLOGUE, hook_add_entry);
	orig_add_chat = (GmlScript)hook_install(ADDR(GML_SCR_MULTIPLAYER_ADD_CHAT_MESSAGE), PROLOGUE, sizeof PROLOGUE, hook_add_chat);
	orig_init_mp = (GmlScript)hook_install(ADDR(GML_SCR_INIT_MULTIPLAYER_MODE), PROLOGUE, sizeof PROLOGUE, hook_init_mp);
	orig_reset_mp = (GmlScript)hook_install(ADDR(GML_SCR_RESET_MULTIPLAYER), PROLOGUE, sizeof PROLOGUE, hook_reset_mp);
	orig_upload = (GmlScript)hook_install(ADDR(GML_SCR_STEAM_LEADERBOARD_UPLOAD), PROLOGUE, sizeof PROLOGUE, hook_upload);
	orig_upload_retry = (GmlScript)hook_install(ADDR(GML_SCR_STEAM_LEADERBOARD_UPLOAD_RETRY), PROLOGUE, sizeof PROLOGUE, hook_upload_retry);
	orig_add_experience = (GmlScript)hook_install(ADDR(GML_SCR_STEAM_ADD_MP_EXPERIENCE), PROLOGUE, sizeof PROLOGUE, hook_add_experience);
	orig_send_metadata = (GmlScript)hook_install(ADDR(GML_SCR_SEND_PLAYER_METADATA), PROLOGUE, sizeof PROLOGUE, hook_send_metadata);
}
