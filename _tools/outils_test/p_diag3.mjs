import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`			mod_log("DIAG cam yaw %.1f pitch %.1f | main_direction %.1f | player.mouse_direction %.1f z_angle %.1f | cam.mouse_direction %.1f z_angle %.1f | pos %.1f %.1f", yaw, pitch,
				safe_var(OBJ_PLAYER, 814), safe_var(OBJ_PLAYER, 934), safe_var(OBJ_PLAYER, 1751), safe_var(OBJ_CAMERA, 934), safe_var(OBJ_CAMERA, 1751), f[0], f[1]);`,
`			(void)yaw;
			(void)pitch;
			mod_log("DIAG md %.1f za %.1f fo %.2f | obj0 from %.2f %.2f %.2f to %.2f %.2f %.2f | proj from %.2f %.2f %.2f to %.2f %.2f %.2f | player %.2f %.2f %.2f",
				safe_var(OBJ_CAMERA, 934), safe_var(OBJ_CAMERA, 1751), safe_var(OBJ_CAMERA, 570), f[0], f[1], f[2], t[0], t[1], t[2],
				game_from[0], game_from[1], game_from[2], game_to[0], game_to[1], game_to[2],
				obj_var(OBJ_PLAYER, VAR_Z) * 0 + safe_var(OBJ_PLAYER, 1708) * 0 + 0, 0.0, obj_var(OBJ_PLAYER, VAR_Z));`],
]);
