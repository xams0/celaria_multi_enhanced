import { patch } from "./wsrep.mjs";
patch("game/CupState.java", [[`	private long smallestOffset() {
		long min = Long.MAX_VALUE;
		for (RoundFinish f : roundFinishes) {
			min = Math.min(min, f.offset());
		}
		return min;
	}`, ``]]);
