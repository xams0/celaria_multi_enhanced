package lp3;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/** Page "Jouer": the game (detection), start with the mod, server browser LAN (start and join), Direct-IP. */
final class PlayPage extends JPanel {
	private final LauncherFrame frame;
	private final JLabel gamePath = Theme.label(" ", Font.PLAIN, 13f, Theme.MUTED);
	private final JLabel gameState = Theme.label(" ", Font.BOLD, 14f, Theme.TEXT);
	private final JButton removeOldMod = Theme.danger(T.t("Retirer l'ancien mod"));
	private final JButton playButton = Theme.primary(T.t("JOUER  —  CELARIA + MOD LANPPART3"));
	private final JButton joinButton = Theme.primary(T.t("REJOINDRE"));
	private final JButton refreshButton = Theme.secondary(T.t("Actualiser"));
	private final JTextField directIp = Theme.field(Env.get("direct.ip", "127.0.0.1:6510"), 16);
	private final ServerTableModel model = new ServerTableModel();
	private final JTable table = new JTable(model);
	private final JLabel scanState = Theme.label(" ", Font.PLAIN, 12f, Theme.MUTED);
	private volatile boolean busy, scanning;
	private final Timer autoRefresh;

	PlayPage(LauncherFrame frame) {
		super(new BorderLayout(0, 14));
		this.frame = frame;
		setOpaque(false);

		// ---- game
		Theme.Card game = new Theme.Card(new BorderLayout(14, 6));
		JPanel info = new JPanel(new GridLayout(0, 1, 0, 2));
		info.setOpaque(false);
		info.add(Theme.label("CELARIA", Font.BOLD | Font.ITALIC, 18f, Theme.TEXT));
		info.add(gameState);
		info.add(gamePath);
		game.add(info, BorderLayout.CENTER);
		JPanel gameButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		gameButtons.setOpaque(false);
		JButton browse = Theme.secondary(T.t("Choisir Celaria.exe..."));
		browse.addActionListener(e -> browse());
		JButton detect = Theme.secondary(T.t("Détecter"));
		detect.addActionListener(e -> {
			Env.set("game.exe", null);
			updateGame();
			frame.loadSplash();
		});
		removeOldMod.addActionListener(e -> removeOldMod());
		gameButtons.add(removeOldMod);
		gameButtons.add(detect);
		gameButtons.add(browse);
		game.add(gameButtons, BorderLayout.EAST);

		playButton.setFont(Theme.font(Font.BOLD | Font.ITALIC, 19f));
		playButton.setPreferredSize(new Dimension(10, 58));
		playButton.addActionListener(e -> startGame(null));
		JPanel north = new JPanel(new BorderLayout(0, 12));
		north.setOpaque(false);
		north.add(game, BorderLayout.NORTH);
		north.add(playButton, BorderLayout.CENTER);
		add(north, BorderLayout.NORTH);

		// ---- server browser LAN
		Theme.Card lan = new Theme.Card(new BorderLayout(0, 10));
		JPanel lanTop = new JPanel(new BorderLayout());
		lanTop.setOpaque(false);
		lanTop.add(Theme.title(T.t("Serveurs LAN")), BorderLayout.WEST);
		JPanel lanTopRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		lanTopRight.setOpaque(false);
		lanTopRight.add(scanState);
		refreshButton.addActionListener(e -> scan());
		lanTopRight.add(refreshButton);
		lanTop.add(lanTopRight, BorderLayout.EAST);
		lan.add(lanTop, BorderLayout.NORTH);
		styleTable();
		lan.add(Theme.scroll(table), BorderLayout.CENTER);

		JPanel lanBottom = new JPanel(new BorderLayout(10, 0));
		lanBottom.setOpaque(false);
		JPanel direct = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		direct.setOpaque(false);
		direct.add(Theme.label(T.t("Direct-IP :"), Font.PLAIN, 14f, Theme.MUTED));
		direct.add(directIp);
		JButton directJoin = Theme.secondary(T.t("Rejoindre cette adresse"));
		directJoin.addActionListener(e -> {
			String a = directIp.getText().trim();
			if (a.matches("[^:\\s]+:\\d{1,5}") == false) {
				frame.error(T.t("Adresse invalide : adresse:port (exemple 26.12.34.56:6510)."));
				return;
			}
			Env.set("direct.ip", a);
			startGame(a);
		});
		direct.add(directJoin);
		lanBottom.add(direct, BorderLayout.WEST);
		joinButton.addActionListener(e -> joinSelected());
		lanBottom.add(joinButton, BorderLayout.EAST);
		lan.add(lanBottom, BorderLayout.SOUTH);
		add(lan, BorderLayout.CENTER);

		autoRefresh = new Timer(6000, e -> {
			if (isShowing() && busy == false) {
				scan();
			}
		});
		autoRefresh.start();
		updateGame();
	}

	/** the page is shown */
	void shown() {
		updateGame();
		scan();
	}

	private void updateGame() {
		File exe = Game.locate();
		if (exe == null) {
			gameState.setText(T.t("Jeu introuvable"));
			gameState.setForeground(Theme.RED);
			gamePath.setText(T.t("Choisis Celaria.exe (dossier Steam : steamapps\\common\\Celaria)."));
		} else {
			gameState.setText(T.t("Jeu détecté"));
			gameState.setForeground(Theme.GREEN);
			gamePath.setText(exe.getAbsolutePath());
		}
		boolean old = Game.oldModInstalled(exe);
		removeOldMod.setVisible(old);
		if (old) {
			gameState.setText(T.t("Ancienne version du mod installée dans le dossier du jeu"));
			gameState.setForeground(Theme.ACCENT);
		}
		playButton.setEnabled(exe != null && busy == false);
	}

	private void browse() {
		JFileChooser c = new JFileChooser();
		c.setDialogTitle("Celaria.exe");
		c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Celaria.exe", "exe"));
		File current = Game.locate();
		if (current != null) {
			c.setCurrentDirectory(current.getParentFile());
		}
		if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File f = c.getSelectedFile();
			if (f.getName().equalsIgnoreCase("Celaria.exe") == false) {
				frame.error(T.t("Choisis le fichier Celaria.exe."));
				return;
			}
			Env.set("game.exe", f.getAbsolutePath());
			updateGame();
			frame.loadSplash();
		}
	}

	private void removeOldMod() {
		File exe = Game.locate();
		if (exe == null || Dialogs.confirm(frame, T.t("Ancien mod"),
				T.t("Le launcher charge le mod sans rien copier dans le jeu. L'ancienne version (winExt.dll remplacé) va être retirée et le winExt.dll d'origine restauré. Continuer ?")) == false) {
			return;
		}
		String error = Game.removeOldMod(exe);
		if (error != null) {
			frame.error(error);
		} else {
			frame.status(T.t("Ancien mod retiré : le dossier du jeu est d'origine."));
		}
		updateGame();
	}

	/** starts the game with the mod (connect: "address:port" or null) */
	void startGame(String connect) {
		if (busy) {
			return;
		}
		busy = true;
		playButton.setEnabled(false);
		joinButton.setEnabled(false);
		new Thread(() -> {
			String error = Game.start(Game.locate(), connect, frame::status);
			if (error != null) {
				frame.error(error);
			} else {
				frame.status(connect != null ? T.f("Celaria est lancé avec le mod : connexion automatique à %s après le menu.", connect)
						: T.t("Celaria est lancé avec le mod Lanppart3."));
			}
			Game.sleep(4000);// no double start
			busy = false;
			SwingUtilities.invokeLater(() -> {
				updateGame();
				joinButton.setEnabled(true);
			});
		}, "start game").start();
	}

	private void joinSelected() {
		int row = table.getSelectedRow();
		if (row < 0) {
			if (model.servers.size() == 1) {
				row = 0;
			} else {
				frame.error(model.servers.isEmpty() ? T.t("Aucun serveur LAN trouvé : lance un serveur (page Serveur) ou utilise Direct-IP.") : T.t("Choisis un serveur dans la liste."));
				return;
			}
		}
		LanScanner.LanServer s = model.servers.get(table.convertRowIndexToModel(row));
		if (s.password()) {
			Dialogs.message(frame, T.t("Mot de passe"), T.t("Ce serveur a un mot de passe : le jeu te le demandera à la connexion."), false);
		}
		startGame(s.endpoint());
	}

	void scan() {
		if (scanning) {
			return;
		}
		scanning = true;
		scanState.setText(T.t("Recherche..."));
		new Thread(() -> {
			List<LanScanner.LanServer> list = LanScanner.scan(900);
			SwingUtilities.invokeLater(() -> {
				String selected = table.getSelectedRow() >= 0 ? model.servers.get(table.convertRowIndexToModel(table.getSelectedRow())).endpoint() : null;
				model.set(list);
				for (int i = 0; i < list.size(); i++) {
					if (list.get(i).endpoint().equals(selected)) {
						table.setRowSelectionInterval(i, i);
					}
				}
				scanState.setText(list.isEmpty() ? T.t("Aucun serveur trouvé") : T.f("%d serveur(s)", list.size()));
				scanning = false;
			});
		}, "lan scan").start();
	}

	private void styleTable() {
		table.setRowHeight(34);
		table.setFont(Theme.font(Font.PLAIN, 14f));
		table.setForeground(Theme.TEXT);
		table.setBackground(Theme.PANEL2);
		table.setGridColor(Theme.LINE);
		table.setShowVerticalLines(false);
		table.setSelectionBackground(new Color(0x4A4020));
		table.setSelectionForeground(Color.WHITE);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JTableHeader h = table.getTableHeader();
		h.setReorderingAllowed(false);
		h.setDefaultRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
				JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
				l.setBackground(new Color(0x14171C));
				l.setForeground(Theme.ACCENT);
				l.setFont(Theme.font(Font.BOLD | Font.ITALIC, 13f));
				l.setBorder(new EmptyBorder(6, 10, 6, 10));
				return l;
			}
		});
		DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
				JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, false, r, c);
				l.setBorder(new EmptyBorder(0, 10, 0, 10));
				if (s == false) {
					l.setBackground(r % 2 == 0 ? Theme.PANEL2 : new Color(0x1F242C));
				}
				l.setForeground(c == 1 && T.t("COUPE").equals(v) ? Theme.ACCENT : c == 1 ? Theme.BLUE : Theme.TEXT);
				l.setFont(c == 0 || c == 1 ? Theme.font(Font.BOLD, 14f) : Theme.font(Font.PLAIN, 14f));
				return l;
			}
		};
		table.setDefaultRenderer(Object.class, cell);
		int[] widths = { 230, 110, 170, 80, 170 };
		for (int i = 0; i < widths.length; i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
		}
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
					joinSelected();
				}
			}
		});
	}

	private static final class ServerTableModel extends AbstractTableModel {
		final List<LanScanner.LanServer> servers = new ArrayList<>();
		private final String[] columns = { T.t("Serveur"), T.t("Mode"), T.t("Map"), T.t("Joueurs"), T.t("Adresse") };

		void set(List<LanScanner.LanServer> list) {
			servers.clear();
			servers.addAll(list);
			fireTableDataChanged();
		}

		public int getRowCount() {
			return servers.size();
		}

		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int c) {
			return columns[c];
		}

		public Object getValueAt(int r, int c) {
			LanScanner.LanServer s = servers.get(r);
			return switch (c) {
			case 0 -> (s.password() ? "🔒 " : "") + s.name();
			case 1 -> s.mode().equals("cup") ? T.t("COUPE") : T.t("TIME ATTACK");
			case 2 -> s.map();
			case 3 -> s.players() + " / " + s.maxPlayers();
			default -> s.endpoint();
			};
		}
	}
}
