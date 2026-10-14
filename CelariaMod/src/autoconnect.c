// Connection to the server chosen in the Celaria Lanppart3 launcher.
//
// The launcher starts the game with the environment variable LP3_CONNECT=<address>:<port>. The mod does what the player
// would do, with the scripts of the buttons, in the step event of the game (like a click):
//   1. main menu shown (menu_main), 3 seconds later: the Multiplayer button (bev_multiplayer: destroys the daily featured
//      preview loaded by the main menu, menu transition);
//   2. multiplayer menu shown (menu_multiplayer), 1 second later: what the Direct-IP button does once the address is
//      entered (bev_accept_ip_address): server address and ports of obj_network_handler, the menu is deleted and
//      scr_start_multiplayer connects.
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define GML_MENU_MAIN 0x004e0a20
#define GML_MENU_MULTIPLAYER 0x0052cbf0
#define GML_BEV_MULTIPLAYER 0x0053a4b0// behaviour of the Multiplayer button of the main menu
#define GML_STEP_CONTROLLER 0x0075b100// called by the step event of obj_engine (100 Hz)
#define GML_SCR_START_MULTIPLAYER 0x0065c7f0
#define GML_SCR_DESTROY_MAPLOCKSCREEN 0x008cbf10
#define GML_SCR_DELETE_ALL_GUI_ELEMENTS 0x004da340
#define OBJ_NETWORK_HANDLER 0x58
#define VAR_SERVER_ADRESS 1237
#define VAR_SERVER_PORT 1239
#define VAR_UDP_PORT 1530

static GmlScript orig_menu_main, orig_menu_multiplayer, orig_step_controller;
static char address[128];
static int port;
// 0 = nothing to do, 1 = waiting for the main menu, 2 = main menu shown, 3 = waiting for the multiplayer menu,
// 4 = multiplayer menu shown, 5 = done
static int state;
static int steps;

static RValue *__cdecl hook_menu_main(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_menu_main(self, other, result, argc, args);
	if (state == 1) {
		state = 2;
		steps = 0;
	}
	return result;
}

static RValue *__cdecl hook_menu_multiplayer(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_menu_multiplayer(self, other, result, argc, args);
	if (state == 3) {
		state = 4;
		steps = 0;
	}
	return result;
}

static void call_script(uintptr_t script, void *self, void *other) {
	RValue r = { 0 };
	((GmlScript)ADDR(script))(self, other, &r, 0, NULL);
	yy_clear(&r);
}

static void connect_now(void *self, void *other) {
	RValue v = { 0 };
	YY_CREATE_STRING(&v, address);
	YY_VAR_SET(OBJ_NETWORK_HANDLER, VAR_SERVER_ADRESS, ARRAY_NONE, &v);
	yy_clear(&v);
	v.real = port;
	YY_VAR_SET(OBJ_NETWORK_HANDLER, VAR_SERVER_PORT, ARRAY_NONE, &v);
	YY_VAR_SET(OBJ_NETWORK_HANDLER, VAR_UDP_PORT, ARRAY_NONE, &v);
	call_script(GML_SCR_DESTROY_MAPLOCKSCREEN, self, other);
	call_script(GML_SCR_DELETE_ALL_GUI_ELEMENTS, self, other);
	call_script(GML_SCR_START_MULTIPLAYER, self, other);
	mod_log("launcher: connection to %s:%d", address, port);
}

static RValue *__cdecl hook_step_controller(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_step_controller(self, other, result, argc, args);
	if (state == 2 && ++steps >= 300) {
		state = 3;
		mod_log("launcher: Multiplayer menu");
		call_script(GML_BEV_MULTIPLAYER, self, other);
	} else if (state == 4 && ++steps >= 100) {
		state = 5;
		connect_now(self, other);
	}
	return result;
}

void autoconnect_update(void *self, void *other) {
	(void)self;
	(void)other;
}

void autoconnect_install(void) {
	char value[160];
	DWORD n = GetEnvironmentVariableA("LP3_CONNECT", value, sizeof value);
	if (n == 0 || n >= sizeof value) return;
	SetEnvironmentVariableA("LP3_CONNECT", NULL);// not inherited by other processes
	char *colon = strrchr(value, ':');
	if (colon == NULL) return;
	*colon = 0;
	port = atoi(colon + 1);
	snprintf(address, sizeof address, "%s", value);
	if (port <= 0 || port > 65535 || address[0] == 0) return;
	orig_menu_main = (GmlScript)hook_install(ADDR(GML_MENU_MAIN), PROLOGUE, sizeof PROLOGUE, hook_menu_main);
	orig_menu_multiplayer = (GmlScript)hook_install(ADDR(GML_MENU_MULTIPLAYER), PROLOGUE, sizeof PROLOGUE, hook_menu_multiplayer);
	orig_step_controller = (GmlScript)hook_install(ADDR(GML_STEP_CONTROLLER), PROLOGUE, sizeof PROLOGUE, hook_step_controller);
	if (orig_menu_main && orig_menu_multiplayer && orig_step_controller) state = 1;
	mod_log("launcher: connection to %s:%d after the main menu", address, port);
}
