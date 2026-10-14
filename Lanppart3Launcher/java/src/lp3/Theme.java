package lp3;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Colours, fonts and components of the launcher (Celaria style: dark panels, white text, yellow accent, slanted shapes). */
public final class Theme {
	public static final Color BG = new Color(0x111318);
	public static final Color PANEL = new Color(0x1A1D24);
	public static final Color PANEL2 = new Color(0x232831);
	public static final Color LINE = new Color(0x323945);
	public static final Color TEXT = new Color(0xF2F4F7);
	public static final Color MUTED = new Color(0x9AA3AE);
	public static final Color ACCENT = new Color(0xFFD21F);// yellow of the Celaria logo
	public static final Color ACCENT_DARK = new Color(0xC9A300);
	public static final Color BLUE = new Color(0x2F8CFF);
	public static final Color RED = new Color(0xE5484D);
	public static final Color GREEN = new Color(0x3CCF6E);

	public static final String FAMILY = pickFamily();

	private Theme() {
	}

	private static String pickFamily() {
		for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
			if (f.equals("Segoe UI")) {
				return f;
			}
		}
		return Font.SANS_SERIF;
	}

	public static Font font(int style, float size) {
		return new Font(FAMILY, style, 12).deriveFont(size);
	}

	public static void install() {
		UIManager.put("Panel.background", BG);
		UIManager.put("OptionPane.background", PANEL);
		UIManager.put("OptionPane.messageForeground", TEXT);
		UIManager.put("Label.foreground", TEXT);
		UIManager.put("ToolTip.background", PANEL2);
		UIManager.put("ToolTip.foreground", TEXT);
		UIManager.put("ToolTip.border", BorderFactory.createLineBorder(LINE));
		UIManager.put("ComboBox.background", PANEL2);
		UIManager.put("ComboBox.foreground", TEXT);
		UIManager.put("ComboBox.selectionBackground", ACCENT);
		UIManager.put("ComboBox.selectionForeground", Color.BLACK);
		UIManager.put("ComboBox.disabledBackground", PANEL2);
		UIManager.put("ComboBox.disabledForeground", MUTED);
		UIManager.put("List.background", PANEL2);
		UIManager.put("List.foreground", TEXT);
		UIManager.put("ScrollPane.background", PANEL);
		UIManager.put("Viewport.background", PANEL);
		UIManager.put("FileChooser.readOnly", Boolean.TRUE);
	}

	public static void antialias(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	}

	/** parallelogram slanted to the right (like the letters of the Celaria logo) */
	public static Shape slant(float x, float y, float w, float h, float skew) {
		Path2D p = new Path2D.Float();
		p.moveTo(x + skew, y);
		p.lineTo(x + w, y);
		p.lineTo(x + w - skew, y + h);
		p.lineTo(x, y + h);
		p.closePath();
		return p;
	}

	public static JLabel label(String text, int style, float size, Color color) {
		JLabel l = new JLabel(text);
		l.setFont(font(style, size));
		l.setForeground(color);
		return l;
	}

	public static JLabel title(String text) {
		JLabel l = label(text.toUpperCase(), Font.BOLD | Font.ITALIC, 20f, TEXT);
		l.setBorder(new EmptyBorder(0, 0, 6, 0));
		return l;
	}

	// ---------------------------------------------------------------- buttons

	/** slanted button: primary = yellow with black text, secondary = dark with white text, danger = red */
	public static class SlantButton extends JButton {
		public enum Kind {
			PRIMARY, SECONDARY, DANGER
		}

		private final Kind kind;
		private boolean hover, pressed;

		public SlantButton(String text, Kind kind) {
			super(text);
			this.kind = kind;
			setFont(font(Font.BOLD | Font.ITALIC, kind == Kind.PRIMARY ? 15f : 13f));
			setFocusPainted(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setBorder(new EmptyBorder(9, 26, 9, 26));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hover = false;
					pressed = false;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e) {
					pressed = true;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					pressed = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			antialias(g);
			int w = getWidth(), h = getHeight();
			float skew = Math.min(12f, h * 0.32f);
			Color fill, text;
			switch (kind) {
			case PRIMARY:
				fill = !isEnabled() ? new Color(0x5A5130) : pressed ? ACCENT_DARK : hover ? new Color(0xFFE066) : ACCENT;
				text = isEnabled() ? new Color(0x111111) : new Color(0x9A9070);
				break;
			case DANGER:
				fill = !isEnabled() ? new Color(0x4A2A2C) : pressed ? new Color(0xB8383C) : hover ? new Color(0xF0666A) : RED;
				text = Color.WHITE;
				break;
			default:
				fill = !isEnabled() ? new Color(0x20242B) : pressed ? new Color(0x2B313B) : hover ? new Color(0x3A4250) : PANEL2;
				text = isEnabled() ? TEXT : MUTED;
			}
			g.setColor(fill);
			g.fill(slant(0, 0, w, h, skew));
			if (kind == Kind.SECONDARY) {
				g.setColor(hover && isEnabled() ? ACCENT : LINE);
				g.setStroke(new BasicStroke(1.2f));
				g.draw(slant(0.6f, 0.6f, w - 1.2f, h - 1.2f, skew));
			}
			g.setFont(getFont());
			g.setColor(text);
			FontMetrics fm = g.getFontMetrics();
			String s = getText();
			g.drawString(s, (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
			g.dispose();
		}
	}

	public static SlantButton primary(String text) {
		return new SlantButton(text, SlantButton.Kind.PRIMARY);
	}

	public static SlantButton secondary(String text) {
		return new SlantButton(text, SlantButton.Kind.SECONDARY);
	}

	public static SlantButton danger(String text) {
		return new SlantButton(text, SlantButton.Kind.DANGER);
	}

	// ---------------------------------------------------------------- panels

	/** dark card with a thin yellow slanted mark in the corner */
	public static class Card extends JPanel {
		public Card(LayoutManager layout) {
			super(layout);
			setOpaque(false);
			setBorder(new EmptyBorder(16, 18, 16, 18));
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			antialias(g);
			g.setColor(PANEL);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
			g.setColor(LINE);
			g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
			g.setColor(ACCENT);
			g.fill(slant(0, 0, 46, 4, 3));
			g.dispose();
		}
	}

	public static JTextField field(String value, int columns) {
		JTextField f = new JTextField(value, columns);
		styleField(f);
		return f;
	}

	public static JPasswordField password(String value, int columns) {
		JPasswordField f = new JPasswordField(value, columns);
		styleField(f);
		return f;
	}

	public static void styleField(JTextField f) {
		f.setFont(font(Font.PLAIN, 14f));
		f.setBackground(PANEL2);
		f.setForeground(TEXT);
		f.setCaretColor(ACCENT);
		f.setSelectionColor(ACCENT);
		f.setSelectedTextColor(Color.BLACK);
		f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), new EmptyBorder(6, 8, 6, 8)));
	}

	public static JCheckBox check(String text, boolean selected) {
		JCheckBox c = new JCheckBox(text, selected);
		c.setOpaque(false);
		c.setForeground(TEXT);
		c.setFont(font(Font.PLAIN, 14f));
		c.setFocusPainted(false);
		c.setIcon(new CheckIcon(false));
		c.setSelectedIcon(new CheckIcon(true));
		c.setIconTextGap(10);
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return c;
	}

	private static class CheckIcon implements Icon {
		private final boolean on;

		CheckIcon(boolean on) {
			this.on = on;
		}

		public int getIconWidth() {
			return 38;
		}

		public int getIconHeight() {
			return 20;
		}

		public void paintIcon(Component c, Graphics g0, int x, int y) {
			Graphics2D g = (Graphics2D) g0.create();
			antialias(g);
			g.setColor(on ? ACCENT : PANEL2);
			g.fillRoundRect(x, y + 1, 38, 18, 18, 18);
			g.setColor(on ? ACCENT_DARK : LINE);
			g.drawRoundRect(x, y + 1, 37, 17, 18, 18);
			g.setColor(on ? new Color(0x111111) : MUTED);
			g.fillOval(on ? x + 22 : x + 3, y + 4, 12, 12);
			g.dispose();
		}
	}

	public static <T> JComboBox<T> combo(T[] items) {
		JComboBox<T> c = new JComboBox<>(items);
		c.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
			@Override
			protected JButton createArrowButton() {
				JButton b = new JButton() {
					@Override
					protected void paintComponent(Graphics g0) {
						Graphics2D g = (Graphics2D) g0.create();
						antialias(g);
						g.setColor(PANEL2);
						g.fillRect(0, 0, getWidth(), getHeight());
						g.setColor(ACCENT);
						int cx = getWidth() / 2, cy = getHeight() / 2;
						g.fillPolygon(new int[] { cx - 5, cx + 5, cx }, new int[] { cy - 2, cy - 2, cy + 4 }, 3);
						g.dispose();
					}
				};
				b.setBorder(BorderFactory.createEmptyBorder());
				b.setFocusPainted(false);
				return b;
			}

			@Override
			public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
				g.setColor(PANEL2);
				g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		});
		c.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
				JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
				l.setBorder(new EmptyBorder(5, 8, 5, 8));
				l.setBackground(selected && index >= 0 ? ACCENT : PANEL2);
				l.setForeground(selected && index >= 0 ? Color.BLACK : TEXT);
				return l;
			}
		});
		c.setFont(font(Font.PLAIN, 14f));
		c.setBackground(PANEL2);
		c.setForeground(TEXT);
		c.setBorder(BorderFactory.createLineBorder(LINE));
		return c;
	}

	/** panel which follows the width of the scroll pane (no horizontal scroll) */
	public static class WidthPanel extends JPanel implements Scrollable {
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
			return 16;
		}

		public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
			return r.height;
		}

		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	public static JScrollPane scroll(Component c) {
		JScrollPane s = new JScrollPane(c);
		s.setBorder(BorderFactory.createLineBorder(LINE));
		s.getViewport().setBackground(PANEL2);
		s.setBackground(PANEL2);
		s.getVerticalScrollBar().setUI(new DarkScrollBar());
		s.getHorizontalScrollBar().setUI(new DarkScrollBar());
		s.getVerticalScrollBar().setUnitIncrement(16);
		return s;
	}

	private static class DarkScrollBar extends BasicScrollBarUI {
		@Override
		protected void configureScrollBarColors() {
			thumbColor = new Color(0x4A5260);
			trackColor = PANEL2;
		}

		@Override
		protected JButton createDecreaseButton(int o) {
			return zero();
		}

		@Override
		protected JButton createIncreaseButton(int o) {
			return zero();
		}

		private JButton zero() {
			JButton b = new JButton();
			b.setPreferredSize(new Dimension(0, 0));
			return b;
		}

		@Override
		protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
			Graphics2D g = (Graphics2D) g0.create();
			antialias(g);
			g.setColor(isDragging ? ACCENT : thumbColor);
			g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
			g.dispose();
		}
	}

	// ---------------------------------------------------------------- icon of the launcher

	/** icon of the launcher (also written as .ico by the build): dark tile, yellow slash, slanted "L3" */
	public static Image icon(int size) {
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		antialias(g);
		float s = size / 64f;
		g.setColor(new Color(0x15181E));
		g.fill(new RoundRectangle2D.Float(1 * s, 1 * s, 62 * s, 62 * s, 14 * s, 14 * s));
		g.setColor(ACCENT);
		g.fill(slant(6 * s, 40 * s, 52 * s, 8 * s, 6 * s));
		g.setColor(Color.WHITE);
		g.setFont(font(Font.BOLD | Font.ITALIC, 34f * s));
		FontMetrics fm = g.getFontMetrics();
		String t = "L3";
		g.drawString(t, (size - fm.stringWidth(t)) / 2f + 1 * s, 37 * s);
		g.dispose();
		return img;
	}
}
