import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "ServerProcessor.java", [
[`		if (ready == 0) {
			return 0;
		}
		return ready == total ? 2 : 1;`, `		if (ready == 0) {
			return total > 0 ? 3 : 0;// 3 = players are connected but nobody is ready yet
		}
		return ready == total ? 2 : 1;`],
[`			int ready = playersReadyState();// nobody on the server or still loading: the round doesn't start
			if (waited >= CUP_START_MIN_WAIT_MS && (ready == 2 || (ready == 1 && waited >= CUP_START_MAX_WAIT_MS))) {`,
 `			int ready = playersReadyState();// nobody on the server or still loading: the round doesn't start
			if (waited >= CUP_START_MIN_WAIT_MS && (ready == 2 || (ready == 1 && waited >= CUP_START_MAX_WAIT_MS)
					|| (ready == 3 && waited >= 2 * CUP_START_MAX_WAIT_MS))) {`],
]);
