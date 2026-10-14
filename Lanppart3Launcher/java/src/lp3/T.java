package lp3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Languages of the launcher (French / English). The French text is the key: T.t("Jeu détecté") returns the English text
 * when the language is English. T.f() formats (String.format) the translated text.
 */
public final class T {
	private static final Map<String, String> EN = new HashMap<>();
	private static boolean english = Locale.getDefault().getLanguage().equals("fr") == false;

	private T() {
	}

	public static void init() {
		String lang = Env.get("lang", null);
		if (lang != null) {
			english = lang.equals("en");
		}
	}

	public static boolean english() {
		return english;
	}

	public static void setEnglish(boolean en) {
		english = en;
		Env.set("lang", en ? "en" : "fr");
	}

	public static String t(String fr) {
		if (english == false || fr == null) {
			return fr;
		}
		return EN.getOrDefault(fr, fr);
	}

	public static String f(String fr, Object... args) {
		return String.format(t(fr), args);
	}

	private static void e(String fr, String en) {
		EN.put(fr, en);
	}

	static {
		// window, navigation
		e("JOUER", "PLAY");
		e("SERVEUR", "SERVER");
		e("MAPS", "MAPS");
		e("NOUVEAUTÉS", "PATCH NOTES");
		e("Launcher LAN · Coupe & Time Attack · mod chargé sans toucher au jeu", "LAN launcher · Cup & Time Attack · mod loaded without touching the game");
		e("Réduire", "Minimize");
		e("Quitter", "Quit");
		e("Le serveur est lancé : il sera arrêté. Quitter ?", "The server is running: it will be stopped. Quit?");
		e("Arrêt du serveur...", "Stopping the server...");
		e("Celaria Lanppart3 est déjà ouvert.", "Celaria Lanppart3 is already open.");
		e("Annuler", "Cancel");
		e("Oui", "Yes");
		// updates
		e("Mise à jour %s disponible", "Update %s available");
		e("Installer la mise à jour", "Install the update");
		e("Nouvelle version %s (actuelle : %s).\n\n%s\n\nInstaller maintenant ? Le launcher redémarrera.",
				"New version %s (current: %s).\n\n%s\n\nInstall now? The launcher will restart.");
		e("Téléchargement de la mise à jour : %d %%", "Downloading the update: %d %%");
		e("Mise à jour installée : redémarrage...", "Update installed: restarting...");
		e("Mise à jour", "Update");
		e("Le launcher est à jour (v%s).", "The launcher is up to date (v%s).");
		e("Mise à jour automatique impossible : télécharge-la sur ", "Automatic update not possible: download it from ");
		e("Le fichier téléchargé n'est pas valide.", "The downloaded file is not valid.");
		e("Échec du téléchargement : ", "Download failed: ");
		// play page
		e("Jeu détecté", "Game found");
		e("Jeu introuvable", "Game not found");
		e("Choisis Celaria.exe (dossier Steam : steamapps\\common\\Celaria).", "Choose Celaria.exe (Steam folder: steamapps\\common\\Celaria).");
		e("Ancienne version du mod installée dans le dossier du jeu", "Old version of the mod installed in the game folder");
		e("Retirer l'ancien mod", "Remove the old mod");
		e("Détecter", "Detect");
		e("Choisir Celaria.exe...", "Choose Celaria.exe...");
		e("Choisis le fichier Celaria.exe.", "Choose the file Celaria.exe.");
		e("JOUER  —  CELARIA + MOD LANPPART3", "PLAY  —  CELARIA + LANPPART3 MOD");
		e("Serveurs LAN", "LAN servers");
		e("Actualiser", "Refresh");
		e("Recherche...", "Searching...");
		e("Aucun serveur trouvé", "No server found");
		e("%d serveur(s)", "%d server(s)");
		e("Direct-IP :", "Direct-IP:");
		e("Rejoindre cette adresse", "Join this address");
		e("REJOINDRE", "JOIN");
		e("Serveur", "Server");
		e("Mode", "Mode");
		e("Map", "Map");
		e("Joueurs", "Players");
		e("Adresse", "Address");
		e("COUPE", "CUP");
		e("TIME ATTACK", "TIME ATTACK");
		e("Adresse invalide : adresse:port (exemple 26.12.34.56:6510).", "Invalid address: address:port (example 26.12.34.56:6510).");
		e("Ancien mod", "Old mod");
		e("Le launcher charge le mod sans rien copier dans le jeu. L'ancienne version (winExt.dll remplacé) va être retirée et le winExt.dll d'origine restauré. Continuer ?",
				"The launcher loads the mod without copying anything into the game. The old version (replaced winExt.dll) will be removed and the original winExt.dll restored. Continue?");
		e("Ancien mod retiré : le dossier du jeu est d'origine.", "Old mod removed: the game folder is original.");
		e("Celaria est lancé avec le mod : connexion automatique à %s après le menu.", "Celaria started with the mod: automatic connection to %s after the menu.");
		e("Celaria est lancé avec le mod Lanppart3.", "Celaria started with the Lanppart3 mod.");
		e("Aucun serveur LAN trouvé : lance un serveur (page Serveur) ou utilise Direct-IP.", "No LAN server found: start a server (Server page) or use Direct-IP.");
		e("Choisis un serveur dans la liste.", "Choose a server in the list.");
		e("Mot de passe", "Password");
		e("Ce serveur a un mot de passe : le jeu te le demandera à la connexion.", "This server has a password: the game will ask for it when connecting.");
		// game
		e("Celaria.exe introuvable : choisis le dossier du jeu.", "Celaria.exe not found: choose the game folder.");
		e("L'ancienne version du mod est installée dans le dossier du jeu (winExt_orig.dll). Retire-la d'abord (bouton « Retirer l'ancien mod »).",
				"The old version of the mod is installed in the game folder (winExt_orig.dll). Remove it first (button \"Remove the old mod\").");
		e("Celaria est déjà lancé : ferme-le d'abord.", "Celaria is already running: close it first.");
		e("Fichiers du mod manquants dans ", "Mod files missing in ");
		e("Steam n'a pas pu être démarré.", "Steam could not be started.");
		e("Démarrage de Steam...", "Starting Steam...");
		e("Lancement de Celaria et connexion à %s...", "Starting Celaria and connecting to %s...");
		e("Lancement de Celaria (mod Lanppart3)...", "Starting Celaria (Lanppart3 mod)...");
		e("Le jeu s'est fermé tout de suite (Steam n'est pas connecté ?).", "The game closed at once (Steam not logged in?).");
		e("Impossible de démarrer Celaria.exe.", "Celaria.exe could not be started.");
		e("Le mod n'a pas pu être chargé dans le jeu (code %d).", "The mod could not be loaded into the game (code %d).");
		e("Erreur : ", "Error: ");
		e("winExt.dll n'est pas le mod : rien n'a été changé.", "winExt.dll is not the mod: nothing was changed.");
		e("Impossible de restaurer winExt.dll : ", "Could not restore winExt.dll: ");
		// server page
		e("Réglages du serveur", "Server settings");
		e("Général", "General");
		e("Mode Coupe", "Cup mode");
		e("Mode Time Attack", "Time Attack mode");
		e("Avancé", "Advanced");
		e("Ouvrir le dossier du serveur", "Open the server folder");
		e("Valeurs par défaut", "Default values");
		e("Réglages", "Settings");
		e("Remettre tous les réglages par défaut ?", "Reset every setting to its default value?");
		e("Mon serveur", "My server");
		e("Arrêté", "Stopped");
		e("En ligne", "Online");
		e("DÉMARRER LE SERVEUR", "START THE SERVER");
		e("ARRÊTER LE SERVEUR", "STOP THE SERVER");
		e("Redémarrer", "Restart");
		e("Relancer la map", "Restart map");
		e("Map suivante", "Next map");
		e("Map :", "Map:");
		e("Changer", "Change");
		e("JOUER SUR MON SERVEUR", "PLAY ON MY SERVER");
		e("Manche", "Round");
		e("Manche %s / %s", "Round %s / %s");
		e("Envoyer", "Send");
		e("Message aux joueurs : say <texte>", "Message to the players: say <text>");
		e("Commandes : say <texte>, nextmap, restartmap, map <fichier>, players, cupreset, bots <n>",
				"Commands: say <text>, nextmap, restartmap, map <file>, players, cupreset, bots <n>");
		e("Le serveur n'est pas lancé.", "The server isn't running.");
		e("Aucune map : ajoute des maps (page Maps).", "No map: add maps (Maps page).");
		e("Serveur introuvable : ", "Server not found: ");
		e("=== Démarrage du serveur (%s, port %d) ===", "=== Starting the server (%s, port %d) ===");
		e("Coupe", "Cup");
		e("Serveur lancé sur le port %d.", "Server started on port %d.");
		e("Impossible de lancer le serveur : ", "Could not start the server: ");
		e("Redémarrage du serveur...", "Restarting the server...");
		e("Serveur arrêté.", "Server stopped.");
		e("=== Serveur arrêté ===", "=== Server stopped ===");
		e("Impossible d'enregistrer server.conf : ", "Could not save server.conf: ");
		e("« %s » : nombre attendu (%s)", "\"%s\": number expected (%s)");
		e("« Points par place » : nombres séparés par des virgules", "\"Points per rank\": numbers separated by commas");
		e("Port invalide (1024 à 65535)", "Invalid port (1024 to 65535)");
		// settings
		e("Nom du serveur", "Server name");
		e("Nom affiché dans le navigateur LAN", "Name shown in the LAN browser");
		e("Port", "Port");
		e("Port TCP/UDP du serveur", "TCP/UDP port of the server");
		e("Joueurs max", "Max players");
		e("1 à 32", "1 to 32");
		e("Vide = serveur ouvert", "Empty = open server");
		e("Mot de passe admin", "Admin password");
		e("Pour /login en jeu (commandes admin)", "For /login in game (admin commands)");
		e("Manches de warmup", "Warmup rounds");
		e("Au début de chaque map, sans points (0 = pas de warmup)", "At the start of every map, no points (0 = no warmup)");
		e("Points pour être finaliste", "Points to become finalist");
		e("Manches par map", "Rounds per map");
		e("Nombre de vainqueurs", "Number of winners");
		e("La coupe s'arrête quand ce nombre est atteint", "The cup ends when this number is reached");
		e("Points par place", "Points per rank");
		e("Les places suivantes gagnent la dernière valeur", "Lower ranks get the last value");
		e("Temps après le 1er arrivé (s)", "Time after the first finisher (s)");
		e("-1 = automatique (5 s + médaille platine / 6)", "-1 = automatic (5 s + platinum medal / 6)");
		e("Durée max d'une manche (s)", "Max length of a round (s)");
		e("Si personne n'arrive", "If nobody finishes");
		e("Respawn au départ = DNF", "Respawn at the start = DNF");
		e("Touche Respawn sans checkpoint : retour au départ, le chrono continue (avec le mod)",
				"Respawn key without checkpoint: back to the start, the timer continues (with the mod)");
		e("Restart = DNF", "Restart = DNF");
		e("Touche Restart (remise à zéro complète) : éliminé pour la manche", "Restart key (complete reset): out for the round");
		e("Fin de manche : classement (s)", "End of round: leaderboard (s)");
		e("Durée d'affichage du classement entre deux manches", "Leaderboard shown between two rounds");
		e("Fin de coupe : classement (s)", "End of cup: leaderboard (s)");
		e("Fin quand tous sont arrivés (s)", "End when everybody finished (s)");
		e("Attente avant le départ (s)", "Wait before the start (s)");
		e("Après le chargement de la map (+ 3 s de compte à rebours)", "After the map loading (+ 3 s countdown)");
		e("Bots", "Bots");
		e("Rejouent le meilleur run de la map (/bots en jeu)", "Replay the best run of the map (/bots in game)");
		e("Warmup (s)", "Warmup (s)");
		e("Au début de chaque map, les temps ne comptent pas (0 = pas de warmup)", "At the start of every map, times don't count (0 = no warmup)");
		e("Durée d'une manche (s)", "Round length (s)");
		e("Positions envoyées par seconde", "Positions sent per second");
		e("Réseau local : 60 (5 à 120)", "Local network: 60 (5 to 120)");
		// maps page
		e("Maps du serveur", "Server maps");
		e("%d map(s)", "%d map(s)");
		e("AJOUTER DES MAPS...", "ADD MAPS...");
		e("Importer mes maps du jeu...", "Import my game maps...");
		e("Maps créées avec l'éditeur de Celaria (custom_maps)", "Maps made with the Celaria editor (custom_maps)");
		e("Supprimer", "Delete");
		e("Ouvrir le dossier", "Open the folder");
		e("Les maps tournent dans l'ordre alphabétique des fichiers. Un serveur lancé prend en compte les changements à la map suivante.",
				"Maps rotate in the alphabetical order of the files. A running server uses the changes at the next map.");
		e("Ajouter des maps Celaria (.cmap)", "Add Celaria maps (.cmap)");
		e("Maps Celaria (*.cmap)", "Celaria maps (*.cmap)");
		e("%s : ce n'est pas une map Celaria", "%s: not a Celaria map");
		e("%d map(s) ajoutée(s).", "%d map(s) added.");
		e("Choisis les maps à supprimer.", "Choose the maps to delete.");
		e("Garde au moins une map sur le serveur.", "Keep at least one map on the server.");
		e("Supprimer %d map(s) du serveur ?", "Delete %d map(s) from the server?");
		e("%d map(s) supprimée(s).", "%d map(s) deleted.");
		e("format %d (trop récent pour le serveur ?)", "format %d (too recent for the server?)");
		e("%d Ko", "%d KB");
		e("Nouvelle partie", "New game");
		e("Nouvelle partie + warmup", "New game + warmup");
		e("Relancer le warmup", "Restart warmup");
		e("Warmup +1 min", "Warmup +1 min");
		e("+1 manche warmup", "+1 warmup round");
		e("Les points (Coupe) / les temps (Time Attack) de tous les joueurs sont remis à zéro et la map recommence. Continuer ?",
				"The points (Cup) / times (Time Attack) of every player are reset and the map starts again. Continue?");
		e("Les points (Coupe) / les temps (Time Attack) de tous les joueurs sont remis à zéro et la map recommence avec son warmup. Continuer ?",
				"The points (Cup) / times (Time Attack) of every player are reset and the map starts again with its warmup. Continue?");
		e("Warmup au temps", "Warmup with a duration");
		e("Coché : warmup d'une durée (entraînement libre). Sinon : en manches", "Checked: warmup with a duration (free practice). Else: in rounds");
		e("Durée du warmup (s)", "Warmup duration (s)");
		e("Warmup au temps (0 = pas de warmup)", "Warmup with a duration (0 = no warmup)");
		e("INSTALLER LA MISE À JOUR", "INSTALL THE UPDATE");
		e("Mise à jour obligatoire", "Mandatory update");
		e("MISE À JOUR OBLIGATOIRE", "MANDATORY UPDATE");
		e("Version %s disponible (installée : %s)", "Version %s available (installed: %s)");
		e("Le launcher doit être mis à jour pour être utilisé (tous les joueurs de la LAN doivent avoir la même version).",
				"The launcher must be updated to be used (every player of the LAN must have the same version).");
		e("RÉESSAYER", "RETRY");
		e("Celaria Lanppart3 : recherche de mise à jour...", "Celaria Lanppart3: checking for updates...");
		// patch notes page
		e("Patch notes", "Patch notes");
		e("Vérifier les mises à jour", "Check for updates");
		e("Page GitHub", "GitHub page");
		e("Langue", "Language");
	}
}
