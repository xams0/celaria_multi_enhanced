import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "clock.c", [
[`#define VAR_START_TIME 1384// obj_timer: value of the timer after a restart (- initial countdown of the settings)`,
 `#define VAR_START_TIME 1384// obj_timer: value of the timer after a restart (- initial countdown of the settings)
#define OBJ_MAP_LOADER 0x16
#define VAR_MLOAD_COMPLETE 895
#define OBJ_CONTROLLER 0x50
#define VAR_MAP_LOAD_ALPHA_OVERLAY 823// fade in after the map loading (the countdown of the game is displayed after it)
#define VAR_ROUND_OVER 1174`],
[`static double saved_start_time;`, `static double saved_start_time;
static int map_ready_sent;// the server knows that the map is displayed (packet 209)`],
[`	aligning = 0;
	restore_start_time();
}

static void recompute(void) {`, `	aligning = 0;
	map_ready_sent = 0;
	restore_start_time();
}

static void recompute(void) {`],
[`	double server_now = clock_server_ms();
	if (countdown_at >= 0 && server_now >= countdown_at) {`, `	// map loaded and displayed (fade in finished): the server starts the round when every player is ready
	int map_ready = get_var(OBJ_MAP_LOADER, VAR_MLOAD_COMPLETE) > 0.5 && get_var(OBJ_CONTROLLER, VAR_MAP_LOAD_ALPHA_OVERLAY) < 0.001;
	if (map_ready == 0 || get_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) > 0.5) {
		map_ready_sent = 0;// map loading, or round over (the map is reloaded for the next round)
	} else if (map_ready_sent == 0) {
		map_ready_sent = 1;
		RValue value = { 0 };
		value.real = 1;
		net_send_tcp(self, other, 209, BUFFER_U8, &value);
		mod_log("map ready");
	}
	double server_now = clock_server_ms();
	if (countdown_at >= 0 && server_now >= countdown_at) {
		// the countdown of the game is only displayed after the fade in of the map
		if (get_var(OBJ_MAP_LOADER, VAR_MLOAD_COMPLETE) > 0.5 && get_var(OBJ_CONTROLLER, VAR_MAP_LOAD_ALPHA_OVERLAY) > 0) {
			set_var(OBJ_CONTROLLER, VAR_MAP_LOAD_ALPHA_OVERLAY, 0);
			mod_log("fade in of the map finished for the countdown");
		}`],
]);
