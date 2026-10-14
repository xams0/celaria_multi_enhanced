package lp3;

import java.awt.*;
import java.net.URI;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Page "Nouveautés / Patch notes": everything added since the first version, in French and English; updates. */
final class PatchNotesPage extends JPanel {

	/** {French, English} */
	private record Item(String fr, String en) {
	}

	private record Section(String fr, String en, Item... items) {
	}

	private record Version(String number, String date, Section... sections) {
	}

	private static final Version[] VERSIONS = {
			new Version("2.4.3", "14/10/2026",
					new Section("Spectateur", "Spectator",
							new Item("Vue 3e personne : le modèle et le nom du joueur suivi ne sont plus dédoublés (la caméra suit maintenant la position affichée du joueur, à la même heure que les modèles et les noms).",
									"Third person view: the model and the name of the followed player are no longer doubled (the camera now follows the displayed position of the player, at the same time as the models and the names)."))),
			new Version("2.4.2", "14/10/2026",
					new Section("Spectateur", "Spectator",
							new Item("Les noms des joueurs restent affichés tout le temps (ils disparaissaient au bout de quelques secondes : le jeu mesurait la distance depuis la position de votre joueur).",
									"The names of the players stay shown all the time (they disappeared after a few seconds: the game measured the distance from the position of your player)."),
							new Item("Chaque nom est affiché au-dessus de son joueur (ils étaient placés depuis la caméra de votre joueur et se superposaient) ; seuls les joueurs présents à l'image ont leur nom.",
									"Each name is shown above its player (they were placed from the camera of your player and overlapped); only the players in the picture have their name."),
							new Item("Touche B pour cacher / afficher les noms des joueurs.", "Key B to hide / show the names of the players."))),
			new Version("2.4.1", "14/10/2026",
					new Section("Spectateur", "Spectator",
							new Item("Cliquer sur un joueur dans le classement (Tab, manche ou coupe) le suit à la caméra (la caméra libre passe en vue 1re personne).",
									"Clicking a player in the leaderboard (Tab, round or cup) follows him with the camera (the free camera switches to the first person view)."),
							new Item("La caméra ne bouge plus avec la souris quand le classement (Tab) ou le menu Échap est ouvert.",
									"The camera no longer moves with the mouse while the leaderboard (Tab) or the Escape menu is open."),
							new Item("Les spectateurs connectés sont affichés en bas à gauche (œil + nom), au-dessus de « Lanppart3 Mod », quand les classements sont à l'écran.",
									"The connected spectators are shown at the bottom left (eye + name), above \"Lanppart3 Mod\", while the rankings are on screen.")),
					new Section("Interface", "Interface",
							new Item("Fin de manche / arrivée : la bande noire du menu à gauche ne couvre plus que ses boutons (en bas) ; le classement des écarts en haut à gauche reste visible.",
									"End of the round / finish: the black bar of the left menu only covers its buttons (at the bottom); the gap ranking at the top left stays visible."))),
			new Version("2.4.0", "14/10/2026",
					new Section("Coupe", "Cup",
							new Item("Deux classements séparés quand on affiche le classement (Tab) : en haut la manche en cours (temps, DNF, points gagnés), en dessous le classement de la coupe (points, FINALIST / WINNER, points gagnés à la dernière manche). 16 joueurs visibles dans chacun.",
									"Two separate rankings when the leaderboard is shown (Tab): on top the current round (times, DNF, points won), below the cup ranking (points, FINALIST / WINNER, points won in the last round). 16 players visible in each one."),
							new Item("Les spectateurs sont listés dans la barre de titre du classement de la coupe (plus dans les tableaux).",
									"The spectators are listed in the title bar of the cup ranking (no longer in the tables).")),
					new Section("Classements", "Rankings",
							new Item("Les classements ne clignotent plus à l'arrivée de chaque joueur (classement de la coupe et classement des écarts en haut à gauche).",
									"The rankings no longer flicker when a player finishes (cup ranking and gap ranking at the top left)."),
							new Item("Classement des écarts en haut à gauche : si vous êtes au-delà de la 10e place, la dernière ligne affiche votre position (ou celle du joueur suivi en spectateur).",
									"Gap ranking at the top left: if you are beyond the 10th place, the last line shows your position (or the one of the player followed as a spectator)."))),
			new Version("2.3.1", "14/10/2026",
					new Section("Positions des joueurs", "Positions of the players",
							new Item("En course, les adversaires sont affichés plus près de leur position réelle (retard ≈ 28 ms, ~60 cm à pleine vitesse au lieu de ~1 m). En spectateur, le retard reste de 45 ms pour une restitution parfaite.",
									"While racing, the opponents are shown closer to their real position (delay ≈ 28 ms, ~60 cm at full speed instead of ~1 m). While spectating, the delay stays at 45 ms for a perfect restitution."))),
			new Version("2.3.0", "14/10/2026",
					new Section("Positions des joueurs", "Positions of the players",
							new Item("Positions exactes : le serveur d'origine arrondissait les positions des autres joueurs à 10 cm (vers le bas : joueurs dans le sol). Le serveur Lanppart3 envoie maintenant les positions exactes aux joueurs qui ont le mod.",
									"Exact positions: the original server rounded the positions of the other players to 10 cm (downwards: players in the floor). The Lanppart3 server now sends the exact positions to the players with the mod."),
							new Item("Plus d'extrapolation : les autres joueurs sont affichés à leurs positions réelles, interpolées à chaque image avec un léger retard (≈ 45 ms). Plus de passages sous le sol ou dans les murs, sauts et wallrides fidèles.",
									"No more extrapolation: the other players are shown at their real positions, interpolated at every frame with a small delay (≈ 45 ms). No more going through the floor or the walls, faithful jumps and wall runs."),
							new Item("Positions envoyées toutes les 20 ms (au lieu de 30).", "Positions sent every 20 ms (instead of 30).")),
					new Section("Spectateur", "Spectator",
							new Item("Vue 1re personne : la vraie caméra du joueur suivi (roulades, glissades, inclinaison des wallrides, hauteur des yeux).",
									"First person view: the real camera of the followed player (rolls, slides, tilt of the wall runs, eye height)."),
							new Item("Classement aux checkpoints en haut à gauche, sous la manche / le warmup.", "Checkpoint ranking at the top left, below the round / warmup.")),
					new Section("Coupe", "Cup",
							new Item("Classement en deux parties : la manche en cours, puis le classement de la coupe.", "Leaderboard in two parts: the current round, then the cup ranking."),
							new Item("Plus de bouton « Restart Map » une fois son temps validé (la manche continue pour les autres).",
									"No \"Restart Map\" button any more once your time is validated (the round goes on for the others)."))),
			new Version("2.2.0", "14/10/2026",
					new Section("Launcher", "Launcher",
							new Item("Mise à jour obligatoire : au démarrage, si une nouvelle version existe, une fenêtre propose de l'installer (ou de quitter) ; le launcher n'est pas utilisable sans la mise à jour. Tous les joueurs de la LAN ont ainsi la même version.",
									"Mandatory update: at start, if a new version exists, a window offers to install it (or to quit); the launcher can't be used without the update. Every player of the LAN has the same version."),
							new Item("Sans accès à Internet, le launcher s'ouvre normalement.", "Without Internet access, the launcher opens normally."))),
			new Version("2.1.0", "14/10/2026",
					new Section("Course", "Racing",
							new Item("Les adversaires proches ne saccadent plus : ils sont dessinés entre deux pas de simulation, comme son propre personnage.",
									"Close opponents don't judder any more: they are drawn between two simulation steps, like your own character."),
							new Item("L'écart avec le 1er s'affiche à chaque checkpoint (pas seulement à l'arrivée).",
									"The gap with the first player is shown at every checkpoint (not only at the finish)."),
							new Item("Compte à rebours identique pour tous : 3, 2, 1, Go! (plus de chiffre sauté).",
									"Same countdown for everybody: 3, 2, 1, Go! (no skipped digit any more).")),
					new Section("Warmup", "Warmup",
							new Item("Coupe : warmup en manches ou au temps (ex. 3 manches ou 2 minutes d'entraînement libre).",
									"Cup: warmup in rounds or with a duration (e.g. 3 rounds or 2 minutes of free practice)."),
							new Item("Launcher : relancer le warmup, ajouter une minute ou une manche de warmup en cours de partie.",
									"Launcher: restart the warmup, add one minute or one warmup round during the game.")),
					new Section("Spectateur", "Spectator",
							new Item("Le mode spectateur se lance avec le bouton Spectator du menu Échap (V ne le lance plus pendant la course).",
									"The spectator mode starts with the Spectator button of the Escape menu (V doesn't start it any more while racing)."),
							new Item("V passe seulement entre les 3 vues : 1re personne, 3e personne, caméra libre (plus de vue figée sur son personnage).",
									"V only switches between the 3 views: first person, third person, free camera (no frozen view of your character any more)."),
							new Item("Plus de « Waiting for server... » en spectateur.", "No \"Waiting for server...\" any more while spectating.")),
					new Section("Serveur", "Server",
							new Item("Nouvelle partie sans quitter le serveur : points / temps de tous les joueurs remis à zéro (avec ou sans warmup).",
									"New game without leaving the server: points / times of every player reset (with or without warmup)."),
							new Item("« Relancer la map » recommence la map sans warmup, « Relancer le warmup » avec.",
									"\"Restart map\" starts the map again without warmup, \"Restart warmup\" with it."))),
			new Version("2.0.0", "14/10/2026",
					new Section("Launcher Celaria Lanppart3", "Celaria Lanppart3 launcher",
							new Item("Un seul fichier .exe : Java, serveur, mod et maps inclus, rien à installer.",
									"One single .exe: Java, server, mod and maps included, nothing to install."),
							new Item("Le mod est chargé dans le jeu au lancement : plus aucun fichier copié dans le dossier du jeu.",
									"The mod is loaded into the game at start: no file copied into the game folder any more."),
							new Item("Détection automatique de Celaria (bibliothèques Steam), démarrage de Steam si besoin.",
									"Automatic detection of Celaria (Steam libraries), Steam started if needed."),
							new Item("Navigateur de serveurs LAN (mode, map, joueurs) : « Rejoindre » lance le jeu et se connecte tout seul.",
									"LAN server browser (mode, map, players): \"Join\" starts the game and connects by itself."),
							new Item("Serveur : mode Coupe / Time Attack et tous les réglages, démarrer, arrêter, redémarrer, relancer la map, map suivante, changer de map, console et messages aux joueurs.",
									"Server: Cup / Time Attack mode and every setting, start, stop, restart, restart the map, next map, change the map, console and messages to the players."),
							new Item("Maps : ajouter, importer ses maps de l'éditeur, supprimer (prises en compte à la map suivante).",
									"Maps: add, import your editor maps, delete (used from the next map)."),
							new Item("Français / anglais, patch notes, mises à jour automatiques depuis GitHub.",
									"French / English, patch notes, automatic updates from GitHub."),
							new Item("Retire l'ancienne installation du mod (winExt.dll remplacé) en un clic.",
									"Removes the old installation of the mod (replaced winExt.dll) in one click.")),
					new Section("Course", "Racing",
							new Item("Écart avec le 1er au milieu de l'écran à chaque checkpoint et à l'arrivée (position, temps, écart rouge / bleu), aussi pour le joueur suivi en spectateur.",
									"Gap with the first player in the middle of the screen at every checkpoint and at the finish (position, time, red / blue gap), also for the followed player when spectating."),
							new Item("Respawn : retour au dernier checkpoint ou au départ, le chrono continue. Plus de DNF (réglable).",
									"Respawn: back to the last checkpoint or to the start, the timer continues. No DNF any more (setting)."),
							new Item("Restart (remise à zéro complète) = DNF en Coupe (réglable).", "Restart (complete reset) = DNF in Cup mode (setting)."),
							new Item("Warmup au début de chaque map (manches sans points en Coupe, durée en Time Attack), affiché à côté du numéro de manche.",
									"Warmup at the start of every map (rounds without points in Cup mode, duration in Time Attack), shown next to the round number."),
							new Item("Fin de manche sans flash blanc, transitions plus rapides (délais réglables).",
									"End of round without white flash, faster transitions (adjustable delays).")),
					new Section("Spectateur et Time Attack", "Spectator and Time Attack",
							new Item("Vue 1re personne fluide : la caméra du joueur suivi est interpolée, positions envoyées 60 fois par seconde.",
									"Smooth first person view: the camera of the followed player is interpolated, positions sent 60 times per second."),
							new Item("Toutes les fonctions du mod marchent aussi en Time Attack (observateur, classement aux checkpoints, chrono du joueur suivi).",
									"Every feature of the mod also works in Time Attack (observer, checkpoint ranking, timer of the followed player)."),
							new Item("Le serveur accepte les maps de l'éditeur actuel (format 3).", "The server accepts the maps of the current editor (format 3)."))),
			new Version("1.0.0", "13/10/2026",
					new Section("Mode Coupe (règles Trackmania)", "Cup mode (Trackmania rules)",
							new Item("Points par place, finaliste à la limite de points, un finaliste qui gagne une manche gagne la coupe, plusieurs vainqueurs possibles.",
									"Points per rank, finalist at the points limit, a finalist who wins a round wins the cup, several winners possible."),
							new Item("Plusieurs manches par map, temps limite après le 1er arrivé, temps mesurés au millième.",
									"Several rounds per map, time limit after the first finisher, times measured to the millisecond."),
							new Item("Départ synchronisé : le compte à rebours du jeu est calé sur l'horloge du serveur pour tout le monde.",
									"Synchronized start: the countdown of the game follows the server clock for everybody."),
							new Item("Interface : ROUND x / y, classement aux checkpoints, badges WINNER / FINALIST, classement plus large.",
									"Interface: ROUND x / y, checkpoint ranking, WINNER / FINALIST badges, wider leaderboard."),
							new Item("Bots qui rejouent le meilleur run de la map (/bots).", "Bots replaying the best run of the map (/bots).")),
					new Section("Mod Lanppart3", "Lanppart3 mod",
							new Item("Actif seulement sur un serveur du réseau local (LAN, Radmin VPN, Hamachi) : étiquette « Lanppart3 Mod ».",
									"Only active on a server of the local network (LAN, Radmin VPN, Hamachi): \"Lanppart3 Mod\" label."),
							new Item("Chrono et temps au millième (affichage ; le temps officiel du jeu n'est jamais changé).",
									"Timer and times to the millisecond (display; the official time of the game is never changed)."),
							new Item("Adversaires affichés au même temps de course que soi, sans retard de lissage.",
									"Opponents shown at the same race time as you, without smoothing delay."),
							new Item("Spectateur : 1re personne, 3e personne, caméra libre, automatique après l'arrivée, nom, chrono et vitesse du joueur suivi.",
									"Spectator: first person, third person, free camera, automatic after the finish, name, timer and speed of the followed player."),
							new Item("Mode observateur (caster) : bouton Spectator du menu Échap, /spec et /play.",
									"Observer (caster) mode: Spectator button of the Escape menu, /spec and /play."),
							new Item("Onglet Records (records du serveur au millième), navigateur de serveurs LAN dans le jeu.",
									"Records tab (server records to the millisecond), LAN server browser in the game."),
							new Item("Rien n'est envoyé à Steam sur un serveur Lanppart3 (scores, expérience).",
									"Nothing is sent to Steam on a Lanppart3 server (scores, experience)."))),
	};

	private final LauncherFrame frame;

	PatchNotesPage(LauncherFrame frame) {
		super(new BorderLayout(0, 14));
		this.frame = frame;
		setOpaque(false);
		Theme.Card card = new Theme.Card(new BorderLayout(0, 12));
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(Theme.title(T.t("Patch notes")), BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		buttons.setOpaque(false);
		JLabel version = Theme.label("v" + Updater.currentVersion(), Font.BOLD | Font.ITALIC, 14f, Theme.ACCENT);
		buttons.add(version);
		JButton check = Theme.secondary(T.t("Vérifier les mises à jour"));
		check.addActionListener(e -> frame.checkUpdate(true));
		JButton github = Theme.secondary(T.t("Page GitHub"));
		github.addActionListener(e -> {
			try {
				Desktop.getDesktop().browse(new URI("https://github.com/" + Updater.REPO));
			} catch (Exception ignored) {
			}
		});
		buttons.add(check);
		buttons.add(github);
		top.add(buttons, BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);

		JEditorPane html = new JEditorPane("text/html", html());
		html.setEditable(false);
		html.setOpaque(true);
		html.setBackground(Theme.PANEL2);
		html.setBorder(new EmptyBorder(10, 16, 10, 16));
		html.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		html.setFont(Theme.font(Font.PLAIN, 14f));
		html.setCaretPosition(0);
		card.add(Theme.scroll(html), BorderLayout.CENTER);
		add(card, BorderLayout.CENTER);
	}

	private static String hex(Color c) {
		return String.format("#%06x", c.getRGB() & 0xFFFFFF);
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String html() {
		boolean en = T.english();
		StringBuilder b = new StringBuilder("<html><body style='font-family:\"" + Theme.FAMILY + "\"; color:" + hex(Theme.TEXT) + "; font-size:11pt'>");
		for (Version v : VERSIONS) {
			b.append("<div style='margin-top:6px'><span style='font-size:17pt; font-weight:bold; font-style:italic; color:").append(hex(Theme.ACCENT))
					.append("'>V").append(v.number()).append("</span>&nbsp;&nbsp;<span style='color:").append(hex(Theme.MUTED)).append("'>")
					.append(v.date()).append("</span></div>");
			for (Section s : v.sections()) {
				b.append("<div style='margin-top:8px; font-weight:bold; font-size:12pt'>").append(esc(en ? s.en() : s.fr())).append("</div><ul style='margin-top:2px'>");
				for (Item i : s.items()) {
					b.append("<li style='margin-bottom:3px'>").append(esc(en ? i.en() : i.fr())).append("</li>");
				}
				b.append("</ul>");
			}
		}
		return b.append("</body></html>").toString();
	}
}
