import { patch } from "./wsrep.mjs";
patch("mod.c", [
[`static double smooth_running_time(double ticks) {
	static double origin = -1;// wall clock (in ticks) when akt_time was 0`, `// The value is computed once per frame (g_frame): the timer is drawn several times per frame (shadow, text) and the
// digits must be the same.
static double smooth_running_time(double ticks) {
	static unsigned cached_frame = (unsigned)-1;
	static double cached_ticks, cached_result;
	if (cached_frame == g_frame && cached_ticks == ticks) return cached_result;
	static double origin = -1;// wall clock (in ticks) when akt_time was 0`],
[`	if (t < steps) t = steps;
	if (t > steps + 0.999) t = steps + 0.999;
	return t;
}`, `	if (t < steps) t = steps;
	if (t > steps + 0.999) t = steps + 0.999;
	cached_frame = g_frame;
	cached_ticks = ticks;
	cached_result = t;
	return t;
}`],
]);
patch("mod.h", [[`extern int g_records_tab;`, `extern int g_records_tab;
// spectator.c: frame counter (incremented once per drawn frame)
extern unsigned g_frame;`]]);
patch("spectator.c", [
[`static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_draw_gui(self, other, result, argc, args);`, `unsigned g_frame;

static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_draw_gui(self, other, result, argc, args);
	g_frame++;`],
]);
