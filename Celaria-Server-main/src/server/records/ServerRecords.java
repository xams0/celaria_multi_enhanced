package server.records;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Server records: best time of every player on every map, stored in a JSON file in the server folder.
 *
 * Times are in milliseconds (clients with the cup mod send their time to the millisecond, the others to 1/100 second).
 * The ranking uses the official time (1/100 second, same value with or without the mod) first, then the milliseconds.
 *
 * Thread safety: all public methods are synchronized.
 */
public class ServerRecords {

	public static class Record {
		public String name;
		public String steamId;
		public long timeTicks;// official time (1/100 second)
		public long timeMS;// precise time (milliseconds)
		public long date;// unix time (ms)

		public Record(String name, String steamId, long timeTicks, long timeMS, long date) {
			this.name = name;
			this.steamId = steamId;
			this.timeTicks = timeTicks;
			this.timeMS = timeMS;
			this.date = date;
		}

		boolean isBetterThan(Record other) {
			if (timeTicks != other.timeTicks) {
				return timeTicks < other.timeTicks;
			}
			return timeMS < other.timeMS;
		}
	}

	/**
	 * Result of a new time.
	 */
	public static class Result {
		public final boolean personalBest;
		public final boolean serverRecord;
		public final int rank;// rank on the map (starting at 1)
		public final Record previousRecord;// previous server record (null if none)

		Result(boolean personalBest, boolean serverRecord, int rank, Record previousRecord) {
			this.personalBest = personalBest;
			this.serverRecord = serverRecord;
			this.rank = rank;
			this.previousRecord = previousRecord;
		}
	}

	private static final Comparator<Record> ORDER = new Comparator<Record>() {
		@Override
		public int compare(Record a, Record b) {
			if (a.timeTicks != b.timeTicks) {
				return Long.compare(a.timeTicks, b.timeTicks);
			}
			if (a.timeMS != b.timeMS) {
				return Long.compare(a.timeMS, b.timeMS);
			}
			return Long.compare(a.date, b.date);
		}
	};

	private final File file;
	private final Gson gson;
	private Map<String, List<Record>> maps;// map file name -> records sorted by time

	public ServerRecords(File file) {
		this.file = file;
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.maps = new LinkedHashMap<String, List<Record>>();
		load();
	}

	private void load() {
		if (file.exists() == false) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			Map<String, List<Record>> loaded = gson.fromJson(reader, new TypeToken<LinkedHashMap<String, List<Record>>>() {
			}.getType());
			if (loaded != null) {
				maps = loaded;
				for (List<Record> list : maps.values()) {
					Collections.sort(list, ORDER);
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("Unable to read " + file + ": " + e.getMessage());
		}
	}

	private void save() {
		File tmp = new File(file.getPath() + ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
				gson.toJson(maps, writer);
			}
			Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			System.err.println("Unable to write " + file + ": " + e.getMessage());
		}
	}

	private static String playerKey(String name, String steamId) {
		if (steamId == null || steamId.isEmpty() || steamId.equals("0")) {
			return "name:" + name;
		}
		return steamId;
	}

	/**
	 * Adds a finished run.
	 */
	public synchronized Result addTime(String map, String name, String steamId, long timeTicks, long timeMS) {
		List<Record> list = maps.get(map);
		if (list == null) {
			list = new ArrayList<Record>();
			maps.put(map, list);
		}
		Record previousTop = list.isEmpty() ? null : list.get(0);
		Record record = new Record(name, steamId, timeTicks, timeMS, System.currentTimeMillis());

		String key = playerKey(name, steamId);
		Record own = null;
		for (Record r : list) {
			if (playerKey(r.name, r.steamId).equals(key)) {
				own = r;
				break;
			}
		}
		boolean personalBest = own == null || record.isBetterThan(own);
		if (personalBest) {
			if (own != null) {
				list.remove(own);
			}
			list.add(record);
			Collections.sort(list, ORDER);
			save();
		} else {
			own.name = name;// keep the last known name
		}
		boolean serverRecord = personalBest && list.get(0) == record;
		int rank = list.indexOf(personalBest ? record : own) + 1;
		return new Result(personalBest, serverRecord, rank, serverRecord ? previousTop : null);
	}

	/**
	 * @return copy of the records of a map, sorted by time.
	 */
	public synchronized List<Record> getRecords(String map) {
		List<Record> list = maps.get(map);
		return list == null ? new ArrayList<Record>() : new ArrayList<Record>(list);
	}
}
