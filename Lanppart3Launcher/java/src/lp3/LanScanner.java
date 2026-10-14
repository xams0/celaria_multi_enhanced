package lp3;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Server browser LAN of the launcher: "CCPI" is broadcast on every local network (LAN, Radmin VPN, Hamachi...) to UDP port
 * 26510, the Celaria Lanppart3 servers answer "CSPI|port|players|max|protocol|password|mode|map|name".
 */
public final class LanScanner {
	public static final int DISCOVERY_PORT = 26510;

	public record LanServer(String address, int port, int players, int maxPlayers, boolean password, String mode, String map, String name) {
		public String endpoint() {
			return address + ":" + port;
		}
	}

	private LanScanner() {
	}

	public static List<LanServer> scan(int timeoutMS) {
		Map<String, LanServer> found = new LinkedHashMap<>();
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setBroadcast(true);
			byte[] request = "CCPI".getBytes(StandardCharsets.US_ASCII);
			Set<InetAddress> targets = new LinkedHashSet<>();
			targets.add(InetAddress.getByName("255.255.255.255"));
			targets.add(InetAddress.getLoopbackAddress());
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (ni.isUp() == false) {
					continue;
				}
				for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
					if (ia.getBroadcast() != null) {
						targets.add(ia.getBroadcast());
					}
				}
			}
			for (InetAddress t : targets) {
				try {
					socket.send(new DatagramPacket(request, request.length, t, DISCOVERY_PORT));
				} catch (Exception ignored) {
				}
			}
			long end = System.currentTimeMillis() + timeoutMS;
			byte[] buffer = new byte[1024];
			while (true) {
				long left = end - System.currentTimeMillis();
				if (left <= 0) {
					break;
				}
				socket.setSoTimeout((int) left);
				DatagramPacket answer = new DatagramPacket(buffer, buffer.length);
				try {
					socket.receive(answer);
				} catch (SocketTimeoutException e) {
					break;
				}
				String text = new String(answer.getData(), 0, answer.getLength(), StandardCharsets.UTF_8);
				String[] p = text.split("\\|", 9);
				if (p.length < 9 || p[0].equals("CSPI") == false) {
					continue;
				}
				String address = answer.getAddress().getHostAddress();
				try {
					LanServer s = new LanServer(address, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[5].equals("1"),
							p[6], p[7].replaceAll("(?i)\\.cmap$", ""), p[8]);
					String key = s.name() + "|" + s.port();
					LanServer known = found.get(key);
					// one line per server (several networks reach the same server): the local address is preferred
					if (known == null || address.startsWith("127.") || (address.startsWith("192.168.") && known.address().startsWith("127.") == false)) {
						found.put(key, s);
					}
				} catch (NumberFormatException ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		return new ArrayList<>(found.values());
	}
}
