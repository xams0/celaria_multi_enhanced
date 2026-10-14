#pragma once
#include <stdint.h>
#include "yyc.h"

#define MOD_VERSION 3

// first bytes of every YYC script: push ebp; mov ebp,esp; push ebx; push edi
static const uint8_t PROLOGUE[] = { 0x55, 0x89, 0xE5, 0x53, 0x57 };

// net.c: sends a TCP packet to the server like the game does (one value)
void net_send_tcp(void *self, void *other, int packetId, int bufferType, RValue *value);
// set by the server through a hidden chat message: the server supports the mod
extern int g_server_supports_mod;

void ms_timing_install(void);
// precise time (ticks with fraction) of a checkpoint/goal of the current run, or the official value
double ms_precise_ticks(double officialTicks);
void net_install(void);
void spectator_install(void);
void records_tab_install(void);
// clock.c: clock synchronisation with the cup server and scheduled start
int clock_handle_message(const char *text);
void clock_update(void *self, void *other);
void clock_reset(void);

double clock_local_ms(void);
// prediction.c: other players displayed at the same race time
void prediction_install(void);
void leaderboard_install(void);
// lanbrowser.c: server browser LAN
void lanbrowser_install(void);
void lanbrowser_update(void *self, void *other);
void prediction_reset(void);
// camera of another player sent by its mod (world yaw / pitch, degrees); 0 if unknown
int prediction_look(int id, double *yaw, double *pitch);
// camera of another player at the displayed time: eye (world), unit direction, up vector; 0 if unknown
int prediction_camera(int id, double eye[3], double dir[3], double up[3]);
int prediction_handle_message(const char *text);
extern int g_records_tab;
// spectator.c: frame counter (incremented once per drawn frame)
extern unsigned g_frame;
void spectator_round_starts(void);
int spectator_active(void);
// gap with the first player in the middle of the screen: local player (checkpoint/finish) or followed player
void spectator_local_checkpoint(int index, long ms);
void spectator_player_checkpoint(int id, int index, long ms);
// race time of another player now (time stamps of his positions), -1 if unknown
double prediction_race_time(int id);
// the other players are drawn between their last two steps (call around the drawing)
void prediction_interpolate_begin(void *self, void *other);
void prediction_interpolate_end(void);
// native functions of the game wrapped by the mod (the table is filled at the start of the game)
void prediction_wrap_natives(void);
void lanbrowser_wrap_natives(void);
// respawn.c: respawn without timer reset, restart = DNF (cup)
void respawn_install(void);
void respawn_update(void *self, void *other);
// autoconnect.c: connection to the server chosen in the launcher
void autoconnect_install(void);
void autoconnect_update(void *self, void *other);
// mod.c: path of a file next to the mod DLL
const char *mod_file_path(const char *name);
const char *player_name(int id);
double spectator_timer_ms(void);
// cupstate.c: state of the cup sent by the server
typedef struct {
	int id;
	int place;
	char where[8];
	char time[24];
} CupRankLine;
int cupstate_handle_message(const char *text);
void cupstate_reset(void);
int cup_known(void);
// the server runs the cup mode (else time attack)
int cup_mode(void);
int cup_warmup(int *index, int *count, double *remaining_ms);
#define MAX_RANKING 64
typedef struct {
	int place, id, points, last_points, finalist, winner, badge;
	char name[96];
} CupRankEntry;
// cup ranking sent by the server (second table of the leaderboard), names of the observers ("" if none)
int cup_ranking(const CupRankEntry **entries);
const char *cup_spectators(void);
int cup_spectator_list(const char *names[], int max);
// leaderboard.c: second table (cup ranking) under the leaderboard of the game, drawn at the end of the GUI
void leaderboard_draw_cup(void *self, void *other);
// the leaderboard panel is open; player of the row at this GUI position (last frame), -1 if none
int leaderboard_panel_open(void);
int leaderboard_player_at(double x, double y);
#define MAX_CP 64
void cup_local_checkpoint(int index, long ms);
// rank and gap (ms) with the best time at a checkpoint (0 = finish); 0 if nothing to compare with
int cup_compare(int id, int checkpoint, long ms, int *rank, long *gap);
int cup_round(void);
int cup_rounds(void);
int cup_round_running(void);
int cup_local_id(void);
char cup_state(int id);
long cup_finish_ms(int id);
void cup_local_round_starts(void);
void cup_set_local_state(char state);
int cup_checkpoint(int id, long *ms);
int cup_build_ranking(CupRankLine *lines, int max_lines, int keep_id);
// clock.c
double clock_server_ms(void);
double clock_rtt_ms(void);
double clock_race_start(void);
void clock_set_race_start(double start);
// mod.c: race time of the local player (ms, with the fraction of the step), or the time since the race start; -1 = none
double race_time_now_ms(void);
// lan.c: the mod is only active in a multiplayer game on a server of the local network
extern int g_lan_session;
int mod_active(void);
void lan_check_session(void);
void lan_end_session(void);
