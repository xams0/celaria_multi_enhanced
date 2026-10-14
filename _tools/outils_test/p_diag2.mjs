import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`static void set_obj_var(int instOrObject, int varId, double value) {`, `// -9999 if the variable isn't set (YY_REAL on an unset value is a fatal error of the game)
static double safe_var(int instOrObject, int varId) {
	RValue v = { 0 };
	v.kind = KIND_UNDEFINED;
	YY_VAR_GET(instOrObject, varId, ARRAY_NONE, &v);
	int kind = v.kind & 0xffffff;
	double d = kind == KIND_REAL ? v.real : -9999;
	yy_clear(&v);
	return d;
}

static void set_obj_var(int instOrObject, int varId, double value) {`],
[`			mod_log("DIAG cam yaw %.1f pitch %.1f | main_direction %.1f | cam.mouse_direction %.1f z_angle %.1f | ctl.mouse_direction %.1f | pos %.1f %.1f", yaw, pitch,
				obj_var(OBJ_PLAYER, 814), obj_var(OBJ_CAMERA, 934), obj_var(OBJ_CAMERA, 1751), obj_var(OBJ_CONTROLLER, 934), f[0], f[1]);`,
`			mod_log("DIAG cam yaw %.1f pitch %.1f | main_direction %.1f | player.mouse_direction %.1f z_angle %.1f | cam.mouse_direction %.1f z_angle %.1f | pos %.1f %.1f", yaw, pitch,
				safe_var(OBJ_PLAYER, 814), safe_var(OBJ_PLAYER, 934), safe_var(OBJ_PLAYER, 1751), safe_var(OBJ_CAMERA, 934), safe_var(OBJ_CAMERA, 1751), f[0], f[1]);`],
]);
