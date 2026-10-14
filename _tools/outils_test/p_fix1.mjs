import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(M + "prediction.c", [
[`#define SLOT_FN_BUFFER_GET_SIZE 0x00f385c4`, `#define SLOT_FN_BUFFER_GET_SIZE 0x00f385c4
#define SLOT_FN_BUFFER_RESIZE 0x00f38a4c`],
[`		if (p && size >= POSITION_PACKET_SIZE + 5 && length >= POSITION_PACKET_SIZE + 5 && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {`,
 `		if (p && size < POSITION_PACKET_SIZE + 5 && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
			// the buffer of the game is a bit too small for the race time: enlarged once
			RValue a[2] = { args[3], { { 0 } } }, r = { 0 };
			a[1].real = POSITION_PACKET_SIZE + 5;
			RValue *ra[2] = { &a[0], &a[1] };
			YY_CALL_BUILTIN(self, other, &r, 2, *(void **)ADDR(SLOT_FN_BUFFER_RESIZE), ra);
			yy_clear(&r);
			p = buffer_address(self, other, &args[3], &size);
			mod_log("UDP buffer resized to %d", size);
		}
		if (p && size >= POSITION_PACKET_SIZE + 5 && memcmp(p, "CCPU", 4) == 0 && p[5] == 1) {
			if (length < POSITION_PACKET_SIZE + 5) yy_set_real(&args[4], POSITION_PACKET_SIZE + 5);`],
]);
patch(M + "cupstate.c", [
[`		int r, n, running, limit;
		if (sscanf(text + 10, "%d %d %d %d", &r, &n, &running, &limit) == 4) {`, `		int r, n, running, limit;
		double start = 0;
		if (sscanf(text + 10, "%d %d %d %d %lf", &r, &n, &running, &limit, &start) >= 4) {
			if (running && start > 0) clock_set_race_start(start);// joined during the round`],
]);
patch(M + "clock.c", [[`double clock_race_start(void) { return race_start; }`, `double clock_race_start(void) { return race_start; }

void clock_set_race_start(double start) {
	if (race_start < 0) race_start = start;
}`]]);
patch(M + "mod.h", [[`double clock_race_start(void);`, `double clock_race_start(void);
void clock_set_race_start(double start);`]]);
patch(S + "game/CupState.java", [
[`	public synchronized List<String> buildStateMessages() {`, `	public synchronized List<String> buildStateMessages(double raceStartClock) {`],
[`(roundRunning ? 1 : 0) + " " + settings.pointsLimit);`, `(roundRunning ? 1 : 0) + " " + settings.pointsLimit + " " + server.util.ServerClock.format(raceStartClock));`],
]);
patch(S + "ServerCore.java", [[`java.util.List<String> messages = getGameState().getCup().buildStateMessages();`, `java.util.List<String> messages = getGameState().getCup().buildStateMessages(getGameState().getCupStartClockMS());`]]);
