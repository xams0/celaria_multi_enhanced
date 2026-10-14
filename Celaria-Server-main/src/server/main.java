package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.ConfFileReader;
import server.ListenerHandler.ServerListener;
import window.Window;

public class main {
	public static void main(String[] args){
		boolean headless = false;
		boolean managed = false;
		String confFile = "./server.conf";
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--headless")) {
				headless = true;
			} else if (args[i].equals("--managed")) {
				headless = true;
				managed = true;// started by the launcher: the server stops if the launcher closes (end of stdin)
			} else if (args[i].equals("--config") && i + 1 < args.length) {
				confFile = args[++i];
			}
		}

		ServerConfig conf = new ServerConfig();

		ConfFileReader reader = new ConfFileReader();
		reader.loadFile(confFile);


		conf.apply(reader);


		Server s = new Server(conf);//create server
		if (headless) {
			runHeadless(s, managed);
			return;
		}
		Window window = new Window(s);//create Window and pass server as Reference
		window.setServerHeader("Celaria Server "+s.SERVER_VERSION);//set WIndow hander


	}

	/**
	 * Without window (Celaria Lanppart3 launcher): the console is written to stdout, commands are read from stdin
	 * (stop, nextmap, say <text>, cupreset, bots <count>, players).
	 */
	private static void runHeadless(Server s, boolean managed) {
		s.addServerListener(new ServerListener() {
			public void consoleLinePrint(String message) {
				System.out.println(message);
				System.out.flush();
			}

			public void playerJoined(int id, String playerName) {
				System.out.println("[players] +" + id + " " + playerName);
				System.out.flush();
			}

			public void playerLeft(int id) {
				System.out.println("[players] -" + id);
				System.out.flush();
			}
		});
		s.start();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.equals("stop")) {
					break;
				}
				System.out.println(s.consoleCommand(line));
				System.out.flush();
			}
			if (managed == false && line == null) {
				Thread.sleep(Long.MAX_VALUE);// no console: runs until the process is stopped
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			s.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
}
