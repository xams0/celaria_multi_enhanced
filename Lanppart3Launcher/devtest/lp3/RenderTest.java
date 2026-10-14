package lp3;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** dev: renders the pages of the launcher to PNG files (language = args[0], output folder = args[1]) without showing them */
public class RenderTest {
	public static void main(String[] args) throws Exception {
		Env.init();
		T.setEnglish(args[0].equals("en"));
		SwingUtilities.invokeAndWait(() -> {
			Theme.install();
			LauncherFrame f = new LauncherFrame();
			f.pack();
			f.setSize(1200, 800);
			for (String card : new String[] { "play", "server", "maps", "notes" }) {
				f.show(card);
				JComponent root = (JComponent) f.getContentPane();
				root.setSize(1200, 800);
				layout(root);
				BufferedImage img = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = img.createGraphics();
				root.paint(g);
				g.dispose();
				try {
					ImageIO.write(img, "png", new File(args[1], args[0] + "_" + card + ".png"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			System.exit(0);
		});
	}

	static void layout(Component c) {
		c.doLayout();
		if (c instanceof Container k) {
			for (Component x : k.getComponents()) {
				layout(x);
			}
		}
	}
}
