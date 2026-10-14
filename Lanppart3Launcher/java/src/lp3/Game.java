package lp3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Celaria: detection of the installation (Steam libraries) and start with the mod.
 * The game is started by inject.exe (part of the launcher): the mod DLL is loaded into the game process when it starts,
 * nothing is copied in the folder of the game.
 */
public final class Game {
	public static final String APP_ID = "1081650";

	private Game() {
	}

	/** Celaria.exe of the settings, else found in the Steam libraries; null if not found */
	public static File locate() {
		String saved = Env.get("game.exe", null);
		if (saved != null && new File(saved).isFile()) {
			return new File(saved);
		}
		File found = detect();
		if (found != null) {
			Env.set("game.exe", found.getAbsolutePath());
		}
		return found;
	}

	public static File detect() {
		List<File> steamRoots = new ArrayList<>();
		String reg = Env.run("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath");
		if (reg != null) {
			Matcher m = Pattern.compile("SteamPath\\s+REG_SZ\\s+(.+)").matcher(reg);
			if (m.find()) {
				steamRoots.add(new File(m.group(1).trim().replace('/', '\\')));
			}
		}
		for (String key : new String[] { "HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "HKLM\\SOFTWARE\\Valve\\Steam" }) {
			String r = Env.run("reg", "query", key, "/v", "InstallPath");
			if (r != null) {
				Matcher m = Pattern.compile("InstallPath\\s+REG_SZ\\s+(.+)").matcher(r);
				if (m.find()) {
					steamRoots.add(new File(m.group(1).trim()));
				}
			}
		}
		steamRoots.add(new File("C:\\Program Files (x86)\\Steam"));
		Set<File> libraries = new LinkedHashSet<>();
		for (File root : steamRoots) {
			libraries.add(root);
			File vdf = new File(root, "steamapps/libraryfolders.vdf");
			try {
				String text = Files.readString(vdf.toPath(), StandardCharsets.UTF_8);
				Matcher m = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(text);
				while (m.find()) {
					libraries.add(new File(m.group(1).replace("\\\\", "\\")));
				}
			} catch (IOException ignored) {
			}
		}
		for (File lib : libraries) {
			File exe = new File(lib, "steamapps/common/Celaria/Celaria.exe");
			if (exe.isFile()) {
				return exe;
			}
		}
		// uninstall entry of Steam
		String u = Env.run("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App " + APP_ID, "/v", "InstallLocation");
		if (u != null) {
			Matcher m = Pattern.compile("InstallLocation\\s+REG_SZ\\s+(.+)").matcher(u);
			if (m.find() && new File(m.group(1).trim(), "Celaria.exe").isFile()) {
				return new File(m.group(1).trim(), "Celaria.exe");
			}
		}
		// other drives
		for (char d = 'C'; d <= 'Z'; d++) {
			for (String p : new String[] { ":\\SteamLibrary\\steamapps\\common\\Celaria\\Celaria.exe", ":\\Steam\\steamapps\\common\\Celaria\\Celaria.exe",
					":\\Games\\Steam\\steamapps\\common\\Celaria\\Celaria.exe" }) {
				File f = new File(d + p);
				if (f.isFile()) {
					return f;
				}
			}
		}
		return null;
	}

	/** the old version of the mod (winExt.dll proxy copied in the folder of the game) is installed */
	public static boolean oldModInstalled(File exe) {
		return exe != null && new File(exe.getParentFile(), "winExt_orig.dll").isFile();
	}

	/** restores the original winExt.dll of the game (removes the old version of the mod) */
	public static String removeOldMod(File exe) {
		File dir = exe.getParentFile();
		File proxy = new File(dir, "winExt.dll"), orig = new File(dir, "winExt_orig.dll");
		try {
			byte[] data = Files.readAllBytes(proxy.toPath());
			if (new String(data, StandardCharsets.ISO_8859_1).contains("winExt_orig.") == false) {
				return T.t("winExt.dll n'est pas le mod : rien n'a été changé.");
			}
			Files.move(orig.toPath(), proxy.toPath(), StandardCopyOption.REPLACE_EXISTING);
			new File(dir, "celaria_mod.log").delete();
			return null;
		} catch (IOException e) {
			return T.t("Impossible de restaurer winExt.dll : ") + e.getMessage();
		}
	}

	/** Steam must run (the game uses the Steam client): started if needed */
	public static boolean ensureSteam(java.util.function.Consumer<String> status) {
		if (Env.processRunning("steam.exe")) {
			return true;
		}
		status.accept(T.t("Démarrage de Steam..."));
		String reg = Env.run("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamExe");
		File steam = new File("C:\\Program Files (x86)\\Steam\\steam.exe");
		if (reg != null) {
			Matcher m = Pattern.compile("SteamExe\\s+REG_SZ\\s+(.+)").matcher(reg);
			if (m.find()) {
				steam = new File(m.group(1).trim().replace('/', '\\'));
			}
		}
		try {
			new ProcessBuilder(steam.getAbsolutePath(), "-silent").start();
		} catch (IOException e) {
			return false;
		}
		for (int i = 0; i < 60; i++) {// steam.exe, then a few seconds for the login
			sleep(1000);
			if (Env.processRunning("steamwebhelper.exe") && i > 8) {
				sleep(4000);
				return true;
			}
		}
		return Env.processRunning("steam.exe");
	}

	/**
	 * Starts the game with the mod. connect = "address:port" of a server to join at once, or null.
	 * @return null if the game started with the mod, else the error
	 */
	public static String start(File exe, String connect, java.util.function.Consumer<String> status) {
		if (exe == null || exe.isFile() == false) {
			return T.t("Celaria.exe introuvable : choisis le dossier du jeu.");
		}
		if (oldModInstalled(exe)) {
			return T.t("L'ancienne version du mod est installée dans le dossier du jeu (winExt_orig.dll). Retire-la d'abord (bouton « Retirer l'ancien mod »).");
		}
		if (Env.processRunning("Celaria.exe")) {
			return T.t("Celaria est déjà lancé : ferme-le d'abord.");
		}
		if (Env.modDll().isFile() == false || Env.injector().isFile() == false) {
			return T.t("Fichiers du mod manquants dans ") + Env.APP;
		}
		if (ensureSteam(status) == false) {
			return T.t("Steam n'a pas pu être démarré.");
		}
		status.accept(connect != null ? T.f("Lancement de Celaria et connexion à %s...", connect) : T.t("Lancement de Celaria (mod Lanppart3)..."));
		ProcessBuilder pb = new ProcessBuilder(Env.injector().getAbsolutePath(), "--inject", exe.getAbsolutePath(), Env.modDll().getAbsolutePath());
		if (connect != null) {
			pb.environment().put("LP3_CONNECT", connect);
		} else {
			pb.environment().remove("LP3_CONNECT");
		}
		try {
			Process p = pb.start();
			int code = p.waitFor();
			switch (code) {
			case 0:
				return null;
			case 15:
				return T.t("Le jeu s'est fermé tout de suite (Steam n'est pas connecté ?).");
			case 12:
				return T.t("Impossible de démarrer Celaria.exe.");
			default:
				return T.f("Le mod n'a pas pu être chargé dans le jeu (code %d).", code);
			}
		} catch (Exception e) {
			return T.t("Erreur : ") + e.getMessage();
		}
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}
}
