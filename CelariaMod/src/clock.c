// Clock synchronisation with the cup server and scheduled start.
//
// The mod regularly sends its clock (packet 206). The server answers with a hidden chat message
// "\x01CUPCLK <client time> <server reception time> <server sending time>" (NTP like: the processing time of the server
// cancels out). The sample with the smallest round trip gives the offset between the two clocks.
//
// Before a cup round, the server sends "\x01CUPSTART <server time> <round ticks>": the race starts at this time (server
// clock). The countdown of the game (3, 2, 1, Go!) is started by the ROUND_START message and lasts the "initial countdown"
// of the settings of the player. The mod forces the default countdown (3 seconds), starts it itself 3 seconds before the
// race start and keeps it aligned with the server clock: the timer of the game reaches 0 (Go!) at the same real time for
// every player, whatever his ping. Only the countdown (negative timer) is adjusted, never the run time.
#include <windows.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define OBJ_NETWORK_DATA 0x59
#define VAR_DELAY_TIME_OVER 324
#define VAR_ROUND_TIME_REMAINING 1176
#define VAR_SESSION_TIME 1250
#define VAR_START_TIME 1384// obj_timer: value of the timer after a restart (- initial countdown of the settings)
#define OBJ_MAP_LOADER 0x16
#define VAR_MLOAD_COMPLETE 895
#define OBJ_CONTROLLER 0x50
#define VAR_MAP_LOAD_ALPHA_OVERLAY 823// fade in after the map loading (the countdown of the game is displayed after it)
#define VAR_ROUND_OVER 1174

#define COUNTDOWN_TICKS 300.0// countdown of the default settings (3 seconds)
#define STEP_MS 10.0

#define SAMPLE_COUNT 16

static double sample_offset[SAMPLE_COUNT], sample_rtt[SAMPLE_COUNT];
static int sample_count, sample_next;
static double clock_offset;// server clock - local clock (ms)
static double clock_rtt = -1;// round trip of the best sample (-1 = not synchronised)
static double last_request;

static double race_start = -1;// race start of the current round (server clock), -1 = unknown
static double countdown_at = -1;// start of the countdown (server clock), -1 = done
static double start_ticks;
static int aligning;// the countdown is aligned with the server clock
static int saved_start_time_valid;
static double saved_start_time;
static int map_ready_sent;// the server knows that the map is displayed (packet 209)

double clock_local_ms(void) {
	static LARGE_INTEGER freq;
	LARGE_INTEGER now;
	if (freq.QuadPart == 0) QueryPerformanceFrequency(&freq);
	QueryPerformanceCounter(&now);
	return (double)now.QuadPart * 1000.0 / (double)freq.QuadPart;
}

int clock_synchronised(void) { return clock_rtt >= 0; }

double clock_server_ms(void) { return clock_local_ms() + clock_offset; }

double clock_rtt_ms(void) { return clock_rtt; }

double clock_race_start(void) { return race_start; }

void clock_set_race_start(double start) {
	race_start = start;// the server is the reference (players who didn't get CUPSTART: joined during the round, winners, observers)
}

static double get_var(int obj, int var) {
	RValue v = { 0 };
	YY_VAR_GET(obj, var, ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}

static void set_var(int obj, int var, double value) {
	RValue v = { 0 };
	v.real = value;
	YY_VAR_SET(obj, var, ARRAY_NONE, &v);
}

static void restore_start_time(void) {
	if (saved_start_time_valid) {
		set_var(OBJ_TIMER, VAR_START_TIME, saved_start_time);
		saved_start_time_valid = 0;
	}
}

void clock_reset(void) {
	sample_count = sample_next = 0;
	clock_rtt = -1;
	race_start = -1;
	countdown_at = -1;
		aligning = 0;
	map_ready_sent = 0;
	restore_start_time();
}

static void recompute(void) {
	int best = -1;
	for (int i = 0; i < sample_count; i++)
		if (best < 0 || sample_rtt[i] < sample_rtt[best]) best = i;
	if (best >= 0) {
		clock_offset = sample_offset[best];
		clock_rtt = sample_rtt[best];
	}
}

// "\x01CUPCLK <client ms (u32)> <server reception> <server sending>"
int clock_handle_message(const char *text) {
	if (strncmp(text, "\x01" "CUPCLK ", 8) == 0) {
		double now = clock_local_ms();
		double c, sr, ss;
		if (sscanf(text + 8, "%lf %lf %lf", &c, &sr, &ss) == 3) {
			// the client time was sent modulo 2^32 ms: rebuild it from the current local time
			double sent = now - fmod(fmod(now, 4294967296.0) - c + 4294967296.0, 4294967296.0);
			double rtt = (now - sent) - (ss - sr);
			double offset = ((sr - sent) + (ss - now)) / 2.0;
			if (rtt >= 0 && rtt < 2000) {
				sample_offset[sample_next] = offset;
				sample_rtt[sample_next] = rtt;
				sample_next = (sample_next + 1) % SAMPLE_COUNT;
				if (sample_count < SAMPLE_COUNT) sample_count++;
				recompute();
			}
		}
		return 1;
	}
	if (strncmp(text, "\x01" "CUPSTART ", 10) == 0) {
		double at, ticks;
		if (sscanf(text + 10, "%lf %lf", &at, &ticks) == 2 && clock_synchronised()) {
			race_start = at;
			countdown_at = at - COUNTDOWN_TICKS * STEP_MS;
			start_ticks = ticks;
			aligning = 1;
			// default countdown for the restart done by ROUND_START (the setting of the player is restored after the start)
			if (saved_start_time_valid == 0) {
				saved_start_time = get_var(OBJ_TIMER, VAR_START_TIME);
				saved_start_time_valid = 1;
			}
			set_var(OBJ_TIMER, VAR_START_TIME, -COUNTDOWN_TICKS);
			spectator_round_starts();
			mod_log("race start in %.1f ms (clock rtt %.1f ms)", at - clock_server_ms(), clock_rtt);
		}
		return 1;
	}
	return 0;
}

// countdown of the game aligned with the race start: timer = (server time - race start) / 10 ms while it is negative
static void align_countdown(double server_now) {
	if (get_var(OBJ_TIMER, VAR_TIME_RUNNING) < 0.5) return;
	double timer = get_var(OBJ_TIMER, VAR_AKT_TIME);
	if (timer >= 0) {
		aligning = 0;
		restore_start_time();
		mod_log("race started %.1f ms after the scheduled time", server_now - race_start);
		return;
	}
	double target = floor((server_now - race_start) / STEP_MS);
	if (target > -1) target = -1;// the last step before 0 is done by the game
	if (target < -COUNTDOWN_TICKS) target = -COUNTDOWN_TICKS;
	if (fabs(timer - target) >= 1) set_var(OBJ_TIMER, VAR_AKT_TIME, target);
}

// called once per frame
void clock_update(void *self, void *other) {
	if (g_server_supports_mod == 0 || mod_active() == 0) return;
	double now = clock_local_ms();
	// clock requests: quickly at the beginning, then every 2 seconds
	double interval = sample_count < 8 ? 250 : 2000;
	if (now - last_request >= interval) {
		last_request = now;
		RValue value = { 0 };
		value.real = fmod(floor(now), 4294967296.0);
		net_send_tcp(self, other, 206, BUFFER_U32, &value);
	}
		// map loaded and displayed (fade in finished): the server starts the round when every player is ready
	int map_ready = get_var(OBJ_MAP_LOADER, VAR_MLOAD_COMPLETE) > 0.5 && get_var(OBJ_CONTROLLER, VAR_MAP_LOAD_ALPHA_OVERLAY) < 0.001;
	if (map_ready == 0 || get_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) > 0.5) {
		map_ready_sent = 0;// map loading, or round over (the map is reloaded for the next round)
	} else if (map_ready_sent == 0) {
		map_ready_sent = 1;
		RValue value = { 0 };
		value.real = 1;
		net_send_tcp(self, other, 209, BUFFER_U8, &value);
		mod_log("map ready");
	}
	double server_now = clock_server_ms();
	if (countdown_at >= 0 && server_now >= countdown_at) {
		// the countdown of the game is only displayed after the fade in of the map
		if (get_var(OBJ_MAP_LOADER, VAR_MLOAD_COMPLETE) > 0.5 && get_var(OBJ_CONTROLLER, VAR_MAP_LOAD_ALPHA_OVERLAY) > 0) {
			set_var(OBJ_CONTROLLER, VAR_MAP_LOAD_ALPHA_OVERLAY, 0);
			mod_log("fade in of the map finished for the countdown");
		}
		// what the ROUND_START message does: the game restarts the player and starts the countdown
		set_var(OBJ_NETWORK_DATA, VAR_ROUND_TIME_REMAINING, start_ticks);
		set_var(OBJ_NETWORK_DATA, VAR_DELAY_TIME_OVER, 1);
		set_var(OBJ_NETWORK_DATA, VAR_SESSION_TIME, 0);
		countdown_at = -1;
		mod_log("countdown started (%.1f ms after the scheduled time)", server_now - (race_start - COUNTDOWN_TICKS * STEP_MS));
	}
	if (aligning && countdown_at < 0) {
		if (server_now > race_start + 1000) {
			aligning = 0;// the player didn't start (map loading...)
			restore_start_time();
		} else {
			align_countdown(server_now);
		}
	}
}
