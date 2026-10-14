package lp3;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Celaria Lanppart3 launcher. "--icon <file.ico>" writes the icon of the launcher (build). */
public final class Main {
	public static void main(String[] args) throws Exception {
		if (args.length == 2 && args[0].equals("--icon")) {
			writeIco(new File(args[1]), 16, 24, 32, 48, 64, 128, 256);
			return;
		}
		System.setProperty("awt.useSystemAAFontSettings", "on");
		// one launcher at a time
		File lock = new File(Env.DATA, "launcher.lock");
		Env.DATA.mkdirs();
		@SuppressWarnings("resource")
		java.nio.channels.FileChannel channel = new RandomAccessFile(lock, "rw").getChannel();
		if (channel.tryLock() == null) {
			JOptionPane.showMessageDialog(null, T.t("Celaria Lanppart3 est déjà ouvert."));
			return;
		}
		Env.init();
		T.init();
		Updater.cleanup();
		SwingUtilities.invokeLater(() -> {
			Theme.install();
			LauncherFrame f = new LauncherFrame();
			f.setVisible(true);
			f.checkUpdate(false);
			// started by Celaria Lanppart3.exe (which exits): Windows may leave the new window behind, it is brought to the front
			f.setAlwaysOnTop(true);
			f.toFront();
			f.requestFocus();
			new javax.swing.Timer(400, e -> {
				f.setAlwaysOnTop(false);
				f.toFront();
				((javax.swing.Timer) e.getSource()).stop();
			}).start();
		});
	}

	/** .ico with PNG images (Windows Vista and later) */
	static void writeIco(File out, int... sizes) throws IOException {
		byte[][] images = new byte[sizes.length][];
		for (int i = 0; i < sizes.length; i++) {
			BufferedImage img = new BufferedImage(sizes[i], sizes[i], BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.drawImage(Theme.icon(sizes[i]), 0, 0, null);
			g.dispose();
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			ImageIO.write(img, "png", b);
			images[i] = b.toByteArray();
		}
		try (DataOutputStream d = new DataOutputStream(new FileOutputStream(out))) {
			d.write(new byte[] { 0, 0, 1, 0 });
			d.write(le16(sizes.length));
			int offset = 6 + 16 * sizes.length;
			for (int i = 0; i < sizes.length; i++) {
				d.write(sizes[i] >= 256 ? 0 : sizes[i]);
				d.write(sizes[i] >= 256 ? 0 : sizes[i]);
				d.write(0);
				d.write(0);
				d.write(le16(1));
				d.write(le16(32));
				d.write(le32(images[i].length));
				d.write(le32(offset));
				offset += images[i].length;
			}
			for (byte[] img : images) {
				d.write(img);
			}
		}
	}

	private static byte[] le16(int v) {
		return new byte[] { (byte) v, (byte) (v >> 8) };
	}

	private static byte[] le32(int v) {
		return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) };
	}
}
