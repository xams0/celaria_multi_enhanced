package lp3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * The Celaria server (Java) started by the launcher, without window ("--managed"): its console is shown in the launcher,
 * commands are sent on its standard input (nextmap, restartmap, map <file>, maps, status, say <text>, ...). The server
 * stops if the launcher closes (end of its standard input).
 */
public final class ServerProcess {

	/** state polled with the "status" command */
	public record Status(String mode, String map, int players, int maxPlayers, String round, String rounds, boolean warmup) {
	}

	private Process process;
	private Writer input;
	private volatile Consumer<String> console;
	private volatile Consumer<Status> statusListener;
	private volatile Consumer<String[]> mapsListener;
	private volatile Runnable stoppedListener;

	/** the page which shows the server (the window is created again when the language changes) */
	public void setListeners(Consumer<String> console, Consumer<Status> statusListener, Consumer<String[]> mapsListener, Runnable stoppedListener) {
		this.console = console;
		this.statusListener = statusListener;
		this.mapsListener = mapsListener;
		this.stoppedListener = stoppedListener;
	}

	public synchronized boolean running() {
		return process != null && process.isAlive();
	}

	public synchronized void start() throws IOException {
		if (running()) {
			return;
		}
		ProcessBuilder pb = new ProcessBuilder(Env.javaExe().getAbsolutePath(), "-Dfile.encoding=UTF-8", "-jar",
				Env.serverJar().getAbsolutePath(), "--managed", "--config", "server.conf");
		pb.directory(Env.SERVER_DIR);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		process = p;
		input = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
		Thread reader = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					handle(line);
				}
			} catch (IOException ignored) {
			}
			console.accept(T.t("=== Serveur arrêté ==="));
			stoppedListener.run();
		}, "server console");
		reader.setDaemon(true);
		reader.start();
	}

	private void handle(String line) {
		if (line.startsWith("@@STATUS|")) {
			String[] p = line.split("\\|", -1);
			if (p.length >= 8) {
				statusListener.accept(new Status(p[1], p[2], parse(p[3]), parse(p[4]), p[5], p[6], p[7].equals("1")));
			}
			return;
		}
		if (line.startsWith("@@MAPS")) {
			String[] p = line.split("\\|");
			mapsListener.accept(java.util.Arrays.copyOfRange(p, 1, p.length));
			return;
		}
		console.accept(line);
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public synchronized void command(String command) {
		if (running() == false) {
			return;
		}
		try {
			input.write(command + "\n");
			input.flush();
		} catch (IOException ignored) {
		}
	}

	/** stops the server (command "stop", then the process is killed after 5 seconds) */
	public void stop() {
		Process p;
		synchronized (this) {
			p = process;
		}
		if (p == null || p.isAlive() == false) {
			return;
		}
		command("stop");
		try {
			if (p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) == false) {
				p.destroyForcibly();
				p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
			}
		} catch (InterruptedException ignored) {
		}
	}
}
