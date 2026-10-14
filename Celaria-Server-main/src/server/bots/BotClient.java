package server.bots;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Bot of the cup mode: a client connected to the server through the network like the game (TCP + UDP on the loopback
 * address). At every round it replays the best recorded run of the map (ghost), a bit slower or faster, starting after
 * the countdown of the game (3 seconds after the ROUND_START message), and sends its checkpoints and its goal.
 */
public class BotClient implements Runnable {

	public interface GhostSource {
		Ghost currentGhost();
	}

	private static final int PROTOCOL_VERSION = 4;
	private static final long COUNTDOWN_MS = 3000;
	private static final long UDP_INTERVAL_MS = 30;

	private final String name;
	private final long steamID;
	private final int port;
	private final double paceBase;// > 1: slower than the ghost
	private final GhostSource ghosts;
	private volatile boolean running = true;

	private Socket socket;
	private OutputStream out;
	private DatagramSocket udp;
	private InetAddress server;
	private int id = -1, key = -1, mapMagic;
	private boolean udpReady, mapLoadedSent;
	private int updateNumber;

	// current round
	private volatile Ghost ghost;
	private volatile long goTime;// wall time of the start, 0 = not racing
	private volatile double pace = 1;
	private int nextCheckpoint;
	private boolean goalSent;

	public BotClient(String name, long steamID, int port, double paceBase, GhostSource ghosts) {
		this.name = name;
		this.steamID = steamID;
		this.port = port;
		this.paceBase = paceBase;
		this.ghosts = ghosts;
	}

	public String getName() {
		return name;
	}

	public void stop() {
		running = false;
		try {
			sendTcp(20, new byte[0]);// disconnect
		} catch (IOException e) {
		}
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException e) {
		}
		if (udp != null) {
			udp.close();
		}
	}

	// ---------------------------------------------------------------- network

	private synchronized void sendTcp(int packetID, byte[] payload) throws IOException {
		if (out == null) {
			return;
		}
		ByteBuffer b = ByteBuffer.allocate(7 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) 'C').put((byte) 'C').put((byte) 'P').put((byte) 'T');
		b.put((byte) packetID);
		b.putShort((short) payload.length);
		b.put(payload);
		out.write(b.array());
		out.flush();
	}

	private void sendUdp(int packetID, byte[] payload) throws IOException {
		if (id < 0) {
			return;
		}
		ByteBuffer b = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) 'C').put((byte) 'C').put((byte) 'P').put((byte) 'U');
		b.put((byte) id).put((byte) packetID).putShort((short) key);
		b.put(payload);
		udp.send(new DatagramPacket(b.array(), b.position(), server, port));
	}

	private static byte[] u16(int v) {
		return new byte[] { (byte) v, (byte) (v >> 8) };
	}

	private static byte[] u32(long v) {
		return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) };
	}

	private void sendPlayerInfo() throws IOException {
		ByteBuffer b = ByteBuffer.allocate(1 + name.length() * 2 + 8 + 10).order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) name.length());
		for (int i = 0; i < name.length(); i++) {
			b.putShort((short) name.charAt(i));
		}
		b.putLong(steamID);
		b.put(new byte[] { 0, (byte) 200, (byte) 150, (byte) 120, (byte) 255, (byte) 255, (byte) 255, 0, (byte) 200, (byte) 255 });// skin
		sendTcp(3, b.array());
	}

	private void handle(int packetID, byte[] p) throws IOException {
		switch (packetID) {
		case 1:// server information
			sendPlayerInfo();
			break;
		case 8:// id and UDP key
			id = p[0] & 0xFF;
			key = (p[1] & 0xFF) | ((p[2] & 0xFF) << 8);
			break;
		case 180:// new map
			goTime = 0;
			break;
		case 182:// map id: map transfer
			mapMagic = (p[0] & 0xFF) | ((p[1] & 0xFF) << 8);
			sendTcp(199, u16(mapMagic));
			break;
		case 4:// map part
			sendTcp(199, u16(mapMagic));
			break;
		case 5:// map transferred: the game confirms the loading only for the first map of the session
			if (mapLoadedSent == false) {
				mapLoadedSent = true;
				sendTcp(6, new byte[0]);
			}
			break;
		case 183:// ROUND_START: the countdown of the game starts
			ghost = ghosts.currentGhost();
			pace = paceBase + Math.random() * 0.02;
			nextCheckpoint = 0;
			goalSent = false;
			goTime = System.currentTimeMillis() + COUNTDOWN_MS;
			break;
		case 184:// ROUND_END
			goTime = 0;
			break;
		default:
			break;
		}
	}

	private void readTcp() {
		try {
			InputStream in = socket.getInputStream();
			byte[] header = new byte[7];
			while (running) {
				readFully(in, header);
				int packetID = header[4] & 0xFF;
				int length = (header[5] & 0xFF) | ((header[6] & 0xFF) << 8);
				byte[] payload = new byte[length];
				readFully(in, payload);
				handle(packetID, payload);
			}
		} catch (IOException e) {
			running = false;
		}
	}

	private static void readFully(InputStream in, byte[] b) throws IOException {
		int o = 0;
		while (o < b.length) {
			int n = in.read(b, o, b.length - o);
			if (n < 0) {
				throw new IOException("closed");
			}
			o += n;
		}
	}

	private void readUdp() {
		byte[] buf = new byte[2048];
		while (running) {
			try {
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				udp.receive(packet);
				if (packet.getLength() >= 5 && buf[0] == 'C' && buf[1] == 'S' && buf[2] == 'P' && buf[3] == 'U' && buf[4] == 10 && udpReady == false) {
					udpReady = true;
					sendTcp(240, new byte[0]);// UDP connection confirmed
				}
			} catch (IOException e) {
				if (running == false) {
					return;
				}
			}
		}
	}

	// ---------------------------------------------------------------- replay

	private void sendPosition() throws IOException {
		Ghost g = ghost;
		if (g == null) {
			g = ghosts.currentGhost();
		}
		Ghost.Sample s;
		long now = System.currentTimeMillis();
		if (g == null) {
			s = new Ghost.Sample();
		} else if (goTime == 0 || now < goTime) {
			s = g.sample(0);// on the start line
		} else {
			long t = (long) ((now - goTime) / pace);
			s = g.sample(t);
			while (nextCheckpoint < g.checkpoints.size() && g.checkpoints.get(nextCheckpoint) <= t) {
				long ms = (long) (g.checkpoints.get(nextCheckpoint) * pace);
				sendTcp(202, u32((ms + 9) / 10));
				nextCheckpoint++;
			}
			if (goalSent == false && t >= g.timeMS) {
				goalSent = true;
				long ms = (long) (g.timeMS * pace);
				sendTcp(203, u32((ms + 9) / 10));
			}
		}
		updateNumber = (updateNumber + 1) % 256;
		ByteBuffer b = ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) updateNumber).put((byte) s.respawn);
		b.putFloat(s.x).putFloat(s.y).putFloat(s.z);
		float len = (float) Math.sqrt(s.mx * s.mx + s.my * s.my + s.mz * s.mz);
		float nx = len > 0.001f ? s.mx / len : 0, ny = len > 0.001f ? s.my / len : 0, nz = len > 0.001f ? s.mz / len : 0;
		b.put((byte) Math.round((nx + 1) / 2 * 255)).put((byte) Math.round((ny + 1) / 2 * 255)).put((byte) Math.round((nz + 1) / 2 * 255));
		b.put((byte) Math.round(Math.min(1f, len / 3f) * 255));
		b.put((byte) Math.round(s.rot / 360f * 255));
		b.put((byte) s.anim);
		b.put((byte) Math.round(s.animStep * 255));
		sendUdp(1, b.array());
	}

	@Override
	public void run() {
		try {
			server = InetAddress.getLoopbackAddress();
			for (int attempt = 0;; attempt++) {// the server may still be starting
				try {
					socket = new Socket();
					socket.connect(new InetSocketAddress(server, port), 2000);
					break;
				} catch (IOException e) {
					if (attempt >= 10 || running == false) {
						throw e;
					}
					Thread.sleep(1000);
				}
			}
			socket.setTcpNoDelay(true);
			out = socket.getOutputStream();
			udp = new DatagramSocket();
			Thread tcp = new Thread(this::readTcp, name + " TCP");
			tcp.setDaemon(true);
			tcp.start();
			Thread udpThread = new Thread(this::readUdp, name + " UDP");
			udpThread.setDaemon(true);
			udpThread.start();
			ByteBuffer hello = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
			hello.putShort((short) PROTOCOL_VERSION).put((byte) 1);
			sendTcp(1, hello.array());
			long lastAlive = 0;
			while (running) {
				long now = System.currentTimeMillis();
				if (id >= 0) {
					if (udpReady == false) {
						sendUdp(10, new byte[0]);// UDP test packet
					}
					sendPosition();
				}
				if (now - lastAlive >= 3000) {
					lastAlive = now;
					sendTcp(210, new byte[0]);// still alive
				}
				Thread.sleep(UDP_INTERVAL_MS);
			}
		} catch (IOException | InterruptedException e) {
			// disconnected
		} finally {
			running = false;
			if (udp != null) {
				udp.close();
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

}
