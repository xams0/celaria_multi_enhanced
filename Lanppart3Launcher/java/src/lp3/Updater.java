package lp3;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.regex.*;

/**
 * Updates of the launcher: the latest release of the GitHub repository (tag "v2.1.0", asset "Celaria.Lanppart3.exe").
 * Only the launcher contacts GitHub (never the game or the mod). The new exe replaces the current one (the exe isn't locked:
 * Celaria Lanppart3.exe exits once the launcher is started), then the launcher restarts.
 */
public final class Updater {
	public static final String REPO = "xams0/celaria_multi_enhanced";
	public static final String PAGE = "https://github.com/" + REPO + "/releases/latest";

	public record Release(String version, String notes, String downloadUrl, long size) {
	}

	private Updater() {
	}

	/** current version of the launcher (version.txt written by the build) */
	public static String currentVersion() {
		try (InputStream in = Updater.class.getResourceAsStream("/version.txt")) {
			if (in != null) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
			}
		} catch (IOException ignored) {
		}
		return "0.0.0";
	}

	/** latest release on GitHub, null if there is none or GitHub can't be reached */
	public static Release latest() {
		try {
			HttpURLConnection c = (HttpURLConnection) URI.create("https://api.github.com/repos/" + REPO + "/releases/latest").toURL().openConnection();
			c.setConnectTimeout(5000);
			c.setReadTimeout(8000);
			c.setRequestProperty("Accept", "application/vnd.github+json");
			c.setRequestProperty("User-Agent", "Celaria-Lanppart3-launcher");
			if (c.getResponseCode() != 200) {
				return null;
			}
			String json = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			String tag = field(json, "tag_name");
			if (tag == null) {
				return null;
			}
			// first .exe asset of the release
			Matcher m = Pattern.compile("\"size\"\\s*:\\s*(\\d+)[^{}]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"", Pattern.DOTALL).matcher(json);
			String url = null;
			long size = 0;
			if (m.find()) {
				size = Long.parseLong(m.group(1));
				url = m.group(2);
			} else {
				Matcher u = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"").matcher(json);
				if (u.find()) {
					url = u.group(1);
				}
			}
			String notes = field(json, "body");
			return new Release(tag.replaceFirst("^[vV]", ""), notes == null ? "" : notes, url, size);
		} catch (Exception e) {
			return null;
		}
	}

	private static String field(String json, String name) {
		Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
		if (m.find() == false) {
			return null;
		}
		return m.group(1).replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
	}

	/** a > b (versions "2.1.0") */
	public static boolean newer(String a, String b) {
		String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
			if (p != q) {
				return p > q;
			}
		}
		return false;
	}

	private static int num(String s) {
		try {
			return Integer.parseInt(s.replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** the exe started by the user (passed by Celaria Lanppart3.exe), null when the launcher runs without it */
	public static File launcherExe() {
		String p = System.getProperty("lp3.exe");
		return p == null ? null : new File(p);
	}

	/** removes the previous exe kept after an update */
	public static void cleanup() {
		File exe = launcherExe();
		if (exe != null) {
			new File(exe.getPath() + ".old").delete();
		}
	}

	/**
	 * Downloads the new exe and replaces the current one (the old one is kept as .old until the next start).
	 * @return null on success, else the error
	 */
	public static String install(Release r, Consumer<Integer> progress) {
		File exe = launcherExe();
		if (exe == null || r.downloadUrl() == null) {
			return T.t("Mise à jour automatique impossible : télécharge-la sur ") + PAGE;
		}
		File part = new File(exe.getPath() + ".download");
		try {
			HttpURLConnection c = (HttpURLConnection) URI.create(r.downloadUrl()).toURL().openConnection();
			c.setInstanceFollowRedirects(true);
			c.setConnectTimeout(8000);
			c.setReadTimeout(20000);
			c.setRequestProperty("User-Agent", "Celaria-Lanppart3-launcher");
			long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : r.size();
			try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
				byte[] buffer = new byte[65536];
				long done = 0;
				int n, last = -1;
				while ((n = in.read(buffer)) > 0) {
					out.write(buffer, 0, n);
					done += n;
					int pct = total > 0 ? (int) (done * 100 / total) : 0;
					if (pct != last) {
						last = pct;
						progress.accept(pct);
					}
				}
			}
			if (isExe(part) == false || part.length() < 1_000_000) {
				part.delete();
				return T.t("Le fichier téléchargé n'est pas valide.");
			}
			File old = new File(exe.getPath() + ".old");
			old.delete();
			Files.move(exe.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(part.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				Files.move(old.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING);// back to the current version
				throw e;
			}
			return null;
		} catch (Exception e) {
			part.delete();
			return T.t("Échec du téléchargement : ") + e.getMessage();
		}
	}

	private static boolean isExe(File f) {
		try (InputStream in = new FileInputStream(f)) {
			return in.read() == 'M' && in.read() == 'Z';
		} catch (IOException e) {
			return false;
		}
	}

	/** starts the new exe once this launcher (and its runtime folder) is closed */
	public static void restart() {
		File exe = launcherExe();
		try {
			new ProcessBuilder("cmd.exe", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" \"" + exe.getAbsolutePath() + "\"").start();
		} catch (IOException ignored) {
		}
	}
}
