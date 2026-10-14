// "Records" tab of the leaderboard: server records of the current map (to the millisecond), on a server supporting the mod.
//
// On such a server, the "Friends" tab of the multiplayer leaderboard becomes "Records". Clicking it shows the leaderboard
// of the server ("Server" tab path) but the request is sent with the type 2, answered by the cup server with its records.
// Official servers and single player are not affected.
#include <windows.h>
#include <string.h>
#include <stdio.h>
#include "hook.h"
#include "mod.h"

#define GML_SCR_CREATE_BUTTON 0x004ceb00
#define GML_BEV_LEADERBOARD_SET_FRIENDS 0x008ec6d0
#define GML_BEV_LEADERBOARD_SET_SERVER 0x008e8b10
#define GML_SCR_SEND_LEADERBOARD_REQUEST 0x00868890
#define GML_SCR_MENU_LEADERBOARD_DESTROY 0x008f0060
#define OBJ_CONTROLLER 0x50
#define VAR_MULTIPLAYER 965
#define REQUEST_SERVER_RECORDS 2

static GmlScript orig_create_button;
static GmlScript orig_set_friends;
static GmlScript orig_set_server;
static GmlScript orig_send_request;
static GmlScript orig_leaderboard_destroy;

int g_records_tab;// the "Records" tab is selected

static int mod_server_session(void) {
	return g_server_supports_mod && mod_active();
}

// scr_create_button(..., text, ...): "Friends" -> "Records"
static RValue *__cdecl hook_create_button(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_server_session()) {
		for (int i = 0; i < argc; i++) {
			const char *s = yy_string(args[i]);
			if (s && strcmp(s, "Friends") == 0) {
				yy_set_string(args[i], "Records");
				break;
			}
		}
	}
	return orig_create_button(self, other, result, argc, args);
}

static RValue *__cdecl hook_set_friends(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_server_session() == 0) return orig_set_friends(self, other, result, argc, args);
	g_records_tab = 1;
	mod_log("Records tab selected");
	return orig_set_server(self, other, result, argc, args);// leaderboard of the server, request type 2
}

static RValue *__cdecl hook_set_server(void *self, void *other, RValue *result, int argc, RValue **args) {
	g_records_tab = 0;
	return orig_set_server(self, other, result, argc, args);
}

// scr_send_leaderboard_request_packet(type, offset, size, ownEntry): type 0 (current leaderboard) -> 2 (server records)
static RValue *__cdecl hook_send_request(void *self, void *other, RValue *result, int argc, RValue **args) {
	static int logged;
	if (logged == 0 && argc > 0) {
		logged = 1;
		for (int i = 0; i < argc; i++) mod_log("  leaderboard request arg %d kind %d value %.1f", i, args[i]->kind & 0xffffff, yy_to_real(args[i]));
	}
	if (g_records_tab && mod_server_session() && argc >= 1 && yy_to_real(args[0]) == 0) {
		RValue type = { 0 };
		type.real = REQUEST_SERVER_RECORDS;
		RValue *copy[8];
		for (int i = 0; i < argc && i < 8; i++) copy[i] = args[i];
		copy[0] = &type;
		return orig_send_request(self, other, result, argc, copy);
	}
	return orig_send_request(self, other, result, argc, args);
}

static RValue *__cdecl hook_leaderboard_destroy(void *self, void *other, RValue *result, int argc, RValue **args) {
	g_records_tab = 0;
	return orig_leaderboard_destroy(self, other, result, argc, args);
}

void records_tab_install(void) {
	orig_create_button = (GmlScript)hook_install(ADDR(GML_SCR_CREATE_BUTTON), PROLOGUE, sizeof PROLOGUE, hook_create_button);
	orig_set_friends = (GmlScript)hook_install(ADDR(GML_BEV_LEADERBOARD_SET_FRIENDS), PROLOGUE, sizeof PROLOGUE, hook_set_friends);
	orig_set_server = (GmlScript)hook_install(ADDR(GML_BEV_LEADERBOARD_SET_SERVER), PROLOGUE, sizeof PROLOGUE, hook_set_server);
	orig_send_request = (GmlScript)hook_install(ADDR(GML_SCR_SEND_LEADERBOARD_REQUEST), PROLOGUE, sizeof PROLOGUE, hook_send_request);
	orig_leaderboard_destroy = (GmlScript)hook_install(ADDR(GML_SCR_MENU_LEADERBOARD_DESTROY), PROLOGUE, sizeof PROLOGUE, hook_leaderboard_destroy);
}
