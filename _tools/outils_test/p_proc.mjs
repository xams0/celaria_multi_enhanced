import { patch } from "./wsrep.mjs";
patch("ServerProcessor.java", [
[`			if (p.getPlayerServerData().isSigned()) {
				total++;`, `			if (p.getPlayerServerData().isSigned() && core.getGameState().getCup().canRace(p.getID())) {// winners and observers don't race
				total++;`],
[`						lastPlayerDelaysSent = System.currentTimeMillis();
						core.cupSendPlayerDelays();`, `						lastPlayerDelaysSent = System.currentTimeMillis();
						core.cupSendPlayerDelays();
						core.cupSendState();`],
[`							state.startEndRoundTimer(cup.isMatchOver() ? 15*1000L : 10*1000L);`, `							state.startEndRoundTimer(cup.isMatchOver() ? 15*1000L : 10*1000L);
							core.cupSendState();`],
]);
