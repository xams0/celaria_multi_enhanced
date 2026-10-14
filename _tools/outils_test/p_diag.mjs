import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`	collect_players(self, other);
	int allowed = spectating_allowed();`, `	collect_players(self, other);
	{// DIAG
		static double last_diag;
		if (now_ms - last_diag > 400) {
			last_diag = now_ms;
			double f[3], t[3];
			for (int k = 0; k < 3; k++) {
				f[k] = obj_var(OBJ_CAMERA, CAMERA_VARS[k]);
				t[k] = obj_var(OBJ_CAMERA, CAMERA_VARS[3 + k]);
			}
			double yaw = atan2(t[1] - f[1], t[0] - f[0]) * 180 / M_PI;
			double len = sqrt((t[0] - f[0]) * (t[0] - f[0]) + (t[1] - f[1]) * (t[1] - f[1]) + (t[2] - f[2]) * (t[2] - f[2]));
			double pitch = len > 0 ? asin((t[2] - f[2]) / len) * 180 / M_PI : 0;
			mod_log("DIAG cam yaw %.1f pitch %.1f | main_direction %.1f | cam.mouse_direction %.1f z_angle %.1f | ctl.mouse_direction %.1f | pos %.1f %.1f", yaw, pitch,
				obj_var(OBJ_PLAYER, 814), obj_var(OBJ_CAMERA, 934), obj_var(OBJ_CAMERA, 1751), obj_var(OBJ_CONTROLLER, 934), f[0], f[1]);
		}
	}
	int allowed = spectating_allowed();`],
]);
