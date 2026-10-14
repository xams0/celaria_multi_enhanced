import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "prediction.c", [
[`		double ms = lag_known ? lag_ms[id] : 0;
		for (int k = 0; k < stamps; k++)
			if (ids[k] == id && now >= 0) ms = now - times[k];`, `		double ms = lag_known ? lag_ms[id] : 0;
		int stamped = 0;
		for (int k = 0; k < stamps; k++)
			if (ids[k] == id && now >= 0) {
				ms = now - times[k];
				stamped = 1;
			}
		static double last_log;
		if (clock_local_ms() - last_log > 5000) {
			last_log = clock_local_ms();
			mod_log("prediction: player %d %.0f ms (%s)", id, ms, stamped ? "race time" : "estimated delay");
		}`],
]);
