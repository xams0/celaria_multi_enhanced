import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "prediction.c", [
[`#define POSITION_PACKET_SIZE 29`, `#define POSITION_PACKET_SIZE 29
#define EXTENSION_SIZE 9// "R" + u32 race time + u16 camera yaw + s16 camera pitch
#define OBJ_CAMERA 0
#define VAR_MOUSE_DIRECTION 934// yaw of the camera (world yaw = - mouse_direction, degrees)
#define VAR_Z_ANGLE 1751// pitch of the camera (degrees, up > 0)`],
[`static Correction corrections[256];`, `static Correction corrections[256];

// camera of the other players (cup mod): world yaw / pitch in degrees
static double look_yaw[256], look_pitch[256];
static int look_valid[256];

int prediction_look(int id, double *yaw, double *pitch) {
	if (id < 0 || id > 255 || look_valid[id] == 0) return 0;
	*yaw = look_yaw[id];
	*pitch = look_pitch[id];
	return 1;
}`],
[`	memset(corrections, 0, sizeof corrections);
}`, `	memset(corrections, 0, sizeof corrections);
	memset(look_valid, 0, sizeof look_valid);
}`],
[`		if (p && size < POSITION_PACKET_SIZE + 5 && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {`, `		if (p && size < POSITION_PACKET_SIZE + EXTENSION_SIZE && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {`],
[`			a[1].real = POSITION_PACKET_SIZE + 5;`, `			a[1].real = POSITION_PACKET_SIZE + EXTENSION_SIZE;`],
[`		if (p && size >= POSITION_PACKET_SIZE + 5 && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
			if (length < POSITION_PACKET_SIZE + 5) yy_set_real(&args[4], POSITION_PACKET_SIZE + 5);`,
 `		if (p && size >= POSITION_PACKET_SIZE + EXTENSION_SIZE && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
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
			memcpy(p + POSITION_PACKET_SIZE + 7, &pitch16, 2);`],
[`// race times at the end of a position packet of the server: [id u8, race time u32] * count, count u8, "RX"
static int read_race_times(void *self, void *other, RValue *buffer, int *ids, double *times, int max) {
	int size = 0;
	uint8_t *p = buffer_address(self, other, buffer, &size);
	if (p == NULL || size < 3 || p[size - 2] != 'R' || p[size - 1] != 'X') return 0;
	int count = p[size - 3];
	int start = size - 3 - count * 5;
	if (count > max || start < 8) return 0;
	for (int i = 0; i < count; i++) {
		uint32_t ms;
		ids[i] = p[start + i * 5];
		memcpy(&ms, p + start + i * 5 + 1, 4);
		times[i] = ms;
	}
	return count;
}`, `// race times at the end of a position packet of the server: [id u8, race time u32, yaw u16, pitch s16] * count,
// count u8, "SX" (older servers: [id u8, race time u32] * count, count u8, "RX")
static int read_race_times(void *self, void *other, RValue *buffer, int *ids, double *times, int max) {
	int size = 0;
	uint8_t *p = buffer_address(self, other, buffer, &size);
	if (p == NULL || size < 3 || p[size - 1] != 'X' || (p[size - 2] != 'R' && p[size - 2] != 'S')) return 0;
	int entry = p[size - 2] == 'S' ? 9 : 5;
	int count = p[size - 3];
	int start = size - 3 - count * entry;
	if (count > max || start < 8) return 0;
	for (int i = 0; i < count; i++) {
		const uint8_t *e = p + start + i * entry;
		uint32_t ms;
		ids[i] = e[0];
		memcpy(&ms, e + 1, 4);
		times[i] = ms;
		if (entry == 9 && ids[i] < 256) {
			uint16_t yaw16;
			int16_t pitch16;
			memcpy(&yaw16, e + 5, 2);
			memcpy(&pitch16, e + 7, 2);
			look_valid[ids[i]] = yaw16 != 0xFFFF;
			look_yaw[ids[i]] = yaw16 / 65535.0 * 360.0;
			look_pitch[ids[i]] = pitch16 / 100.0;
		}
	}
	return count;
}`],
]);
patch(M + "mod.h", [[`void prediction_reset(void);`, `void prediction_reset(void);
// camera of another player sent by its mod (world yaw / pitch, degrees); 0 if unknown
int prediction_look(int id, double *yaw, double *pitch);`]]);
