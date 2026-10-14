package lp3;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** dialogs in the style of the launcher */
public final class Dialogs {
	private Dialogs() {
	}

	private static boolean show(Component parent, String title, String text, boolean error, boolean question) {
		Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
		if (parent instanceof Window w) {
			owner = w;
		}
		JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
		d.setUndecorated(true);
		Theme.Card card = new Theme.Card(new BorderLayout(0, 14));
		card.setBorder(new EmptyBorder(20, 24, 18, 24));
		JLabel t = Theme.label(title.toUpperCase(), Font.BOLD | Font.ITALIC, 16f, error ? Theme.RED : Theme.ACCENT);
		card.add(t, BorderLayout.NORTH);
		JTextArea area = new JTextArea(text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setOpaque(false);
		area.setForeground(Theme.TEXT);
		area.setFont(Theme.font(Font.PLAIN, 14f));
		area.setColumns(36);
		area.setRows(Math.min(10, Math.max(2, text.length() / 40 + 1)));
		card.add(area, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		buttons.setOpaque(false);
		final boolean[] result = { false };
		if (question) {
			JButton no = Theme.secondary(T.t("Annuler"));
			no.addActionListener(e -> d.dispose());
			buttons.add(no);
		}
		JButton ok = Theme.primary(question ? T.t("Oui") : "OK");
		ok.addActionListener(e -> {
			result[0] = true;
			d.dispose();
		});
		buttons.add(ok);
		card.add(buttons, BorderLayout.SOUTH);
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.PANEL);
		root.setBorder(BorderFactory.createLineBorder(Theme.ACCENT_DARK));
		root.add(card);
		d.setContentPane(root);
		d.getRootPane().setDefaultButton((JButton) ok);
		d.pack();
		d.setLocationRelativeTo(owner);
		d.setVisible(true);
		return result[0];
	}

	public static void message(Component parent, String title, String text, boolean error) {
		show(parent, title, text, error, false);
	}

	public static boolean confirm(Component parent, String title, String text) {
		return show(parent, title, text, false, true);
	}
}
