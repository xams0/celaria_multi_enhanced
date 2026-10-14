import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`int g_in_timer_draw;`, `int g_in_timer_draw;
static unsigned timer_drawn_frame;// last frame where the game drew its timer`],
[`static void __cdecl hook_timer_draw(void *self, void *other) {
	g_in_timer_draw = 1;`, `static void __cdecl hook_timer_draw(void *self, void *other) {
	timer_drawn_frame = g_frame;
	g_in_timer_draw = 1;`],
[`// checkpoint ranking of the round (bottom right, above the speed)`, `// timer of the followed player when the game doesn't draw its timer (local player finished)
static void draw_spectated_timer(void *self, void *other, double w, double h) {
	if (spectating == 0 || view == VIEW_FREE || g_frame - timer_drawn_frame < 3) return;
	g_in_timer_draw = 1;
	double ms = spectator_timer_ms();
	g_in_timer_draw = 0;
	if (ms < 0) return;
	long t = (long)ms;
	char text[32];
	snprintf(text, sizeof text, "%02ld:%02ld:%03ld", t / 60000, (t / 1000) % 60, t % 1000);
	hud_font(1, 1);
	hud_text(self, other, w / 2 + 2, h * 0.918 + 2, text, 2.6, COLOUR_BLACK);
	hud_text(self, other, w / 2, h * 0.918, text, 2.6, COLOUR_WHITE);
}

// checkpoint ranking of the round (bottom right, above the speed)`],
[`		draw_checkpoint_ranking(self, other, w, h);`, `		draw_checkpoint_ranking(self, other, w, h);
		draw_spectated_timer(self, other, w, h);`],
]);
