import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`#define GML_GET_RENDER_W 0x469a50`, `#define GML_SCR_DELETE_ALL_GUI_ELEMENTS 0x004da340
#define GML_SCR_MENU_LEADERBOARD_DESTROY_ALL 0x008e09e0
#define FINISH_SCREEN_DELAY_MS 2500
#define GML_GET_RENDER_W 0x469a50`],
[`static double target_lost_since;// the followed player stopped racing (local ms), 0 = racing`, `static double target_lost_since;// the followed player stopped racing (local ms), 0 = racing
static double finished_since;// the map finished screen is displayed since (local ms), 0 = not displayed
static int finish_screen_closed;`],
[`	free_initialised = 0;
}

static int key_pressed(int vk) {`, `	free_initialised = 0;
	finished_since = 0;
	finish_screen_closed = 0;
}

// the screen of the game after the goal (leaderboard + menu) stays until the next round: closed for the spectator view
static void close_finish_screen(void *self, void *other) {
	RValue r = { 0 };
	((GmlScript)ADDR(GML_SCR_MENU_LEADERBOARD_DESTROY_ALL))(self, other, &r, 0, NULL);
	yy_clear(&r);
	((GmlScript)ADDR(GML_SCR_DELETE_ALL_GUI_ELEMENTS))(self, other, &r, 0, NULL);
	yy_clear(&r);
	mod_log("map finished screen closed for the spectator view");
}

static int key_pressed(int vk) {`],
[`	spectating = view != VIEW_OFF;
	if (spectating == 0) return;`, `	spectating = view != VIEW_OFF;
	if (spectating == 0) return;
	// goal reached: the map finished screen is closed after a moment (not at the end of the round: final leaderboard)
	if (obj_var(OBJ_CONTROLLER, VAR_MAP_FINISHED) > 0.5 && obj_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) < 0.5 && finish_screen_closed == 0) {
		if (finished_since == 0) finished_since = now_ms;
		if (now_ms - finished_since > FINISH_SCREEN_DELAY_MS) {
			finish_screen_closed = 1;
			close_finish_screen(self, other);
		}
	}`],
[`	if (mod_active() && g_server_supports_mod) {
		double w, h;`, `	if (mod_active() && g_server_supports_mod && obj_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) < 0.5) {
		double w, h;`],
]);
