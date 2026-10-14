import { patch } from "./wsrep.mjs";
patch("ServerProcessor.java", [
[`	private static final long CUP_COUNTDOWN_MS = 3*1000L;`, `	// countdown of the game (3, 2, 1, Go!) started by the ROUND_START message (initial countdown of the default settings,
	// the mod forces it): the race starts CUP_GAME_COUNTDOWN_MS after the ROUND_START message
	public static final long CUP_GAME_COUNTDOWN_MS = 3*1000L;
	private static final long CUP_ANNOUNCE_ADVANCE_MS = 700;// time for the scheduled start message to reach the clients`],
[`			if (waited >= CUP_START_MIN_WAIT_MS && (ready == 2 || (ready == 1 && waited >= CUP_START_MAX_WAIT_MS))) {
				double startClock = nowClock + CUP_COUNTDOWN_MS;`, `			if (waited >= CUP_START_MIN_WAIT_MS && (ready == 2 || (ready == 1 && waited >= CUP_START_MAX_WAIT_MS))) {
				double startClock = nowClock + CUP_ANNOUNCE_ADVANCE_MS + CUP_GAME_COUNTDOWN_MS;// race start (end of the countdown)`],
[`		double remaining = state.getCupStartClockMS() - nowClock;
		if (remaining > CUP_START_SEND_ADVANCE_MS) {
			int sec = (int) Math.ceil(remaining / 1000.0);
			if (sec != lastStartCountdownAnnounce) {
				lastStartCountdownAnnounce = sec;
				core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Start in " + sec + "...");
			}
			return;
		}
		//GO: every player who loaded the map starts now`, `		double remaining = state.getCupStartClockMS() - CUP_GAME_COUNTDOWN_MS - nowClock;// until the start of the countdown
		if (remaining > CUP_START_SEND_ADVANCE_MS) {
			return;// the countdown is displayed by the game (3, 2, 1, Go!)
		}
		//countdown of every player who loaded the map: the race starts at the scheduled time`],
[`		core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "GO! Cup - round "+(cup.getRoundsPlayedOnMap()+1)+"/"+cup.getSettings().roundsPerMap+" on this map. Points limit: "+cup.getSettings().pointsLimit);`,
 `		core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Cup - round "+(cup.getRoundsPlayedOnMap()+1)+"/"+cup.getSettings().roundsPerMap+" on this map. Points limit: "+cup.getSettings().pointsLimit);`],
]);
patch("ServerCore.java", [
[`		final long startTime = ServerClock.toWallMS(startClock);// time when every client gets the start message (scheduled start)`,
 `		final long startTime = ServerClock.toWallMS(startClock);// race start (end of the countdown of the game)
		final double countdownClock = startClock - ServerProcessor.CUP_GAME_COUNTDOWN_MS;// every client gets ROUND_START at this time`],
[`			double sendClock = startClock - delay;`, `			double sendClock = countdownClock - delay;`],
]);
