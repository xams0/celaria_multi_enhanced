package server.bots;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

/**
 * A recorded run (cup mode): positions of the player by race time, checkpoint times and finish time.
 * The best run of every map is saved in ghosts/<map>.json and replayed by the bots.
 */
public class Ghost {

	public static class Sample {
		public long t;// race time (ms)
		public float x, y, z, mx, my, mz, rot, animStep;
		public int anim, respawn;
	}

	public String map;
	public String player;
	public long timeMS;
	public List<Long> checkpoints = new ArrayList<Long>();// race time of every checkpoint (ms)
	public List<Sample> samples = new ArrayList<Sample>();

	/** position at a race time (linear interpolation, the last position after the end) */
	public Sample sample(long t) {
		if (samples.isEmpty()) {
			return null;
		}
		if (t <= samples.get(0).t) {
			return samples.get(0);
		}
		int lo = 0, hi = samples.size() - 1;
		if (t >= samples.get(hi).t) {
			return samples.get(hi);
		}
		while (hi - lo > 1) {
			int mid = (lo + hi) / 2;
			if (samples.get(mid).t <= t) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		Sample a = samples.get(lo), b = samples.get(hi);
		float f = b.t == a.t ? 0 : (float) (t - a.t) / (float) (b.t - a.t);
		Sample s = new Sample();
		s.t = t;
		s.x = a.x + (b.x - a.x) * f;
		s.y = a.y + (b.y - a.y) * f;
		s.z = a.z + (b.z - a.z) * f;
		s.mx = a.mx + (b.mx - a.mx) * f;
		s.my = a.my + (b.my - a.my) * f;
		s.mz = a.mz + (b.mz - a.mz) * f;
		float d = ((b.rot - a.rot) % 360 + 540) % 360 - 180;// shortest rotation
		s.rot = ((a.rot + d * f) % 360 + 360) % 360;
		s.anim = f < 0.5f ? a.anim : b.anim;
		s.animStep = f < 0.5f ? a.animStep : b.animStep;
		s.respawn = f < 0.5f ? a.respawn : b.respawn;
		return s;
	}

	private static File file(String map) {
		String name = map.replaceAll("[^A-Za-z0-9_.-]", "_");
		return new File("ghosts", name + ".json");
	}

	public static Ghost load(String map) {
		File f = file(map);
		if (f.exists() == false) {
			return null;
		}
		try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
			return new Gson().fromJson(r, Ghost.class);
		} catch (Exception e) {
			System.err.println("Unable to read " + f + ": " + e);
			return null;
		}
	}

	public void save() {
		File f = file(map);
		f.getParentFile().mkdirs();
		File tmp = new File(f.getPath() + ".tmp");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
			new Gson().toJson(this, w);
		} catch (IOException e) {
			System.err.println("Unable to write " + f + ": " + e);
			return;
		}
		f.delete();
		tmp.renameTo(f);
	}
}
