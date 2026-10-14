// Millisecond timing (display only).
//
// Celaria simulates the player with a fixed step of 1/100 second and its timer (obj_timer.akt_time) counts these steps.
// A checkpoint/goal is detected at the end of the step where the player entered the marker. The exact moment is found
// by testing the game's own collision function at intermediate positions between the previous and the current step
// (binary search): the precise time is "n - 1 + fraction" ticks.
//
// The official time of the game is NEVER changed: records, replays, Steam leaderboards and the goal packet keep the
// integer number of steps (same value as a client without the mod). The precise time is only used for the display
// (scr_time_format) and sent to the cup server in a separate packet (204).
#include <windows.h>
#include <math.h>
#include "hook.h"
#include "yyc.h"
#include "mod.h"

static GmlScript orig_collided;
static GmlScript orig_send_goal;
static GmlScript orig_send_checkpoint;

#define GML_SCR_SEND_CHECKPOINT_PACKET 0x00866cf0

// position of the player during the previous call (one call per step)
static double last_pos[3];
static double last_step = -1;
static double last_marker = -4;

// precise times of the markers entered during the current run: official step -> precise ticks
#define MAX_MARKERS 128
static double marker_steps[MAX_MARKERS];
static double marker_precise[MAX_MARKERS];
static int marker_count;

static double timer_steps(void) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}

double ms_precise_ticks(double officialTicks) {
	for (int i = marker_count - 1; i >= 0; i--) {
		if (marker_steps[i] == officialTicks) return marker_precise[i];
	}
	return officialTicks;
}

// scr_get_collided_checkpoint_marker(x, y, z, radius): returns the touched marker instance or noone (-4)
static double call_collided(void *self, void *other, const double p[3], RValue *radius) {
	RValue a[4] = { { { 0 } } };
	for (int k = 0; k < 3; k++) a[k].real = p[k];
	a[3] = *radius;// read only copy (real)
	RValue *args[4] = { &a[0], &a[1], &a[2], &a[3] };
	RValue r = { 0 };
	orig_collided(self, other, &r, 4, args);
	double d = yy_to_real(&r);
	yy_clear(&r);
	return d;
}

static RValue *__cdecl hook_collided(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_collided(self, other, result, argc, args);
	if (argc < 4 || mod_active() == 0) return result;
	double marker = yy_to_real(result);
	double step = timer_steps();
	double pos[3];
	for (int k = 0; k < 3; k++) pos[k] = yy_to_real(args[k]);

	if (step < last_step) marker_count = 0;// new run

	int entered = marker >= 0 && marker != last_marker;
	int consecutive = last_step >= 0 && step == last_step + 1;
	double dx = pos[0] - last_pos[0], dy = pos[1] - last_pos[1], dz = pos[2] - last_pos[2];
	int small_move = dx * dx + dy * dy + dz * dz < 64.0 * 64.0;// no teleport (respawn)

	if (entered && consecutive && small_move && (args[3]->kind & 0xffffff) == KIND_REAL) {
		// binary search of the first position (between the previous and the current step) touching the marker
		double lo = 0, hi = 1, p[3];
		for (int i = 0; i < 12; i++) {
			double mid = (lo + hi) / 2;
			for (int k = 0; k < 3; k++) p[k] = last_pos[k] + (pos[k] - last_pos[k]) * mid;
			if (call_collided(self, other, p, args[3]) == marker) hi = mid;
			else lo = mid;
		}
		if (marker_count == MAX_MARKERS) marker_count = 0;
		marker_steps[marker_count] = step;
		marker_precise[marker_count] = step - 1 + hi;
		marker_count++;
		mod_log("marker %.0f entered at step %.0f fraction %.4f", marker, step, hi);
	}
	last_marker = marker;
	last_step = step;
	for (int k = 0; k < 3; k++) last_pos[k] = pos[k];
	return result;
}

// the goal packet carries the official time (1/100 second): a packet 204 with the milliseconds is sent before
// (ignored by servers without the cup mode)
static RValue *__cdecl hook_send_goal(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc >= 1 && mod_active()) {
		double ms = floor(ms_precise_ticks(yy_to_real(args[0])) * 10.0 + 1e-6);
		RValue value = { 0 };
		value.real = ms;
		net_send_tcp(self, other, 204, BUFFER_U32, &value);
		if (g_server_supports_mod) spectator_local_checkpoint(0, (long)ms);
	}
	return orig_send_goal(self, other, result, argc, args);
}

// number of checkpoints of the current run: ds_list_size(obj_controller.current_times)
#define OBJ_CONTROLLER 0x50
#define VAR_CURRENT_TIMES 300
#define SLOT_FN_DS_LIST_SIZE 0x00f38564
static int run_checkpoints(void *self, void *other) {
	RValue list = { 0 }, r = { 0 };
	YY_VAR_GET(OBJ_CONTROLLER, VAR_CURRENT_TIMES, ARRAY_NONE, &list);
	RValue *args[1] = { &list };
	YY_CALL_BUILTIN(self, other, &r, 1, *(void **)ADDR(SLOT_FN_DS_LIST_SIZE), args);
	int n = (int)yy_to_real(&r);
	yy_clear(&r);
	yy_clear(&list);
	return n;
}

// same for the checkpoint packet (202): packet 207 with the milliseconds (checkpoint ranking of the cup mod)
static RValue *__cdecl hook_send_checkpoint(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc >= 1 && g_server_supports_mod && mod_active()) {
		double ms = floor(ms_precise_ticks(yy_to_real(args[0])) * 10.0 + 1e-6);
		RValue value = { 0 };
		value.real = ms;
		net_send_tcp(self, other, 207, BUFFER_U32, &value);
		int index = run_checkpoints(self, other);
		mod_log("checkpoint %d: %.0f ms", index, ms);
		if (index > 0) {
			cup_local_checkpoint(index, (long)ms);
			spectator_local_checkpoint(index, (long)ms);
		}
	}
	return orig_send_checkpoint(self, other, result, argc, args);
}

void ms_timing_install(void) {
	orig_send_checkpoint = (GmlScript)hook_install(ADDR(GML_SCR_SEND_CHECKPOINT_PACKET), PROLOGUE, sizeof PROLOGUE, hook_send_checkpoint);
	orig_collided = (GmlScript)hook_install(ADDR(GML_SCR_GET_COLLIDED_CHECKPOINT_MARKER), PROLOGUE, sizeof PROLOGUE, hook_collided);
	orig_send_goal = (GmlScript)hook_install(ADDR(GML_SCR_SEND_GOAL_PACKET), PROLOGUE, sizeof PROLOGUE, hook_send_goal);
}
