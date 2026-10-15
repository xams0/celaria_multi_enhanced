package lp3;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Page "Maps": maps of the server: add (files .cmap dropped on the list or chosen, maps of the game), remove, order of the
 * rotation (drag and drop in the list, up / down) or random order. The order is saved in maps/maplist.txt, read by the
 * server at every map change.
 */
final class MapsPage extends JPanel {
	static final File ORDER_FILE = new File(Env.MAPS_DIR, "maplist.txt");
	private final LauncherFrame frame;
	private final DefaultListModel<File> model = new DefaultListModel<>();
	private final JList<File> list = new JList<>(model);
	private final JLabel count = Theme.label(" ", Font.PLAIN, 13f, Theme.MUTED);
	private final JCheckBox random = Theme.check(T.t("Ordre aléatoire"), false);
	private boolean refreshing;

	MapsPage(LauncherFrame frame) {
		super(new BorderLayout(0, 14));
		this.frame = frame;
		setOpaque(false);
		Theme.Card card = new Theme.Card(new BorderLayout(0, 12));
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(Theme.title(T.t("Maps du serveur")), BorderLayout.WEST);
		JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
		topRight.setOpaque(false);
		random.setToolTipText(T.t("Le serveur mélange les maps : chacune passe une fois avant qu'une map revienne"));
		random.addActionListener(e -> {
			if (refreshing == false) {
				saveOrder(names());
				frame.status(random.isSelected() ? T.t("Maps dans un ordre aléatoire.") : T.t("Maps dans l'ordre de la liste."));
			}
		});
		topRight.add(random);
		topRight.add(count);
		top.add(topRight, BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);

		UIManager.put("List.dropLineColor", Theme.ACCENT);
		list.setCellRenderer(new MapRenderer());
		list.setBackground(Theme.PANEL2);
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.setFixedCellHeight(46);
		MapTransfer transfer = new MapTransfer();
		list.setDragEnabled(true);
		list.setDropMode(DropMode.INSERT);
		list.setTransferHandler(transfer);
		JScrollPane scroll = Theme.scroll(list);
		scroll.setTransferHandler(transfer);// files dropped below the last map
		scroll.getViewport().setTransferHandler(transfer);
		card.add(scroll, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		buttons.setOpaque(false);
		JButton add = Theme.primary(T.t("AJOUTER DES MAPS..."));
		add.addActionListener(e -> addMaps(null));
		JButton fromGame = Theme.secondary(T.t("Importer mes maps du jeu..."));
		fromGame.setToolTipText(T.t("Maps créées avec l'éditeur de Celaria (custom_maps)"));
		fromGame.addActionListener(e -> addMaps(new File(System.getenv().getOrDefault("LOCALAPPDATA", ""), "Celaria/custom_maps")));
		JButton up = Theme.secondary("▲");
		up.setToolTipText(T.t("Monter"));
		up.addActionListener(e -> moveSelected(-1));
		JButton down = Theme.secondary("▼");
		down.setToolTipText(T.t("Descendre"));
		down.addActionListener(e -> moveSelected(1));
		JButton remove = Theme.danger(T.t("Supprimer"));
		remove.addActionListener(e -> removeSelected());
		JButton folder = Theme.secondary(T.t("Ouvrir le dossier"));
		folder.addActionListener(e -> Env.openFolder(Env.MAPS_DIR));
		buttons.add(add);
		buttons.add(fromGame);
		buttons.add(up);
		buttons.add(down);
		buttons.add(remove);
		buttons.add(folder);
		JPanel south = new JPanel(new BorderLayout(0, 8));
		south.setOpaque(false);
		south.add(buttons, BorderLayout.NORTH);
		south.add(Theme.label("<html>" + T.t("Glisse des fichiers .cmap sur la liste pour les ajouter, glisse les maps dans la liste pour changer l'ordre de passage. Un serveur lancé prend en compte les changements à la map suivante.").replaceFirst("\\. ", ".<br>") + "</html>",
				Font.PLAIN, 12f, Theme.MUTED), BorderLayout.SOUTH);
		card.add(south, BorderLayout.SOUTH);
		add(card, BorderLayout.CENTER);
		refresh();
	}

	void refresh() {
		File[] files = Env.MAPS_DIR.listFiles((d, n) -> n.toLowerCase().endsWith(".cmap"));
		model.clear();
		List<String> order = readOrder();
		if (files != null) {
			// same order as the server: maps of maplist.txt, then the others in alphabetical order
			Map<String, Integer> rank = new HashMap<>();
			for (String n : order) {
				rank.putIfAbsent(n.toLowerCase(), rank.size());
			}
			Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
			Arrays.sort(files, Comparator.comparing(f -> rank.getOrDefault(f.getName().toLowerCase(), Integer.MAX_VALUE)));
			for (File f : files) {
				model.addElement(f);
			}
		}
		refreshing = true;
		random.setSelected(order.stream().anyMatch(l -> l.replace(" ", "").equalsIgnoreCase("random=true")));
		refreshing = false;
		count.setText(T.f("%d map(s)", model.size()));
		list.repaint();
	}

	/** lines of maplist.txt (no comment) */
	private static List<String> readOrder() {
		List<String> lines = new ArrayList<>();
		try {
			for (String l : Files.readAllLines(ORDER_FILE.toPath(), StandardCharsets.UTF_8)) {
				l = l.trim();
				if (l.isEmpty() == false && l.startsWith("#") == false) {
					lines.add(l);
				}
			}
		} catch (IOException ignored) {
		}
		return lines;
	}

	private List<String> names() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < model.size(); i++) {
			names.add(model.get(i).getName());
		}
		return names;
	}

	/** writes the order of the rotation (and the random setting) for the server */
	private void saveOrder(List<String> names) {
		StringBuilder b = new StringBuilder("# Celaria Lanppart3: order of the maps (written by the launcher)\n");
		b.append("random = ").append(random.isSelected()).append("\n");
		for (String n : names) {
			b.append(n).append("\n");
		}
		try {
			Files.writeString(ORDER_FILE.toPath(), b.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			frame.error(e.getMessage());
		}
		refresh();
		frame.server.mapsChanged();
	}

	private void select(Collection<String> names) {
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < model.size(); i++) {
			if (names.contains(model.get(i).getName())) {
				indices.add(i);
			}
		}
		list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
		if (indices.isEmpty() == false) {
			list.ensureIndexIsVisible(indices.get(0));
		}
	}

	/** moves the maps at these indices before the map at index "at" (model.size() = at the end) */
	private void move(int[] indices, int at) {
		List<String> names = names();
		List<String> moved = new ArrayList<>();
		int insert = at;
		for (int i : indices) {
			if (i >= 0 && i < names.size()) {
				moved.add(names.get(i));
				if (i < at) {
					insert--;
				}
			}
		}
		if (moved.isEmpty()) {
			return;
		}
		names.removeAll(moved);
		insert = Math.max(0, Math.min(insert, names.size()));
		names.addAll(insert, moved);
		saveOrder(names);
		select(moved);
	}

	private void moveSelected(int direction) {
		int[] selected = list.getSelectedIndices();
		if (selected.length == 0) {
			frame.status(T.t("Choisis les maps à déplacer."));
			return;
		}
		move(selected, direction < 0 ? Math.max(0, selected[0] - 1) : selected[selected.length - 1] + 2);
	}

	private void addMaps(File startDir) {
		JFileChooser c = new JFileChooser(startDir != null && startDir.isDirectory() ? startDir : new File(Env.get("maps.lastdir", System.getProperty("user.home"))));
		c.setDialogTitle(T.t("Ajouter des maps Celaria (.cmap)"));
		c.setMultiSelectionEnabled(true);
		c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(T.t("Maps Celaria (*.cmap)"), "cmap"));
		if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		if (c.getSelectedFiles().length > 0) {
			Env.set("maps.lastdir", c.getSelectedFiles()[0].getParent());
		}
		importMaps(Arrays.asList(c.getSelectedFiles()), model.size());
	}

	/** copies the maps (files, or folders of maps) in the maps of the server, the new ones inserted at index "at" */
	private void importMaps(List<File> files, int at) {
		List<File> all = new ArrayList<>();
		for (File f : files) {
			File[] inside = f.isDirectory() ? f.listFiles((d, n) -> n.toLowerCase().endsWith(".cmap")) : null;
			if (inside != null) {
				Arrays.sort(inside, Comparator.comparing(m -> m.getName().toLowerCase()));
				all.addAll(Arrays.asList(inside));
			} else if (f.isFile()) {
				all.add(f);
			}
		}
		List<String> names = names();
		List<String> added = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		int insert = Math.max(0, Math.min(at, names.size()));
		for (File f : all) {
			if (isMap(f) == false) {
				errors.add(T.f("%s : ce n'est pas une map Celaria", f.getName()));
				continue;
			}
			File target = new File(Env.MAPS_DIR, f.getName());
			try {
				if (f.getCanonicalFile().equals(target.getCanonicalFile()) == false) {
					Files.copy(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				added.add(f.getName());
				if (names.stream().noneMatch(n -> n.equalsIgnoreCase(f.getName()))) {
					names.add(insert++, f.getName());// a map replaced by a new version keeps its place
				}
			} catch (IOException e) {
				errors.add(f.getName() + " : " + e.getMessage());
			}
		}
		if (added.isEmpty() == false) {
			saveOrder(names);
			select(added);
		}
		frame.status(T.f("%d map(s) ajoutée(s).", added.size()));
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
		List<String> names = names();
		for (File f : selected) {
			f.delete();
			names.remove(f.getName());
		}
		saveOrder(names);
		frame.status(T.f("%d map(s) supprimée(s).", selected.size()));
	}

	/** drag and drop: .cmap files (Explorer) dropped on the list, maps moved inside the list */
	private final class MapTransfer extends TransferHandler {
		private static final String MARK = "lp3-maps-move";
		private int[] dragged;

		@Override
		public int getSourceActions(JComponent c) {
			return MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			dragged = list.getSelectedIndices();
			return dragged.length == 0 ? null : new StringSelection(MARK);
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			dragged = null;
		}

		@Override
		public boolean canImport(TransferSupport support) {
			if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				if (support.isDrop() && (support.getSourceDropActions() & COPY) != 0) {
					support.setDropAction(COPY);
				}
				return true;
			}
			return dragged != null && support.isDataFlavorSupported(DataFlavor.stringFlavor);
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean importData(TransferSupport support) {
			if (canImport(support) == false) {
				return false;
			}
			int at = model.size();
			if (support.isDrop() && support.getDropLocation() instanceof JList.DropLocation dl && dl.getIndex() >= 0) {
				at = dl.getIndex();
			}
			try {
				if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					int where = at;
					SwingUtilities.invokeLater(() -> importMaps(files, where));// after the drop (error dialogs)
					return true;
				}
				if (dragged != null && MARK.equals(support.getTransferable().getTransferData(DataFlavor.stringFlavor))) {
					int[] indices = dragged;
					int where = at;
					SwingUtilities.invokeLater(() -> move(indices, where));// after the drop: the list is rebuilt
					return true;
				}
			} catch (UnsupportedFlavorException | IOException e) {
				frame.error(e.getMessage());
			}
			return false;
		}
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

	private final class MapRenderer extends JPanel implements ListCellRenderer<File> {
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
			// random order: no number (the server shuffles the maps)
			name.setText((random.isSelected() ? "" : (index + 1) + ".  ") + (n == null || n.isEmpty() ? f.getName() : n));
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
