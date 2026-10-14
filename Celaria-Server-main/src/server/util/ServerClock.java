package server.util;

/**
 * Monotonic high resolution clock of the server (milliseconds with a fractional part).
 * Used for the clock synchronisation of the cup mod and the scheduled start (System.currentTimeMillis() can have a
 * resolution of ~15 ms on Windows).
 */
public final class ServerClock {

	private static final long ORIGIN = System.nanoTime();

	private ServerClock() {
	}

	public static double nowMS() {
		return (System.nanoTime() - ORIGIN) / 1e6;
	}

	/**
	 * Converts a time of this clock to System.currentTimeMillis().
	 */
	public static long toWallMS(double clockMS) {
		return System.currentTimeMillis() + Math.round(clockMS - nowMS());
	}

	public static String format(double clockMS) {
		return String.format(java.util.Locale.ROOT, "%.3f", clockMS);
	}
}
