import { patch } from "./wsrep.mjs";
patch("mod.c", [
[`static RValue *__cdecl hook_time_format(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc < 1 || mod_active() == 0) return orig_time_format(self, other, result, argc, args);
	// display only: precise time of a checkpoint/goal of the current run, or the running timer smoothed
	double official = yy_to_real(args[0]);
	double ticks = ms_precise_ticks(official);
	if (ticks == official) ticks = smooth_running_time(official);`, `// race time of the local player (ms): timer of the run with the fraction of the step; between the start and the first
// step or when the local player doesn't race (spectator), time since the synchronised race start. -1 if unknown.
double race_time_now_ms(void) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_TIMER, VAR_TIME_RUNNING, ARRAY_NONE, &v);
	int running = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
	double steps = yy_to_real(&v);
	yy_clear(&v);
	if (running && steps > 0) return smooth_running_time(steps) * 10.0;
	if (clock_race_start() > 0 && cup_round_running()) return clock_server_ms() - clock_race_start();
	return -1;
}

static RValue *__cdecl hook_time_format(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc < 1 || mod_active() == 0) return orig_time_format(self, other, result, argc, args);
	// display only: precise time of a checkpoint/goal of the current run, or the running timer smoothed
	double official = yy_to_real(args[0]);
	double ticks = ms_precise_ticks(official);
	if (ticks == official) ticks = smooth_running_time(official);
	double spectated = spectator_timer_ms();// timer of the followed player while spectating
	if (spectated >= 0) ticks = spectated / 10.0;`],
]);
patch("net.c", [
[`		if (s && s[0] == 1 && (clock_handle_message(s) || prediction_handle_message(s))) return return_noone(result);`,
 `		if (s && s[0] == 1 && (clock_handle_message(s) || prediction_handle_message(s) || cupstate_handle_message(s))) return return_noone(result);`],
[`	g_server_supports_mod = 0;
	lan_end_session();
	time_count = 0;
	clock_reset();
	prediction_reset();`, `	g_server_supports_mod = 0;
	lan_end_session();
	time_count = 0;
	clock_reset();
	prediction_reset();
	cupstate_reset();`],
[`	g_server_supports_mod = 0;
	lan_end_session();
	clock_reset();
	prediction_reset();`, `	g_server_supports_mod = 0;
	lan_end_session();
	clock_reset();
	prediction_reset();
	cupstate_reset();`],
]);
