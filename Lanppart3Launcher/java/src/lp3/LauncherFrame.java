package lp3;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Main window: title bar, header (picture of the game), navigation on the left (like the menus of Celaria), pages. */
public final class LauncherFrame extends JFrame {
	private final CardLayout cards = new CardLayout();
	private final JPanel pages = new JPanel(cards);
	private final JLabel status = Theme.label(" ", Font.PLAIN, 13f, Theme.MUTED);
	private final java.util.List<NavButton> nav = new java.util.ArrayList<>();
	final PlayPage play;
	final ServerPage server;
	final MapsPage maps;
	final PatchNotesPage notes;
	private BufferedImage splash, logo;
	private final JButton updateButton = Theme.primary(" ");
	private Updater.Release available;

	public LauncherFrame() {
		super("Celaria Lanppart3");
		setUndecorated(true);
		setIconImages(java.util.List.of(Theme.icon(16), Theme.icon(32), Theme.icon(64), Theme.icon(128)));
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});
		loadSplash();

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.BG);
		root.setBorder(BorderFactory.createLineBorder(Theme.LINE));
		setContentPane(root);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(titleBar(), BorderLayout.NORTH);
		top.add(new Header(), BorderLayout.CENTER);
		root.add(top, BorderLayout.NORTH);

		play = new PlayPage(this);
		server = new ServerPage(this);
		maps = new MapsPage(this);
		notes = new PatchNotesPage(this);
		pages.setOpaque(false);
		pages.add(wrap(play), "play");
		pages.add(wrap(server), "server");
		pages.add(wrap(maps), "maps");
		pages.add(wrap(notes), "notes");

		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBackground(new Color(0x16191F));
		side.setBorder(new EmptyBorder(14, 0, 14, 0));
		side.setPreferredSize(new Dimension(190, 10));
		addNav(side, T.t("JOUER"), "play", NavButton.Glyph.PLAY);
		addNav(side, T.t("SERVEUR"), "server", NavButton.Glyph.SERVER);
		addNav(side, T.t("MAPS"), "maps", NavButton.Glyph.MAPS);
		addNav(side, T.t("NOUVEAUTÉS"), "notes", NavButton.Glyph.NOTES);
		side.add(Box.createVerticalGlue());
		// language: FR / EN
		JPanel lang = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		lang.setOpaque(false);
		lang.setAlignmentX(LEFT_ALIGNMENT);
		lang.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		lang.setBorder(new EmptyBorder(0, 12, 8, 0));
		lang.add(langButton("FR", false));
		lang.add(langButton("EN", true));
		side.add(lang);
		JLabel version = Theme.label("  v" + Updater.currentVersion() + " · mod Lanppart3", Font.PLAIN, 11f, Theme.MUTED);
		version.setBorder(new EmptyBorder(0, 14, 0, 0));
		side.add(version);

		JPanel center = new JPanel(new BorderLayout());
		center.setOpaque(false);
		center.add(side, BorderLayout.WEST);
		center.add(pages, BorderLayout.CENTER);
		root.add(center, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout());
		bottom.setBackground(new Color(0x0C0E12));
		bottom.setBorder(new EmptyBorder(6, 14, 6, 14));
		bottom.add(status, BorderLayout.CENTER);
		updateButton.setVisible(false);
		updateButton.setFont(Theme.font(Font.BOLD | Font.ITALIC, 12f));
		updateButton.setBorder(new EmptyBorder(3, 18, 3, 18));
		updateButton.addActionListener(e -> installUpdate());
		bottom.add(updateButton, BorderLayout.EAST);
		root.add(bottom, BorderLayout.SOUTH);

		show("play");
		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setSize(Math.min(1200, screen.width - 40), Math.min(800, screen.height - 40));
		setLocationRelativeTo(null);
	}

	private JButton langButton(String text, boolean english) {
		JButton b = english == T.english() ? Theme.primary(text) : Theme.secondary(text);
		b.setFont(Theme.font(Font.BOLD | Font.ITALIC, 12f));
		b.setBorder(new EmptyBorder(4, 14, 4, 14));
		b.setToolTipText(T.t("Langue"));
		b.addActionListener(e -> {
			if (english != T.english()) {
				T.setEnglish(english);
				rebuild();
			}
		});
		return b;
	}

	/** new language: the window is created again (the server keeps running) */
	private void rebuild() {
		Point location = getLocation();
		LauncherFrame f = new LauncherFrame();
		f.setLocation(location);
		f.setVisible(true);
		dispose();
		f.show("notes".equals(currentCard) ? "notes" : currentCard);
	}

	private String currentCard = "play";

	// ---------------------------------------------------------------- updates

	/** checks the latest release on GitHub (at start: silent if up to date) */
	void checkUpdate(boolean manual) {
		new Thread(() -> {
			Updater.Release r = Updater.latest();
			SwingUtilities.invokeLater(() -> {
				if (r != null && Updater.newer(r.version(), Updater.currentVersion())) {
					available = r;
					updateButton.setText(T.f("Mise à jour %s disponible", "v" + r.version()));
					updateButton.setVisible(true);
					if (manual) {
						installUpdate();
					}
				} else if (manual) {
					Dialogs.message(this, T.t("Mise à jour"), T.f("Le launcher est à jour (v%s).", Updater.currentVersion()), false);
				}
			});
		}, "update check").start();
	}

	/** notes of the release without the markdown marks */
	private static String shortNotes(String notes) {
		String s = notes.replace("**", "").replace("__", "").trim();
		return s.length() > 600 ? s.substring(0, 600) + "..." : s;
	}

	private void installUpdate() {
		Updater.Release r = available;
		if (r == null) {
			return;
		}
		String text = T.f("Nouvelle version %s (actuelle : %s).\n\n%s\n\nInstaller maintenant ? Le launcher redémarrera.", "v" + r.version(),
				"v" + Updater.currentVersion(), shortNotes(r.notes()));
		if (Dialogs.confirm(this, T.t("Installer la mise à jour"), text) == false) {
			return;
		}
		updateButton.setEnabled(false);
		new Thread(() -> {
			String error = Updater.install(r, pct -> status(T.f("Téléchargement de la mise à jour : %d %%", pct)));
			if (error != null) {
				error(error);
				SwingUtilities.invokeLater(() -> updateButton.setEnabled(true));
				return;
			}
			status(T.t("Mise à jour installée : redémarrage..."));
			if (server.running()) {
				server.stopServer(true);
			}
			Updater.restart();
			System.exit(0);
		}, "update").start();
	}

	private static JComponent wrap(JComponent page) {
		page.setBorder(new EmptyBorder(18, 22, 18, 22));
		return page;
	}

	private void addNav(JPanel side, String text, String card, NavButton.Glyph glyph) {
		NavButton b = new NavButton(text, glyph);
		b.addActionListener(e -> show(card));
		b.putClientProperty("card", card);
		nav.add(b);
		side.add(b);
		side.add(Box.createVerticalStrut(4));
	}

	public void show(String card) {
		currentCard = card;
		cards.show(pages, card);
		for (NavButton b : nav) {
			b.setSelected(card.equals(b.getClientProperty("card")));
		}
		if (card.equals("play")) {
			play.shown();
		} else if (card.equals("maps")) {
			maps.refresh();
		}
	}

	public void status(String text) {
		SwingUtilities.invokeLater(() -> status.setText(text));
	}

	public void error(String text) {
		SwingUtilities.invokeLater(() -> {
			status.setText(text);
			Dialogs.message(this, "Celaria Lanppart3", text, true);
		});
	}

	void loadSplash() {
		File exe = Game.locate();
		splash = null;
		if (exe != null) {
			try {
				splash = ImageIO.read(new File(exe.getParentFile(), "splash.png"));
				logo = extractLogo(splash);
			} catch (Exception ignored) {
			}
		}
		repaint();
	}

	/** logo of the game: top of the splash picture without the blue sky (pixels where blue dominates become transparent) */
	private static BufferedImage extractLogo(BufferedImage s) {
		int x0 = 60, y0 = 18, w = Math.min(690, s.getWidth() - x0), h = Math.min(132, s.getHeight() - y0);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = s.getRGB(x0 + x, y0 + y), r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
				int blueness = b - Math.max(r, g);// sky: strongly blue; logo: white, grey, yellow, black outline
				int alpha = blueness > 60 ? 0 : blueness > 25 ? (60 - blueness) * 255 / 35 : 255;
				out.setRGB(x, y, (alpha << 24) | (p & 0xFFFFFF));
			}
		}
		return out;
	}

	private void quit() {
		if (server.running()) {
			if (Dialogs.confirm(this, T.t("Quitter"), T.t("Le serveur est lancé : il sera arrêté. Quitter ?")) == false) {
				return;
			}
			status(T.t("Arrêt du serveur..."));
			server.stopServer(true);
		}
		dispose();
		System.exit(0);
	}

	// ---------------------------------------------------------------- title bar

	private JComponent titleBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(new Color(0x0C0E12));
		bar.setBorder(new EmptyBorder(4, 10, 4, 4));
		JLabel t = Theme.label("CELARIA LANPPART3", Font.BOLD | Font.ITALIC, 12f, Theme.MUTED);
		t.setIcon(new ImageIcon(Theme.icon(18)));
		t.setIconTextGap(8);
		bar.add(t, BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setOpaque(false);
		buttons.add(barButton("–", e -> setState(Frame.ICONIFIED), false));
		buttons.add(barButton("✕", e -> quit(), true));
		bar.add(buttons, BorderLayout.EAST);
		MouseAdapter drag = new MouseAdapter() {
			Point start;

			@Override
			public void mousePressed(MouseEvent e) {
				start = e.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				Point p = getLocation();
				setLocation(p.x + e.getX() - start.x, p.y + e.getY() - start.y);
			}
		};
		bar.addMouseListener(drag);
		bar.addMouseMotionListener(drag);
		return bar;
	}

	/** button of the title bar: the glyph (minimize line / close cross) is drawn, not a character of the font */
	private JButton barButton(String text, ActionListener action, boolean close) {
		JButton b = new JButton() {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0.create();
				Theme.antialias(g);
				g.setColor(getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(getForeground());
				g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				int cx = getWidth() / 2, cy = getHeight() / 2, r = 5;
				if (close) {
					g.drawLine(cx - r, cy - r, cx + r, cy + r);
					g.drawLine(cx - r, cy + r, cx + r, cy - r);
				} else {
					g.drawLine(cx - r, cy, cx + r, cy);
				}
				g.dispose();
			}
		};
		b.setToolTipText(close ? T.t("Quitter") : T.t("Réduire"));
		b.setPreferredSize(new Dimension(46, 30));
		b.setForeground(Theme.MUTED);
		b.setBackground(new Color(0x0C0E12));
		b.setBorder(new EmptyBorder(0, 0, 0, 0));
		b.setFocusPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(true);
		b.addActionListener(action);
		b.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				b.setBackground(close ? Theme.RED : Theme.PANEL2);
				b.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				b.setBackground(new Color(0x0C0E12));
				b.setForeground(Theme.MUTED);
			}
		});
		return b;
	}

	// ---------------------------------------------------------------- header

	/** picture of the game (splash.png of the installation) with the logo, "LANPPART3" in yellow */
	private final class Header extends JComponent {
		Header() {
			setPreferredSize(new Dimension(10, 132));
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			Theme.antialias(g);
			int w = getWidth(), h = getHeight();
			if (splash != null) {
				// background: the scene of the splash picture (below the logo), covering the header
				int sx = 0, sy = 170, sw = splash.getWidth(), sh = splash.getHeight() - 170;
				double scale = Math.max((double) w / sw, (double) h / sh);
				int dw = (int) (sw * scale), dh = (int) (sh * scale);
				g.drawImage(splash, 0, (h - dh) / 2, dw, (h - dh) / 2 + dh, sx, sy, sx + sw, sy + sh, null);
				g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 16, 235), w * 0.75f, 0, new Color(10, 12, 16, 120)));
				g.fillRect(0, 0, w, h);
				// logo of the game (top of the splash picture)
				int lw = 360, lh = lw * logo.getHeight() / logo.getWidth();
				g.drawImage(logo, 22, 12, lw, lh, null);
			} else {
				g.setPaint(new GradientPaint(0, 0, new Color(0x1B4F9C), w, h, new Color(0x0E1E3A)));
				g.fillRect(0, 0, w, h);
				g.setColor(Color.WHITE);
				g.setFont(Theme.font(Font.BOLD | Font.ITALIC, 54f));
				g.drawString("CELARIA", 30, 74);
			}
			// "LANPPART3" on a slanted yellow band
			g.setFont(Theme.font(Font.BOLD | Font.ITALIC, 26f));
			FontMetrics fm = g.getFontMetrics();
			String t = "LANPPART3";
			int bx = 34, by = 92, bw = fm.stringWidth(t) + 40, bh = 34;
			g.setColor(Theme.ACCENT);
			g.fill(Theme.slant(bx, by, bw, bh, 10));
			g.setColor(new Color(0x111111));
			g.drawString(t, bx + 20, by + (bh - fm.getHeight()) / 2 + fm.getAscent());
			g.setFont(Theme.font(Font.PLAIN, 14f));
			g.setColor(new Color(255, 255, 255, 220));
			g.drawString(T.t("Launcher LAN · Coupe & Time Attack · mod chargé sans toucher au jeu"), bx + bw + 16, by + 23);
			g.setColor(Theme.ACCENT);
			g.fillRect(0, h - 3, w, 3);
			g.dispose();
		}
	}

	// ---------------------------------------------------------------- navigation

	static final class NavButton extends JButton {
		enum Glyph {
			PLAY, SERVER, MAPS, NOTES
		}

		private final Glyph glyph;
		private boolean selectedPage, hover;

		NavButton(String text, Glyph glyph) {
			super(text);
			this.glyph = glyph;
			setFont(Theme.font(Font.BOLD | Font.ITALIC, 16f));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setAlignmentX(LEFT_ALIGNMENT);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
			setPreferredSize(new Dimension(190, 58));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hover = false;
					repaint();
				}
			});
		}

		@Override
		public void setSelected(boolean b) {
			selectedPage = b;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			Theme.antialias(g);
			int w = getWidth(), h = getHeight();
			if (selectedPage || hover) {
				g.setColor(selectedPage ? new Color(0x262B35) : new Color(0x1E222A));
				g.fill(Theme.slant(-10, 4, w + 4, h - 8, 10));
			}
			if (selectedPage) {
				g.setColor(Theme.ACCENT);
				g.fill(Theme.slant(0, 4, 9, h - 8, 4));
			}
			Color c = selectedPage ? Theme.ACCENT : hover ? Color.WHITE : Theme.TEXT;
			g.setColor(c);
			int ix = 26, iy = h / 2 - 11;
			switch (glyph) {
			case PLAY -> g.fillPolygon(new int[] { ix + 3, ix + 21, ix + 3 }, new int[] { iy, iy + 11, iy + 22 }, 3);
			case SERVER -> {
				for (int i = 0; i < 3; i++) {
					g.fillRoundRect(ix, iy + i * 8, 22, 6, 3, 3);
				}
			}
			case MAPS -> {
				g.setStroke(new BasicStroke(2.2f));
				g.drawPolygon(new int[] { ix, ix + 7, ix + 15, ix + 22, ix + 22, ix + 15, ix + 7, ix }, new int[] { iy + 3, iy, iy + 3, iy, iy + 19, iy + 22,
						iy + 19, iy + 22 }, 8);
				g.drawLine(ix + 7, iy, ix + 7, iy + 19);
				g.drawLine(ix + 15, iy + 3, ix + 15, iy + 22);
			}
			case NOTES -> {
				g.setStroke(new BasicStroke(2.2f));
				g.drawRoundRect(ix + 1, iy, 20, 22, 4, 4);
				g.drawLine(ix + 6, iy + 7, ix + 16, iy + 7);
				g.drawLine(ix + 6, iy + 12, ix + 16, iy + 12);
				g.drawLine(ix + 6, iy + 17, ix + 13, iy + 17);
			}
			}
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			g.drawString(getText(), ix + 38, (h - fm.getHeight()) / 2 + fm.getAscent());
			g.dispose();
		}
	}
}
