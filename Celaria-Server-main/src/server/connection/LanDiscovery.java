package server.connection;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import server.ServerCore;

/**
 * LAN discovery (server browser LAN of the cup mod): the mod broadcasts "CCPD" on the local networks (LAN, Radmin VPN,
 * Hamachi...) to UDP port 26510; the server answers "CSPD|<port>|<players>|<max players>|<protocol>|<password 0/1>|<name>".
 * Several servers can run on the same computer (the port is shared).
 */
public class LanDiscovery implements Runnable {

	public static final int DISCOVERY_PORT = 26510;

	private final ServerCore server;
	private volatile boolean running = true;
	private DatagramSocket socket;

	public LanDiscovery(ServerCore server) {
		this.server = server;
	}

	public void stop() {
		running = false;
		if (socket != null) {
			socket.close();
		}
	}

	@Override
	public void run() {
		try {
			socket = new DatagramSocket(null);
			socket.setReuseAddress(true);
			socket.setBroadcast(true);
			socket.bind(new InetSocketAddress(DISCOVERY_PORT));
		} catch (Exception e) {
			server.consolePrintln("LAN discovery unavailable (UDP port " + DISCOVERY_PORT + "): " + e.getMessage());
			return;
		}
		byte[] buffer = new byte[64];
		while (running) {
			try {
				DatagramPacket request = new DatagramPacket(buffer, buffer.length);
				socket.receive(request);
				boolean info = request.getLength() >= 4 && buffer[0] == 'C' && buffer[1] == 'C' && buffer[2] == 'P' && buffer[3] == 'I';
				if (info == false && (request.getLength() < 4 || buffer[0] != 'C' || buffer[1] != 'C' || buffer[2] != 'P' || buffer[3] != 'D')) {
					continue;
				}
				String name = server.getServerConfig().getServer_online_name();
				if (name == null || name.isEmpty()) {
					name = "Celaria server";
				}
				name = name.replace('|', '/');
				String answer = "CSPD|" + server.getServerConfig().getTcpPort() + "|" + server.getPlayerCount() + "|"
						+ server.getServerConfig().getMaxPlayerCount() + "|" + ServerCore.SERVER_PROTOCOL_VERSION + "|"
						+ (server.getServerConfig().getPassword() != null ? 1 : 0) + "|" + name;
				if (info) {
					// launcher (Celaria Lanppart3): "CSPI|<port>|<players>|<max>|<protocol>|<password>|<mode>|<map>|<name>"
					String map = server.getGameState().getCurrentMapName().replace('|', '/');
					answer = "CSPI|" + server.getServerConfig().getTcpPort() + "|" + server.getPlayerCount() + "|"
							+ server.getServerConfig().getMaxPlayerCount() + "|" + ServerCore.SERVER_PROTOCOL_VERSION + "|"
							+ (server.getServerConfig().getPassword() != null ? 1 : 0) + "|"
							+ (server.getGameState().isCupMode() ? "cup" : "timeattack") + "|" + map + "|" + name;
				}
				byte[] data = answer.getBytes(StandardCharsets.UTF_8);
				socket.send(new DatagramPacket(data, data.length, request.getSocketAddress()));
			} catch (Exception e) {
				if (running == false) {
					return;
				}
			}
		}
	}
}
