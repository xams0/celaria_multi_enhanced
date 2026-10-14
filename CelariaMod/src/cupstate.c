// State of the server sent by the server (hidden chat messages), cup and time attack modes:
//   "\x01CUPROUND <round on the map> <rounds per map> <round running 0/1> <points limit>"   (cup mode only)
//   "\x01CUPST <id>:<state>[:<finish ms>] ..."  state: R racing, F finished, O out (complete reset), W winner,
//                                               V observer, N not racing this round (time attack: R or V)
//   "\x01CUPCP <id> <checkpoint> <ms>"          a player passed a checkpoint (checkpoint 0: his run restarted)
//   "\x01CUPPB <id> <finish ms> <cp1 ms> ..."   time attack: best run of a player on the map ("CUPPB CLEAR": new map)
// Used for the round display, the checkpoint ranking, the gap with the first player and the automatic spectator mode.
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define OBJ_NETWORK_HANDLER 0x58
#define VAR_PLAYER_ID_SERVER 1027

#define MAX_ID 256

// cup: the states of a CUPROUND message are collected apart (CUPST messages) and replace the shown ones at the first
// other message (no empty checkpoint ranking between the messages)
static char pending_states[MAX_ID];
static int collecting_states, pending_seen;

// cup ranking (second table under the leaderboard of the round) and observers
static CupRankEntry ranking[MAX_RANKING], pending[MAX_RANKING];
static int ranking_count, pending_count;
static char spectator_names[256];

static int known;// a CUPROUND or CUPST message was received
static int cup_mode_flag;// the server runs the cup mode (CUPROUND)
static int round_on_map, rounds_per_map, round_running, points_limit;
static char states[MAX_ID];
static long finish_ms[MAX_ID];
static int cp_index[MAX_ID];
static long cp_ms[MAX_ID];
static long cp_times[MAX_ID][MAX_CP + 1];// times of the round (cup) / of the current run (time attack), 0 = not passed
static long best_run[MAX_ID][MAX_CP + 1];// time attack: best run of every player (checkpoints)
static long best_finish[MAX_ID];// -1 = none
static int best_count[MAX_ID];
static char local_override;
static int warmup_active, warmup_index, warmup_count;
static double warmup_end;// local time of the end of the warmup (time attack), 0 = unknown// state of the local player until the next CUPST message (round start)

static void clear_best(void) {
	memset(best_run, 0, sizeof best_run);
	memset(best_count, 0, sizeof best_count);
	for (int i = 0; i < MAX_ID; i++) best_finish[i] = -1;
}

static void clear_round(void) {
	memset(cp_index, 0, sizeof cp_index);
	memset(cp_ms, 0, sizeof cp_ms);
	memset(cp_times, 0, sizeof cp_times);
	for (int i = 0; i < MAX_ID; i++) finish_ms[i] = -1;
}

void cupstate_reset(void) {
	known = 0;
	cup_mode_flag = 0;
	round_on_map = rounds_per_map = round_running = points_limit = 0;
	memset(states, 0, sizeof states);
	clear_round();
	clear_best();
	local_override = 0;
	warmup_active = 0;
	ranking_count = 0;
	pending_count = 0;
	collecting_states = 0;
	spectator_names[0] = 0;
}

int cup_known(void) { return known; }
int cup_mode(void) { return cup_mode_flag; }
int cup_round(void) { return round_on_map; }
int cup_rounds(void) { return rounds_per_map; }
int cup_round_running(void) { return round_running; }

int cup_local_id(void) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_NETWORK_HANDLER, VAR_PLAYER_ID_SERVER, ARRAY_NONE, &v);
	int id = (int)yy_to_real(&v);
	yy_clear(&v);
	return id;
}

char cup_state(int id) {
	if (id < 0 || id >= MAX_ID) return 0;
	if (local_override && id == cup_local_id()) return local_override;
	return states[id];
}

long cup_finish_ms(int id) { return id >= 0 && id < MAX_ID && states[id] == 'F' ? finish_ms[id] : -1; }

// observer mode requested: shown at once (the server confirms with the next CUPST message)
void cup_set_local_state(char state) {
	local_override = state;
}

// the local player starts the round (CUPSTART received)
void cup_local_round_starts(void) {
	local_override = 'R';
	clear_round();
}

int cup_checkpoint(int id, long *ms) {
	if (id < 0 || id >= MAX_ID) return 0;
	if (ms) *ms = cp_ms[id];
	return cp_index[id];
}

static void set_checkpoint(int id, int index, long ms) {
	if (id < 0 || id >= MAX_ID) return;
	if (index <= 0) {// run restarted (time attack)
		cp_index[id] = 0;
		cp_ms[id] = 0;
		memset(cp_times[id], 0, sizeof cp_times[id]);
		return;
	}
	if (cup_mode_flag == 0 && index < cp_index[id]) memset(cp_times[id], 0, sizeof cp_times[id]);
	cp_index[id] = index;
	cp_ms[id] = ms;
	if (index <= MAX_CP) cp_times[id][index] = ms > 0 ? ms : 1;
}

// the local player passed a checkpoint (shown at once, before the message of the server)
void cup_local_checkpoint(int index, long ms) { set_checkpoint(cup_local_id(), index, ms); }

// warmup of the map: 1 if active (index/count of the cup warmup rounds, remaining ms in time attack or -1)
int cup_warmup(int *index, int *count, double *remaining_ms) {
	*index = warmup_index;
	*count = warmup_count;
	*remaining_ms = warmup_end > 0 ? warmup_end - clock_local_ms() : -1;
	if (*remaining_ms < -1) *remaining_ms = 0;
	return warmup_active;
}


int cup_ranking(const CupRankEntry **entries) {
	*entries = ranking;
	return ranking_count;
}

const char *cup_spectators(void) { return spectator_names; }

int cupstate_handle_message(const char *text) {
	if (collecting_states && pending_seen && strncmp(text, "\x01" "CUPST", 6) != 0) {
		memcpy(states, pending_states, sizeof states);
		collecting_states = 0;
	}
	// "\x01CUPRK <index> <count> <place> <id> <points> <last round points> <finalist> <winner rank> <badge> <name>"
	if (strncmp(text, "\x01" "CUPRK ", 7) == 0) {
		// the list is built apart and replaces the shown one when complete (no flicker during the update)
		int index, count, n = 0;
		CupRankEntry e = { 0 };
		int fields = sscanf(text + 7, "%d %d %d %d %d %d %d %d %d %n", &index, &count, &e.place, &e.id, &e.points, &e.last_points, &e.finalist,
			&e.winner, &e.badge, &n);
		if (fields < 2) return 1;
		if (index == 0) pending_count = 0;
		if (fields >= 9 && n > 0 && index == pending_count && index < MAX_RANKING) {
			snprintf(e.name, sizeof e.name, "%s", text + 7 + n);
			pending[pending_count++] = e;
		}
		if (count <= 0) {
			ranking_count = 0;
		} else if (pending_count == (count < MAX_RANKING ? count : MAX_RANKING)) {
			memcpy(ranking, pending, sizeof(CupRankEntry) * pending_count);
			ranking_count = pending_count;
		}
		return 1;
	}
	// "\x01CUPSPEC <name>\x02<name>..."
	if (strncmp(text, "\x01" "CUPSPEC", 8) == 0) {
		const char *p = text[8] == ' ' ? text + 9 : text + 8;
		size_t i = 0;
		for (; *p && i + 3 < sizeof spectator_names; p++) {
			if (*p == 2) {
				spectator_names[i++] = ',';
				spectator_names[i++] = ' ';
			} else {
				spectator_names[i++] = *p;
			}
		}
		spectator_names[i] = 0;
		return 1;
	}
	if (strncmp(text, "" "CUPWU ", 7) == 0) {
		int active, index, count;
		double remaining;
		if (sscanf(text + 7, "%d %d %d %lf", &active, &index, &count, &remaining) == 4) {
			warmup_active = active;
			warmup_index = index;
			warmup_count = count;
			warmup_end = remaining >= 0 ? clock_local_ms() + remaining : 0;
		}
		return 1;
	}
	if (strncmp(text, "\x01" "CUPROUND ", 10) == 0) {
		int r, n, running, limit;
		double start = 0;
		if (sscanf(text + 10, "%d %d %d %d %lf", &r, &n, &running, &limit, &start) >= 4) {
			if (running && start > 0) clock_set_race_start(start);// joined during the round
			if (running && (round_running == 0 || r != round_on_map)) clear_round();// new round
			known = 1;
			cup_mode_flag = 1;
			round_on_map = r;
			rounds_per_map = n;
			round_running = running;
			points_limit = limit;
			memset(pending_states, 0, sizeof pending_states);// followed by the CUPST messages
			collecting_states = 1;
			pending_seen = 0;
		}
		return 1;
	}
	if (strncmp(text, "\x01" "CUPST", 6) == 0) {
		const char *p = text + 6;
		int id, n;
		char state;
		while (sscanf(p, " %d:%c%n", &id, &state, &n) == 2) {
			p += n;
			long ms = -1;
			if (*p == ':') {
				ms = strtol(p + 1, (char **)&p, 10);
			}
			if (id >= 0 && id < MAX_ID) {
				states[id] = state;
				pending_states[id] = state;
				if (ms >= 0) finish_ms[id] = ms;
			}
		}
		pending_seen = 1;
		known = 1;
		local_override = 0;
		return 1;
	}
	if (strncmp(text, "\x01" "CUPCP ", 7) == 0) {
		int id, index;
		long ms;
		if (sscanf(text + 7, "%d %d %ld", &id, &index, &ms) == 3 && id >= 0 && id < MAX_ID) {
			int was = cp_index[id];
			set_checkpoint(id, index, ms);
			if (index > 0 && (id != cup_local_id() || was != index)) spectator_player_checkpoint(id, index, ms);
		}
		return 1;
	}
	if (strncmp(text, "\x01" "CUPPB ", 7) == 0) {
		if (strncmp(text + 7, "CLEAR", 5) == 0) {
			clear_best();
			clear_round();
			return 1;
		}
		const char *p = text + 7;
		int id, n;
		long fin, ms;
		if (sscanf(p, "%d %ld%n", &id, &fin, &n) == 2 && id >= 0 && id < MAX_ID) {
			p += n;
			best_finish[id] = fin;
			best_count[id] = 0;
			memset(best_run[id], 0, sizeof best_run[id]);
			while (best_count[id] < MAX_CP && sscanf(p, " %ld%n", &ms, &n) == 1) {
				p += n;
				best_run[id][++best_count[id]] = ms;
			}
		}
		return 1;
	}
	return 0;
}

// ---------------------------------------------------------------- gap with the first player

// Time attack: the best runs of every player on the map (including the own best run of the player for the gap).
// Cup: the times of the other players at the same checkpoint in this round (finish: the finishers of the round).
// checkpoint 0 = finish. Returns 0 if there is no other time to compare with.
int cup_compare(int id, int checkpoint, long ms, int *rank, long *gap) {
	long best = -1;
	int better = 0;
	for (int o = 0; o < MAX_ID; o++) {
		long t;
		if (cup_mode_flag) {
			if (o == id) continue;
			if (checkpoint == 0) t = cup_finish_ms(o);
			else t = checkpoint <= MAX_CP ? cp_times[o][checkpoint] : 0;
			if (t <= 0) continue;
		} else {
			if (checkpoint == 0) t = best_finish[o];
			else t = checkpoint <= best_count[o] ? best_run[o][checkpoint] : 0;
			if (t <= 0) continue;
			if (o == id) {// own best run: the pace of the record (not counted for the position)
				if (best < 0 || t < best) best = t;
				continue;
			}
		}
		if (t < ms) better++;
		if (best < 0 || t < best) best = t;
	}
	*rank = better + 1;
	if (best < 0) return 0;
	*gap = ms - best;
	return 1;
}

// ---------------------------------------------------------------- display

static void format_ms(char *buf, size_t size, long ms) {
	if (ms < 0) ms = 0;
	snprintf(buf, size, "%02ld:%02ld:%03ld", ms / 60000, (ms / 1000) % 60, ms % 1000);
}

typedef struct {
	int id;
	int finished;
	int cp;
	long ms;
} RankLine;

static int compare_lines(const void *a, const void *b) {
	const RankLine *x = a, *y = b;
	if (x->finished != y->finished) return y->finished - x->finished;
	if (x->cp != y->cp) return y->cp - x->cp;
	return (x->ms > y->ms) - (x->ms < y->ms);
}

// checkpoint ranking of the round: finishers by time, then the other players by checkpoint and time at the checkpoint
// (time attack: current runs of the players)
int cup_build_ranking(CupRankLine *lines, int max_lines, int keep_id) {
	RankLine list[MAX_ID];
	int n = 0;
	for (int id = 0; id < MAX_ID; id++) {
		char s = cup_state(id);
		if (cup_mode_flag && s == 'F' && finish_ms[id] >= 0) {
			list[n++] = (RankLine){ id, 1, 0, finish_ms[id] };
		} else if ((s == 'R' || s == 'O') && cp_index[id] > 0) {
			list[n++] = (RankLine){ id, 0, cp_index[id], cp_ms[id] };
		}
	}
	qsort(list, n, sizeof list[0], compare_lines);
	// keep_id (local or spectated player) beyond the shown lines: on the last line, with his place
	int keep = -1;
	for (int i = max_lines - 1; i < n; i++)
		if (list[i].id == keep_id) keep = i;
	int count = 0;
	for (int i = 0; i < n && count < max_lines; i++) {
		if (keep >= 0 && count == max_lines - 1) i = keep;
		RankLine *l = &list[i];
		// gap with the best time at the same checkpoint (or the best finish)
		long best = -1;
		for (int j = 0; j < i; j++)
			if (list[j].finished == l->finished && list[j].cp == l->cp) {
				best = list[j].ms;
				break;
			}
		CupRankLine *out = &lines[count++];
		out->id = l->id;
		out->place = i + 1;
		if (best >= 0) {
			out->time[0] = '+';
			format_ms(out->time + 1, sizeof out->time - 1, l->ms - best);
		} else {
			format_ms(out->time, sizeof out->time, l->ms);
		}
		if (l->finished) snprintf(out->where, sizeof out->where, "FIN");
		else snprintf(out->where, sizeof out->where, "CP%d", l->cp);
	}
	return count;
}
