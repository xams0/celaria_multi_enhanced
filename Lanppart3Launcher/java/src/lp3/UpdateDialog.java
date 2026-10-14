package lp3;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Mandatory update: when a newer version is published on GitHub, this window opens before the launcher can be used.
 * The only choices are to install the update (download, replacement of the exe, restart) or to quit.
 */
final class UpdateDialog extends JDialog {
	private final Updater.Release release;
	private final JButton install = Theme.primary(T.t("INSTALLER LA MISE À JOUR"));
	private final JButton quit = Theme.secondary(T.t("Quitter"));
	private final JProgressBar progress = new JProgressBar(0, 100);
	private final JLabel state = Theme.label(" ", Font.PLAIN, 13f, Theme.MUTED);
	private final Runnable beforeRestart;

	/** owner: the launcher window (or null at start); beforeRestart: stops the server if needed */
	UpdateDialog(Window owner, Updater.Release release, Runnable beforeRestart) {
		super(owner, T.t("Mise à jour obligatoire"), ModalityType.APPLICATION_MODAL);
		this.release = release;
		this.beforeRestart = beforeRestart;
		setUndecorated(true);
		setIconImages(java.util.List.of(Theme.icon(16), Theme.icon(32), Theme.icon(64)));
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				exit();
			}
		});

		Theme.Card card = new Theme.Card(new BorderLayout(0, 14));
		card.setBorder(new EmptyBorder(22, 26, 20, 26));
		JPanel head = new JPanel(new BorderLayout(14, 4));
		head.setOpaque(false);
		head.add(new JLabel(new ImageIcon(Theme.icon(56))), BorderLayout.WEST);
		JPanel titles = new JPanel(new GridLayout(2, 1));
		titles.setOpaque(false);
		titles.add(Theme.label(T.t("MISE À JOUR OBLIGATOIRE"), Font.BOLD | Font.ITALIC, 20f, Theme.ACCENT));
		titles.add(Theme.label(T.f("Version %s disponible (installée : %s)", "v" + release.version(), "v" + Updater.currentVersion()),
				Font.PLAIN, 14f, Theme.TEXT));
		head.add(titles, BorderLayout.CENTER);
		card.add(head, BorderLayout.NORTH);

		JTextArea notes = new JTextArea(T.t("Le launcher doit être mis à jour pour être utilisé (tous les joueurs de la LAN doivent avoir la même version).")
				+ "\n\n" + clean(release.notes()));
		notes.setEditable(false);
		notes.setLineWrap(true);
		notes.setWrapStyleWord(true);
		notes.setFont(Theme.font(Font.PLAIN, 13f));
		notes.setForeground(Theme.TEXT);
		notes.setBackground(Theme.PANEL2);
		notes.setBorder(new EmptyBorder(10, 12, 10, 12));
		notes.setCaretPosition(0);
		JScrollPane scroll = Theme.scroll(notes);
		scroll.setPreferredSize(new Dimension(560, 230));
		JPanel middle = new JPanel(new BorderLayout(0, 10));
		middle.setOpaque(false);
		middle.add(scroll, BorderLayout.CENTER);
		progress.setStringPainted(false);
		progress.setForeground(Theme.ACCENT);
		progress.setBackground(Theme.PANEL2);
		progress.setBorder(BorderFactory.createLineBorder(Theme.LINE));
		progress.setPreferredSize(new Dimension(10, 10));
		progress.setVisible(false);
		JPanel progressBox = new JPanel(new BorderLayout(0, 4));
		progressBox.setOpaque(false);
		progressBox.add(progress, BorderLayout.NORTH);
		progressBox.add(state, BorderLayout.SOUTH);
		middle.add(progressBox, BorderLayout.SOUTH);
		card.add(middle, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		buttons.setOpaque(false);
		quit.addActionListener(e -> exit());
		install.addActionListener(e -> install());
		buttons.add(quit);
		buttons.add(install);
		card.add(buttons, BorderLayout.SOUTH);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.PANEL);
		root.setBorder(BorderFactory.createLineBorder(Theme.ACCENT_DARK, 2));
		root.add(card);
		setContentPane(root);
		getRootPane().setDefaultButton(install);
		pack();
		setLocationRelativeTo(owner);
	}

	private static String clean(String notes) {
		return notes.replace("**", "").replace("__", "").trim();
	}

	private void exit() {
		if (install.isEnabled() == false) {
			return;// download running
		}
		System.exit(0);
	}

	private void install() {
		install.setEnabled(false);
		quit.setEnabled(false);
		progress.setVisible(true);
		progress.setValue(0);
		state.setForeground(Theme.MUTED);
		new Thread(() -> {
			String error = Updater.install(release, pct -> SwingUtilities.invokeLater(() -> {
				progress.setValue(pct);
				state.setText(T.f("Téléchargement de la mise à jour : %d %%", pct));
			}));
			if (error != null) {
				SwingUtilities.invokeLater(() -> {
					state.setText(error);
					state.setForeground(Theme.RED);
					install.setText(T.t("RÉESSAYER"));
					install.setEnabled(Updater.launcherExe() != null);
					quit.setEnabled(true);
				});
				return;
			}
			SwingUtilities.invokeLater(() -> state.setText(T.t("Mise à jour installée : redémarrage...")));
			if (beforeRestart != null) {
				beforeRestart.run();
			}
			Updater.restart();
			System.exit(0);
		}, "update").start();
	}

	/** small window while GitHub is checked at start */
	static JWindow checking() {
		JWindow w = new JWindow();
		Theme.Card card = new Theme.Card(new BorderLayout(12, 0));
		card.setBorder(new EmptyBorder(16, 20, 16, 24));
		card.add(new JLabel(new ImageIcon(Theme.icon(40))), BorderLayout.WEST);
		card.add(Theme.label(T.t("Celaria Lanppart3 : recherche de mise à jour..."), Font.BOLD | Font.ITALIC, 14f, Theme.TEXT), BorderLayout.CENTER);
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.PANEL);
		root.setBorder(BorderFactory.createLineBorder(Theme.LINE));
		root.add(card);
		w.setContentPane(root);
		w.pack();
		w.setLocationRelativeTo(null);
		w.setAlwaysOnTop(true);
		w.setIconImages(java.util.List.of(Theme.icon(16), Theme.icon(32)));
		return w;
	}
}
