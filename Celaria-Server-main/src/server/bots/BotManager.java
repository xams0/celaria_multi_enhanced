package server.bots;

import java.util.ArrayList;
import java.util.List;

/**
 * Bots of the cup mode (chat command /bots <count>): they replay the best recorded run of the map.
 */
public class BotManager {

	/** steam IDs of the bots (their runs aren't recorded) */
	public static final long BOT_STEAM_ID = 76561190000000000L + 900000L;

	private final List<BotClient> bots = new ArrayList<BotClient>();
	private final int port;
	private final BotClient.GhostSource ghosts;

	public BotManager(int port, BotClient.GhostSource ghosts) {
		this.port = port;
		this.ghosts = ghosts;
	}

	public static boolean isBot(String steamID) {
		try {
			long id = Long.parseUnsignedLong(steamID);
			return id >= BOT_STEAM_ID && id < BOT_STEAM_ID + 1000;
		} catch (Exception e) {
			return false;
		}
	}

	public synchronized int count() {
		bots.removeIf(b -> b.isRunning() == false);
		return bots.size();
	}

	/** starts or stops bots to have this number of bots */
	public synchronized void setCount(int count) {
		count = Math.max(0, Math.min(15, count));
		bots.removeIf(b -> b.isRunning() == false);
		while (bots.size() > count) {
			bots.remove(bots.size() - 1).stop();
		}
		while (bots.size() < count) {
			int n = bots.size() + 1;
			// every bot is a bit slower than the recorded run (1 %, 2.5 %, 4 %...)
			BotClient bot = new BotClient("Bot " + n, BOT_STEAM_ID + n, port, 1.01 + 0.015 * (n - 1), ghosts);
			Thread t = new Thread(bot, "Bot " + n);
			t.setDaemon(true);
			t.start();
			bots.add(bot);
		}
	}
}
