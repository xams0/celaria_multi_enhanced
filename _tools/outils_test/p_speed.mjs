import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`	{// DIAG speed
		static double last_diag;
		if (now_ms - last_diag > 100 && obj_var(OBJ_PLAYER, 64) > 0.01) {
			last_diag = now_ms;
			mod_log("DIAG speed %.3f xs %.4f ys %.4f fall %.4f", obj_var(OBJ_PLAYER, 64), obj_var(OBJ_PLAYER, 65), obj_var(OBJ_PLAYER, 66), obj_var(OBJ_PLAYER, 547));
		}
	}
`, ``],
[`static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_draw_gui(self, other, result, argc, args);`, `#define VAR_SPEED 64// obj_player._speed: horizontal speed (per step) shown by the speedometer
#define VAR_MOVEMENT_X 960
#define VAR_MOVEMENT_Y 961
#define VAR_GUI_MAP_END_CREATED 624// obj_controller: the map finished screen was created (no speedometer)

static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	// spectator: the speedometer shows the speed of the followed player (velocity of the network player per step)
	NetPlayer *t = spectating && view != VIEW_FREE ? find_player(target_id) : NULL;
	double saved_speed = 0, saved_map_end = 0;
	if (t && t->inst) {
		double mx = yy_to_real(yy_self_var(t->inst, VAR_MOVEMENT_X)), my = yy_to_real(yy_self_var(t->inst, VAR_MOVEMENT_Y));
		saved_speed = obj_var(OBJ_PLAYER, VAR_SPEED);
		saved_map_end = obj_var(OBJ_CONTROLLER, VAR_GUI_MAP_END_CREATED);
		set_obj_var(OBJ_PLAYER, VAR_SPEED, sqrt(mx * mx + my * my));
		set_obj_var(OBJ_CONTROLLER, VAR_GUI_MAP_END_CREATED, 0);
	}
	orig_draw_gui(self, other, result, argc, args);
	if (t && t->inst) {
		set_obj_var(OBJ_PLAYER, VAR_SPEED, saved_speed);
		set_obj_var(OBJ_CONTROLLER, VAR_GUI_MAP_END_CREATED, saved_map_end);
	}`],
]);
