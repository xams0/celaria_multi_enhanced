package server;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import DataStructures.TcpMessage;
import debug.DebugIO;
import server.ListenerHandler.ListenerHandler;
import server.Server.TCPDISCONNECTTYPE;
import server.ServerProcessor.LeaderboardAdd;
import server.connection.BroadcasterController;
import server.connection.RecieverController;
import server.connection.TcpNetworkConnector;
import server.connection.TcpPlayerWriter;
import server.game.GameState;
import server.leaderboard.LeaderboardEntry;
import server.leaderboard.LeaderboardState;
import server.player.Player;
import server.player.PlayerProcessor;
import server.player.data.PlayerRuntimeUdpData;
import server.util.BufferConverter;
import server.util.StreamConverter;
import server.util.ServerClock;
import server.util.Util;
import masterserver.HttpConnection;
import masterserver.HttpConnection.Response;
import masterserver.ServerConnectionTest;

/**
 * Class which implements all the server logic. Not accessible from outside.
 * (everything has to go through the "Server" class.)
 * 
 * 
 *
 */

public class ServerCore {

	// Has to match the same value in the client (for checking if communication protocol is the same)
	public static final int SERVER_PROTOCOL_VERSION = 4;

	public static final boolean MODIFIED = true;// modified server (cup mode)

	public static final int TICKS_PER_SECOND = 10;

	// ---------------------------------------------------------

	ServerSocketChannel serverChannel;

	private DatagramSocket socket_udp;// udp socket
	private boolean run = false;// Define the boolean run to keep the server running
	private ConcurrentHashMap<Integer, Player> players;// Create an array list to store all the players

	private ConcurrentHashMap<SocketChannel, Player> socketChannelToPlayerMap;// Create an array list to store all the
																				// players

	private ExecutorService tcpServerFullPool = null;// Executorservice with short lived threads which simply respond to
														// clients with a "server is full" message after connecting with
														// the server.
	// (these threads are writing to the sockets)

	// handles the direct TCP communication with the clients (read and write)
	TcpNetworkConnector tcpConnector;

	private RessourceHandler ressources;

	private BroadcasterController broadCaster;// TCP and UDP broadcaster
	private RecieverController reciever;// TCP and UDP reciever

	public boolean stopped;// if the server is running

	private ListenerHandler listeners;

	// Locks
	// Playermapblock
	/*
	 * Even though the map is already a concurrenthashmap, to avoid any issues in
	 * operations like reading and writing simultaneously, this lock comes into
	 * play. (it secures such operations with one global lock)
	 */
	private ReentrantReadWriteLock playerListLock;
	private Lock readPlayerListLock;
	private Lock writePlayerListLock;

	private ServerConfig serverConfig;

	ServerProcessor serverRealtime;
	Thread serverRealtimeThread;

	PlayerProcessor playerProcessor;
	Thread playerProcessorThread;

	GameState state;

	public ServerCore(ServerConfig conf) {

		serverConfig = conf;

		state = new GameState(conf);

		players = new ConcurrentHashMap<Integer, Player>();

		socketChannelToPlayerMap = new ConcurrentHashMap<SocketChannel, Player>();

		playerListLock = new ReentrantReadWriteLock();
		readPlayerListLock = playerListLock.readLock();
		writePlayerListLock = playerListLock.writeLock();

		listeners = new ListenerHandler();

		ressources = new RessourceHandler(this); // handles map files

		serverRealtime = new ServerProcessor(this);

		playerProcessor = new PlayerProcessor(this);

	}

	public ListenerHandler getListenerHandler() {
		return listeners;
	}

	public GameState getGameState() {
		return state;
	}

	private boolean testOnline() {
		// =============================
		// =========== Handle Online Connectivity ============
		if (serverConfig.onlineMode()) {
			this.consolePrintln("Onlinemode enabled");

			this.consolePrintln("Testing internet connection");
			Response ipResponse = HttpConnection.getIP();
			if (ipResponse.success == false) {
				this.consolePrintln("Error: Unable to obtain IP-address");
				return false;
			} else {

				ServerConnectionTest connectionTest = new ServerConnectionTest(ipResponse.content,
						serverConfig.getTcpPort());

				boolean checkPassed = connectionTest.checkBlocked();
				if (checkPassed == false) {
					this.consolePrintln(
							"Internet connection verification failed. Please check your firewall and/or port-settings.");
					return false;

				} else {

					this.consolePrintln("Connecting to masterserver...");
					this.consolePrintln("Registering server...");
					HttpConnection.ResponseMap response = serverRealtime.sendHTTPregisterTimed();

					if (response.getSuccess() == false) {
						// error
						this.consolePrintln("ERROR: unable to connect to the masterserver.");
						return false;
					} else {
						boolean registerError = serverRealtime.parseRegisterResponse(response);

						if (registerError == false) {
							this.consolePrintln("Registering successful.");
						} else {
							this.consolePrintln("Registration error.");

							return false;
						}
					}
				}

			}

		}

		return true;
	}

	public void startServer() {

		this.consolePrintln("=== Starting Server ===");// Display the message that the server is starting

		boolean mapListOK = false;
		if (serverConfig.getMapList() != null) {
			if (serverConfig.getMapList().length > 0) {
				mapListOK = true;
			}
		}
		if (mapListOK == false) {

			this.consolePrintln("Error: no mapfiles defined!");
			this.consolePrintln("");
			return;
		}

		state.reset();

		if (serverConfig.onlineMode()) {
			boolean success = testOnline();
			if (success == false) {
				return;
			}
		}

		serverRealtime.setRunning(true);
		serverRealtimeThread = new Thread(serverRealtime, "Server Realtime");
		serverRealtimeThread.start();

		playerProcessor.setRunning(true);
		playerProcessorThread = new Thread(playerProcessor, "Player Processor");
		playerProcessorThread.start();

		try {
			socket_udp = new DatagramSocket(serverConfig.getUdpPort());

		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			this.consolePrintln("Unable to create UDP socket on port: " + serverConfig.getUdpPort()
					+ ". Please check if this port is not already used.");
			closeServer();
			return;
		} // udp

		run = true;
		stopped = false;

		players.clear();

		socketChannelToPlayerMap.clear();

		tcpServerFullPool = Executors.newFixedThreadPool(4);

		// create a new serversocketchannel. The channel is unbound.
		try {
			serverChannel = ServerSocketChannel.open();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.consolePrintln("Unable to open serverChannel.");
			closeServer();
			return;
		}

		InetSocketAddress socketAddress = null;
		int socketPort = serverConfig.getTcpPort();
		if (this.serverConfig.getHostAddress() != null
				&& this.serverConfig.getHostAddress().equals("default") == false) {
			socketAddress = new InetSocketAddress(this.serverConfig.getHostAddress(), socketPort);
		} else {
			socketAddress = new InetSocketAddress(socketPort);// uses wildcard address (binds to all ports)
		}

		try {
			serverChannel.bind(socketAddress);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.consolePrintln(
					"Unable to bind TCP port: " + socketPort + ". Please check if this port is not already used.");
			closeServer();
			return;
		}

		// mark the serversocketchannel as non blocking
		try {
			serverChannel.configureBlocking(false);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.consolePrintln("Error: cannot configure serversocket to non-blocking I/O");
			closeServer();
			return;
		}

		broadCaster = new BroadcasterController(this, socket_udp);

		reciever = new RecieverController(this);

		tcpConnector = new TcpNetworkConnector(this);

		this.consolePrintln("=== Server Started ===");// Display the message that the server started
		lanDiscovery = new server.connection.LanDiscovery(this);// server browser LAN of the cup mod
		Thread discovery = new Thread(lanDiscovery, "LAN discovery");
		discovery.setDaemon(true);
		discovery.start();
		if (getGameState().isCupMode() && getServerConfig().getCupBots() > 0) {
			getBots().setCount(getServerConfig().getCupBots());
			this.consolePrintln("Bots: " + getServerConfig().getCupBots());
		}

	}

	public void addLeaderboardEntry(LeaderboardEntry e) {
		// ------ Used for calculating if a player set a new record in the given round
		serverRealtime.getNewLeaderboardEntryList()
				.offerConcurrent(new LeaderboardAdd(LeaderboardState.LEADERBOARDADDTYPE.REPLACE, e));
		// ------
	}

	public void addLeaderboardEntry(LeaderboardState.LEADERBOARDADDTYPE type, LeaderboardEntry e) {
		// ------ Used for calculating if a player set a new record in the given round
		serverRealtime.getNewLeaderboardEntryList().offerConcurrent(new LeaderboardAdd(type, e));
		// ------

	}

	public ConcurrentHashMap<SocketChannel, Player> getSocketChannelToPlayerMap() {
		return socketChannelToPlayerMap;
	}

	public boolean getRunning() {
		return run;
	}

	public void closeServer() {
		run = false;

		consolePrintln("=== Closing server ===");
		if (lanDiscovery != null) {
			lanDiscovery.stop();
		}

		stopReciever();// stops UDP reciever

		stopBroadcaster();

		stopAllPlayerThreads();// also clears the hashmap

		try {
			stopSockets();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			this.consolePrintln("Error encountered while closing sockets.");
		}

		if (tcpConnector != null) {
			tcpConnector.close();
			tcpConnector = null;
		}

		tcpServerFullPool = null;

		if (serverRealtime != null) {
			serverRealtime.setRunning(false);
			serverRealtime.reset();
		}

		if (serverRealtimeThread != null) {
			try {
				serverRealtimeThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			serverRealtimeThread = null;
		}

		ressources.reset();// resets loaded maps and any other types of ressoruces

		// remove server from online database
		if (serverConfig.onlineMode()) {
			HttpConnection.removeFromOnline(serverConfig.getTcpPort());
		}

		consolePrintln("=== Server closed ===");
		consolePrintln("");

	}

	public void stopReciever() {
		if (reciever != null) {
			reciever.stop();
			reciever = null;
		}
	}

	public void stopBroadcaster() {
		if (broadCaster != null) {
			broadCaster.stop();
			broadCaster = null;
		}
	}

	public void stopAllPlayerThreads() {

		writePlayerListLock.lock();

		ConcurrentHashMap<Integer, Player> pl = getPlayers();
		for (int i = 0; i < getMaxPlayerCount(); i++) {

			int id = i;
			if (pl.containsKey(id) == true) {
				Player p = pl.get(id);

				p.closePlayer(TCPDISCONNECTTYPE.SERVERCLOSED);

			}
		}
		writePlayerListLock.unlock();

		if (tcpConnector != null) {
			tcpConnector.stop();
		}

		playerProcessor.setRunning(false);
		try {
			playerProcessorThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		players.clear();

	}

	public ServerConfig getServerConfig() {
		return serverConfig;
	}

	public void consolePrintln(String string) {
		listeners.getEventCaller().consolePrintln(string);
	}

	public void stopSockets() throws IOException {

		if (serverChannel != null) {
			serverChannel.socket().close();
			serverChannel.close();
			serverChannel = null;
		}

		// socket_udp.disconnect();
		if (socket_udp != null) {
			socket_udp.close();
			socket_udp = null;
		}

		DebugIO.println("SocketsStopped");

	}

	// returns a available playerID
	public int getNewPlayerID() {
		int newKey = 0;
		readPlayerListLock.lock();

		int i = 0;

		while (players.containsKey(i) == true) {
			i++;
		}
		newKey = i;
		readPlayerListLock.unlock();

		return newKey;
	}

	public void removePlayer(int id) {

		// removes player from highscore
		getGameState().getHighscoreState().remove(id);
		getGameState().getTimeAttackMod().playerLeft(id);
		lastRespawnAtStart.remove(id);
		pendingCompleteResets.remove(id);
		if (getGameState().isCupMode()) {
			getGameState().getCup().playerLeft(id);
		}

		writePlayerListLock.lock();// Using a lock in order to make sure that nothing accesses the playerHashmap
									// between containskey() and remove().

		if (players.containsKey(id)) {
			players.remove(id);

		} else {
			DebugIO.println("player with ID " + id + " not removed as it doesnt exist?!");
		}
		writePlayerListLock.unlock();
	}

	public ServerSocketChannel getServerSocketChannel() {
		return serverChannel;

	}

	public DatagramSocket getServerSocketUDP() {
		return socket_udp;
	}

	public TcpNetworkConnector getTcpConnector() {
		return tcpConnector;
	}

	public ExecutorService getTcpServerFullPool() {
		return tcpServerFullPool;

	}

	public ConcurrentHashMap<Integer, Player> getPlayers() {
		return players;

	}

	public void sendTcpBroadcast(TcpMessage message) {
		if (broadCaster != null) {
			broadCaster.getTcpBroadcaster().addTcpBroadcastMessage(message);
		}
	}

	/**
	 * Cup mode: the player reset his run completely. He becomes spectator until the next round:
	 * the map is reloaded on his client and he doesn't get a ROUND_START message (he can't move but sees the other players).
	 */
	public void cupEliminate(Player player, boolean restartKey) {
		if (getGameState().isCupMode() == false || getGameState().getCup().eliminate(player.getID()) == false) {
			return;
		}
		String name = player.getPlayerProfileData().getPlayerName();
		sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Player [" + name + "] " + (restartKey ? "restarted the run (DNF)" : "respawned at the start (DNF)") + ": spectator until the next round.");
		sendTcpChatMessage(player, ChatMessageColors.errorColor, restartKey ? "Restart = DNF in cup mode (respawn key: back to your last checkpoint, the timer continues)."
				: "Respawn at the start = DNF on this server.");
		player.initNewMapLoading();
		if (getGameState().getCup().allParticipantsFinished()) {
			getGameState().shortenRoundAllFinished();
		}
		cupSendState();
		sendTcpBroadcast(new TcpMessage(PlayerProcessor.TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
	}

	// complete resets of the run (cup mode, settings cup_respawnAtStartDNF / cup_forceRestartDNF)
	private final ConcurrentHashMap<Integer, Long> lastRespawnAtStart = new ConcurrentHashMap<Integer, Long>();
	private final ConcurrentHashMap<Integer, Long> pendingCompleteResets = new ConcurrentHashMap<Integer, Long>();

	/**
	 * Packet 201: the respawn key without checkpoint (back to the start). With the mod the timer continues; without the mod
	 * the run of the client restarts and the server adds the lost time to his finish time.
	 */
	public void cupRespawnAtStart(Player player) {
		if (getGameState().isCupMode() == false) {
			return;
		}
		lastRespawnAtStart.put(player.getID(), System.currentTimeMillis());
		if (getGameState().getCup().getSettings().respawnAtStartDNF) {
			cupEliminate(player, false);
		}
	}

	/** mod packet 211: the restart key (complete reset of the run) */
	public void cupForceRestart(Player player) {
		if (getGameState().isCupMode() && getGameState().getCup().getSettings().forceRestartDNF) {
			cupEliminate(player, true);
		}
	}

	/**
	 * Client without the mod back on its start position (UDP): respawn key (it sends the packet 201 too, maybe processed
	 * later) or restart key. Decided by cupProcessPendingResets().
	 */
	public void cupCompleteResetDetected(Player player) {
		pendingCompleteResets.put(player.getID(), System.currentTimeMillis());
	}

	/** called by the server processor (10 times per second) */
	public void cupProcessPendingResets() {
		long now = System.currentTimeMillis();
		for (java.util.Map.Entry<Integer, Long> e : pendingCompleteResets.entrySet()) {
			if (now - e.getValue() < 1500) {
				continue;
			}
			pendingCompleteResets.remove(e.getKey());
			Long respawn = lastRespawnAtStart.get(e.getKey());
			Player p = players.get(e.getKey());
			if (p != null && getGameState().isCupMode() && (respawn == null || Math.abs(respawn - e.getValue()) > 3000)
					&& getGameState().getCup().getSettings().forceRestartDNF) {
				cupEliminate(p, true);
			}
		}
	}

	private static final long MAX_START_LATENCY_COMPENSATION_MS = 400;

	/**
	 * Cup mode: sends the ROUND_START message to all players who loaded the map.
	 * Latency compensation: the message is sent first to the players with the highest ping, so that every client
	 * receives it (and starts moving) at the same real time.
	 * Players who didn't load the map yet don't take part in the round (spectators).
	 */
	/**
	 * Cup mode: the start of the round is scheduled (ServerClock). Clients with the mod synchronise their clock with the server
	 * and start by themselves at this time (hidden message), so that every player starts at the same real time.
	 */
	public void cupAnnounceScheduledStart(double startClock) {
		server.game.CupState.Settings settings = getGameState().getCup().getSettings();
		long roundTicks = (getGameState().getCup().isWarmup() && settings.warmupTimeMode ? settings.warmupTimeSec : settings.roundTimeLimitSec) * 100L;
		long now = System.currentTimeMillis();
		readPlayerListLock.lock();
		try {
			for (Player p : players.values()) {
								if (p.getModVersion() > 0 && p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now)
						&& getGameState().getCup().canRace(p.getID())) {
					sendTcpChatMessage(p, ChatMessageColors.defaultColor, "\u0001CUPSTART " + ServerClock.format(startClock) + " " + roundTicks);
				}
			}
		} finally {
			readPlayerListLock.unlock();
		}
	}

	public void cupSynchronizedStart(double startClock) {
		long now = System.currentTimeMillis();
		java.util.List<Player> ready = new java.util.ArrayList<Player>();
		readPlayerListLock.lock();
		try {
			for (Player p : players.values()) {
								if (getGameState().getCup().canRace(p.getID()) == false) {
					continue;// winners and observers are spectators
				}
				if (p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now)) {
					ready.add(p);
				} else if (p.getPlayerServerData().isSigned()) {
					sendTcpChatMessage(p, ChatMessageColors.serverNotificationColor, "You will join the race at the next round.");
				}
			}
		} finally {
			readPlayerListLock.unlock();
		}

		// one way delay = half of the round trip time
		long maxDelay = 0;
		for (Player p : ready) {
			maxDelay = Math.max(maxDelay, Math.min(MAX_START_LATENCY_COMPENSATION_MS, p.getTimings().getLastPingTime() / 2));
		}
		final long startTime = ServerClock.toWallMS(startClock);// race start (end of the countdown of the game)
		final double countdownClock = startClock - ServerProcessor.CUP_GAME_COUNTDOWN_MS;// every client gets ROUND_START at this time
		java.util.Collections.sort(ready, new java.util.Comparator<Player>() {
			@Override
			public int compare(Player a, Player b) {
				return Integer.compare(b.getTimings().getLastPingTime(), a.getTimings().getLastPingTime());
			}
		});
		getGameState().getCup().setRoundStartTime(startTime);
		getGameState().getGhosts().startRound();

		ByteBuffer message = ByteBuffer.allocate(4);
		BufferConverter.buffer_write_u32(message, getGameState().getRemainingRoundTimeTicks());
		for (Player p : ready) {
			long delay = Math.min(MAX_START_LATENCY_COMPENSATION_MS, p.getTimings().getLastPingTime() / 2);
			double sendClock = countdownClock - delay;
			long wait = Math.round(sendClock - ServerClock.nowMS());
			if (wait > 0) {
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			p.getPlayerServerData().setSendStartRoundMessage(true);
			sendTcpMessageLocked(p, PlayerProcessor.TcpServerMessage.ROUND_START, message.duplicate());
			PlayerRuntimeUdpData d = p.getPlayerDataCopy();
			getGameState().getCup().addParticipant(p.getID(), d.getX(), d.getY(), d.getZ(), d.getRespawnNumber());
		}
		DebugIO.println("CUP ROUND STARTED WITH " + ready.size() + " PLAYERS (latency compensation " + maxDelay + " ms)");
		cupSendState();
	}

	/**
	 * Cup mode, warmup with a duration (free practice): a player who joins or loads the map during the warmup starts at
	 * once (start signal + participant), he doesn't wait for the next round. Called by the server processor.
	 */
	public void cupWarmupLateStart() {
		server.game.CupState cup = getGameState().getCup();
		if (getGameState().isCupMode() == false || cup.isTimedWarmupRound() == false) {
			return;
		}
		long now = System.currentTimeMillis();
		java.util.List<Player> late = new java.util.ArrayList<Player>();
		readPlayerListLock.lock();
		try {
			for (Player p : players.values()) {
				if (p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now) && cup.canRace(p.getID())
						&& cup.isParticipant(p.getID()) == false && p.getPlayerServerData().getSendStartRoundMessage() == false) {
					late.add(p);
				}
			}
		} finally {
			readPlayerListLock.unlock();
		}
		for (Player p : late) {
			ByteBuffer message = ByteBuffer.allocate(4);
			BufferConverter.buffer_write_u32(message, getGameState().getRemainingRoundTimeTicks());
			p.getPlayerServerData().setSendStartRoundMessage(true);
			sendTcpMessageLocked(p, PlayerProcessor.TcpServerMessage.ROUND_START, message);
			PlayerRuntimeUdpData d = p.getPlayerDataCopy();
			cup.addParticipant(p.getID(), d.getX(), d.getY(), d.getZ(), d.getRespawnNumber());
			sendTcpChatMessage(p, ChatMessageColors.serverNotificationColor, "Warmup running: you can drive at once.");
		}
		if (late.isEmpty() == false) {
			cupSendState();
		}
	}

	private java.util.List<Player> modPlayers() {
		java.util.List<Player> list = new java.util.ArrayList<Player>();
		readPlayerListLock.lock();
		try {
			for (Player p : players.values()) {
				if (p.getModVersion() > 0 && p.getPlayerServerData().isSigned()) {
					list.add(p);
				}
			}
		} finally {
			readPlayerListLock.unlock();
		}
		return list;
	}

	/**
	 * Cup mod: round on the current map and state of every player (racing, finished, out, winner, observer), used by the mod
	 * for the round display and the automatic spectator mode.
	 */
	private server.bots.BotManager bots;
	private server.connection.LanDiscovery lanDiscovery;

	/** bots of the cup mode (they replay the best run of the map) */
	public synchronized server.bots.BotManager getBots() {
		if (bots == null) {
			bots = new server.bots.BotManager(getServerConfig().getTcpPort(),
					() -> getGameState().getGhosts().bestGhost(getGameState().getCurrentMapName()));
		}
		return bots;
	}

	public static boolean isBot(Player p) {
		return server.bots.BotManager.isBot(p.getPlayerProfileData().getSteamID());
	}

	public void cupSendState() {
		java.util.List<String> messages;
		if (getGameState().isCupMode()) {
			server.game.CupState cupState = getGameState().getCup();
			messages = cupState.buildStateMessages(getGameState().getCupStartClockMS(),
					cupState.isTimedWarmupRound() ? getGameState().getRemainingRoundMS() : -1);
		} else {
			java.util.List<Integer> ids = new java.util.ArrayList<Integer>(players.keySet());
			messages = getGameState().getTimeAttackMod().stateMessages(ids);
			boolean warmup = getGameState().isTimeAttackWarmup();
			boolean pending = getGameState().isTimeAttackWarmupPending();
			messages.add("CUPWU " + (warmup || pending ? 1 : 0) + " 1 1 " + (warmup ? getGameState().getRemainingRoundMS() : -1));
		}
		for (Player p : modPlayers()) {
			for (String m : messages) {
				sendTcpChatMessage(p, ChatMessageColors.defaultColor, m);
			}
		}
	}

	/**
	 * Cup mod: a player passed a checkpoint (index starting at 1, time in ms), for the checkpoint ranking of the mod.
	 */
	public void cupCheckpoint(Player player, int index, long timeMS) {
		if (getGameState().isCupMode() == false) {
			// time attack: checkpoint of the current run of the player
			int cp = getGameState().getTimeAttackMod().checkpoint(player.getID(), timeMS);
			String m = "CUPCP " + player.getID() + " " + cp + " " + timeMS;
			for (Player p : modPlayers()) {
				sendTcpChatMessage(p, ChatMessageColors.defaultColor, m);
			}
			return;
		}
		if (getGameState().getRoundRunning() == false
				|| getGameState().getCup().isParticipant(player.getID()) == false) {
			return;
		}
		if (isBot(player) == false) {
			getGameState().getGhosts().addCheckpoint(player.getID(), timeMS);
		}
		String m = "CUPCP " + player.getID() + " " + index + " " + timeMS;
		for (Player p : modPlayers()) {
			sendTcpChatMessage(p, ChatMessageColors.defaultColor, m);
		}
	}

	/**
	 * Cup mode: observer (caster) mode. The player doesn't race and isn't ranked; the mod shows the spectator view.
	 */
	public void cupSetObserver(Player player, boolean observer) {
		if (getGameState().isCupMode() == false) {
			taSetObserver(player, observer);
			return;
		}
		if (getGameState().getCup().setObserver(player.getID(), observer) == false) {
			return;
		}
		String name = player.getPlayerProfileData().getPlayerName();
		sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, observer ? "[" + name + "] is now observer." : "[" + name + "] will race from the next round.");
		if (getGameState().getCup().allParticipantsFinished()) {
			getGameState().shortenRoundAllFinished();
		}
		cupSendState();
		sendTcpBroadcast(new TcpMessage(PlayerProcessor.TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
	}

	/**
	 * Time attack: observer mode. The map of the player is reloaded: an observer gets no start signal (he can't move), a
	 * player gets it after the loading.
	 */
	private void taSetObserver(Player player, boolean observer) {
		if (getGameState().getTimeAttackMod().setObserver(player.getID(), observer) == false) {
			return;
		}
		String name = player.getPlayerProfileData().getPlayerName();
		sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, observer ? "[" + name + "] is now observer." : "[" + name + "] races again.");
		player.getCheckpointQueue().clearConcurrent();
		getGameState().getTimeAttackMod().runRestarted(player.getID());
		player.initNewMapLoading();
		player.getPlayerServerData().setSendStartRoundMessage(false);
		cupSendState();
		taSendRunReset(player);
		sendTcpBroadcast(new TcpMessage(PlayerProcessor.TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
	}

	public boolean isObserver(int playerID) {
		return getGameState().isCupMode() ? getGameState().getCup().isObserver(playerID) : getGameState().getTimeAttackMod().isObserver(playerID);
	}

	/** time attack: the run of the player restarted (mod packet 210, packet 201): his checkpoint line is removed */
	public void taRunRestarted(Player player) {
		if (getGameState().isCupMode()) {
			return;
		}
		player.getCheckpointQueue().clearConcurrent();
		getGameState().getTimeAttackMod().runRestarted(player.getID());
		taSendRunReset(player);
	}

	private void taSendRunReset(Player player) {
		String m = "CUPCP " + player.getID() + " 0 0";
		for (Player p : modPlayers()) {
			sendTcpChatMessage(p, ChatMessageColors.defaultColor, m);
		}
	}

	/** time attack: a player finished (precise time in ms) */
	public void taFinished(Player player, long timeMS) {
		String best = getGameState().getTimeAttackMod().finished(player.getID(), timeMS);
		taSendRunReset(player);
		if (best != null) {
			for (Player p : modPlayers()) {
				sendTcpChatMessage(p, ChatMessageColors.defaultColor, best);
			}
		}
	}

	/** a new map is loaded */
	public void modMapChanged() {
		getGameState().getTimeAttackMod().mapChanged();
		for (Player p : modPlayers()) {
			sendTcpChatMessage(p, ChatMessageColors.defaultColor, "CUPPB CLEAR");
		}
	}

	/** a client with the mod joined: state of the server */
	public void modPlayerAnnounced(Player player) {
		if (getGameState().isCupMode() == false) {
			for (String m : getGameState().getTimeAttackMod().bestMessages()) {
				sendTcpChatMessage(player, ChatMessageColors.defaultColor, m);
			}
		}
		cupSendState();
	}

	// average waiting times of a position update (cup mode): client sending interval (3 steps of 10 ms) and server relay (30 Hz)
	private static final double CLIENT_SEND_HALF_INTERVAL_MS = 15;
	private static final double SERVER_RELAY_HALF_INTERVAL_MS = 16;

	/**
	 * Cup mod: sends to every client with the mod the estimated delay of the position of every other player
	 * (sender ping / 2 + relay + receiver ping / 2), used by the mod to display the other players at the same race time.
	 */
	public void cupSendPlayerDelays() {
		java.util.List<Player> list = new java.util.ArrayList<Player>();
		readPlayerListLock.lock();
		try {
			list.addAll(players.values());
		} finally {
			readPlayerListLock.unlock();
		}
		for (Player receiver : list) {
			if (receiver.getModVersion() <= 0 || receiver.getPlayerServerData().isSigned() == false) {
				continue;
			}
			StringBuilder text = new StringBuilder("CUPLAG");
			for (Player sender : list) {
				if (sender == receiver || sender.getPlayerServerData().isSigned() == false) {
					continue;
				}
				double delay = sender.getTimings().getLastPingTime() / 2.0 + receiver.getTimings().getLastPingTime() / 2.0
						+ CLIENT_SEND_HALF_INTERVAL_MS + SERVER_RELAY_HALF_INTERVAL_MS;
				String part = " " + sender.getID() + ":" + Math.round(delay);
				if (text.length() + part.length() > 255) {// chat message length is sent as u8
					break;
				}
				text.append(part);
			}
			sendTcpChatMessage(receiver, ChatMessageColors.defaultColor, text.toString());
		}
	}

	/**
	 * Command of the server console (launcher without window).
	 */
	public String consoleCommand(String command) {
		String[] parts = command.trim().split("\\s+", 2);
		String arg = parts.length > 1 ? parts[1] : "";
		switch (parts[0].toLowerCase()) {
		case "nextmap":
			serverRealtime.requestMap(1, null, true);
			return "Next map requested.";
		case "restartmap":
			serverRealtime.requestMap(2, null, false);
			return "Restart of the map requested (without warmup).";
		case "newgame": {
			// new game on the current map: points (cup) / times (time attack) reset; "newgame warmup": with the warmup
			boolean warmup = arg.trim().equalsIgnoreCase("warmup");
			serverRealtime.requestMap(4, null, warmup);
			return "New game requested" + (warmup ? " (with warmup)." : ".");
		}
		case "warmup": {
			String[] w = arg.trim().split("\\s+");
			GameState s = getGameState();
			if (w[0].equalsIgnoreCase("restart")) {
				serverRealtime.requestMap(2, null, true);
				return "Restart of the warmup requested.";
			}
			if (w[0].equalsIgnoreCase("add")) {
				int seconds = 60;
				try {
					seconds = Integer.parseInt(w.length > 1 ? w[1] : "60");
				} catch (NumberFormatException e) {
					return "usage: warmup add <seconds>";
				}
				boolean timed = s.isCupMode() ? s.getCup().isTimedWarmupRound() : s.isTimeAttackWarmup();
				if (timed == false) {
					return "No warmup with a duration is running (cup with rounds: warmup addround).";
				}
				extendTime(seconds);
				sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Warmup extended by " + seconds + " seconds.");
				return "Warmup extended by " + seconds + " s.";
			}
			if (w[0].equalsIgnoreCase("addround")) {
				if (s.isCupMode() == false) {
					return "Warmup rounds need the cup mode (time attack: warmup add <seconds>).";
				}
				s.getCup().addWarmupRound();
				sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "One more warmup round on this map.");
				cupSendState();
				return "One more warmup round.";
			}
			return "usage: warmup restart | warmup add <seconds> | warmup addround";
		}
		case "map":
			if (arg.trim().isEmpty()) {
				return "usage: map <file name>";
			}
			serverRealtime.requestMap(3, arg.trim(), true);
			return "Map requested: " + arg.trim();
		case "maps": {
			// launcher: list of the maps of the rotation
			String[] list = server.util.FileUtil.getMapFileList(serverConfig.getMapDirectory());
			StringBuilder b = new StringBuilder("@@MAPS");
			for (String m : list) {
				b.append("|").append(m);
			}
			return b.toString();
		}
		case "status": {
			// launcher: @@STATUS|mode|map|players|max|round|rounds|warmup
			GameState s = getGameState();
			String round = "", rounds = "";
			boolean warmup;
			if (s.isCupMode()) {
				round = String.valueOf(s.getCup().getRoundsPlayedOnMap() + (s.getRoundRunning() || s.getCup().isWarmup() ? 1 : 0));
				rounds = String.valueOf(s.getCup().getSettings().roundsPerMap);
				warmup = s.getCup().isWarmup();
			} else {
				warmup = s.isTimeAttackWarmup() || s.isTimeAttackWarmupPending();
			}
			return "@@STATUS|" + (s.isCupMode() ? "cup" : "timeattack") + "|" + s.getCurrentMapName() + "|" + players.size() + "|"
					+ getMaxPlayerCount() + "|" + round + "|" + rounds + "|" + (warmup ? 1 : 0);
		}
		case "say":
			sendTcpBroadcastChatMessage(ChatMessageColors.serverNotificationColor, "[Server] " + arg);
			return "[Server] " + arg;
		case "cupreset":
			if (getGameState().isCupMode() == false) {
				return "The server is not running the cup mode.";
			}
			getGameState().getCup().resetMatch();
			sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "The cup has been reset by the server.");
			sendTcpBroadcast(new TcpMessage(PlayerProcessor.TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
			return "Cup reset.";
		case "bots":
			if (getGameState().isCupMode() == false) {
				return "The bots need the cup mode.";
			}
			try {
				int count = Integer.parseInt(arg.trim());
				getBots().setCount(count);
				return "Bots: " + count;
			} catch (NumberFormatException e) {
				return "usage: bots <count>";
			}
		case "players": {
			StringBuilder b = new StringBuilder("Players (" + players.size() + "):");
			for (Player p : players.values()) {
				b.append(" [").append(p.getID()).append("] ").append(p.getPlayerProfileData().getPlayerName());
			}
			return b.toString();
		}
		default:
			return "Commands: nextmap, restartmap, map <file>, maps, newgame [warmup], warmup restart|add <s>|addround, say <text>, cupreset, bots <count>, players, status, stop";
		}
	}

	public void sendTcpBroadcastChatMessage(String string) {
		sendTcpBroadcastChatMessage(ChatMessageColors.defaultColor, string);
	}

	public void extendTime(int seconds) {
		serverRealtime.extendTime(seconds);
	}

	public void sendTcpChatMessage(Player player, Color color, String string) {

		if (broadCaster != null) {

			ByteBuffer b = ByteBuffer.allocate((string.length() * 2) + 4);

			// color of the text
			BufferConverter.buffer_write_u8(b, (byte) color.getRed());
			BufferConverter.buffer_write_u8(b, (byte) color.getGreen());
			BufferConverter.buffer_write_u8(b, (byte) color.getBlue());

			// string
			BufferConverter.buffer_write_u8(b, (byte) string.length());
			BufferConverter.buffer_write_chars_u16(b, string);

			sendTcpMessageLocked(player, PlayerProcessor.TcpServerMessage.CHATMESSAGE, b);

		}
	}

	public void sendTcpBroadcastChatMessage(Color color, String string) {

		if (broadCaster != null) {

			ByteBuffer b = ByteBuffer.allocate((string.length() * 2) + 4);

			// color of the text
			BufferConverter.buffer_write_u8(b, (byte) color.getRed());
			BufferConverter.buffer_write_u8(b, (byte) color.getGreen());
			BufferConverter.buffer_write_u8(b, (byte) color.getBlue());

			// string
			BufferConverter.buffer_write_u8(b, (byte) string.length());
			BufferConverter.buffer_write_chars_u16(b, string);

			broadCaster.getTcpBroadcaster()
					.addTcpBroadcastMessage(new TcpMessage(PlayerProcessor.TcpServerMessage.CHATMESSAGE, b));

		}
	}

	public String[] getPlayerArray() {
		String[] players = new String[] { "test" };
		return players;
	}

	public void sendTcpMessageLocked(Player p, int messageID, ByteBuffer buffer) {
		p.getOutpoutMessageQueue().offerConcurrent(new TcpMessage(messageID, buffer));
	}

	public int getPlayerCount() {
		int s = 0;
		readPlayerListLock.lock();

		if (players != null) {
			s = getPlayers().size();
		}
		readPlayerListLock.unlock();
		return s;
	}

	public int getMaxPlayerCount() {
		return serverConfig.getMaxPlayerCount();
	}

	public RessourceHandler getRessourceHandler() {
		return ressources;
	}

}