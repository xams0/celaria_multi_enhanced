// Prediction of the other players (cup server): the other players are displayed at the same race time as the local player.
//
// Race time stamps: the mod appends "R" + u32 race time (ms) to the position packet of the game (the server reads it, the
// official server ignores it). The cup server sends the race time of every position after the end of the data read by the
// game (estimated with the ping for the players without the mod). When a position is applied, the mod moves the player
// forward by velocity * (local race time - race time of the position). Without time stamps (no round running), the delay
// estimated by the server ("\x01CUPLAG <id>:<ms> ...") is used.
//
// Display: the game draws the other players at model_x/y/z, smoothed towards x/y/z by 8 % per step (100 steps per second):
// the displayed position is about 115 ms behind. The mod draws them at x/y/z + a correction which only smooths the jumps
// of the network updates (it decays in a few steps): no delay.
#include <windows.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define GML_SCR_READ_APPLY_NETWORK_COORDINATES 0x00860c30
#define GML_STEP_NETWORK_PLAYER 0x00791e40
#define OBJ_NETWORK_PLAYER 0x69
#define VAR_NETWORK_PLAYER_ID 981
#define VAR_LAST_UDP_UPDATE_NUMBER 733
#define VAR_MOVEMENT_X 960
#define VAR_MOVEMENT_Y 961
#define VAR_MOVEMENT_Z 962
#define VAR_MODEL_X 924
#define VAR_MODEL_Y 925
#define VAR_MODEL_Z 926
#define STEP_MS 10.0// engine step (step_networkPlayer runs at 100 Hz)
#define MAX_PREDICTION_MS 300.0
#define MIN_PREDICTION_MS -150.0
#define CORRECTION_DECAY 0.88// per step (small network corrections fade over ~8 steps)
#define MAX_CORRECTION 4.0// larger jumps (respawn) aren't smoothed

#define SLOT_FN_BUFFER_GET_ADDRESS 0x00f3851c
#define SLOT_FN_BUFFER_GET_SIZE 0x00f385c4
#define SLOT_FN_BUFFER_RESIZE 0x00f38a4c
#define SLOT_FN_NETWORK_SEND_UDP_RAW 0x00f38b7c
#define FUNCTION_TABLE 0x031b3104// pointer to the table of the native functions (80 bytes per function, code at +0x40)
#define POSITION_PACKET_SIZE 29
#define EXTENSION_SIZE 9// "R" + u32 race time + u16 camera yaw + s16 camera pitch
#define OBJ_CAMERA 0
#define VAR_MOUSE_DIRECTION 934// yaw of the camera (world yaw = - mouse_direction, degrees)
#define VAR_Z_ANGLE 1751// pitch of the camera (degrees, up > 0)

typedef int(__cdecl *FnWithNew)(void *iterator, void **pSelf, void **pOther, int object);
typedef char(__cdecl *FnWithNext)(void *iterator, void **pSelf, void **pOther);
typedef void(__cdecl *FnWithDelete)(void *iterator, void **pSelf, void **pOther);
#define YY_WITH_NEW ((FnWithNew)ADDR(0x00b9d720))
#define YY_WITH_NEXT ((FnWithNext)ADDR(0x00b8fd20))
#define YY_WITH_DELETE ((FnWithDelete)ADDR(0x00b8fd50))

typedef void(__cdecl *NativeFn)(RValue *result, void *self, void *other, int argc, RValue *args);

static GmlScript orig_read_apply;
static GmlScript orig_step_network_player;
static NativeFn orig_send_udp;
static double lag_ms[256];// estimated delay of every player (server id)
static int lag_known;

typedef struct {
	int valid;
	double x, y, z;// correction: displayed position - x/y/z
} Correction;
static Correction corrections[256];

// Camera of the other players (mod): world yaw / pitch in degrees, received 30 to 60 times per second.
// The first person spectator view must turn smoothly at every frame: the samples are kept with their time (race time of
// the sender, or the local reception time if he doesn't race) and the camera is interpolated a little in the past
// (LOOK_DELAY_MS), on a playout clock aligned with the reception times (the jitter of the network is absorbed).
#define LOOK_SAMPLES 24
#define LOOK_DELAY_MS 55.0
typedef struct {
	double key;// race time of the sender (ms) or local reception time
	double yaw, pitch;
} LookSample;
typedef struct {
	LookSample s[LOOK_SAMPLES];
	int count, head;// ring buffer: head = next write
	int race_keys;// keys are race times
	double offset;// key - local time of the fastest samples (playout clock)
	double last_arrival;
} LookTrack;
static LookTrack looks[256];
static int step_count[256];// steps recorded for the interpolation of the displayed position (see below)
// race time of the last position of every player (time stamp of the mod) and its local reception time
static double stamp_time[256], stamp_arrival[256];

static void look_add(int id, double key, int race_key, double yaw, double pitch, double arrival) {
	LookTrack *t = &looks[id];
	LookSample *last = t->count ? &t->s[(t->head + LOOK_SAMPLES - 1) % LOOK_SAMPLES] : NULL;
	if (last && race_key == t->race_keys && key == last->key) return;// same position relayed again
	// new run, new round or other kind of key: the buffer restarts
	if (last == NULL || race_key != t->race_keys || key < last->key - 50 || key > last->key + 2000 || arrival - t->last_arrival > 1000) {
		t->count = t->head = 0;
		t->race_keys = race_key;
		t->offset = key - arrival;
		last = NULL;
	}
	// keep the yaw continuous (no jump between 359 and 0 degrees)
	if (last) {
		while (yaw - last->yaw > 180) yaw -= 360;
		while (yaw - last->yaw < -180) yaw += 360;
	}
	t->s[t->head] = (LookSample){ key, yaw, pitch };
	t->head = (t->head + 1) % LOOK_SAMPLES;
	if (t->count < LOOK_SAMPLES) t->count++;
	double o = key - arrival;
	if (o > t->offset) t->offset = o;// a faster sample: the playout clock moves forward at once
	else t->offset += (o - t->offset) * 0.02;// slowly follows a longer delay (or the drift of the clocks)
	t->last_arrival = arrival;
}

int prediction_look(int id, double *yaw, double *pitch) {
	if (id < 0 || id > 255) return 0;
	LookTrack *t = &looks[id];
	if (t->count == 0 || clock_local_ms() - t->last_arrival > 1500) return 0;
	double key = clock_local_ms() + t->offset - LOOK_DELAY_MS;
	const LookSample *newer = &t->s[(t->head + LOOK_SAMPLES - 1) % LOOK_SAMPLES];
	if (key >= newer->key || t->count == 1) {
		*yaw = newer->yaw;
		*pitch = newer->pitch;
		return 1;
	}
	for (int i = 2; i <= t->count; i++) {
		const LookSample *older = &t->s[(t->head + LOOK_SAMPLES - i) % LOOK_SAMPLES];
		if (older->key <= key) {
			double f = newer->key > older->key ? (key - older->key) / (newer->key - older->key) : 1;
			*yaw = older->yaw + (newer->yaw - older->yaw) * f;
			*pitch = older->pitch + (newer->pitch - older->pitch) * f;
			return 1;
		}
		newer = older;
	}
	*yaw = newer->yaw;// older than every sample
	*pitch = newer->pitch;
	return 1;
}

double prediction_race_time(int id) {
	if (id < 0 || id > 255 || stamp_arrival[id] <= 0) return -1;
	double age = clock_local_ms() - stamp_arrival[id];
	if (age > 1000) return -1;
	return stamp_time[id] + age;
}

void prediction_reset(void) {
	memset(lag_ms, 0, sizeof lag_ms);
	lag_known = 0;
	memset(corrections, 0, sizeof corrections);
	memset(looks, 0, sizeof looks);
	memset(step_count, 0, sizeof step_count);
	memset(stamp_arrival, 0, sizeof stamp_arrival);
}

// "\x01CUPLAG <id>:<ms> <id>:<ms> ..."
int prediction_handle_message(const char *text) {
	if (strncmp(text, "\x01" "CUPLAG", 7) != 0) return 0;
	const char *p = text + 7;
	int id, n;
	double ms;
	while (sscanf(p, " %d:%lf%n", &id, &ms, &n) == 2) {
		if (id >= 0 && id < 256) lag_ms[id] = ms;
		p += n;
	}
	lag_known = 1;
	return 1;
}

static int active(void) { return g_server_supports_mod && mod_active(); }

// ---------------------------------------------------------------- buffers

static RValue builtin1(void *self, void *other, uintptr_t slot, RValue *arg) {
	RValue r = { 0 };
	RValue *args[1] = { arg };
	YY_CALL_BUILTIN(self, other, &r, 1, *(void **)ADDR(slot), args);
	return r;
}

static uint8_t *buffer_address(void *self, void *other, RValue *buffer, int *size) {
	RValue r = builtin1(self, other, SLOT_FN_BUFFER_GET_ADDRESS, buffer);
	uint8_t *p = (r.kind & 0xffffff) == KIND_REAL ? (uint8_t *)(uintptr_t)r.real : *(uint8_t **)&r;
	yy_clear(&r);
	r = builtin1(self, other, SLOT_FN_BUFFER_GET_SIZE, buffer);
	*size = (int)yy_to_real(&r);
	yy_clear(&r);
	return p;
}

// race time of the current step of the local player (ms), or the time since the race start; -1 if unknown
static double race_now_ms(void) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_TIMER, VAR_TIME_RUNNING, ARRAY_NONE, &v);
	int running = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
	double steps = yy_to_real(&v);
	yy_clear(&v);
	if (running && steps > 0) return steps * STEP_MS;
	if (clock_race_start() > 0 && cup_round_running()) return clock_server_ms() - clock_race_start();
	return -1;
}

// network_send_udp_raw(socket, url, port, buffer, size): the race time is appended to the position packet
static void __cdecl hook_send_udp(RValue *result, void *self, void *other, int argc, RValue *args) {
	if (argc >= 5 && active()) {
		int size = 0;
		uint8_t *p = buffer_address(self, other, &args[3], &size);
		int length = (int)yy_to_real(&args[4]);
		static int logged;
		if (logged == 0 && p && p[5] == 1) {
			logged = 1;
			mod_log("UDP position packet: buffer size %d, sent size %d", size, length);
		}
		if (p && size < POSITION_PACKET_SIZE + EXTENSION_SIZE && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
			// the buffer of the game is a bit too small for the race time: enlarged once
			RValue a[2] = { args[3], { { 0 } } }, r = { 0 };
			a[1].real = POSITION_PACKET_SIZE + EXTENSION_SIZE;
			RValue *ra[2] = { &a[0], &a[1] };
			YY_CALL_BUILTIN(self, other, &r, 2, *(void **)ADDR(SLOT_FN_BUFFER_RESIZE), ra);
			yy_clear(&r);
			p = buffer_address(self, other, &args[3], &size);
			mod_log("UDP buffer resized to %d", size);
		}
		if (p && size >= POSITION_PACKET_SIZE + EXTENSION_SIZE && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
			if (length < POSITION_PACKET_SIZE + EXTENSION_SIZE) yy_set_real(&args[4], POSITION_PACKET_SIZE + EXTENSION_SIZE);
			// camera of the local player (first person spectator view of the other players)
			RValue c = { 0 };
			YY_VAR_GET(OBJ_CAMERA, VAR_MOUSE_DIRECTION, ARRAY_NONE, &c);
			double yaw = fmod(-yy_to_real(&c), 360.0);
			yy_clear(&c);
			if (yaw < 0) yaw += 360.0;
			YY_VAR_GET(OBJ_CAMERA, VAR_Z_ANGLE, ARRAY_NONE, &c);
			double pitch = yy_to_real(&c);
			yy_clear(&c);
			uint16_t yaw16 = (uint16_t)(yaw / 360.0 * 65535.0);
			int16_t pitch16 = (int16_t)(pitch < -90 ? -9000 : pitch > 90 ? 9000 : pitch * 100.0);
			memcpy(p + POSITION_PACKET_SIZE + 5, &yaw16, 2);
			memcpy(p + POSITION_PACKET_SIZE + 7, &pitch16, 2);
			RValue v = { 0 };
			YY_VAR_GET(OBJ_TIMER, VAR_TIME_RUNNING, ARRAY_NONE, &v);
			int running = yy_to_real(&v) > 0.5;
			yy_clear(&v);
			YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
			double steps = yy_to_real(&v);
			yy_clear(&v);
			if (running && steps >= 0) {
				uint32_t ms = (uint32_t)(steps * STEP_MS);
				p[POSITION_PACKET_SIZE] = 'R';
				memcpy(p + POSITION_PACKET_SIZE + 1, &ms, 4);
			} else {
				p[POSITION_PACKET_SIZE] = 'N';
			}
		}
	}
	orig_send_udp(result, self, other, argc, args);
}

// race times at the end of a position packet of the server: [id u8, race time u32, yaw u16, pitch s16] * count,
// count u8, "SX" (older servers: [id u8, race time u32] * count, count u8, "RX")
static int read_race_times(void *self, void *other, RValue *buffer, int *ids, double *times, int max) {
	int size = 0;
	uint8_t *p = buffer_address(self, other, buffer, &size);
	if (p == NULL || size < 3 || p[size - 1] != 'X' || (p[size - 2] != 'R' && p[size - 2] != 'S')) return 0;
	int entry = p[size - 2] == 'S' ? 9 : 5;
	int count = p[size - 3];
	int start = size - 3 - count * entry;
	if (count > max || start < 8) return 0;
	double now = clock_local_ms();
	for (int i = 0; i < count; i++) {
		const uint8_t *e = p + start + i * entry;
		uint32_t ms;
		ids[i] = e[0];
		memcpy(&ms, e + 1, 4);
		times[i] = ms == 0xFFFFFFFFu ? -1 : (double)ms;// -1: the player doesn't race
		if (times[i] >= 0 && ids[i] < 256) {
			stamp_time[ids[i]] = times[i];
			stamp_arrival[ids[i]] = now;
		}
		if (entry == 9 && ids[i] < 256) {
			uint16_t yaw16;
			int16_t pitch16;
			memcpy(&yaw16, e + 5, 2);
			memcpy(&pitch16, e + 7, 2);
			if (yaw16 != 0xFFFF)
				look_add(ids[i], times[i] >= 0 ? times[i] : now, times[i] >= 0, yaw16 / 65535.0 * 360.0, pitch16 / 100.0, now);
		}
	}
	return count;
}

// ---------------------------------------------------------------- positions

#define MAX_TRACKED 64
static void *tracked_instance[MAX_TRACKED];
static double tracked_update[MAX_TRACKED];
static double tracked_model[MAX_TRACKED][3];

static RValue *__cdecl hook_read_apply(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (active() == 0 || argc < 1) return orig_read_apply(self, other, result, argc, args);

	// update numbers and displayed positions before the packet
	uint8_t iterator[1024];
	void *s = self, *o = other;
	int count = 0;
	if (YY_WITH_NEW(iterator, &s, &o, OBJ_NETWORK_PLAYER) > 0) {
		do {
			if (count < MAX_TRACKED) {
				tracked_instance[count] = s;
				tracked_update[count] = yy_to_real(yy_self_var(s, VAR_LAST_UDP_UPDATE_NUMBER));
				tracked_model[count][0] = yy_to_real(yy_self_var(s, VAR_MODEL_X));
				tracked_model[count][1] = yy_to_real(yy_self_var(s, VAR_MODEL_Y));
				tracked_model[count][2] = yy_to_real(yy_self_var(s, VAR_MODEL_Z));
				count++;
			}
		} while (YY_WITH_NEXT(iterator, &s, &o));
	}
	YY_WITH_DELETE(iterator, &s, &o);

	RValue buffer = *args[0];// read only copy (number)
	orig_read_apply(self, other, result, argc, args);

	int ids[64];
	double times[64];
	int stamps = read_race_times(self, other, &buffer, ids, times, 64);
	double now = race_now_ms();

	// players updated by this packet: moved forward to the local race time
	int idA = *(int *)ADDR(SLOT_BUILTIN_VAR_A), idB = *(int *)ADDR(SLOT_BUILTIN_VAR_B);
	for (int i = 0; i < count; i++) {
		void *inst = tracked_instance[i];
		if (yy_to_real(yy_self_var(inst, VAR_LAST_UDP_UPDATE_NUMBER)) == tracked_update[i]) continue;
		int id = (int)yy_to_real(yy_self_var(inst, VAR_NETWORK_PLAYER_ID));
		if (id < 0 || id > 255) continue;
		double ms = lag_known ? lag_ms[id] : 0;
		int stamped = 0;
		for (int k = 0; k < stamps; k++)
			if (ids[k] == id && now >= 0 && times[k] >= 0 && cup_mode()) {// time attack: the players don't race together
				ms = now - times[k];
				stamped = 1;
			}
		static double last_log;
		if (clock_local_ms() - last_log > 5000) {
			last_log = clock_local_ms();
			mod_log("prediction: player %d %.0f ms (%s)", id, ms, stamped ? "race time" : "estimated delay");
		}
		if (ms > MAX_PREDICTION_MS) ms = MAX_PREDICTION_MS;
		if (ms < MIN_PREDICTION_MS) ms = MIN_PREDICTION_MS;
		double steps = ms / STEP_MS;
		double pos[3];
		RValue v = { 0 };
		YY_BUILTIN_GET(inst, idA, ARRAY_NONE, &v);
		pos[0] = yy_to_real(&v) + yy_to_real(yy_self_var(inst, VAR_MOVEMENT_X)) * steps;
		v.real = pos[0];
		v.kind = KIND_REAL;
		YY_BUILTIN_SET(inst, idA, ARRAY_NONE, &v);
		YY_BUILTIN_GET(inst, idB, ARRAY_NONE, &v);
		pos[1] = yy_to_real(&v) + yy_to_real(yy_self_var(inst, VAR_MOVEMENT_Y)) * steps;
		v.real = pos[1];
		v.kind = KIND_REAL;
		YY_BUILTIN_SET(inst, idB, ARRAY_NONE, &v);
		RValue *z = yy_self_var(inst, VAR_Z);
		pos[2] = yy_to_real(z) + yy_to_real(yy_self_var(inst, VAR_MOVEMENT_Z)) * steps;
		yy_set_real(z, pos[2]);
		// the displayed position continues from where it was (the jump is smoothed)
		Correction *c = &corrections[id];
		c->x = tracked_model[i][0] - pos[0];
		c->y = tracked_model[i][1] - pos[1];
		c->z = tracked_model[i][2] - pos[2];
		if (fabs(c->x) > MAX_CORRECTION || fabs(c->y) > MAX_CORRECTION || fabs(c->z) > MAX_CORRECTION) c->x = c->y = c->z = 0;
		c->valid = 1;
	}
	return result;
}

// Displayed position of the other players at every frame. The game moves them once per engine step (100 Hz) and draws
// them at this position, while the local player and the camera are interpolated between two steps at every frame: at
// 144 fps a close opponent judders. The mod keeps the positions of the last two steps and draws the other players
// between them, with the interpolation factor of the game (get_render_delta), like the local player.
#define GML_GET_RENDER_DELTA 0x004cc000
static double step_prev[256][3], step_cur[256][3];
static void *interp_instance[MAX_TRACKED];
static int interp_id[MAX_TRACKED];
static int interp_count = -1;// -1: no interpolation applied

static void record_step(void *self, int id) {
	memcpy(step_prev[id], step_cur[id], sizeof step_cur[id]);
	step_cur[id][0] = yy_to_real(yy_self_var(self, VAR_MODEL_X));
	step_cur[id][1] = yy_to_real(yy_self_var(self, VAR_MODEL_Y));
	step_cur[id][2] = yy_to_real(yy_self_var(self, VAR_MODEL_Z));
	if (step_count[id] < 2) step_count[id]++;
}

void prediction_interpolate_begin(void *self, void *other) {
	if (active() == 0 || interp_count >= 0) return;
	RValue r = { 0 };
	((GmlScript)ADDR(GML_GET_RENDER_DELTA))(self, other, &r, 0, NULL);
	double alpha = yy_to_real(&r);
	yy_clear(&r);
	if (alpha < 0) alpha = 0;
	if (alpha > 1) alpha = 1;
	uint8_t iterator[1024];
	void *s = self, *o = other;
	interp_count = 0;
	if (YY_WITH_NEW(iterator, &s, &o, OBJ_NETWORK_PLAYER) > 0) {
		do {
			int id = (int)yy_to_real(yy_self_var(s, VAR_NETWORK_PLAYER_ID));
			if (id < 0 || id > 255 || step_count[id] < 2 || interp_count >= MAX_TRACKED) continue;
			double *a = step_prev[id], *b = step_cur[id];
			// the model must still be at the position of the last step (no teleport / respawn in between)
			if (fabs(yy_to_real(yy_self_var(s, VAR_MODEL_X)) - b[0]) > 0.001 || fabs(b[0] - a[0]) > 6 || fabs(b[1] - a[1]) > 6
				|| fabs(b[2] - a[2]) > 6) continue;
			yy_set_real(yy_self_var(s, VAR_MODEL_X), a[0] + (b[0] - a[0]) * alpha);
			yy_set_real(yy_self_var(s, VAR_MODEL_Y), a[1] + (b[1] - a[1]) * alpha);
			yy_set_real(yy_self_var(s, VAR_MODEL_Z), a[2] + (b[2] - a[2]) * alpha);
			interp_instance[interp_count] = s;
			interp_id[interp_count++] = id;
		} while (YY_WITH_NEXT(iterator, &s, &o));
	}
	YY_WITH_DELETE(iterator, &s, &o);
}

void prediction_interpolate_end(void) {
	for (int i = 0; i < interp_count; i++) {
		double *b = step_cur[interp_id[i]];
		yy_set_real(yy_self_var(interp_instance[i], VAR_MODEL_X), b[0]);
		yy_set_real(yy_self_var(interp_instance[i], VAR_MODEL_Y), b[1]);
		yy_set_real(yy_self_var(interp_instance[i], VAR_MODEL_Z), b[2]);
	}
	interp_count = -1;
}

// step of another player: displayed at its (extrapolated) position + the decaying correction
static RValue *__cdecl hook_step_network_player(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_step_network_player(self, other, result, argc, args);
	if (active() == 0 || self == NULL) return result;
	int id = (int)yy_to_real(yy_self_var(self, VAR_NETWORK_PLAYER_ID));
	if (id < 0 || id > 255) return result;
	if (corrections[id].valid == 0) {
		record_step(self, id);
		return result;
	}
	Correction *c = &corrections[id];
	c->x *= CORRECTION_DECAY;
	c->y *= CORRECTION_DECAY;
	c->z *= CORRECTION_DECAY;
	int idA = *(int *)ADDR(SLOT_BUILTIN_VAR_A), idB = *(int *)ADDR(SLOT_BUILTIN_VAR_B);
	RValue v = { 0 };
	YY_BUILTIN_GET(self, idA, ARRAY_NONE, &v);
	yy_set_real(yy_self_var(self, VAR_MODEL_X), yy_to_real(&v) + c->x);
	yy_clear(&v);
	YY_BUILTIN_GET(self, idB, ARRAY_NONE, &v);
	yy_set_real(yy_self_var(self, VAR_MODEL_Y), yy_to_real(&v) + c->y);
	yy_clear(&v);
	yy_set_real(yy_self_var(self, VAR_MODEL_Z), yy_to_real(yy_self_var(self, VAR_Z)) + c->z);
	record_step(self, id);
	return result;
}

void prediction_install(void) {
	orig_read_apply = (GmlScript)hook_install(ADDR(GML_SCR_READ_APPLY_NETWORK_COORDINATES), PROLOGUE, sizeof PROLOGUE, hook_read_apply);
	orig_step_network_player = (GmlScript)hook_install(ADDR(GML_STEP_NETWORK_PLAYER), PROLOGUE, sizeof PROLOGUE, hook_step_network_player);
}

// native function network_send_udp_raw: its entry in the function table is replaced (once the table exists)
void prediction_wrap_natives(void) {
	if (orig_send_udp) return;
	uint8_t *table = *(uint8_t **)ADDR(FUNCTION_TABLE);
	int index = *(int *)ADDR(SLOT_FN_NETWORK_SEND_UDP_RAW);
	if (table && index > 0 && index < 10000) {
		NativeFn *entry = (NativeFn *)(table + index * 0x50 + 0x40);
		orig_send_udp = *entry;
		*entry = hook_send_udp;
		mod_log("network_send_udp_raw (%d) wrapped", index);
	}
}
