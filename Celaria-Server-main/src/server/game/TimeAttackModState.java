package server.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Time attack mode, features of the mod (same as the cup mode where it makes sense):
 * - observers (casters): they don't race (no start signal) and are listed last in the leaderboard ("SPECTATE");
 * - checkpoint times of the current run of every player (checkpoint ranking of the mod);
 * - checkpoint times of the best run of every player on the map: the mod shows the gap with the best time at every
 *   checkpoint (position and gap in the middle of the screen).
 *
 * Thread safety: every public method is synchronized.
 */
public class TimeAttackModState {

	private final Set<Integer> observers = new HashSet<Integer>();
	private final Map<Integer, List<Long>> runSplits = new HashMap<Integer, List<Long>>();// current run: checkpoint times (ms)
	private final Map<Integer, long[]> bestSplits = new HashMap<Integer, long[]>();// best run: checkpoint times + finish time (ms)

	public synchronized boolean isObserver(int playerID) {
		return observers.contains(playerID);
	}

	/** @return true if the mode changed */
	public synchronized boolean setObserver(int playerID, boolean observer) {
		return observer ? observers.add(playerID) : observers.remove(playerID);
	}

	public synchronized void playerLeft(int playerID) {
		observers.remove(playerID);
		runSplits.remove(playerID);
		bestSplits.remove(playerID);
	}

	public synchronized void mapChanged() {
		runSplits.clear();
		bestSplits.clear();
	}

	public synchronized void runRestarted(int playerID) {
		runSplits.remove(playerID);
	}

	/** @return index of the checkpoint (starting at 1) */
	public synchronized int checkpoint(int playerID, long timeMS) {
		List<Long> splits = runSplits.get(playerID);
		if (splits == null) {
			splits = new ArrayList<Long>();
			runSplits.put(playerID, splits);
		}
		splits.add(timeMS);
		return splits.size();
	}

	/**
	 * The player finished his run.
	 *
	 * @return the "CUPPB" message (best run of the player: checkpoint times and finish time) if this run is his best, else null
	 */
	public synchronized String finished(int playerID, long timeMS) {
		List<Long> splits = runSplits.remove(playerID);
		long[] best = bestSplits.get(playerID);
		if (best != null && best[best.length - 1] <= timeMS) {
			return null;
		}
		int n = splits == null ? 0 : splits.size();
		long[] run = new long[n + 1];
		for (int i = 0; i < n; i++) {
			run[i] = splits.get(i);
		}
		run[n] = timeMS;
		bestSplits.put(playerID, run);
		return bestMessage(playerID, run);
	}

	/** "CUPPB <id> <finish ms> <cp1 ms> <cp2 ms> ..." (the chat message length is sent as u8) */
	private static String bestMessage(int playerID, long[] run) {
		StringBuilder b = new StringBuilder("CUPPB " + playerID + " " + run[run.length - 1]);
		for (int i = 0; i < run.length - 1; i++) {
			String part = " " + run[i];
			if (b.length() + part.length() > 250) {
				break;
			}
			b.append(part);
		}
		return b.toString();
	}

	/** best runs of every player (for a player who joins) */
	public synchronized List<String> bestMessages() {
		List<String> list = new ArrayList<String>();
		for (Map.Entry<Integer, long[]> e : bestSplits.entrySet()) {
			list.add(bestMessage(e.getKey(), e.getValue()));
		}
		return list;
	}

	/** "CUPST <id>:<R|V> ..." for the connected players */
	public synchronized List<String> stateMessages(Iterable<Integer> playerIDs) {
		List<String> messages = new ArrayList<String>();
		StringBuilder b = new StringBuilder("CUPST");
		for (int id : playerIDs) {
			String part = " " + id + ":" + (observers.contains(id) ? 'V' : 'R');
			if (b.length() + part.length() > 250) {
				messages.add(b.toString());
				b = new StringBuilder("CUPST");
			}
			b.append(part);
		}
		messages.add(b.toString());
		return messages;
	}
}
