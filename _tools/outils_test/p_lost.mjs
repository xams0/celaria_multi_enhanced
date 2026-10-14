import { patch } from "./wsrep.mjs";
patch("game/CupState.java", [
[`	/**
	 * The server measures the elapsed time between the synchronized ROUND_START and the reception of the goal message.
	 * The difference between this server time and the client time is the same for every player (network delay + start reaction).
	 * Everything above the smallest difference of the round (+ this tolerance for network jitter) is time the client timer
	 * didn't count (a reset that wasn't detected) and is added to the run time: the ranking always follows the real time.
	 */
	private static final long RESTART_DETECTION_TOLERANCE_MS = 300;`,
 `	/**
	 * The server measures the elapsed time between the synchronized race start and the reception of the goal message.
	 * The difference between this server time and the client time is the network delay of the goal message. Everything above
	 * this delay (+ this tolerance for network jitter and a different countdown setting of a client without the mod) is time
	 * the client timer didn't count (a reset that wasn't detected) and is added to the run time.
	 */
	private static final long RESTART_DETECTION_TOLERANCE_MS = 2500;`],
[`		final long receiveTimeMS;
		long effectiveTime;`, `		final long receiveTimeMS;
		final long expectedDelayMS;// one way network delay of the goal message
		long effectiveTime;`],
[`		RoundFinish(CupPlayer player, long clientTime, long clientPreciseTime, long serverTime, long receiveTimeMS) {`,
 `		RoundFinish(CupPlayer player, long clientTime, long clientPreciseTime, long serverTime, long receiveTimeMS, long expectedDelayMS) {
			this.expectedDelayMS = expectedDelayMS;`],
[`	private void computeEffectiveTimes() {
		long baseline = smallestOffset();
		for (RoundFinish f : roundFinishes) {
			long lost = f.offset() - baseline - RESTART_DETECTION_TOLERANCE_MS;`,
 `	private void computeEffectiveTimes() {
		for (RoundFinish f : roundFinishes) {
			long lost = f.offset() - f.expectedDelayMS - RESTART_DETECTION_TOLERANCE_MS;`],
[`	public synchronized FinishResult playerFinished(int playerID, long clientTicks, long clientPreciseMS, long receiveTimeMS) {`,
 `	public synchronized FinishResult playerFinished(int playerID, long clientTicks, long clientPreciseMS, long receiveTimeMS, long oneWayDelayMS) {`],
[`		RoundFinish finish = new RoundFinish(p, clientTicks * 10L, clientPreciseMS, serverTime, receiveTimeMS);`,
 `		RoundFinish finish = new RoundFinish(p, clientTicks * 10L, clientPreciseMS, serverTime, receiveTimeMS, oneWayDelayMS);`],
]);
patch("player/PlayerProcessor.java", [
[`		CupState.FinishResult result = cup.playerFinished(player.getID(), goalTicks, goalTimeMS, receiveTimeMS);`,
 `		CupState.FinishResult result = cup.playerFinished(player.getID(), goalTicks, goalTimeMS, receiveTimeMS, player.getTimings().getLastPingTime() / 2);`],
]);
