package lp3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Folders and settings of the launcher.
 * - app folder (extracted by Celaria Lanppart3.exe, read only): jre, launcher.jar, server/, mod/, defaults/maps, inject.exe
 * - data folder %APPDATA%\Lanppart3: launcher.properties, server/ (server.conf, maps, records.json, ghosts)
 */
public final class Env {
	public static final File APP = new File(System.getProperty("lp3.app", new File("").getAbsolutePath()));
	public static final File DATA = new File(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "Lanppart3");
	public static final File SERVER_DIR = new File(DATA, "server");
	public static final File MAPS_DIR = new File(SERVER_DIR, "maps");
	public static final File SETTINGS_FILE = new File(DATA, "launcher.properties");
	public static final String VERSION = "2.0";

	private static final Properties settings = new Properties();

	private Env() {
	}

	public static File modDll() {
		return new File(APP, "mod/lanppart3.dll");
	}

	public static File injector() {
		return new File(APP, "inject.exe");
	}

	public static File serverJar() {
		return new File(APP, "server/CelariaServer.jar");
	}

	public static File javaExe() {
		File f = new File(APP, "jre/bin/java.exe");
		return f.exists() ? f : new File(System.getProperty("java.home"), "bin/java.exe");
	}

	public static File modLog() {
		return new File(APP, "mod/celaria_mod.log");
	}

	/** first start: data folders, default maps */
	public static void init() {
		MAPS_DIR.mkdirs();
		new File(SERVER_DIR, "ghosts").mkdirs();
		File[] maps = MAPS_DIR.listFiles((d, n) -> n.toLowerCase().endsWith(".cmap"));
		if (maps == null || maps.length == 0) {
			File[] defaults = new File(APP, "defaults/maps").listFiles((d, n) -> n.toLowerCase().endsWith(".cmap"));
			if (defaults != null) {
				for (File m : defaults) {
					try {
						Files.copy(m.toPath(), new File(MAPS_DIR, m.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException ignored) {
					}
				}
			}
		}
		try (Reader r = new InputStreamReader(new FileInputStream(SETTINGS_FILE), StandardCharsets.UTF_8)) {
			settings.load(r);
		} catch (IOException ignored) {
		}
	}

	public static String get(String key, String def) {
		return settings.getProperty(key, def);
	}

	public static void set(String key, String value) {
		if (value == null) {
			settings.remove(key);
		} else {
			settings.setProperty(key, value);
		}
		DATA.mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(SETTINGS_FILE), StandardCharsets.UTF_8)) {
			settings.store(w, "Celaria Lanppart3 launcher");
		} catch (IOException ignored) {
		}
	}

	/** runs a command and returns its output (null on error) */
	public static String run(String... command) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			p.waitFor();
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	public static boolean processRunning(String exeName) {
		String out = run("tasklist", "/FI", "IMAGENAME eq " + exeName, "/NH");
		return out != null && out.toLowerCase().contains(exeName.toLowerCase());
	}

	public static void openFolder(File f) {
		try {
			f.mkdirs();
			new ProcessBuilder("explorer.exe", f.getAbsolutePath()).start();
		} catch (IOException ignored) {
		}
	}
}
