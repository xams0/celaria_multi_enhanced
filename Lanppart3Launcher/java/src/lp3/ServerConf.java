package lp3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** server.conf of the launcher server (%APPDATA%\Lanppart3\server\server.conf): settings shown in the launcher */
public final class ServerConf {

	public enum Type {
		TEXT, PASSWORD, INT, DECIMAL, BOOL, WARMUP_MODE// WARMUP_MODE: check box, "time" / "rounds"
	}

	public enum Group {
		GENERAL, CUP, TIMEATTACK, ADVANCED
	}

	public record Setting(String key, Group group, Type type, String label, String def, String help) {
	}

	public static final List<Setting> SETTINGS = List.of(
			new Setting("onlineName", Group.GENERAL, Type.TEXT, "Nom du serveur", "Lanppart3", "Nom affiché dans le navigateur LAN"),
			new Setting("port", Group.GENERAL, Type.INT, "Port", "6510", "Port TCP/UDP du serveur"),
			new Setting("maxPlayerCount", Group.GENERAL, Type.INT, "Joueurs max", "16", "1 à 32"),
			new Setting("password", Group.GENERAL, Type.PASSWORD, "Mot de passe", "", "Vide = serveur ouvert"),
			new Setting("adminPassword", Group.GENERAL, Type.PASSWORD, "Mot de passe admin", "", "Pour /login en jeu (commandes admin)"),

			new Setting("cup_warmupMode", Group.CUP, Type.WARMUP_MODE, "Warmup au temps", "rounds", "Coché : warmup d'une durée (entraînement libre). Sinon : en manches"),
			new Setting("cup_warmupTime", Group.CUP, Type.INT, "Durée du warmup (s)", "120", "Warmup au temps (0 = pas de warmup)"),
			new Setting("cup_warmupRounds", Group.CUP, Type.INT, "Manches de warmup", "1", "Au début de chaque map, sans points (0 = pas de warmup)"),
			new Setting("cup_pointsLimit", Group.CUP, Type.INT, "Points pour être finaliste", "100", ""),
			new Setting("cup_roundsPerMap", Group.CUP, Type.INT, "Manches par map", "5", ""),
			new Setting("cup_nbWinners", Group.CUP, Type.INT, "Nombre de vainqueurs", "3", "La coupe s'arrête quand ce nombre est atteint"),
			new Setting("cup_pointsRepartition", Group.CUP, Type.TEXT, "Points par place", "10,6,4,3,2,1", "Les places suivantes gagnent la dernière valeur"),
			new Setting("cup_finishTimeout", Group.CUP, Type.INT, "Temps après le 1er arrivé (s)", "20", "-1 = automatique (5 s + médaille platine / 6)"),
			new Setting("cup_roundTimeLimit", Group.CUP, Type.INT, "Durée max d'une manche (s)", "300", "Si personne n'arrive"),
			new Setting("cup_respawnAtStartDNF", Group.CUP, Type.BOOL, "Respawn au départ = DNF", "false",
					"Touche Respawn sans checkpoint : retour au départ, le chrono continue (avec le mod)"),
			new Setting("cup_forceRestartDNF", Group.CUP, Type.BOOL, "Restart = DNF", "true", "Touche Restart (remise à zéro complète) : éliminé pour la manche"),
			new Setting("cup_endRoundDelay", Group.CUP, Type.DECIMAL, "Fin de manche : classement (s)", "5", "Durée d'affichage du classement entre deux manches"),
			new Setting("cup_endMatchDelay", Group.CUP, Type.DECIMAL, "Fin de coupe : classement (s)", "10", ""),
			new Setting("cup_allFinishedDelay", Group.CUP, Type.DECIMAL, "Fin quand tous sont arrivés (s)", "1", ""),
			new Setting("cup_startDelay", Group.CUP, Type.DECIMAL, "Attente avant le départ (s)", "1.5", "Après le chargement de la map (+ 3 s de compte à rebours)"),
			new Setting("cup_bots", Group.CUP, Type.INT, "Bots", "0", "Rejouent le meilleur run de la map (/bots en jeu)"),

			new Setting("timeattack_warmup", Group.TIMEATTACK, Type.DECIMAL, "Warmup (s)", "60", "Au début de chaque map, les temps ne comptent pas (0 = pas de warmup)"),
			new Setting("roundTime", Group.TIMEATTACK, Type.INT, "Durée d'une manche (s)", "600", ""),
			new Setting("timeattack_endRoundDelay", Group.TIMEATTACK, Type.DECIMAL, "Fin de manche : classement (s)", "10", ""),
			new Setting("timeattack_startDelay", Group.TIMEATTACK, Type.DECIMAL, "Attente avant le départ (s)", "5", ""),

			new Setting("udpRelayRate", Group.ADVANCED, Type.INT, "Positions envoyées par seconde", "60", "Réseau local : 60 (5 à 120)"));

	private final Map<String, String> values = new LinkedHashMap<>();
	private final File file;

	public ServerConf(File file) {
		this.file = file;
		for (Setting s : SETTINGS) {
			values.put(s.key(), s.def());
		}
		values.put("gameMode", "cup");
		load();
	}

	public String get(String key) {
		return values.getOrDefault(key, "");
	}

	public void set(String key, String value) {
		values.put(key, value == null ? "" : value.trim());
	}

	public boolean isCup() {
		return get("gameMode").equalsIgnoreCase("cup");
	}

	public int port() {
		try {
			return Integer.parseInt(get("port").trim());
		} catch (NumberFormatException e) {
			return 6510;
		}
	}

	private void load() {
		try {
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
				line = line.trim();
				if (line.startsWith("#") || line.contains("=") == false) {
					continue;
				}
				int eq = line.indexOf('=');
				values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
			}
		} catch (IOException ignored) {
		}
	}

	/** checks the values, null if they are valid */
	public String validate() {
		for (Setting s : SETTINGS) {
			String v = get(s.key());
			try {
				if (s.type() == Type.INT) {
					Integer.parseInt(v);
				} else if (s.type() == Type.DECIMAL) {
					Double.parseDouble(v.replace(',', '.'));
				}
			} catch (NumberFormatException e) {
				return T.f("« %s » : nombre attendu (%s)", T.t(s.label()), v);
			}
		}
		if (get("cup_pointsRepartition").matches("\\s*\\d+(\\s*,\\s*\\d+)*\\s*") == false) {
			return T.t("« Points par place » : nombres séparés par des virgules");
		}
		int port = port();
		if (port < 1024 || port > 65535) {
			return T.t("Port invalide (1024 à 65535)");
		}
		return null;
	}

	public void save() throws IOException {
		file.getParentFile().mkdirs();
		StringBuilder b = new StringBuilder();
		b.append("# Celaria Lanppart3 - server.conf (modifié par le launcher)\n");
		b.append("mapDirectory = ./maps/\n");
		b.append("serverList_register = false\n");
		b.append("# Game mode: timeattack or cup\n");
		b.append("gameMode = ").append(get("gameMode")).append("\n");
		Group last = null;
		for (Setting s : SETTINGS) {
			if (s.group() != last) {
				last = s.group();
				b.append("\n# --- ").append(switch (last) {
				case GENERAL -> "General";
				case CUP -> "Cup mode";
				case TIMEATTACK -> "Time attack";
				case ADVANCED -> "Advanced";
				}).append(" ---\n");
			}
			if (s.help().isEmpty() == false) {
				b.append("# ").append(s.label()).append(" : ").append(s.help()).append("\n");
			}
			String v = get(s.key());
			if (s.type() == Type.DECIMAL) {
				v = v.replace(',', '.');
			}
			b.append(s.key()).append(" = ").append(v).append("\n");
		}
		Files.writeString(file.toPath(), b.toString(), StandardCharsets.UTF_8);
	}
}
