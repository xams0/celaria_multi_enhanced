import { patch } from "./wsrep.mjs";
patch("cupstate.c", [
[`int cup_build_ranking(char lines[][96], int max_lines, int *highlight, int highlight_id) {`, `int cup_build_ranking(CupRankLine *lines, int max_lines) {`],
[`	int count = 0;
	*highlight = -1;
	for (int i = 0; i < n && count < max_lines; i++) {`, `	int count = 0;
	for (int i = 0; i < n && count < max_lines; i++) {`],
[`		char time[32];
		if (best >= 0) {
			time[0] = '+';
			format_ms(time + 1, sizeof time - 1, l->ms - best);
		} else {
			format_ms(time, sizeof time, l->ms);
		}
		const char *name = player_name(l->id);
		char where[16];
		if (l->finished) snprintf(where, sizeof where, "FIN");
		else snprintf(where, sizeof where, "CP%d", l->cp);
		snprintf(lines[count], 96, "%d. %-14.14s %-4s %s", i + 1, name, where, time);
		if (l->id == highlight_id) *highlight = count;
		count++;`, `		CupRankLine *out = &lines[count++];
		out->id = l->id;
		if (best >= 0) {
			out->time[0] = '+';
			format_ms(out->time + 1, sizeof out->time - 1, l->ms - best);
		} else {
			format_ms(out->time, sizeof out->time, l->ms);
		}
		if (l->finished) snprintf(out->where, sizeof out->where, "FIN");
		else snprintf(out->where, sizeof out->where, "CP%d", l->cp);`],
]);
patch("mod.h", [[`// spectator.c: frame counter (incremented once per drawn frame)
extern unsigned g_frame;`, `// spectator.c: frame counter (incremented once per drawn frame)
extern unsigned g_frame;
void spectator_round_starts(void);
const char *player_name(int id);
double spectator_timer_ms(void);
// cupstate.c: state of the cup sent by the server
typedef struct {
	int id;
	char where[8];
	char time[24];
} CupRankLine;
int cupstate_handle_message(const char *text);
void cupstate_reset(void);
int cup_known(void);
int cup_round(void);
int cup_rounds(void);
int cup_round_running(void);
int cup_local_id(void);
char cup_state(int id);
long cup_finish_ms(int id);
void cup_local_round_starts(void);
int cup_checkpoint(int id, long *ms);
int cup_build_ranking(CupRankLine *lines, int max_lines);
// clock.c
double clock_server_ms(void);
double clock_rtt_ms(void);
double clock_race_start(void);
// mod.c: race time of the local player (ms, with the fraction of the step), or the time since the race start; -1 = none
double race_time_now_ms(void);`],
[`const char *clock_countdown_text(void);
`, ``]]);
