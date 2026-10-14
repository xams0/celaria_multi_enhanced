package server.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import server.util.Util;

/**
 * Cup mode (same rules as the Trackmania "Cup" mode).
 *
 * - A match is played over several rounds (a fixed number of rounds per map, then the next map).
 * - Every round all players start at the same time (synchronized start) and race once. Respawning at the last checkpoint is
 *   allowed, a complete reset of the run eliminates the player for the round (he becomes spectator).
 *   Finishers get points depending on their rank
 *   (points repartition, default 10,6,4,3,2,1; players ranked below the list get the last value, non-finishers get 0).
 * - A player reaching the points limit becomes a FINALIST (his score is locked to the limit).
 * - A finalist who wins a round (best finisher among the players who didn't win yet) WINS the cup.
 * - The match is over once the configured number of winners is reached. A new match then starts on the next map.
 *
 * All times are in milliseconds. The client measures and sends its run time in ticks (1/100 second, one engine step):
 * the server converts it to milliseconds and measures the real elapsed time since the synchronized start to the millisecond.
 *
 * Thread safety: every public method is synchronized (called from the player processor and the server processor threads).
 */
public class CupState {

		/**
	 * The server measures the elapsed time between the synchronized race start and the reception of the goal message.
	 * The difference between this server time and the client time is the network delay of the goal message. Everything above
	 * this delay (+ this tolerance for network jitter and a different countdown setting of a client without the mod) is time
	 * the client timer didn't count (a reset that wasn't detected) and is added to the run time.
	 */
	private static final long RESTART_DETECTION_TOLERANCE_MS = 2500;

	/**
	 * Complete reset detection: the client increments its respawn counter on every respawn. A respawn at the last checkpoint is
	 * allowed, a respawn which puts the player back on his start position (after he left it) is a complete reset of the run.
	 */
	private static final double SPAWN_RADIUS = 1.5;
	private static final double LEFT_SPAWN_DISTANCE = 4.0;

	public static class Settings {
		public int pointsLimit = 100;
		public int roundsPerMap = 5;
		public int nbWinners = 1;
		public int[] pointsRepartition = new int[] { 10, 6, 4, 3, 2, 1 };
		public int finishTimeoutSec = -1;// -1 = automatic (5 seconds + platinum medal time / 6)
		public int roundTimeLimitSec = 300;// safety limit if nobody finishes
		// the respawn key without checkpoint (back to the start): DNF? With the mod the timer continues (no DNF needed);
		// without the mod the run restarts and the server adds the lost time
		public boolean respawnAtStartDNF = false;
		public boolean forceRestartDNF = true;// the restart key (complete reset of the run) eliminates the player for the round
		public double endRoundDelaySec = 5;// leaderboard displayed at the end of a round
		public double endMatchDelaySec = 10;// leaderboard displayed at the end of a cup
		public double allFinishedDelaySec = 1;// end of the round once every player finished
		public double startDelaySec = 1.5;// minimum time between the map load and the countdown
		public int warmupRounds = 1;// warmup rounds at the start of every map (no points)

		public String repartitionToString() {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < pointsRepartition.length; i++) {
				if (i > 0) {
					b.append(",");
				}
				b.append(pointsRepartition[i]);
			}
			return b.toString();
		}
	}

	public static class CupPlayer {
		final String key;// steamID (or name for offline players). Used to keep the score if a player reconnects.
		int playerID;
		String name;
		byte badge;
		boolean connected;

		int points;
		boolean finalist;
		boolean observer;// caster: doesn't race, isn't ranked
		int winnerRank;// 0 = not a winner, 1 = first winner, ...

		// results of the last finished round
		int lastRoundPoints;
		int lastRoundRank;// 0 = didn't finish

		CupPlayer(String key, int playerID, String name, byte badge) {
			this.key = key;
			this.playerID = playerID;
			this.name = name;
			this.badge = badge;
			this.connected = true;
		}

		public int getPlayerID() {
			return playerID;
		}

		public String getName() {
			return name;
		}

		public byte getBadge() {
			return badge;
		}

		public int getPoints() {
			return points;
		}

		public boolean isFinalist() {
			return finalist;
		}

		public boolean isWinner() {
			return winnerRank > 0;
		}

		public boolean isObserver() {
			return observer;
		}

		/** a winner can't race any more until the end of the cup (he can't take points from the other players) */
		public boolean canRace() {
			return connected && observer == false && isWinner() == false;
		}

		public int getWinnerRank() {
			return winnerRank;
		}

		public int getLastRoundPoints() {
			return lastRoundPoints;
		}

		public int getLastRoundRank() {
			return lastRoundRank;
		}

		/**
		 * Text shown in front of the name in the leaderboard of the client.
		 */
		public String getStatusText() {
			if (isWinner()) {
				return winnerRank > 1 ? "WINNER " + winnerRank : "WINNER";// no "#": line break in the texts of GameMaker
			}
			if (finalist) {
				return "FINALIST";
			}
			return points + " pts";
		}
	}

	private static class RoundFinish {
		final CupPlayer player;
		final long clientTime;// official client time in ms (client ticks * 10, same value with or without the mod)
		final long clientPreciseTime;// milliseconds sent by the client mod (= clientTime without the mod)
		final long serverTime;// ms measured by the server since the start of the round
		final long receiveTimeMS;
		final long expectedDelayMS;// one way network delay of the goal message
		long effectiveTime;// official time + time lost (ranking)
		long effectivePreciseTime;// precise time + time lost (tie break and display)

		RoundFinish(CupPlayer player, long clientTime, long clientPreciseTime, long serverTime, long receiveTimeMS, long expectedDelayMS) {
			this.expectedDelayMS = expectedDelayMS;
			this.player = player;
			this.clientTime = clientTime;
			this.clientPreciseTime = clientPreciseTime;
			this.serverTime = serverTime;
			this.receiveTimeMS = receiveTimeMS;
			this.effectiveTime = clientTime;
			this.effectivePreciseTime = clientPreciseTime;
		}

		long offset() {
			return serverTime - clientTime;
		}
	}

	/**
	 * Result of a goal message.
	 */
	public static class FinishResult {
		public final boolean firstFinisher;
		public final int position;// provisional position in the round (starting at 1)
		public final long time;// provisional run time (ms, precise)
		public final long officialTime;// provisional run time (ms, official time + time lost)
		public final boolean allFinished;

		FinishResult(boolean firstFinisher, int position, long time, long officialTime, boolean allFinished) {
			this.firstFinisher = firstFinisher;
			this.position = position;
			this.time = time;
			this.officialTime = officialTime;
			this.allFinished = allFinished;
		}
	}

	/**
	 * Entry of the leaderboard sent to the clients.
	 */
	public static class CupLeaderboardEntry {
		public final int playerID;
		public final String displayName;
		public final byte badge;
		public final boolean timeSet;
		public final long time;// precise time (ms)
		public final long officialTime;// official time (ms, multiple of 10): displayed by the clients without the mod

		CupLeaderboardEntry(int playerID, String displayName, byte badge, boolean timeSet, long time, long officialTime) {
			this.playerID = playerID;
			this.displayName = displayName;
			this.badge = badge;
			this.timeSet = timeSet;
			this.time = time;
			this.officialTime = officialTime;
		}
	}

	private final Settings settings;

	private final Map<String, CupPlayer> playersByKey;
	private final Map<Integer, CupPlayer> playersByID;

	// current round
	private boolean roundRunning;
	private final Map<Integer, Long> roundStartTimes;// playerID -> time the ROUND_START message was sent (ms)
	private final List<RoundFinish> roundFinishes;
	private final Set<Integer> roundEliminated;// players who reset their run during the round (spectators)
	private final Map<Integer, double[]> spawnPositions;
	private final Map<Integer, Integer> respawnNumbers;
	private final Set<Integer> leftSpawn;
	private long roundStartTimeMS;

	private int roundsPlayedOnMap;
	private int warmupPlayedOnMap;// warmup rounds played on the current map
	private boolean warmupRound;// the current round is a warmup round
	private int totalRounds;
	private int winnerCount;
	private boolean matchOver;

	public CupState(Settings settings) {
		this.settings = settings;
		playersByKey = new HashMap<String, CupPlayer>();
		playersByID = new HashMap<Integer, CupPlayer>();
		roundStartTimes = new HashMap<Integer, Long>();
		roundFinishes = new ArrayList<RoundFinish>();
		roundEliminated = new HashSet<Integer>();
		spawnPositions = new HashMap<Integer, double[]>();
		respawnNumbers = new HashMap<Integer, Integer>();
		leftSpawn = new HashSet<Integer>();
		resetMatch();
	}

	public Settings getSettings() {
		return settings;
	}

	// ======================= players =======================

	public synchronized void playerJoined(int playerID, String key, String name, byte badge) {
		if (key == null || key.isEmpty() || key.equals("0")) {
			key = "name:" + name;
		}
		CupPlayer p = playersByKey.get(key);
		if (p == null || p.connected) {
			if (p != null) {
				// same key already connected (should not happen with steam IDs). Don't merge the players.
				key = key + "#" + playerID;
			}
			p = new CupPlayer(key, playerID, name, badge);
			playersByKey.put(key, p);
		} else {
			// player reconnected during the match: keep his score
			p.playerID = playerID;
			p.name = name;
			p.badge = badge;
			p.connected = true;
		}
		playersByID.put(playerID, p);
	}

	public synchronized void playerLeft(int playerID) {
		CupPlayer p = playersByID.remove(playerID);
		if (p != null) {
			p.connected = false;
		}
		roundStartTimes.remove(playerID);
	}

	public synchronized void setBadge(int playerID, byte badge) {
		CupPlayer p = playersByID.get(playerID);
		if (p != null) {
			p.badge = badge;
		}
	}

	public synchronized CupPlayer getPlayer(int playerID) {
		return playersByID.get(playerID);
	}

	// ======================= match / map =======================

	public synchronized void resetMatch() {
		// forget disconnected players and reset the scores of the others
		List<String> removeKeys = new ArrayList<String>();
		for (CupPlayer p : playersByKey.values()) {
			if (p.connected == false) {
				removeKeys.add(p.key);
			}
			p.points = 0;
			p.finalist = false;
			p.winnerRank = 0;
			p.lastRoundPoints = 0;
			p.lastRoundRank = 0;
		}
		for (String k : removeKeys) {
			playersByKey.remove(k);
		}
		roundRunning = false;
		roundStartTimes.clear();
		clearRound();
		roundsPlayedOnMap = 0;
		totalRounds = 0;
		winnerCount = 0;
		matchOver = false;
	}

	public synchronized boolean isMatchOver() {
		return matchOver;
	}

	/**
	 * @return true if the next round has to be played on the current map.
	 */
	public synchronized boolean replayCurrentMap() {
		return matchOver == false && roundsPlayedOnMap < settings.roundsPerMap;
	}

	public synchronized void mapChanged() {
		roundsPlayedOnMap = 0;
		warmupPlayedOnMap = 0;
	}

	/** the next round (or the current one) is a warmup round: no points */
	public synchronized boolean isWarmup() {
		return roundRunning ? warmupRound : warmupPlayedOnMap < settings.warmupRounds;
	}

	/** warmup round number (starting at 1) of the current/next warmup round */
	public synchronized int getWarmupIndex() {
		return roundRunning && warmupRound ? warmupPlayedOnMap + 1 : Math.min(warmupPlayedOnMap + 1, settings.warmupRounds);
	}

	/** the round is cancelled (map changed by an admin): no points */
	public synchronized void cancelRound() {
		roundRunning = false;
		warmupRound = false;
		clearRound();
	}

	public synchronized int getRoundsPlayedOnMap() {
		return roundsPlayedOnMap;
	}

	public synchronized int getTotalRounds() {
		return totalRounds;
	}

	// ======================= rounds =======================

	private void clearRound() {
		roundStartTimes.clear();
		roundFinishes.clear();
		roundEliminated.clear();
		spawnPositions.clear();
		respawnNumbers.clear();
		leftSpawn.clear();
	}

	/**
	 * Starts the round. All players start at the same time.
	 */
	public synchronized void startRound(long startTimeMS) {
		warmupRound = warmupPlayedOnMap < settings.warmupRounds;
		roundRunning = true;
		clearRound();
		roundStartTimeMS = startTimeMS;
	}

	/**
	 * Common start time of the round (the start messages are sent so that every client gets them at this time).
	 */
	public synchronized void setRoundStartTime(long startTimeMS) {
		roundStartTimeMS = startTimeMS;
	}

	/**
	 * Adds a player to the round (the synchronized ROUND_START message was sent to him).
	 * x/y/z: start position of the player, respawnNumber: respawn counter of the client.
	 */
		public synchronized void addParticipant(int playerID, double x, double y, double z, int respawnNumber) {
		CupPlayer p = playersByID.get(playerID);
		if (roundRunning && p != null && p.canRace()) {
			roundStartTimes.put(playerID, roundStartTimeMS);
			spawnPositions.put(playerID, new double[] { x, y, z });
			respawnNumbers.put(playerID, respawnNumber);
		}
	}

		public synchronized boolean isParticipant(int playerID) {
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
		public synchronized List<String> buildStateMessages(double raceStartClock) {
		List<String> messages = new ArrayList<String>();
		int round = roundRunning || isWarmup() ? roundsPlayedOnMap + 1 : roundsPlayedOnMap;
		messages.add("CUPROUND " + round + " " + settings.roundsPerMap + " " + (roundRunning ? 1 : 0) + " " + settings.pointsLimit + " " + server.util.ServerClock.format(raceStartClock));
		messages.add("CUPWU " + (isWarmup() ? 1 : 0) + " " + getWarmupIndex() + " " + settings.warmupRounds + " -1");
		StringBuilder b = new StringBuilder("CUPST");
		for (CupPlayer p : playersByID.values()) {
			char state = roundState(p);
			String part = " " + p.playerID + ":" + state;
			RoundFinish f = findFinish(p.playerID);
			if (state == 'F' && f != null) {
				part += ":" + f.effectivePreciseTime;
			}
			if (b.length() + part.length() > 250) {
				messages.add(b.toString());
				b = new StringBuilder("CUPST");
			}
			b.append(part);
		}
		messages.add(b.toString());
		return messages;
	}

	private boolean canBeEliminated(int playerID) {
		return roundRunning && roundStartTimes.containsKey(playerID) && roundEliminated.contains(playerID) == false
				&& findFinish(playerID) == null;
	}

	/**
	 * Complete reset of the run: the player is eliminated for the round.
	 *
	 * @return true if the player was eliminated by this call.
	 */
	public synchronized boolean eliminate(int playerID) {
		if (warmupRound || canBeEliminated(playerID) == false) {// warmup: practice, no DNF
			return false;
		}
		roundEliminated.add(playerID);
		return true;
	}

	public synchronized boolean isEliminated(int playerID) {
		return roundEliminated.contains(playerID);
	}

	/**
	 * Position update of a player (UDP).
	 *
	 * @return true if a complete reset was detected.
	 */
	public synchronized boolean playerStateUpdate(int playerID, int respawnNumber, double x, double y, double z) {
		if (canBeEliminated(playerID) == false) {
			return false;
		}
		double[] spawn = spawnPositions.get(playerID);
		double dx = x - spawn[0], dy = y - spawn[1], dz = z - spawn[2];
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (distance > LEFT_SPAWN_DISTANCE) {
			leftSpawn.add(playerID);
		}
		Integer last = respawnNumbers.put(playerID, respawnNumber);
		if (last != null && last != respawnNumber && leftSpawn.contains(playerID) && distance < SPAWN_RADIUS) {
			return true;// the caller eliminates the player (see eliminate())
		}
		return false;
	}

	public synchronized boolean isRoundRunning() {
		return roundRunning;
	}

	private RoundFinish findFinish(int playerID) {
		for (RoundFinish f : roundFinishes) {
			if (f.player.playerID == playerID) {
				return f;
			}
		}
		return null;
	}

	

		private void computeEffectiveTimes() {
		for (RoundFinish f : roundFinishes) {
			long lost = f.offset() - f.expectedDelayMS - RESTART_DETECTION_TOLERANCE_MS;
			f.effectiveTime = f.clientTime + Math.max(0, lost);
			f.effectivePreciseTime = f.clientPreciseTime + Math.max(0, lost);
		}
		Collections.sort(roundFinishes, new Comparator<RoundFinish>() {
			@Override
			public int compare(RoundFinish a, RoundFinish b) {
				// official time first (1/100 second, a client without the mod isn't disadvantaged), then the milliseconds
				if (a.effectiveTime != b.effectiveTime) {
					return Long.compare(a.effectiveTime, b.effectiveTime);
				}
				if (a.effectivePreciseTime != b.effectivePreciseTime) {
					return Long.compare(a.effectivePreciseTime, b.effectivePreciseTime);
				}
				return Long.compare(a.receiveTimeMS, b.receiveTimeMS);
			}
		});
	}

	/**
	 * Called when a player reached the goal.
	 *
	 * @return null if the finish doesn't count (round not running, player already finished this round, didn't start the round or was eliminated).
	 */
		public synchronized FinishResult playerFinished(int playerID, long clientTicks, long clientPreciseMS, long receiveTimeMS, long oneWayDelayMS) {
		if (roundRunning == false) {
			return null;
		}
		CupPlayer p = playersByID.get(playerID);
		Long start = roundStartTimes.get(playerID);
		if (p == null || start == null || findFinish(playerID) != null || roundEliminated.contains(playerID)) {
			return null;
		}
		long serverTime = Math.max(0, receiveTimeMS - start);
		RoundFinish finish = new RoundFinish(p, clientTicks * 10L, clientPreciseMS, serverTime, receiveTimeMS, oneWayDelayMS);
		boolean first = roundFinishes.isEmpty();
		roundFinishes.add(finish);
		computeEffectiveTimes();

		return new FinishResult(first, roundFinishes.indexOf(finish) + 1, finish.effectivePreciseTime, finish.effectiveTime, allParticipantsFinishedInternal());
	}

	/**
	 * @return true if every player of the round finished, was eliminated or left the server.
	 */
	private boolean allParticipantsFinishedInternal() {
		if (roundStartTimes.isEmpty()) {
			return roundRunning;// nobody started the round
		}
		for (Integer id : roundStartTimes.keySet()) {
			if (playersByID.containsKey(id) && roundEliminated.contains(id) == false && findFinish(id) == null) {
				return false;
			}
		}
		return true;
	}

	public synchronized boolean allParticipantsFinished() {
		return allParticipantsFinishedInternal();
	}

	public synchronized boolean hasFinishers() {
		return roundFinishes.isEmpty() == false;
	}

	/**
	 * Finish timeout (in ms) started by the first finisher.
	 */
	public long getFinishTimeoutMS(long platinumMedalTime) {
		if (settings.finishTimeoutSec >= 0) {
			return settings.finishTimeoutSec * 1000L;
		}
		if (platinumMedalTime > 0) {
			return 5000L + (platinumMedalTime * 10L) / 6L;// ticks -> ms
		}
		return 15000L;
	}

	public int getPointsForRank(int rank) {// rank starting at 1
		int[] rep = settings.pointsRepartition;
		if (rep.length == 0) {
			return 0;
		}
		return rep[Math.min(rank, rep.length) - 1];
	}

	/**
	 * Ends the round: ranks the finishers, gives the points and handles finalists/winners.
	 *
	 * @return messages to broadcast to the players.
	 */
	public synchronized List<String> endRound() {
		List<String> messages = new ArrayList<String>();
		if (roundRunning == false) {
			return messages;
		}
		roundRunning = false;
		for (CupPlayer p : playersByKey.values()) {
			p.lastRoundPoints = 0;
			p.lastRoundRank = 0;
		}
		computeEffectiveTimes();

		if (warmupRound) {
			// warmup: no points, the round doesn't count
			warmupRound = false;
			warmupPlayedOnMap++;
			StringBuilder results = new StringBuilder("Warmup " + warmupPlayedOnMap + "/" + settings.warmupRounds + " over");
			for (int i = 0; i < roundFinishes.size() && i < 3; i++) {
				RoundFinish f = roundFinishes.get(i);
				results.append(i == 0 ? ": " : ", ").append(i + 1).append(". ").append(f.player.name).append(" ").append(Util.timeFormatMS(f.effectivePreciseTime));
			}
			messages.add(results.toString());
			if (warmupPlayedOnMap >= settings.warmupRounds) {
				messages.add("End of the warmup: the next round counts!");
			}
			return messages;
		}
		roundsPlayedOnMap++;
		totalRounds++;

		if (roundFinishes.isEmpty()) {
			messages.add("Round " + totalRounds + ": nobody finished.");
			return messages;
		}

		// 1) a finalist who wins the round wins the cup (status before this round counts)
		CupPlayer roundWinner = null;
		for (RoundFinish f : roundFinishes) {
			if (f.player.isWinner() == false) {
				roundWinner = f.player;
				break;
			}
		}
		CupPlayer newWinner = null;
		if (roundWinner != null && roundWinner.finalist) {
			winnerCount++;
			roundWinner.winnerRank = winnerCount;
			newWinner = roundWinner;
		}

		// 2) points
		StringBuilder results = new StringBuilder("Round " + totalRounds + ": ");
		List<CupPlayer> newFinalists = new ArrayList<CupPlayer>();
		for (int i = 0; i < roundFinishes.size(); i++) {
			RoundFinish f = roundFinishes.get(i);
			CupPlayer p = f.player;
			int rank = i + 1;
			int pts = getPointsForRank(rank);
			p.lastRoundRank = rank;

			if (i < 3) {
				if (i > 0) {
					results.append(", ");
				}
				results.append(rank).append(". ").append(p.name).append(" ").append(Util.timeFormatMS(f.effectivePreciseTime));
			}

			if (p.isWinner() || p.finalist) {
				continue;// finalists keep the points limit as score
			}
			p.lastRoundPoints = pts;
			p.points += pts;
			if (p.points >= settings.pointsLimit) {
				p.points = settings.pointsLimit;
				p.finalist = true;
				newFinalists.add(p);
			}
		}
		messages.add(results.toString());

		for (CupPlayer p : newFinalists) {
			messages.add(p.name + " is now FINALIST! Win a round to win the cup.");
		}
		if (newWinner != null) {
			messages.add(newWinner.name + " WINS the cup" + (settings.nbWinners > 1 ? " (winner " + newWinner.winnerRank + ")" : "") + "!");
			if (winnerCount < settings.nbWinners) {
				messages.add(newWinner.name + " is spectator until the end of the cup.");
			}
		}

		// 3) end of match
		boolean remainingPlayers = false;
		for (CupPlayer p : playersByID.values()) {
			if (p.isWinner() == false && p.observer == false) {
				remainingPlayers = true;
				break;
			}
		}
		if (winnerCount >= settings.nbWinners || (winnerCount > 0 && remainingPlayers == false)) {
			matchOver = true;
			messages.add("The cup is over! A new cup starts on the next map.");
		}

		return messages;
	}

	// ======================= ranking =======================

	private static final Comparator<CupPlayer> STANDINGS_ORDER = new Comparator<CupPlayer>() {
		@Override
		public int compare(CupPlayer a, CupPlayer b) {
			if (a.isWinner() != b.isWinner()) {
				return a.isWinner() ? -1 : 1;
			}
			if (a.isWinner()) {
				return Integer.compare(a.winnerRank, b.winnerRank);
			}
			if (a.finalist != b.finalist) {
				return a.finalist ? -1 : 1;
			}
			if (a.points != b.points) {
				return Integer.compare(b.points, a.points);
			}
			// tie: best rank in the last round
			int ra = a.lastRoundRank == 0 ? Integer.MAX_VALUE : a.lastRoundRank;
			int rb = b.lastRoundRank == 0 ? Integer.MAX_VALUE : b.lastRoundRank;
			if (ra != rb) {
				return Integer.compare(ra, rb);
			}
			return a.name.compareTo(b.name);
		}
	};

	/**
	 * @return connected players sorted by cup ranking.
	 */
		public synchronized List<CupPlayer> getStandings() {
		List<CupPlayer> list = new ArrayList<CupPlayer>();
		for (CupPlayer p : playersByID.values()) {
			if (p.observer == false) {
				list.add(p);
			}
		}
		Collections.sort(list, STANDINGS_ORDER);
		return list;
	}

	/**
	 * Builds the leaderboard displayed by the clients.
	 * While a round is running: finishers of the round (by time) followed by the other players (by cup ranking).
	 * Between rounds: cup ranking with the points won in the last round.
	 */
	public synchronized List<CupLeaderboardEntry> buildLeaderboard() {
		List<CupLeaderboardEntry> entries = new ArrayList<CupLeaderboardEntry>();
		List<CupPlayer> standings = getStandings();

		if (roundRunning) {
			for (RoundFinish f : roundFinishes) {
				if (f.player.connected) {
					entries.add(new CupLeaderboardEntry(f.player.playerID, f.player.getStatusText() + " | " + f.player.name, f.player.badge, true, f.effectivePreciseTime, f.effectiveTime));
				}
			}
			for (CupPlayer p : standings) {
				if (findFinish(p.playerID) == null) {
					String text = p.getStatusText();
					if (roundEliminated.contains(p.playerID)) {
						text += " (out)";
					} else if (roundStartTimes.containsKey(p.playerID) == false) {
						text += " (spec)";
					}
					entries.add(new CupLeaderboardEntry(p.playerID, text + " | " + p.name, p.badge, false, 0, 0));
				}
			}
		} else {
			for (CupPlayer p : standings) {
				String text = p.getStatusText();
				if (p.lastRoundPoints > 0) {
					text += " (+" + p.lastRoundPoints + ")";
				}
				RoundFinish f = findFinish(p.playerID);
				entries.add(new CupLeaderboardEntry(p.playerID, text + " | " + p.name, p.badge, f != null, f != null ? f.effectivePreciseTime : 0, f != null ? f.effectiveTime : 0));
			}
		}
		// observers (casters): out of the competition, below the players
		List<CupPlayer> observers = new ArrayList<CupPlayer>();
		for (CupPlayer p : playersByID.values()) {
			if (p.observer) {
				observers.add(p);
			}
		}
		Collections.sort(observers, new Comparator<CupPlayer>() {
			@Override
			public int compare(CupPlayer a, CupPlayer b) {
				return a.name.compareTo(b.name);
			}
		});
		for (CupPlayer p : observers) {
			entries.add(new CupLeaderboardEntry(p.playerID, "SPECTATE | " + p.name, p.badge, false, 0, 0));
		}
		return entries;
	}
}
