import { patch } from "./wsrep.mjs";
patch("game/CupState.java", [
[`		int points;
		boolean finalist;`, `		int points;
		boolean finalist;
		boolean observer;// caster: doesn't race, isn't ranked`],
[`		public boolean isWinner() {
			return winnerRank > 0;
		}`, `		public boolean isWinner() {
			return winnerRank > 0;
		}

		public boolean isObserver() {
			return observer;
		}

		/** a winner can't race any more until the end of the cup (he can't take points from the other players) */
		public boolean canRace() {
			return connected && observer == false && isWinner() == false;
		}`],
[`	public synchronized void addParticipant(int playerID, double x, double y, double z, int respawnNumber) {
		if (roundRunning && playersByID.containsKey(playerID)) {`, `	public synchronized void addParticipant(int playerID, double x, double y, double z, int respawnNumber) {
		CupPlayer p = playersByID.get(playerID);
		if (roundRunning && p != null && p.canRace()) {`],
[`	public synchronized boolean isParticipant(int playerID) {
		return roundStartTimes.containsKey(playerID);
	}`, `	public synchronized boolean isParticipant(int playerID) {
		return roundStartTimes.containsKey(playerID);
	}

	/** false for winners and observers (they don't take part in the rounds) */
	public synchronized boolean canRace(int playerID) {
		CupPlayer p = playersByID.get(playerID);
		return p == null || p.canRace();
	}

	public synchronized boolean isObserver(int playerID) {
		CupPlayer p = playersByID.get(playerID);
		return p != null && p.observer;
	}

	/**
	 * Observer mode (caster): the player doesn't race and isn't ranked. If he is racing, he leaves the current round.
	 *
	 * @return true if the mode changed
	 */
	public synchronized boolean setObserver(int playerID, boolean observer) {
		CupPlayer p = playersByID.get(playerID);
		if (p == null || p.observer == observer) {
			return false;
		}
		p.observer = observer;
		if (observer && canBeEliminated(playerID)) {
			roundEliminated.add(playerID);
		}
		return true;
	}

	/** state of a player in the current round, for the cup mod: R racing, F finished, O out (reset), W winner, V observer, N not racing */
	private char roundState(CupPlayer p) {
		if (p.observer) {
			return 'V';
		}
		if (p.isWinner()) {
			return 'W';
		}
		if (findFinish(p.playerID) != null) {
			return 'F';
		}
		if (roundEliminated.contains(p.playerID)) {
			return 'O';
		}
		if (roundRunning && roundStartTimes.containsKey(p.playerID)) {
			return 'R';
		}
		return 'N';
	}

	/**
	 * Hidden messages for the cup mod: round on the current map, then the state of every player
	 * ("id:state" or "id:F:time" for finishers). The chat message length is sent as u8: the list is split.
	 */
	public synchronized List<String> buildStateMessages() {
		List<String> messages = new ArrayList<String>();
		int round = roundRunning ? roundsPlayedOnMap + 1 : roundsPlayedOnMap;
		messages.add("\u0001CUPROUND " + round + " " + settings.roundsPerMap + " " + (roundRunning ? 1 : 0) + " " + settings.pointsLimit);
		StringBuilder b = new StringBuilder("\u0001CUPST");
		for (CupPlayer p : playersByID.values()) {
			char state = roundState(p);
			String part = " " + p.playerID + ":" + state;
			RoundFinish f = findFinish(p.playerID);
			if (state == 'F' && f != null) {
				part += ":" + f.effectivePreciseTime;
			}
			if (b.length() + part.length() > 250) {
				messages.add(b.toString());
				b = new StringBuilder("\u0001CUPST");
			}
			b.append(part);
		}
		messages.add(b.toString());
		return messages;
	}`],
[`		boolean remainingPlayers = false;
		for (CupPlayer p : playersByID.values()) {
			if (p.isWinner() == false) {`, `		boolean remainingPlayers = false;
		for (CupPlayer p : playersByID.values()) {
			if (p.isWinner() == false && p.observer == false) {`],
[`	public synchronized List<CupPlayer> getStandings() {
		List<CupPlayer> list = new ArrayList<CupPlayer>(playersByID.values());`, `	public synchronized List<CupPlayer> getStandings() {
		List<CupPlayer> list = new ArrayList<CupPlayer>();
		for (CupPlayer p : playersByID.values()) {
			if (p.observer == false) {
				list.add(p);
			}
		}`],
[`		if (newWinner != null) {
			messages.add(newWinner.name + " WINS the cup" + (settings.nbWinners > 1 ? " (winner #" + newWinner.winnerRank + ")" : "") + "!");
		}`, `		if (newWinner != null) {
			messages.add(newWinner.name + " WINS the cup" + (settings.nbWinners > 1 ? " (winner #" + newWinner.winnerRank + ")" : "") + "!");
			if (winnerCount < settings.nbWinners) {
				messages.add(newWinner.name + " is spectator until the end of the cup.");
			}
		}`],
]);
