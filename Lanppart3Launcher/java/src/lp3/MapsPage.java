package lp3;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Page "Maps": maps of the server (rotation in alphabetical order): add (files .cmap, maps of the game), remove. */
final class MapsPage extends JPanel {
	private final LauncherFrame frame;
	private final DefaultListModel<File> model = new DefaultListModel<>();
	private final JList<File> list = new JList<>(model);
	private final JLabel count = Theme.label(" ", Font.PLAIN, 13f, Theme.MUTED);

	MapsPage(LauncherFrame frame) {
		super(new BorderLayout(0, 14));
		this.frame = frame;
		setOpaque(false);
		Theme.Card card = new Theme.Card(new BorderLayout(0, 12));
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(Theme.title(T.t("Maps du serveur")), BorderLayout.WEST);
		top.add(count, BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);

		list.setCellRenderer(new MapRenderer());
		list.setBackground(Theme.PANEL2);
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.setFixedCellHeight(46);
		card.add(Theme.scroll(list), BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		buttons.setOpaque(false);
		JButton add = Theme.primary(T.t("AJOUTER DES MAPS..."));
		add.addActionListener(e -> addMaps(null));
		JButton fromGame = Theme.secondary(T.t("Importer mes maps du jeu..."));
		fromGame.setToolTipText(T.t("Maps créées avec l'éditeur de Celaria (custom_maps)"));
		fromGame.addActionListener(e -> addMaps(new File(System.getenv().getOrDefault("LOCALAPPDATA", ""), "Celaria/custom_maps")));
		JButton remove = Theme.danger(T.t("Supprimer"));
		remove.addActionListener(e -> removeSelected());
		JButton folder = Theme.secondary(T.t("Ouvrir le dossier"));
		folder.addActionListener(e -> Env.openFolder(Env.MAPS_DIR));
		buttons.add(add);
		buttons.add(fromGame);
		buttons.add(remove);
		buttons.add(folder);
		JPanel south = new JPanel(new BorderLayout(0, 8));
		south.setOpaque(false);
		south.add(buttons, BorderLayout.NORTH);
		south.add(Theme.label(T.t("Les maps tournent dans l'ordre alphabétique des fichiers. Un serveur lancé prend en compte les changements à la map suivante."),
				Font.PLAIN, 12f, Theme.MUTED), BorderLayout.SOUTH);
		card.add(south, BorderLayout.SOUTH);
		add(card, BorderLayout.CENTER);
		refresh();
	}

	void refresh() {
		File[] files = Env.MAPS_DIR.listFiles((d, n) -> n.toLowerCase().endsWith(".cmap"));
		model.clear();
		if (files != null) {
			Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
			for (File f : files) {
				model.addElement(f);
			}
		}
		count.setText(T.f("%d map(s)", model.size()));
	}

	private void addMaps(File startDir) {
		JFileChooser c = new JFileChooser(startDir != null && startDir.isDirectory() ? startDir : new File(Env.get("maps.lastdir", System.getProperty("user.home"))));
		c.setDialogTitle(T.t("Ajouter des maps Celaria (.cmap)"));
		c.setMultiSelectionEnabled(true);
		c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(T.t("Maps Celaria (*.cmap)"), "cmap"));
		if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		int added = 0;
		List<String> errors = new ArrayList<>();
		for (File f : c.getSelectedFiles()) {
			if (isMap(f) == false) {
				errors.add(T.f("%s : ce n'est pas une map Celaria", f.getName()));
				continue;
			}
			try {
				Files.copy(f.toPath(), new File(Env.MAPS_DIR, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
				added++;
			} catch (IOException e) {
				errors.add(f.getName() + " : " + e.getMessage());
			}
		}
		if (c.getSelectedFiles().length > 0) {
			Env.set("maps.lastdir", c.getSelectedFiles()[0].getParent());
		}
		refresh();
		frame.server.mapsChanged();
		frame.status(T.f("%d map(s) ajoutée(s).", added));
		if (errors.isEmpty() == false) {
			frame.error(String.join("\n", errors));
		}
	}

	private void removeSelected() {
		List<File> selected = list.getSelectedValuesList();
		if (selected.isEmpty()) {
			frame.status(T.t("Choisis les maps à supprimer."));
			return;
		}
		if (selected.size() == model.size()) {
			frame.error(T.t("Garde au moins une map sur le serveur."));
			return;
		}
		if (Dialogs.confirm(frame, T.t("Supprimer"), T.f("Supprimer %d map(s) du serveur ?", selected.size())) == false) {
			return;
		}
		for (File f : selected) {
			f.delete();
		}
		refresh();
		frame.server.mapsChanged();
		frame.status(T.f("%d map(s) supprimée(s).", selected.size()));
	}

	/** files of the maps start with "celaria_map" */
	static boolean isMap(File f) {
		try (InputStream in = new FileInputStream(f)) {
			byte[] head = in.readNBytes(11);
			return new String(head, StandardCharsets.US_ASCII).equals("celaria_map");
		} catch (IOException e) {
			return false;
		}
	}

	/** name of the map stored in the file: "celaria_map", version u8, name length u8, name */
	static String mapName(File f) {
		try (InputStream in = new FileInputStream(f)) {
			byte[] head = in.readNBytes(13);
			if (head.length < 13) {
				return null;
			}
			int len = head[12] & 0xFF;
			byte[] name = in.readNBytes(len);
			return new String(name, StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			return null;
		}
	}

	static int mapVersion(File f) {
		try (InputStream in = new FileInputStream(f)) {
			byte[] head = in.readNBytes(12);
			return head.length == 12 ? head[11] & 0xFF : -1;
		} catch (IOException e) {
			return -1;
		}
	}

	private static final class MapRenderer extends JPanel implements ListCellRenderer<File> {
		private final JLabel name = Theme.label("", Font.BOLD, 15f, Theme.TEXT);
		private final JLabel file = Theme.label("", Font.PLAIN, 12f, Theme.MUTED);
		private final JLabel right = Theme.label("", Font.PLAIN, 12f, Theme.MUTED);

		MapRenderer() {
			super(new BorderLayout(10, 0));
			setBorder(new EmptyBorder(4, 14, 4, 14));
			JPanel texts = new JPanel(new GridLayout(2, 1));
			texts.setOpaque(false);
			texts.add(name);
			texts.add(file);
			add(texts, BorderLayout.CENTER);
			add(right, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends File> l, File f, int index, boolean selected, boolean focus) {
			String n = mapName(f);
			name.setText((index + 1) + ".  " + (n == null || n.isEmpty() ? f.getName() : n));
			file.setText(f.getName());
			int version = mapVersion(f);
			right.setText(version > 3 ? T.f("format %d (trop récent pour le serveur ?)", version) : T.f("%d Ko", f.length() / 1024 + 1));
			right.setForeground(version > 3 ? Theme.RED : Theme.MUTED);
			setBackground(selected ? new Color(0x4A4020) : index % 2 == 0 ? Theme.PANEL2 : new Color(0x1F242C));
			name.setForeground(selected ? Theme.ACCENT : Theme.TEXT);
			return this;
		}
	}
}
