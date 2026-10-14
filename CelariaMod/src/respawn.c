// Respawn and restart keys (cup server).
//
// In scr_player_step, the respawn key brings the player back to his last checkpoint (scr_player_respawn, timer unchanged).
// Without checkpoint it restarts the map (bev_map_restart: the timer restarts) and sends the packet 201. With the mod, on a
// cup server, the respawn key without checkpoint brings the player back to the start WITHOUT resetting the timer
// (scr_player_respawn_forcestart, the respawn used by the speedrun mode): like Trackmania, the respawn key never resets the
// timer. The packet 201 is still sent: the server decides (setting cup_respawnAtStartDNF, off by default).
// The restart key (complete reset of the run) is unchanged and the mod tells the server (packet 213): DNF for the round
// (setting cup_forceRestartDNF).
//
// Both modes: when the timer of the run restarts (new run), the server is told (packet 212, checkpoint ranking of the time
// attack mode).
#include <windows.h>
#include "hook.h"
#include "mod.h"

#define GML_BEV_MAP_RESTART 0x005791f0
#define GML_SCR_PLAYER_RESPAWN_FORCESTART 0x0062fd80
#define RET_RESPAWN_KEY 0x0076b96a// scr_player_step: return address of bev_map_restart (respawn key, no checkpoint)
#define RET_RESTART_KEY 0x0076c0ac// scr_player_step: return address of bev_map_restart (restart key)

static GmlScript orig_map_restart;

static RValue *__cdecl hook_map_restart(void *self, void *other, RValue *result, int argc, RValue **args) {
	uintptr_t ret = (uintptr_t)__builtin_return_address(0);
	if (mod_active() && g_server_supports_mod && cup_mode()) {
		if (ret == ADDR(RET_RESPAWN_KEY)) {
			mod_log("respawn key without checkpoint: back to the start, the timer continues");
			RValue r = { 0 };
			((GmlScript)ADDR(GML_SCR_PLAYER_RESPAWN_FORCESTART))(self, other, &r, 0, NULL);
			yy_clear(&r);
			yy_set_real(result, 0);
			return result;
		}
		if (ret == ADDR(RET_RESTART_KEY)) {
			mod_log("restart key: complete reset of the run (DNF)");
			RValue v = { 0 };
			v.real = 1;
			net_send_tcp(self, other, 213, BUFFER_U8, &v);
		}
	}
	return orig_map_restart(self, other, result, argc, args);
}

// once per frame: the timer of the run went back (restart) -> packet 212
void respawn_update(void *self, void *other) {
	static double last = -1e9;
	RValue v = { 0 };
	YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
	double t = yy_to_real(&v);
	yy_clear(&v);
	if (mod_active() && g_server_supports_mod && cup_mode() == 0 && t < last - 20 && last > 0) {
		RValue one = { 0 };
		one.real = 1;
		net_send_tcp(self, other, 212, BUFFER_U8, &one);
		mod_log("run restarted (timer %.0f -> %.0f)", last, t);
	}
	last = t;
}

void respawn_install(void) {
	orig_map_restart = (GmlScript)hook_install(ADDR(GML_BEV_MAP_RESTART), PROLOGUE, sizeof PROLOGUE, hook_map_restart);
}
