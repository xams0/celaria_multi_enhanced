package lp3;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

/**
 * Page T.t("Serveur"): mode (Coupe / Time Attack) and settings (server.conf), start / stop, and management of the running
 * server: restart, restart the map, next map, change of map, console.
 */
final class ServerPage extends JPanel {
	private final LauncherFrame frame;
	private final ServerConf conf = new ServerConf(new File(Env.SERVER_DIR, "server.conf"));
	private final Map<String, JComponent> inputs = new HashMap<>();
	private final JPanel cupSection = new JPanel(new GridBagLayout());
	private final JPanel taSection = new JPanel(new GridBagLayout());
	private final ModeButton cupMode = new ModeButton(T.t("COUPE"));
	private final ModeButton taMode = new ModeButton(T.t("TIME ATTACK"));
	private final JButton startStop = Theme.primary(T.t("DÉMARRER LE SERVEUR"));
	private final JButton restart = Theme.secondary(T.t("Redémarrer"));
	private final JButton restartMap = Theme.secondary(T.t("Relancer la map"));
	private final JButton nextMap = Theme.secondary(T.t("Map suivante"));
	private final JButton newGame = Theme.secondary(T.t("Nouvelle partie"));
	private final JButton newGameWarmup = Theme.secondary(T.t("Nouvelle partie + warmup"));
	private final JButton warmupRestart = Theme.secondary(T.t("Relancer le warmup"));
	private final JButton warmupAddTime = Theme.secondary(T.t("Warmup +1 min"));
	private final JButton warmupAddRound = Theme.secondary(T.t("+1 manche warmup"));
	private final JButton changeMap = Theme.secondary(T.t("Changer"));
	private final JButton joinOwn = Theme.primary(T.t("JOUER SUR MON SERVEUR"));
	private final JComboBox<String> mapChoice = Theme.combo(new String[0]);
	private final JLabel stateLabel = Theme.label(T.t("Arrêté"), Font.BOLD | Font.ITALIC, 18f, Theme.MUTED);
	private final JLabel mapLabel = Theme.label("-", Font.PLAIN, 14f, Theme.TEXT);
	private final JLabel playersLabel = Theme.label("-", Font.PLAIN, 14f, Theme.TEXT);
	private final JLabel roundLabel = Theme.label("-", Font.PLAIN, 14f, Theme.TEXT);
	private final JTextPane console = new JTextPane();
	private final JTextField command = Theme.field("", 20);
	private final ServerProcess process;
	// one server for the whole launcher (it keeps running when the window is created again) and its console
	private static final ServerProcess shared = new ServerProcess();
	private static final java.util.List<String> history = new java.util.ArrayList<>();
	private final javax.swing.Timer statusTimer;
	private volatile boolean restarting;

	ServerPage(LauncherFrame frame) {
		super(new BorderLayout(16, 0));
		this.frame = frame;
		setOpaque(false);
		process = shared;
		process.setListeners(this::log, s -> SwingUtilities.invokeLater(() -> showStatus(s)),
				maps -> SwingUtilities.invokeLater(() -> showMaps(maps)), () -> SwingUtilities.invokeLater(this::stopped));
		synchronized (history) {
			for (String line : history) {
				append(line);
			}
		}
		if (process.running()) {
			process.command("maps");
			process.command("status");
		}

		add(settingsCard(), BorderLayout.CENTER);
		add(manageCard(), BorderLayout.EAST);
		selectMode(conf.isCup());
		updateButtons();
		statusTimer = new javax.swing.Timer(2000, e -> {
			if (process.running()) {
				process.command("status");
			}
		});
		statusTimer.start();
	}

	boolean running() {
		return process.running();
	}

	// ---------------------------------------------------------------- settings

	private JComponent settingsCard() {
		Theme.Card card = new Theme.Card(new BorderLayout(0, 12));
		JPanel top = new JPanel(new BorderLayout(0, 10));
		top.setOpaque(false);
		top.add(Theme.title(T.t("Réglages du serveur")), BorderLayout.NORTH);
		JPanel modes = new JPanel(new GridLayout(1, 2, 10, 0));
		modes.setOpaque(false);
		cupMode.addActionListener(e -> selectMode(true));
		taMode.addActionListener(e -> selectMode(false));
		modes.add(cupMode);
		modes.add(taMode);
		top.add(modes, BorderLayout.CENTER);
		card.add(top, BorderLayout.NORTH);

		JPanel form = new Theme.WidthPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setOpaque(false);
		form.add(section(T.t("Général"), ServerConf.Group.GENERAL, new JPanel(new GridBagLayout())));
		form.add(section(T.t("Mode Coupe"), ServerConf.Group.CUP, cupSection));
		form.add(section(T.t("Mode Time Attack"), ServerConf.Group.TIMEATTACK, taSection));
		form.add(section(T.t("Avancé"), ServerConf.Group.ADVANCED, new JPanel(new GridBagLayout())));
		JScrollPane scroll = Theme.scroll(form);
		scroll.setBorder(null);
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		card.add(scroll, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		bottom.setOpaque(false);
		JButton folder = Theme.secondary(T.t("Ouvrir le dossier du serveur"));
		folder.addActionListener(e -> Env.openFolder(Env.SERVER_DIR));
		JButton defaults = Theme.secondary(T.t("Valeurs par défaut"));
		defaults.addActionListener(e -> {
			if (Dialogs.confirm(frame, T.t("Réglages"), T.t("Remettre tous les réglages par défaut ?"))) {
				for (ServerConf.Setting s : ServerConf.SETTINGS) {
					setInput(s, s.def());
				}
			}
		});
		bottom.add(folder);
		bottom.add(defaults);
		card.add(bottom, BorderLayout.SOUTH);
		return card;
	}

	private JPanel section(String title, ServerConf.Group group, JPanel grid) {
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setOpaque(false);
		p.setBorder(new EmptyBorder(8, 0, 10, 0));
		JLabel t = Theme.label(title.toUpperCase(), Font.BOLD | Font.ITALIC, 14f, Theme.ACCENT);
		p.add(t, BorderLayout.NORTH);
		grid.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 0, 4, 12);
		c.anchor = GridBagConstraints.WEST;
		int row = 0;
		for (ServerConf.Setting s : ServerConf.SETTINGS) {
			if (s.group() != group) {
				continue;
			}
			c.gridy = row++;
			c.gridx = 0;
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.NORTHWEST;
			// left: name of the setting and its help below; right: the input
			JPanel left = new JPanel();
			left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
			left.setOpaque(false);
			JLabel l = Theme.label(T.t(s.label()), Font.PLAIN, 14f, Theme.TEXT);
			left.add(l);
			if (s.help().isEmpty() == false) {
				JLabel help = Theme.label("<html><body style=\"width:165px\">" + T.t(s.help()) + "</body></html>", Font.PLAIN, 11f, Theme.MUTED);
				left.add(help);
			}
			Dimension d = left.getPreferredSize();
			left.setPreferredSize(new Dimension(230, Math.max(30, d.height)));
			left.setMinimumSize(left.getPreferredSize());
			JComponent input;
			if (s.type() == ServerConf.Type.BOOL || s.type() == ServerConf.Type.WARMUP_MODE) {
				input = Theme.check("", on(s, conf.get(s.key())));
			} else if (s.type() == ServerConf.Type.PASSWORD) {
				input = Theme.password(conf.get(s.key()), 10);
			} else {
				input = Theme.field(conf.get(s.key()), s.type() == ServerConf.Type.TEXT ? 12 : 6);
			}
			input.setMinimumSize(input.getPreferredSize());
			input.setToolTipText(s.help().isEmpty() ? null : T.t(s.help()));
			inputs.put(s.key(), input);
			grid.add(left, c);
			c.gridx = 1;
			c.weightx = 1;
			c.fill = s.type() == ServerConf.Type.TEXT || s.type() == ServerConf.Type.PASSWORD ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
			grid.add(input, c);
		}
		p.add(grid, BorderLayout.CENTER);
		p.putClientProperty("group", group);
		return p;
	}

	/** value of a check box setting */
	private static boolean on(ServerConf.Setting s, String value) {
		return s.type() == ServerConf.Type.WARMUP_MODE ? "time".equalsIgnoreCase(value) : Boolean.parseBoolean(value);
	}

	private void setInput(ServerConf.Setting s, String value) {
		JComponent input = inputs.get(s.key());
		if (input instanceof JCheckBox b) {
			b.setSelected(on(s, value));
		} else if (input instanceof JTextField f) {
			f.setText(value);
		}
	}

	private void selectMode(boolean cup) {
		conf.set("gameMode", cup ? "cup" : "timeattack");
		cupMode.setSelected(cup);
		taMode.setSelected(cup == false);
		cupSection.getParent().setVisible(cup);
		taSection.getParent().setVisible(cup == false);
		revalidate();
		repaint();
	}

	/** reads the inputs into the configuration and saves server.conf; false if a value is invalid */
	private boolean save() {
		for (ServerConf.Setting s : ServerConf.SETTINGS) {
			JComponent input = inputs.get(s.key());
			if (input instanceof JCheckBox b) {
				conf.set(s.key(), s.type() == ServerConf.Type.WARMUP_MODE ? (b.isSelected() ? "time" : "rounds") : b.isSelected() ? "true" : "false");
			} else if (input instanceof JPasswordField p) {
				conf.set(s.key(), new String(p.getPassword()));
			} else if (input instanceof JTextField f) {
				conf.set(s.key(), f.getText());
			}
		}
		String error = conf.validate();
		if (error != null) {
			frame.error(error);
			return false;
		}
		try {
			conf.save();
			return true;
		} catch (Exception e) {
			frame.error(T.t("Impossible d'enregistrer server.conf : ") + e.getMessage());
			return false;
		}
	}

	// ---------------------------------------------------------------- management

	private JComponent manageCard() {
		Theme.Card card = new Theme.Card(new BorderLayout(0, 10));
		card.setPreferredSize(new Dimension(440, 10));
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(Theme.title(T.t("Mon serveur")), BorderLayout.WEST);
		head.add(stateLabel, BorderLayout.EAST);
		head.setAlignmentX(LEFT_ALIGNMENT);
		top.add(head);
		startStop.setFont(Theme.font(Font.BOLD | Font.ITALIC, 16f));
		startStop.addActionListener(e -> {
			if (process.running()) {
				stopServer(false);
			} else {
				startServer();
			}
		});
		top.add(row(startStop));
		top.add(Box.createVerticalStrut(8));
		JPanel infos = new JPanel(new GridLayout(0, 2, 6, 4));
		infos.setOpaque(false);
		infos.add(Theme.label(T.t("Map"), Font.PLAIN, 13f, Theme.MUTED));
		infos.add(mapLabel);
		infos.add(Theme.label(T.t("Joueurs"), Font.PLAIN, 13f, Theme.MUTED));
		infos.add(playersLabel);
		infos.add(Theme.label(T.t("Manche"), Font.PLAIN, 13f, Theme.MUTED));
		infos.add(roundLabel);
		infos.setAlignmentX(LEFT_ALIGNMENT);
		top.add(infos);
		top.add(Box.createVerticalStrut(10));
		restart.addActionListener(e -> restartServer());
		restartMap.addActionListener(e -> process.command("restartmap"));
		nextMap.addActionListener(e -> process.command("nextmap"));
		JPanel line1 = new JPanel(new GridLayout(1, 3, 8, 0));
		line1.setOpaque(false);
		line1.add(restart);
		line1.add(restartMap);
		line1.add(nextMap);
		top.add(row(line1));
		top.add(Box.createVerticalStrut(6));
		// new game (scores reset) and warmup
		newGame.addActionListener(e -> {
			if (Dialogs.confirm(frame, T.t("Nouvelle partie"), T.t("Les points (Coupe) / les temps (Time Attack) de tous les joueurs sont remis à zéro et la map recommence. Continuer ?"))) {
				process.command("newgame");
			}
		});
		newGameWarmup.addActionListener(e -> {
			if (Dialogs.confirm(frame, T.t("Nouvelle partie"), T.t("Les points (Coupe) / les temps (Time Attack) de tous les joueurs sont remis à zéro et la map recommence avec son warmup. Continuer ?"))) {
				process.command("newgame warmup");
			}
		});
		JPanel line1b = new JPanel(new GridLayout(1, 2, 8, 0));
		line1b.setOpaque(false);
		line1b.add(newGame);
		line1b.add(newGameWarmup);
		top.add(row(line1b));
		top.add(Box.createVerticalStrut(6));
		warmupRestart.addActionListener(e -> process.command("warmup restart"));
		warmupAddTime.addActionListener(e -> process.command("warmup add 60"));
		warmupAddRound.addActionListener(e -> process.command("warmup addround"));
		for (JButton b : new JButton[] { warmupRestart, warmupAddTime, warmupAddRound }) {
			b.setFont(Theme.font(Font.BOLD | Font.ITALIC, 12f));
			b.setBorder(new javax.swing.border.EmptyBorder(9, 12, 9, 12));
		}
		JPanel line1c = new JPanel(new GridLayout(1, 3, 8, 0));
		line1c.setOpaque(false);
		line1c.add(warmupRestart);
		line1c.add(warmupAddTime);
		line1c.add(warmupAddRound);
		top.add(row(line1c));
		top.add(Box.createVerticalStrut(8));
		JPanel line2 = new JPanel(new BorderLayout(8, 0));
		line2.setOpaque(false);
		line2.add(Theme.label(T.t("Map :"), Font.PLAIN, 14f, Theme.MUTED), BorderLayout.WEST);
		line2.add(mapChoice, BorderLayout.CENTER);
		changeMap.addActionListener(e -> {
			Object m = mapChoice.getSelectedItem();
			if (m != null) {
				process.command("map " + m);
			}
		});
		line2.add(changeMap, BorderLayout.EAST);
		top.add(row(line2));
		top.add(Box.createVerticalStrut(8));
		joinOwn.addActionListener(e -> {
			frame.show("play");
			frame.play.startGame("127.0.0.1:" + conf.port());
		});
		top.add(row(joinOwn));
		top.add(Box.createVerticalStrut(10));
		card.add(top, BorderLayout.NORTH);

		console.setEditable(false);
		console.setBackground(new Color(0x0D0F13));
		console.setForeground(new Color(0xC8D0DA));
		console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		card.add(Theme.scroll(console), BorderLayout.CENTER);

		JPanel cmd = new JPanel(new BorderLayout(8, 0));
		cmd.setOpaque(false);
		command.setToolTipText(T.t("Commandes : say <texte>, nextmap, restartmap, map <fichier>, players, cupreset, bots <n>"));
		command.addActionListener(e -> sendCommand());
		cmd.add(command, BorderLayout.CENTER);
		JButton send = Theme.secondary(T.t("Envoyer"));
		send.addActionListener(e -> sendCommand());
		cmd.add(send, BorderLayout.EAST);
		JLabel hint = Theme.label(T.t("Message aux joueurs : say <texte>"), Font.PLAIN, 11f, Theme.MUTED);
		JPanel south = new JPanel(new BorderLayout(0, 4));
		south.setOpaque(false);
		south.add(cmd, BorderLayout.CENTER);
		south.add(hint, BorderLayout.SOUTH);
		card.add(south, BorderLayout.SOUTH);
		return card;
	}

	private static JComponent row(JComponent c) {
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(c);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
		return p;
	}

	private void sendCommand() {
		String c = command.getText().trim();
		if (c.isEmpty()) {
			return;
		}
		if (process.running() == false) {
			frame.status(T.t("Le serveur n'est pas lancé."));
			return;
		}
		log("> " + c);
		process.command(c);
		command.setText("");
	}

	private void startServer() {
		if (save() == false) {
			return;
		}
		File[] maps = Env.MAPS_DIR.listFiles((d, n) -> n.toLowerCase().endsWith(".cmap"));
		if (maps == null || maps.length == 0) {
			frame.error(T.t("Aucune map : ajoute des maps (page Maps)."));
			return;
		}
		if (Env.serverJar().isFile() == false) {
			frame.error(T.t("Serveur introuvable : ") + Env.serverJar());
			return;
		}
		try {
			console.setText("");
			synchronized (history) {
				history.clear();
			}
			log(T.f("=== Démarrage du serveur (%s, port %d) ===", conf.isCup() ? T.t("Coupe") : "Time Attack", conf.port()));
			process.start();
			frame.status(T.f("Serveur lancé sur le port %d.", conf.port()));
			process.command("maps");
		} catch (Exception e) {
			frame.error(T.t("Impossible de lancer le serveur : ") + e.getMessage());
		}
		updateButtons();
	}

	/** stops the server (blocking if wait) */
	void stopServer(boolean wait) {
		Runnable r = () -> {
			process.stop();
			SwingUtilities.invokeLater(this::updateButtons);
		};
		if (wait) {
			r.run();
		} else {
			frame.status(T.t("Arrêt du serveur..."));
			new Thread(r, "stop server").start();
		}
	}

	private void restartServer() {
		if (save() == false || restarting) {
			return;
		}
		restarting = true;
		frame.status(T.t("Redémarrage du serveur..."));
		new Thread(() -> {
			process.stop();
			Game.sleep(800);
			SwingUtilities.invokeLater(() -> {
				restarting = false;
				startServer();
			});
		}, "restart server").start();
	}

	private void stopped() {
		if (restarting == false) {
			frame.status(T.t("Serveur arrêté."));
		}
		stateLabel.setText(T.t("Arrêté"));
		stateLabel.setForeground(Theme.MUTED);
		mapLabel.setText("-");
		playersLabel.setText("-");
		roundLabel.setText("-");
		updateButtons();
	}

	private void updateButtons() {
		boolean on = process.running();
		startStop.setText(on ? T.t("ARRÊTER LE SERVEUR") : T.t("DÉMARRER LE SERVEUR"));
		restart.setEnabled(on);
		restartMap.setEnabled(on);
		nextMap.setEnabled(on);
		for (JButton b : new JButton[] { newGame, newGameWarmup, warmupRestart, warmupAddTime, warmupAddRound }) {
			b.setEnabled(on);
		}
		changeMap.setEnabled(on);
		mapChoice.setEnabled(on);
		joinOwn.setEnabled(on);
		if (on) {
			stateLabel.setText(T.t("En ligne"));
			stateLabel.setForeground(Theme.GREEN);
		}
		repaint();
	}

	private void showStatus(ServerProcess.Status s) {
		updateButtons();
		mapLabel.setText(s.map().replaceAll("(?i)\\.cmap$", ""));
		playersLabel.setText(s.players() + " / " + s.maxPlayers());
		String round;
		if (s.mode().equals("cup")) {
			round = T.f("Manche %s / %s", s.round(), s.rounds());
		} else {
			round = "Time Attack";
		}
		roundLabel.setText(round + (s.warmup() ? "  ·  WARMUP" : ""));
		roundLabel.setForeground(s.warmup() ? Theme.ACCENT : Theme.TEXT);
		if (s.map().isEmpty() == false && s.map().equals(mapChoice.getSelectedItem()) == false && mapChoice.isPopupVisible() == false
				&& mapChoice.getClientProperty("current") != null && mapChoice.getClientProperty("current").equals(s.map()) == false) {
			mapChoice.setSelectedItem(s.map());
		}
		mapChoice.putClientProperty("current", s.map());
	}

	private void showMaps(String[] maps) {
		Object selected = mapChoice.getSelectedItem();
		mapChoice.setModel(new DefaultComboBoxModel<>(maps));
		if (selected != null) {
			mapChoice.setSelectedItem(selected);
		}
	}

	/** the list of maps changed (page Maps) */
	void mapsChanged() {
		if (process.running()) {
			process.command("maps");
		}
	}

	private void log(String line) {
		synchronized (history) {
			history.add(line);
			if (history.size() > 3000) {
				history.subList(0, 1000).clear();
			}
		}
		append(line);
	}

	private void append(String line) {
		SwingUtilities.invokeLater(() -> {
			StyledDocument doc = console.getStyledDocument();
			SimpleAttributeSet a = new SimpleAttributeSet();
			Color c = new Color(0xC8D0DA);
			if (line.startsWith("===") || line.startsWith(">")) {
				c = Theme.ACCENT;
			} else if (line.startsWith("[players]") || line.contains("connected")) {
				c = Theme.GREEN;
			} else if (line.toLowerCase().contains("error") || line.contains("Exception") || line.startsWith("Unable")) {
				c = Theme.RED;
			}
			StyleConstants.setForeground(a, c);
			try {
				doc.insertString(doc.getLength(), line + "\n", a);
				if (doc.getLength() > 200_000) {
					doc.remove(0, doc.getLength() - 150_000);
				}
			} catch (BadLocationException ignored) {
			}
			console.setCaretPosition(doc.getLength());
		});
	}

	// ---------------------------------------------------------------- mode buttons

	private static final class ModeButton extends JButton {
		private boolean on;

		ModeButton(String text) {
			super(text);
			setFont(Theme.font(Font.BOLD | Font.ITALIC, 17f));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setPreferredSize(new Dimension(10, 46));
		}

		@Override
		public void setSelected(boolean b) {
			on = b;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			Theme.antialias(g);
			int w = getWidth(), h = getHeight();
			g.setColor(on ? Theme.ACCENT : Theme.PANEL2);
			g.fill(Theme.slant(0, 0, w, h, 12));
			if (on == false) {
				g.setColor(Theme.LINE);
				g.draw(Theme.slant(0.5f, 0.5f, w - 1, h - 1, 12));
			}
			g.setFont(getFont());
			g.setColor(on ? new Color(0x111111) : Theme.MUTED);
			FontMetrics fm = g.getFontMetrics();
			g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
			g.dispose();
		}
	}
}
