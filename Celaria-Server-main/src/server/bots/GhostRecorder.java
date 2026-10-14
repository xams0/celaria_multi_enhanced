package server.bots;

import java.util.HashMap;
import java.util.Map;

/**
 * Records the runs of the players during the cup rounds (positions received by UDP, with their race time) and keeps the
 * best run of every map (replayed by the bots).
 */
public class GhostRecorder {

	private static final int MAX_SAMPLES = 60000;// 30 minutes at 33 Hz

	private final Map<Integer, Ghost> recordings = new HashMap<Integer, Ghost>();
	private final Map<String, Ghost> best = new HashMap<String, Ghost>();

	/** new round: the recordings start again */
	public synchronized void startRound() {
		recordings.clear();
	}

	public synchronized void addSample(int playerID, long raceTimeMS, double x, double y, double z, float mx, float my, float mz,
			float rot, int anim, float animStep, int respawn) {
		Ghost g = recordings.get(playerID);
		if (g == null) {
			g = new Ghost();
			recordings.put(playerID, g);
		}
		if (g.samples.size() >= MAX_SAMPLES || (g.samples.isEmpty() == false && g.samples.get(g.samples.size() - 1).t >= raceTimeMS)) {
			return;
		}
		Ghost.Sample s = new Ghost.Sample();
		s.t = raceTimeMS;
		s.x = (float) x;
		s.y = (float) y;
		s.z = (float) z;
		s.mx = mx;
		s.my = my;
		s.mz = mz;
		s.rot = rot;
		s.anim = anim;
		s.animStep = animStep;
		s.respawn = respawn;
		g.samples.add(s);
	}

	public synchronized void addCheckpoint(int playerID, long timeMS) {
		Ghost g = recordings.get(playerID);
		if (g != null) {
			g.checkpoints.add(timeMS);
		}
	}

	/** a player finished: his run becomes the ghost of the map if it is the best one */
	public synchronized boolean finished(int playerID, String playerName, String map, long timeMS) {
		Ghost g = recordings.remove(playerID);
		if (g == null || map == null || g.samples.size() < 10) {
			return false;
		}
		Ghost current = bestGhost(map);
		if (current != null && current.timeMS <= timeMS) {
			return false;
		}
		g.map = map;
		g.player = playerName;
		g.timeMS = timeMS;
		best.put(map, g);
		g.save();
		return true;
	}

	public synchronized Ghost bestGhost(String map) {
		if (map == null) {
			return null;
		}
		Ghost g = best.get(map);
		if (g == null) {
			g = Ghost.load(map);
			if (g != null) {
				best.put(map, g);
			}
		}
		return g;
	}
}
