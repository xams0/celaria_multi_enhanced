// Display of the other players (cup / time attack server with the mod): exact positions, interpolated.
//
// The game receives the positions of the other players from the server compressed to 10 cm (and rounded down: the
// players sink into the floor), extrapolates them with their velocity at every step (100 Hz) and smooths them. Jumps,
// wall runs and collisions make the extrapolation go through the floor and the walls.
//
// With the mod, the server adds to its position packets, for every other player: his position in 32 bit floats (as
// sent by his game), the race time of this position (time stamp) and his camera (direction, up vector, eye position
// relative to the player). The mod keeps these samples and draws every other player at the position interpolated a
// little in the past (RACE_DELAY_MS / SPECTATOR_DELAY_MS) on a playout clock aligned on the time stamps: no extrapolation, no network
// jitter. The positions are computed at every frame (the camera of the local player is interpolated at every frame).
//
// Packet of the mod (client -> server, after the 29 bytes of the game): "R" + u32 race time (ms) ("N" when the timer
// doesn't run) + s16 direction x/y/z (x32767) + s8 up x/y/z (x127) + s16 eye offset x/y/z (1/100 unit) = 20 bytes.
// Server -> client, after the data read by the game: [id u8, flags u8 (1 = time, 2 = camera), u32 race time,
// f32 x, f32 y, f32 z, camera 15 bytes] * count, count u8, "SZ".
#include <windows.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define GML_SCR_READ_APPLY_NETWORK_COORDINATES 0x00860c30
#define GML_STEP_NETWORK_PLAYER 0x00791e40
#define OBJ_NETWORK_PLAYER 0x69
#define OBJ_PLAYER 0x65
#define OBJ_CAMERA 0
#define VAR_NETWORK_PLAYER_ID 981
#define VAR_MODEL_X 924
#define VAR_MODEL_Y 925
#define VAR_MODEL_Z 926
#define VAR_CAM_XFROM 1708
#define VAR_CAM_YFROM 1736
#define VAR_CAM_ZFROM 1785
#define VAR_CAM_XTO 1717
#define VAR_CAM_YTO 1747
#define VAR_CAM_ZTO 1796
#define VAR_CAM_XUP 1718
#define VAR_CAM_YUP 1748
#define VAR_CAM_ZUP 1797

#define SLOT_FN_BUFFER_GET_ADDRESS 0x00f3851c
#define SLOT_FN_BUFFER_GET_SIZE 0x00f385c4
#define SLOT_FN_BUFFER_RESIZE 0x00f38a4c
#define SLOT_FN_NETWORK_SEND_UDP_RAW 0x00f38b7c
#define FUNCTION_TABLE 0x031b3104// pointer to the table of the native functions (80 bytes per function, code at +0x40)
#define POSITION_PACKET_SIZE 29
#define EXTENSION_SIZE 20
#define ENTRY_SIZE 33

// Delay of the display: samples every 20 ms (sent every 2 steps) + relay of the server (60 Hz) + jitter.
// Racing: short delay (the opponents are shown closer to their real position, ~60 cm behind at full speed); spectating:
// longer delay (the followed player is never extrapolated). The delay changes progressively (no jump).
#define RACE_DELAY_MS 28.0
#define SPECTATOR_DELAY_MS 45.0
static double display_delay = RACE_DELAY_MS;

// once per frame: the delay follows the state of the local player (racing / spectating), 20 ms per second
static void update_display_delay(void) {
	static double last;
	double now = clock_local_ms();
	double dt = last > 0 ? now - last : 0;
	last = now;
	if (dt > 100) dt = 100;
	double target = spectator_active() ? SPECTATOR_DELAY_MS : RACE_DELAY_MS;
	double step = dt * 0.02;
	if (display_delay < target) display_delay = display_delay + step > target ? target : display_delay + step;
	else if (display_delay > target) display_delay = display_delay - step < target ? target : display_delay - step;
}
#define MAX_EXTRAPOLATION_MS 60.0// packets lost: the last movement continues a little
#define TELEPORT_DISTANCE 6.0// larger jumps between two samples (respawn) are not interpolated
#define SAMPLES 32

typedef void(__cdecl *NativeFn)(RValue *result, void *self, void *other, int argc, RValue *args);
typedef int(__cdecl *FnWithNew)(void *iterator, void **pSelf, void **pOther, int object);
typedef char(__cdecl *FnWithNext)(void *iterator, void **pSelf, void **pOther);
typedef void(__cdecl *FnWithDelete)(void *iterator, void **pSelf, void **pOther);
#define YY_WITH_NEW ((FnWithNew)ADDR(0x00b9d720))
#define YY_WITH_NEXT ((FnWithNext)ADDR(0x00b8fd20))
#define YY_WITH_DELETE ((FnWithDelete)ADDR(0x00b8fd50))

static GmlScript orig_read_apply;
static GmlScript orig_step_network_player;
static NativeFn orig_send_udp;

typedef struct {
	double key;// race time of the sender (ms) or local reception time
	double pos[3];
	int cam;// camera known
	double dir[3], up[3], eye[3];
} Sample;

typedef struct {
	Sample s[SAMPLES];
	int count, head;// ring buffer, head = next write
	int race_keys;// keys are race times
	double offset;// key - local time of the fastest samples (playout clock)
	double last_arrival;
	double stamp, stamp_arrival;// last race time of this player and its reception time (-1 = none)
} Track;

static Track tracks[256];

static int active(void) { return g_server_supports_mod && mod_active(); }

void prediction_reset(void) {
	memset(tracks, 0, sizeof tracks);
}

// "\x01CUPLAG ..." (estimated delays, used by older versions): ignored, the display is interpolated
int prediction_handle_message(const char *text) {
	return strncmp(text, "\x01" "CUPLAG", 7) == 0;
}

static void track_add(int id, const Sample *in, int race_key, double arrival) {
	Track *t = &tracks[id];
	const Sample *last = t->count ? &t->s[(t->head + SAMPLES - 1) % SAMPLES] : NULL;
	if (last) {
		if (race_key && race_key == t->race_keys && in->key == last->key) return;// same position relayed again
		if (race_key == 0 && t->race_keys == 0 && memcmp(in->pos, last->pos, sizeof in->pos) == 0 && arrival - t->last_arrival < 100) return;
	}
	// new run, new round or other kind of time: the buffer restarts
	if (last == NULL || race_key != t->race_keys || in->key < last->key - 50 || in->key > last->key + 2000 || arrival - t->last_arrival > 1000) {
		t->count = t->head = 0;
		t->race_keys = race_key;
		t->offset = in->key - arrival;
	}
	t->s[t->head] = *in;
	t->head = (t->head + 1) % SAMPLES;
	if (t->count < SAMPLES) t->count++;
	double o = in->key - arrival;
	if (o > t->offset) t->offset = o;// a faster sample: the playout clock moves forward at once
	else t->offset += (o - t->offset) * 0.02;// slowly follows a longer delay (or the drift of the clocks)
	t->last_arrival = arrival;
}

static void lerp3(double *out, const double *a, const double *b, double f) {
	for (int k = 0; k < 3; k++) out[k] = a[k] + (b[k] - a[k]) * f;
}

static double dist3(const double *a, const double *b) {
	double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
	return sqrt(dx * dx + dy * dy + dz * dz);
}

// local time of the frame: the same for every position read during a frame (camera of the spectator, models, names)
static double frame_time(void) {
	static unsigned frame = (unsigned)-1;
	static double time;
	if (frame != g_frame) {
		frame = g_frame;
		time = clock_local_ms();
	}
	return time;
}

// state of a player at the playout time (now - delay); 0 if unknown
static int track_sample(int id, Sample *out) {
	if (id < 0 || id > 255) return 0;
	Track *t = &tracks[id];
	double now = frame_time();
	if (t->count == 0 || now - t->last_arrival > 1500) return 0;
	double key = now + t->offset - display_delay;
	const Sample *newer = &t->s[(t->head + SAMPLES - 1) % SAMPLES];
	if (key >= newer->key || t->count == 1) {
		*out = *newer;
		// no new sample (packet lost): the last movement continues a little
		if (t->count >= 2 && key > newer->key) {
			const Sample *prev = &t->s[(t->head + SAMPLES - 2) % SAMPLES];
			double span = newer->key - prev->key, ahead = key - newer->key;
			if (span > 0 && ahead < MAX_EXTRAPOLATION_MS && dist3(newer->pos, prev->pos) < TELEPORT_DISTANCE)
				for (int k = 0; k < 3; k++) out->pos[k] = newer->pos[k] + (newer->pos[k] - prev->pos[k]) * ahead / span;
		}
		return 1;
	}
	for (int i = 2; i <= t->count; i++) {
		const Sample *older = &t->s[(t->head + SAMPLES - i) % SAMPLES];
		if (older->key <= key) {
			double f = newer->key > older->key ? (key - older->key) / (newer->key - older->key) : 1;
			*out = f < 0.5 ? *older : *newer;
			if (dist3(older->pos, newer->pos) < TELEPORT_DISTANCE) lerp3(out->pos, older->pos, newer->pos, f);
			if (older->cam && newer->cam) {
				lerp3(out->dir, older->dir, newer->dir, f);
				lerp3(out->up, older->up, newer->up, f);
				lerp3(out->eye, older->eye, newer->eye, f);
			}
			return 1;
		}
		newer = older;
	}
	*out = *newer;// older than every sample
	return 1;
}

// camera of another player at the displayed time: eye (world), unit direction, up vector; 0 if unknown
int prediction_camera(int id, double eye[3], double dir[3], double up[3]) {
	Sample s;
	if (track_sample(id, &s) == 0 || s.cam == 0) return 0;
	double l = sqrt(s.dir[0] * s.dir[0] + s.dir[1] * s.dir[1] + s.dir[2] * s.dir[2]);
	if (l < 0.01) return 0;
	for (int k = 0; k < 3; k++) {
		eye[k] = s.pos[k] + s.eye[k];
		dir[k] = s.dir[k] / l;
		up[k] = s.up[k];
	}
	return 1;
}

// world yaw / pitch of the camera of another player (degrees), for the third person view
int prediction_look(int id, double *yaw, double *pitch) {
	double eye[3], dir[3], up[3];
	if (prediction_camera(id, eye, dir, up) == 0) return 0;
	*yaw = atan2(dir[1], dir[0]) * 180.0 / M_PI;
	*pitch = asin(dir[2] > 1 ? 1 : dir[2] < -1 ? -1 : dir[2]) * 180.0 / M_PI;
	return 1;
}

double prediction_race_time(int id) {
	if (id < 0 || id > 255 || tracks[id].stamp_arrival <= 0 || tracks[id].stamp < 0) return -1;
	double age = clock_local_ms() - tracks[id].stamp_arrival;
	if (age > 1000) return -1;
	return tracks[id].stamp + age;
}

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

static double camera_var(int var) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_CAMERA, var, ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}

static int16_t s16(double v) { return (int16_t)(v > 32767 ? 32767 : v < -32767 ? -32767 : v); }
static int8_t s8(double v) { return (int8_t)(v > 127 ? 127 : v < -127 ? -127 : v); }

// network_send_udp_raw(socket, url, port, buffer, size): race time and camera appended to the position packet
static void __cdecl hook_send_udp(RValue *result, void *self, void *other, int argc, RValue *args) {
	if (argc >= 5 && active()) {
		int size = 0;
		uint8_t *p = buffer_address(self, other, &args[3], &size);
		int length = (int)yy_to_real(&args[4]);
		if (p && size < POSITION_PACKET_SIZE + EXTENSION_SIZE && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
			// the buffer of the game is too small for the extension: enlarged once
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
			uint8_t *e = p + POSITION_PACKET_SIZE;
			RValue v = { 0 };
			YY_VAR_GET(OBJ_TIMER, VAR_TIME_RUNNING, ARRAY_NONE, &v);
			int running = yy_to_real(&v) > 0.5;
			yy_clear(&v);
			YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
			double steps = yy_to_real(&v);
			yy_clear(&v);
			uint32_t ms = running && steps >= 0 ? (uint32_t)(steps * 10.0) : 0;
			e[0] = running && steps >= 0 ? 'R' : 'N';
			memcpy(e + 1, &ms, 4);
			// camera of the local player (first person spectator view of the other clients): direction, up vector and
			// eye relative to the position of the packet (floats of the game at bytes 10, 14, 18)
			float px, py, pz;
			memcpy(&px, p + 10, 4);
			memcpy(&py, p + 14, 4);
			memcpy(&pz, p + 18, 4);
			int16_t d[3] = { s16(camera_var(VAR_CAM_XTO) * 32767), s16(camera_var(VAR_CAM_YTO) * 32767), s16(camera_var(VAR_CAM_ZTO) * 32767) };
			int8_t u[3] = { s8(camera_var(VAR_CAM_XUP) * 127), s8(camera_var(VAR_CAM_YUP) * 127), s8(camera_var(VAR_CAM_ZUP) * 127) };
			int16_t o[3] = { s16((camera_var(VAR_CAM_XFROM) - px) * 100), s16((camera_var(VAR_CAM_YFROM) - py) * 100),
				s16((camera_var(VAR_CAM_ZFROM) - pz) * 100) };
			memcpy(e + 5, d, 6);
			memcpy(e + 11, u, 3);
			memcpy(e + 14, o, 6);
		}
	}
	orig_send_udp(result, self, other, argc, args);
}

// extension of a position packet of the server: [id u8, flags u8, u32 race time, f32 x, f32 y, f32 z, camera 15] * count,
// count u8, "SZ"
static void read_extension(void *self, void *other, RValue *buffer) {
	int size = 0;
	uint8_t *p = buffer_address(self, other, buffer, &size);
	if (p == NULL || size < 3 || p[size - 1] != 'Z' || p[size - 2] != 'S') return;
	int count = p[size - 3];
	int start = size - 3 - count * ENTRY_SIZE;
	if (start < 8) return;
	double now = clock_local_ms();
	for (int i = 0; i < count; i++) {
		const uint8_t *e = p + start + i * ENTRY_SIZE;
		int id = e[0], flags = e[1];
		uint32_t ms;
		float x, y, z;
		memcpy(&ms, e + 2, 4);
		memcpy(&x, e + 6, 4);
		memcpy(&y, e + 10, 4);
		memcpy(&z, e + 14, 4);
		Sample s = { 0 };
		s.pos[0] = x;
		s.pos[1] = y;
		s.pos[2] = z;
		int timed = (flags & 1) != 0;
		s.key = timed ? (double)ms : now;
		if (flags & 2) {
			int16_t d[3], o[3];
			int8_t u[3];
			memcpy(d, e + 18, 6);
			memcpy(u, e + 24, 3);
			memcpy(o, e + 27, 6);
			s.cam = 1;
			for (int k = 0; k < 3; k++) {
				s.dir[k] = d[k] / 32767.0;
				s.up[k] = u[k] / 127.0;
				s.eye[k] = o[k] / 100.0;
			}
		}
		Track *t = &tracks[id];
		t->stamp = timed ? (double)ms : -1;
		t->stamp_arrival = now;
		track_add(id, &s, timed, now);
	}
}

static RValue *__cdecl hook_read_apply(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (active() == 0 || argc < 1) return orig_read_apply(self, other, result, argc, args);
	RValue buffer = *args[0];// read only copy (number)
	orig_read_apply(self, other, result, argc, args);
	read_extension(self, other, &buffer);
	return result;
}

// ---------------------------------------------------------------- displayed position

static void set_model(void *inst, const double pos[3]) {
	yy_set_real(yy_self_var(inst, VAR_MODEL_X), pos[0]);
	yy_set_real(yy_self_var(inst, VAR_MODEL_Y), pos[1]);
	yy_set_real(yy_self_var(inst, VAR_MODEL_Z), pos[2]);
}

// at every frame, before the drawing: every other player at his interpolated position
void prediction_interpolate_begin(void *self, void *other) {
	if (active() == 0) return;
	update_display_delay();
	uint8_t iterator[1024];
	void *s = self, *o = other;
	if (YY_WITH_NEW(iterator, &s, &o, OBJ_NETWORK_PLAYER) > 0) {
		do {
			int id = (int)yy_to_real(yy_self_var(s, VAR_NETWORK_PLAYER_ID));
			Sample smp;
			if (track_sample(id, &smp)) set_model(s, smp.pos);
		} while (YY_WITH_NEXT(iterator, &s, &o));
	}
	YY_WITH_DELETE(iterator, &s, &o);
}

void prediction_interpolate_end(void) {
}

// step of another player: the game extrapolates and smooths its position; the mod replaces the displayed position
static RValue *__cdecl hook_step_network_player(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_step_network_player(self, other, result, argc, args);
	if (active() == 0 || self == NULL) return result;
	int id = (int)yy_to_real(yy_self_var(self, VAR_NETWORK_PLAYER_ID));
	Sample smp;
	if (track_sample(id, &smp)) set_model(self, smp.pos);
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
