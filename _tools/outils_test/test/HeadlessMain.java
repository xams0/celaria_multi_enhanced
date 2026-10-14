import io.ConfFileReader;
import server.Server;
import server.ServerConfig;
import server.ListenerHandler.ServerListener;

// Test launcher: starts the server without the Swing window and prints the console to stdout.
public class HeadlessMain {
	public static void main(String[] args) throws Exception {
		ConfFileReader reader = new ConfFileReader();
		reader.loadFile(args[0]);
		ServerConfig conf = new ServerConfig();
		conf.apply(reader);
		Server s = new Server(conf);
		s.addServerListener(new ServerListener() {
			public void consoleLinePrint(String message) { System.out.println("[server] " + message); System.out.flush(); }
			public void playerJoined(int id, String playerName) { System.out.println("[server] joined " + id + " " + playerName); }
			public void playerLeft(int id) { System.out.println("[server] left " + id); }
		});
		s.start();
		Thread.sleep(Long.MAX_VALUE);
	}
}
