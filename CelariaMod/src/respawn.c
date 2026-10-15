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

// Warmup: after the goal, the run restarts by itself after WARMUP_RESTART_MS if the player didn't press respawn / restart
// (the finish screen of the game restarts the map with these keys: step_controller -> bev_map_restart). Not while
// spectating, not for an observer, not at the end of the round.
#define OBJ_CONTROLLER 0x50
#define OBJ_NETWORK_DATA 0x59
#define VAR_MAP_FINISHED 820// obj_controller: the local player reached the goal
#define VAR_MULTIPLAYER 965
#define VAR_ROUND_OVER 1174// obj_network_data
#define WARMUP_RESTART_MS 5000

static double finish_since;// local time (ms) of the goal of a warmup run, 0 = none

static double ctrl_var(int object, int var) {
	RValue v = { 0 };
	YY_VAR_GET(object, var, ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}

// remaining time before the automatic restart of the warmup run (ms), -1 if none
double respawn_warmup_remaining_ms(void) {
	if (finish_since <= 0) return -1;
	double r = WARMUP_RESTART_MS - (clock_local_ms() - finish_since);
	return r < 0 ? 0 : r;
}

// step of the game (100 Hz, step_controller)
void respawn_step(void *self, void *other) {
	int index, count;
	double remaining;
	int waiting = mod_active() && g_server_supports_mod && ctrl_var(OBJ_CONTROLLER, VAR_MULTIPLAYER) > 0.5
		&& cup_warmup(&index, &count, &remaining) && ctrl_var(OBJ_CONTROLLER, VAR_MAP_FINISHED) > 0.5
		&& ctrl_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) < 0.5 && spectator_active() == 0 && cup_state(cup_local_id()) != 'V';
	if (waiting == 0) {
		finish_since = 0;
		return;
	}
	double now = clock_local_ms();
	if (finish_since <= 0) {
		finish_since = now;
	} else if (now - finish_since >= WARMUP_RESTART_MS && orig_map_restart) {
		finish_since = 0;
		mod_log("warmup: automatic restart after the goal");
		RValue r = { 0 };
		orig_map_restart(self, other, &r, 0, NULL);// what the restart key does on the finish screen
		yy_clear(&r);
	}
}

void respawn_install(void) {
	orig_map_restart = (GmlScript)hook_install(ADDR(GML_BEV_MAP_RESTART), PROLOGUE, sizeof PROLOGUE, hook_map_restart);
}
