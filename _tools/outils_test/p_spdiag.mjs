import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`	collect_players(self, other);
	int allowed = spectating_allowed();`, `	collect_players(self, other);
	{// DIAG speed
		static double last_diag;
		if (now_ms - last_diag > 100 && obj_var(OBJ_PLAYER, 64) > 0.01) {
			last_diag = now_ms;
			mod_log("DIAG speed %.3f xs %.4f ys %.4f fall %.4f", obj_var(OBJ_PLAYER, 64), obj_var(OBJ_PLAYER, 65), obj_var(OBJ_PLAYER, 66), obj_var(OBJ_PLAYER, 547));
		}
	}
	int allowed = spectating_allowed();`],
]);
