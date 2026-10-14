package server.player;

import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import java.util.List;
import java.util.Queue;

import DataStructures.TcpMessage;
import DataStructures.ThreadedQueue;
import debug.DebugIO;
import server.ChatMessageColors;
import server.Server;
import server.Server.TCPDISCONNECTTYPE;
import server.ServerCore;
import server.ServerProcessor;
import server.connection.TcpPlayerWriter;
import server.game.CupState;
import server.game.GameState;
import server.records.ServerRecords;
import server.leaderboard.LeaderboardEntry;
import server.leaderboard.LeaderboardState;
import server.player.PlayerServerState.LeaderboardRequest;
import server.player.data.PlayerRuntimeUdpData;
import server.player.data.PlayerSkin;
import server.util.BufferConverter;
import server.util.ServerClock;
import server.util.Util;
import server.util.Util.LeaderboardFindResult;

public class PlayerProcessor implements Runnable {

	ServerCore server;

	boolean run;

	// message which the client sends
	class TcpClientMessage {

	};

	// message which the server sends
	public class TcpServerMessage {
		public static final int LEADERBOARD = 120;
		public static final int NEW_MAPLOAD = 180;
		public static final int SET_MAPID = 182;
		public static final int ROUND_START = 183;
		public static final int ROUND_END = 184;
		public static final int PLAYER_INFO = 10;
		public static final int CHATMESSAGE = 200;
		public static final int UDP_CHECK_CONFIRM = 240;
		public static final int TCP_STILL_ALIVE = 210;
		public static final int SERVER_INFO = 1;
		public static final int PLAYER_VALIDATED_CONFIRMATION = 2;
		public static final int CLIENT_INFO_CONFIRM = 8;
		public static final int PASSWORD_CRYPT_DATA = 150;
		public static final int MAP_DATA_PACKET = 4;
		public static final int LAST_MAP_DATA_PACKET = 5;
		public static final int PLAYER_DISCONNECT = 11;
		public static final int ROUNDTIME_EXTEND = 20;
		public static final int LEADERBOARD_UPDATE = 121;
		public static final int SERVER_DISCONNECT = 250;// disconnect by the server (kick for example)
	};

	private static final String adminRequiredMessage = "Admin privileges required for this command!";

	ByteBuffer emptyBuffer;

	public PlayerProcessor(ServerCore server) {
		this.server = server;

		emptyBuffer = ByteBuffer.allocate(0);

		run = true;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (run == true) {

			Player[] playerArray = server.getPlayers().values().toArray(new Player[0]);

			for (int i = 0; i < playerArray.length; i++) {

				processPlayer(playerArray[i]);

			}

			try {
				Thread.sleep(1000 / ServerCore.TICKS_PER_SECOND);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	public void setRunning(boolean run) {
		this.run = run;
	}

	private void addLeaderboardEntryMessage(ByteBuffer message, int place, LeaderboardEntry entry) throws IOException {
		String playerName = entry.getPlayername();
		int playerID = entry.getPlayerID();

		BufferConverter.buffer_write_u8(message, place);// place
		BufferConverter.buffer_write_u8(message, playerID);// playerID
		BufferConverter.buffer_write_u8(message, playerName.length());// length (max 255)
		BufferConverter.buffer_write_chars_u8(message, playerName);// string

		BufferConverter.buffer_write_u8(message, entry.getBadge());

		if (entry.timeSet() == true) {
			BufferConverter.buffer_write_u8(message, 1);
			BufferConverter.buffer_write_u32(message, (int) entry.getTime());// time
			BufferConverter.buffer_write_u8(message, entry.getMedal());// medal

		} else {
			BufferConverter.buffer_write_u8(message, 0);
		}
	}

	/**
	 * Cup mode: leaderboard with the cup status (points/finalist/winner) in front of the player names.
	 */
	private LeaderboardEntry[] buildCupLeaderboard() {
		List<CupState.CupLeaderboardEntry> cupEntries = server.getGameState().getCup().buildLeaderboard();
		LeaderboardEntry[] leaderboard = new LeaderboardEntry[cupEntries.size()];
		for (int i = 0; i < leaderboard.length; i++) {
			CupState.CupLeaderboardEntry e = cupEntries.get(i);
			String name = e.displayName;
			if (name.length() > 255) {
				name = name.substring(0, 255);// length is sent as u8
			}
			if (e.timeSet) {
				long ticks = (e.officialTime + 9) / 10;// official time in ticks (1/100 second), sent to every client
				byte medal = server.getRessourceHandler().getMapHandler().getMedalIDfromTime(ticks);
				leaderboard[i] = new LeaderboardEntry(e.playerID, name, ticks, medal, e.badge);
				leaderboard[i].setPreciseTimeMS(e.time);// milliseconds for the clients with the mod
			} else {
				leaderboard[i] = new LeaderboardEntry(e.playerID, name, e.badge);
			}
		}
		return leaderboard;
	}

	/**
	 * Leaderboard request type sent by the cup mod for its "Records" tab (server records of the current map).
	 */
	private static final int REQUEST_SERVER_RECORDS = 2;

	private LeaderboardEntry[] buildRecordsLeaderboard() {
		List<ServerRecords.Record> records = server.getGameState().getRecords().getRecords(server.getGameState().getCurrentMapName());
		int count = Math.min(records.size(), 255);// count is sent as u8
		LeaderboardEntry[] leaderboard = new LeaderboardEntry[count];
		for (int i = 0; i < count; i++) {
			ServerRecords.Record r = records.get(i);
			String name = r.name.length() > 255 ? r.name.substring(0, 255) : r.name;
			byte medal = server.getRessourceHandler().getMapHandler().getMedalIDfromTime(r.timeTicks);
			leaderboard[i] = new LeaderboardEntry(-1, name, r.timeTicks, medal, (byte) 0);
			leaderboard[i].setPreciseTimeMS(r.timeMS);
		}
		return leaderboard;
	}

	private void sendPreciseTime(Player player, LeaderboardEntry entry) {
		if (entry.timeSet() == false) {
			return;
		}
		String text = "CUPMS " + entry.getTime() + " " + entry.getTimeMS() + " " + entry.getPlayername();
		if (text.length() <= 255) {// chat message length is sent as u8
			server.sendTcpChatMessage(player, ChatMessageColors.defaultColor, text);
		}
	}

	/**
	 * Server records: stores the time of a finished run and announces the records.
	 */
	private void addServerRecord(Player player, long timeTicks, long timeMS) {
		GameState state = server.getGameState();
		String map = state.getCurrentMapName();
		String name = player.getPlayerProfileData().getPlayerName();
		ServerRecords.Result r = state.getRecords().addTime(map, name, player.getPlayerProfileData().getSteamID(), timeTicks, timeMS);
		if (r.serverRecord) {
			String improvement = "";
			if (r.previousRecord != null) {
				improvement = " (-" + Util.timeFormatMS(Math.max(0, r.previousRecord.timeMS - timeMS)) + ", previous: " + r.previousRecord.name + ")";
			}
			server.sendTcpBroadcastChatMessage(ChatMessageColors.serverNotificationColor,
					"NEW SERVER RECORD: " + name + " " + Util.timeFormatMS(timeMS) + improvement);
		} else if (r.personalBest) {
			server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
					"New personal best on this server: " + Util.timeFormatMS(timeMS) + " (rank " + r.rank + ")");
		}
	}

	private void sendServerRecords(Player player) {
		String map = server.getGameState().getCurrentMapName();
		List<ServerRecords.Record> records = server.getGameState().getRecords().getRecords(map);
		server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor, "Server records on " + map + ":");
		if (records.isEmpty()) {
			server.sendTcpChatMessage(player, ChatMessageColors.defaultColor, "No record yet.");
		}
		for (int i = 0; i < records.size() && i < 10; i++) {
			ServerRecords.Record r = records.get(i);
			server.sendTcpChatMessage(player, ChatMessageColors.defaultColor, (i + 1) + ". " + r.name + " " + Util.timeFormatMS(r.timeMS));
		}
	}

	private static String ordinal(int n) {
		if (n % 100 >= 11 && n % 100 <= 13) {
			return n + "th";
		}
		switch (n % 10) {
		case 1:
			return n + "st";
		case 2:
			return n + "nd";
		case 3:
			return n + "rd";
		default:
			return n + "th";
		}
	}

	/**
	 * Time attack: observers (mod) are listed last with "SPECTATE" in front of their name.
	 */
	private LeaderboardEntry[] withObserversLast(LeaderboardEntry[] entries) {
		List<LeaderboardEntry> list = new java.util.ArrayList<LeaderboardEntry>();
		List<LeaderboardEntry> observers = new java.util.ArrayList<LeaderboardEntry>();
		for (LeaderboardEntry e : entries) {
			if (server.getGameState().getTimeAttackMod().isObserver(e.getPlayerID())) {
				observers.add(new LeaderboardEntry(e.getPlayerID(), "SPECTATE | " + e.getPlayername(), e.getBadge()));
			} else {
				list.add(e);
			}
		}
		if (observers.isEmpty()) {
			return entries;
		}
		list.addAll(observers);
		return list.toArray(new LeaderboardEntry[0]);
	}

	/**
	 * Cup mode: a player reached the goal.
	 */
	private void processCupGoal(Player player, long goalTicks, long goalTimeMS, long receiveTimeMS) {
		GameState state = server.getGameState();
		CupState cup = state.getCup();
		CupState.FinishResult result = cup.playerFinished(player.getID(), goalTicks, goalTimeMS, receiveTimeMS, player.getTimings().getLastPingTime() / 2);

		if (result == null) {
			if (state.getRoundRunning()) {
				String reason;
				if (cup.isEliminated(player.getID())) {
					reason = "You reset your run: this finish doesn't count.";
				} else if (cup.isParticipant(player.getID()) == false) {
					reason = "You didn't start this round: wait for the next one.";
				} else {
					reason = "Only your first finish of the round counts.";
				}
				server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor, reason);
			}
			return;
		}

		server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Player [" + player.getPlayerProfileData().getPlayerName()
				+ "] finished " + ordinal(result.position) + " (" + Util.timeFormatMS(result.time) + ")");
		addServerRecord(player, (result.officialTime + 9) / 10, result.time);
		if (ServerCore.isBot(player) == false && state.getGhosts().finished(player.getID(), player.getPlayerProfileData().getPlayerName(),
				state.getCurrentMapName(), result.time)) {
			server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "New run for the bots on this map: "
					+ player.getPlayerProfileData().getPlayerName() + " " + Util.timeFormatMS(result.time) + " (/bots <count>).");
		}

		if (result.firstFinisher) {
			long timeout = cup.getFinishTimeoutMS(server.getRessourceHandler().getMapHandler().medalTime_Platin);
			state.startFinishTimeout(timeout);
			server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor,
					"Finish timeout: " + (int) Math.ceil(timeout / 1000.0) + " seconds left to finish!");
		}
		if (result.allFinished) {
			state.shortenRoundAllFinished();// everybody finished: end the round
		}

		server.sendTcpBroadcast(new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
		server.cupSendState();
	}

	/**
	 * Cup mode: chat command which displays the current cup ranking.
	 */
	private void sendCupStandings(Player player) {
		CupState cup = server.getGameState().getCup();
		List<CupState.CupPlayer> standings = cup.getStandings();
		CupState.Settings settings = cup.getSettings();
		server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor, "Cup ranking (points limit " + settings.pointsLimit
				+ ", " + settings.nbWinners + " winner" + (settings.nbWinners > 1 ? "s" : "") + "):");
		for (int i = 0; i < standings.size(); i++) {
			CupState.CupPlayer p = standings.get(i);
			if (i < 8 || p.getPlayerID() == player.getID()) {
				server.sendTcpChatMessage(player, ChatMessageColors.defaultColor, (i + 1) + ". " + p.getName() + " - " + p.getStatusText());
			}
		}
	}

	public void processPlayer(Player player) {
		try {
			processTcpMessages(player);
			sendTcpStillAliveMessage(player);
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;// breaks the execution
		}

		/**
		 * Process leaderboard request (if the client requested it)
		 */
		PlayerServerState.LeaderboardRequest leaderboardRequested = player.getPlayerServerData()
				.pollLeaderboardRequested();

		if (leaderboardRequested != null) {// if(player.getPlayerServerData().isLeaderboardRequested() == true){
			DebugIO.println("SENDING LEADERBOARD 1");
			boolean cupMode = player.getServer().getGameState().isCupMode();
			if (cupMode || player.getServer().getGameState().getHighscoreState().isSorted() == true) {
				DebugIO.println("SENDING LEADERBOARD 2");
				LeaderboardEntry[] leaderboard;
				boolean recordsRequest = leaderboardRequested.requestType == REQUEST_SERVER_RECORDS;
				if (recordsRequest) {
					leaderboard = buildRecordsLeaderboard();
				} else if (cupMode) {
					leaderboard = buildCupLeaderboard();
				} else {
					leaderboard = withObserversLast(player.getServer().getGameState().getHighscoreState().getListArrayCopy());
				}

				// player.getPlayerServerData().setLeaderboardRequest(false,0,0);

				int requestedPosition = leaderboardRequested.offset;
				LeaderboardFindResult individualEntry = Util.findPlayerEntry(leaderboard, player.getID());

				boolean additionalPlayerEntry = false;
				if (leaderboardRequested.addOwnEntry == true && individualEntry != null) {
					additionalPlayerEntry = true;
				}

				int iterateEntries = leaderboardRequested.size;// max 10 elements

				if (leaderboardRequested.requestType == 1) {// if requestType == 1 (end screen leaderboard) send the
															// leaderboard instead only the requested size.
					iterateEntries = leaderboard.length;
					requestedPosition = 0;
				}

				// first calculate total bytes of the message(is nessecary)
				int totalBytes = 4;

				int actualPlayerEntries = 0;// the value which holds the final amount of entries in the leaderboard list
											// (is calculated in the first for loop)

				for (int i = 0; i < iterateEntries; i++) {

					if (i + requestedPosition < leaderboard.length) {

						LeaderboardEntry entry = leaderboard[i + requestedPosition];

						totalBytes += 4;
						String playerName = entry.getPlayername();
						totalBytes += playerName.length();
						totalBytes++;
						if (entry.timeSet() == true) {
							totalBytes += 4 + 1;
						}

						actualPlayerEntries++;
					} else {
						break;
					}

				}

				if (additionalPlayerEntry == true) {// add an entry in the list with the data of the specific player

					totalBytes += 4;
					String playerName = individualEntry.getEntry().getPlayername();
					totalBytes += playerName.length();
					totalBytes++;
					if (individualEntry.getEntry().timeSet() == true) {
						totalBytes += 4 + 1;
					}
				}

				try {

					ByteBuffer message = ByteBuffer.allocate(totalBytes);
					message.position(0);

					// LEADERBOARD TYPE (the server records are displayed by the client like the current leaderboard: type 0)
					BufferConverter.buffer_write_u8(message, recordsRequest ? 0 : leaderboardRequested.requestType);

					BufferConverter.buffer_write_u8(message, leaderboard.length);// total number in leaderboard
					BufferConverter.buffer_write_u8(message, actualPlayerEntries + (additionalPlayerEntry ? 1 : 0));// number
																													// of
																													// player
																													// entries
					BufferConverter.buffer_write_u8(message, (additionalPlayerEntry ? 1 : 0));// is the last entry the
																								// players own record?
																								// (true/false)

					for (int i = 0; i < actualPlayerEntries; i++) {
						int place;
						LeaderboardEntry entry = null;

						entry = leaderboard[i + requestedPosition];
						place = i + 1;

						addLeaderboardEntryMessage(message, place, entry);
					}

					if (additionalPlayerEntry == true) {
						addLeaderboardEntryMessage(message, individualEntry.getPlacement() + 1,
								individualEntry.getEntry());
					}

					// cup mod: milliseconds of the entries (hidden messages, the leaderboard packet only carries 1/100 second)
					if (player.getModVersion() > 0) {
						for (int i = 0; i < actualPlayerEntries; i++) {
							sendPreciseTime(player, leaderboard[i + requestedPosition]);
						}
						if (additionalPlayerEntry == true) {
							sendPreciseTime(player, individualEntry.getEntry());
						}
					}

					server.sendTcpMessageLocked(player, TcpServerMessage.LEADERBOARD, message);

				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					return;// breaks the execution
				}
			}
		}

		/**
		 * sends UDP confirmpacket via TCP
		 */
		if (player.getPlayerServerData().getUdpTestPacketRecieved() == true
				&& player.getPlayerServerData().getUdpConnectionEstablished() == true
				&& player.getPlayerServerData().getUdpConfirmPacketSend() == false) {

			// UDP CONFIRMATION PACKET
			ByteBuffer message = ByteBuffer.allocate(0);
			server.sendTcpMessageLocked(player, TcpServerMessage.UDP_CHECK_CONFIRM, message);
			DebugIO.println("UDP CONFIRM END");

			player.getPlayerServerData().setUdpConfirmPacketSend(true);
		}

		/**
		 * send map data to player (if requested)
		 */

		if (player.getPlayerServerData().getUdpTestPacketRecieved() == true
				&& player.getPlayerServerData().getUdpConnectionEstablished() == true
				&& player.getPlayerServerData().getUdpConfirmPacketSend() == true) {
			if (player.getPlayerServerData().requestedNewMapInit() == true) {

				// Now that the connection is established sent gameData to the server (NOTE: The
				// client will top the current session, start a loading screen and wait for the
				// mapdata from the server.)
				// send new gamemode data (initiates new map loading on the server!)

				ByteBuffer message = ByteBuffer.allocate(1);
				message.position(0);

				BufferConverter.buffer_write_u8(message, 1);// 0 = freeroam mode , 1 = time trial mode
				server.sendTcpMessageLocked(player, TcpServerMessage.NEW_MAPLOAD, message);

				// and now send mapData to the server once the map finished loading (currently
				// not used as originally intended, but it works.)
				message = ByteBuffer.allocate(2);
				BufferConverter.buffer_write_u16(message,
						player.getServer().getRessourceHandler().getMapHandler().getMapMagicID());// freeroam mode
				server.sendTcpMessageLocked(player, TcpServerMessage.SET_MAPID, message);
				// reset map transmitter
				player.getMapTransmitter().reset();
				player.getPlayerServerData().newMapInitDone();
			}

			if (player.getPlayerServerData().getPlayerReady()) {
				if (player.getPlayerServerData().getMapSended() == true) {

					// Send a "round started" message to tell the client that he can finally start
					// moving
					if (player.getPlayerServerData().getSendStartRoundMessage() == false
							&& player.getServer().getGameState().getRoundRunning() == true
							&& player.getServer().getGameState().isCupMode() == false// cup mode: synchronized start (ServerCore.cupSynchronizedStart)
							&& server.getGameState().getTimeAttackMod().isObserver(player.getID()) == false) {// observers don't race
						player.getPlayerServerData().setSendStartRoundMessage(true);

						ByteBuffer message = ByteBuffer.allocate(4);
						BufferConverter.buffer_write_u32(message, server.getGameState().getRemainingRoundTimeTicks());
						server.sendTcpMessageLocked(player, TcpServerMessage.ROUND_START, message);
					}

					// Round ended.
					if (player.getPlayerServerData().getSendEndRoundMessage() == false
							&& player.getServer().getGameState().getEndRoundTimerStarted()) {

						player.getPlayerServerData().setSendEndRoundMessage(true);

						ByteBuffer message = ByteBuffer.allocate(0);
						server.sendTcpMessageLocked(player, TcpServerMessage.ROUND_END, message);
					}
				}
			}

		}

		/**
		 * Check if the player requested information about another player and send it to
		 * him (if nessecary)
		 */

		// TODO: nicer playercheck

		if (player.getRequestedPlayerIDs().isEmptyConcurrent() == false) {

			// returns queue and clears internal one to avoid locks.
			Queue<Integer> requestedPlayerIDs = player.getRequestedPlayerIDs().returnAndClearConcurrent();

			while (requestedPlayerIDs.isEmpty() == false) {
				int requestedID = requestedPlayerIDs.poll();
				if (player.getServer().getPlayers().containsKey(requestedID)) {
					if (player.getServer().getPlayers().get(requestedID).getPlayerServerData().isSigned() == true) {

						Player p = player.getServer().getPlayers().get(requestedID);
						PlayerSkin ps = p.getPlayerSkinCopy();
						PlayerRuntimeUdpData data = p.getPlayerDataCopy();

						String name = p.getPlayerProfileData().getPlayerName();

						int namebytes = 1 + (name.length() * 2);// +1 as a leading byte is used to tell the client how
																// many characters of the name are following

						ByteBuffer message = ByteBuffer.allocate(2 + namebytes + 9 + 1);

						BufferConverter.buffer_write_u8(message, requestedID);
						BufferConverter.buffer_write_u8(message, 1);// 1= player exists

						BufferConverter.buffer_write_u8(message, name.length());// 1= player exists
						BufferConverter.buffer_write_chars_u16(message, name);

						BufferConverter.buffer_write_u8(message, ps.skinID);

						BufferConverter.buffer_write_u8(message, ps.skin_R);
						BufferConverter.buffer_write_u8(message, ps.skin_G);
						BufferConverter.buffer_write_u8(message, ps.skin_B);
						BufferConverter.buffer_write_u8(message, ps.armor_R);
						BufferConverter.buffer_write_u8(message, ps.armor_G);
						BufferConverter.buffer_write_u8(message, ps.armor_B);
						BufferConverter.buffer_write_u8(message, ps.eye_R);
						BufferConverter.buffer_write_u8(message, ps.eye_G);
						BufferConverter.buffer_write_u8(message, ps.eye_B);

						server.sendTcpMessageLocked(player, TcpServerMessage.PLAYER_INFO, message);

						DebugIO.println("SEND PLAYER INFO FROM " + requestedID + " to " + player.getID());

					}
				} else {
					// send information that player doesn't exist

					ByteBuffer message = ByteBuffer.allocate(2);

					// TODO: Send player information, lice name, color, etc...
					BufferConverter.buffer_write_u8(message, requestedID);
					BufferConverter.buffer_write_u8(message, 0);// 0 = player doesnt exist

					server.sendTcpMessageLocked(player, TcpServerMessage.PLAYER_INFO, message);
				}
			}
		}

		/**
		 * check UDP timeout (and disconnect if nessecary)
		 */

		// check udp timeout
		if (System.nanoTime() - player.getTimings()
				.getLastUdpTime() > (player.getServer().getServerConfig().getUdpTimeout() * 1000000000L)) {
			// UDP TIMEOUT!
			player.closePlayer(TCPDISCONNECTTYPE.UDPTIMEOUT);

			// UDP still alive packet is handled by the udp broadcaster!
		} else {

			if (System.nanoTime() - player.getTimings()
					.getLastTcpTime() > (player.getServer().getServerConfig().getTcpTimeout() * 1000000000L)) {
				// UDP TIMEOUT!
				player.closePlayer(TCPDISCONNECTTYPE.TCPTIMEOUT);

				// UDP still alive packet is handled by the udp broadcaster!
			}
		}

	}

	public void processTcpMessages(Player player) throws IOException {
		ThreadedQueue<TcpMessage> queue = player.getInputMessageQueue();

		TcpMessage message = null;
		message = queue.pollConcurrent();
		if (message != null) {

			processTcpMessage(player, message);
		}

	}

	void sendTcpStillAliveMessage(Player player) {

		// Send a TCP still alive message to the client
		if (player.getPlayerServerData().getLastTcpSendTimeMS()
				+ server.getServerConfig().getTcpStillAliveIntervallMS() < System.currentTimeMillis()) {
			server.sendTcpMessageLocked(player, TcpServerMessage.TCP_STILL_ALIVE, emptyBuffer);// 210 is the MessageID
																								// of a TCP stillalive
																								// refresh
			player.getPlayerServerData().resetLastTcpSendTime();
		}

	}

	private void processConsoleCommand(Player player, String input) {
		if (input.charAt(0) == '/') {
			input = input.substring(1);

			String[] segments = input.split("\\s+");// split by empty spaces (all empty spaces are removed. no need to
													// trim)

			if (segments.length > 0) {

				switch (segments[0]) {
				case "login":
					if (segments.length > 1) {
						if (server.getServerConfig().getAdminPassword() != null) {
							if (segments[1].equals(server.getServerConfig().getAdminPassword())) {
								this.server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
										"Login successful! You are now an Admin.");

								// NOTE: Look into possible synchronisation locks

								player.getPlayerServerData().setAdmin(true);

							} else {
								this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
										"Login failed: Incorrect password.");
							}
						} else {
							this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
									"Admin account is not set up.");
						}
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
								"Login failed: Password not provided.");
					}
					break;

				case "logout":
					if (player.getPlayerServerData().isAdmin()) {
						player.getPlayerServerData().setAdmin(false);
						this.server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
								"Logout successful.");
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
								"Logout failed: You were not logged in.");
					}
					break;

				case "nextmap":
					if (player.getPlayerServerData().isAdmin()) {
						if (this.server.getGameState().requestNextMapRotation()) {
							this.server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor,
									"Server will switch to the next map in a few seconds!");
						} else {
							this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
									"The server is already in a map-transition. Wait until a new round started!");
						}
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, adminRequiredMessage);
					}
					break;

				case "extend":
					if (player.getPlayerServerData().isAdmin()) {
						if (segments.length == 2) {
							int timeSecAdd = 0;
							boolean error = false;
							try {
								timeSecAdd = Integer.parseInt(segments[1]);// seconds to add to the timer
							} catch (NumberFormatException e) {
								timeSecAdd = 0;
								error = true;
							}
							if (error == false) {
								this.server.extendTime(timeSecAdd);
							} else {
								this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
										"Time is not in number format!");
							}
						} else {
							// adminRequiredMessage
							this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
									"Amount of time not specified.");
						}
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, adminRequiredMessage);
					}
					break;

				case "cup":
				case "points":
					if (server.getGameState().isCupMode()) {
						sendCupStandings(player);
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, "The server is not running the cup mode.");
					}
					break;

					case "records":
					sendServerRecords(player);
					break;

				case "bots":
					if (server.getGameState().isCupMode() == false) {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, "The bots need the cup mode.");
						break;
					}
					{
						int count = 3;
						if (segments.length > 1) {
							try {
								count = Integer.parseInt(segments[1]);
							} catch (NumberFormatException e) {
								count = 3;
							}
						}
						server.getBots().setCount(count);
						if (server.getGameState().getGhosts().bestGhost(server.getGameState().getCurrentMapName()) == null && count > 0) {
							this.server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor,
									"Bots: " + count + ". No run recorded on this map yet: finish the map once, the bots replay the best run.");
						} else {
							this.server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Bots: " + count + " (they replay the best run of the map).");
						}
					}
					break;

				case "spec":
				case "observer":
				case "play":
					server.cupSetObserver(player, segments[0].equals("play") == false);
					break;

				case "cupreset":
					if (player.getPlayerServerData().isAdmin()) {
						if (server.getGameState().isCupMode()) {
							server.getGameState().getCup().resetMatch();
							this.server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "The cup has been reset by an admin.");
							server.sendTcpBroadcast(new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
						} else {
							this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, "The server is not running the cup mode.");
						}
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, adminRequiredMessage);
					}
					break;

				default:
					this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, "unknown server-command.");
					break;
				}

			}
		}

	}

	public void processTcpMessage(Player player, TcpMessage message) throws IOException {
		int packetID = message.getID();

		ByteBuffer input = message.getBuffer();
		input.position(0);// reset position to 0

		switch (packetID) {

		case 1:
			int gameVersion = BufferConverter.buffer_read_u16(input);

			if (gameVersion == ServerCore.SERVER_PROTOCOL_VERSION) {

				// confirm

				int onlineMode = BufferConverter.buffer_read_u8(input);
				player.getPlayerServerData().setPlayerIsOnline(onlineMode == 1 ? true : false);

				if (onlineMode != 1) {
					player.closePlayer(TCPDISCONNECTTYPE.ONLINEMODE_REQUIRED);
				} else {

					String salt = "";
					int saltLen = 0;

					if (server.getServerConfig().hasPassword()) {
						salt = Util.generateRandomString(10);
						player.getPlayerServerData().setPasswordSalt(salt);
						saltLen = salt.length();

						System.out.println(salt);
					}

					ByteBuffer tcpMessage = ByteBuffer.allocate(3 + saltLen);
					BufferConverter.buffer_write_u8(tcpMessage, ServerCore.MODIFIED ? 1 : 0);

					BufferConverter.buffer_write_u8(tcpMessage, saltLen);
					if (saltLen > 0) {
						BufferConverter.buffer_write_chars_u8(tcpMessage, salt);
					}

					BufferConverter.buffer_write_u8(tcpMessage, server.getServerConfig().getClientUdpRefreshRate());

					server.sendTcpMessageLocked(player, TcpServerMessage.SERVER_INFO, tcpMessage);
				}

			} else {
				// if the version is not the right one, then send a player disconnect TCP
				// message and disconnect the player
				String m = "Protocol versions (Client: " + gameVersion + " / Server: "
						+ ServerCore.SERVER_PROTOCOL_VERSION + ")";

				if (gameVersion < ServerCore.SERVER_PROTOCOL_VERSION) {
					m += "#Please update your client to the latest version.";
				} else {
					m += "#The Server needs to be updated to be compatible with the client.";
				}

				player.closePlayer(TCPDISCONNECTTYPE.INCOMPATIBLESERVERVERSION, m);
			}

			break;

		case 2:
			// password packet

			int pwLen = BufferConverter.buffer_read_u8(input);
			String password = BufferConverter.buffer_read_chars_u8(input, pwLen);

			String comparePassword = "";
			try {
				comparePassword = Util
						.sha1(server.getServerConfig().getPassword() + player.getPlayerServerData().getPasswordSalt());
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				player.closePlayer(TCPDISCONNECTTYPE.PROCESSINGERROR);
				break;
			}

			if (password.equals(comparePassword)) {
				ByteBuffer tcpMessage = ByteBuffer.allocate(1);
				BufferConverter.buffer_write_u8(tcpMessage, 1);// 1 = ok, 0 = not ok
				server.sendTcpMessageLocked(player, TcpServerMessage.PLAYER_VALIDATED_CONFIRMATION, tcpMessage);

				player.getPlayerServerData().setServerPasswordPassed(true);
			} else {
				player.closePlayer(TCPDISCONNECTTYPE.WRONGPASSWORD);
			}

			break;

		case 210:// still-alive message (resets TCP counter). Doesn't have data.

			break;

		case 20:// player disconnected
			player.closePlayer();
			break;

		case 240:
			if (player.getPlayerServerData().getUdpTestPacketRecieved() == true) {
				player.getPlayerServerData().setUdpConnectionEstablished(true);
			}
			break;

		default: {

			if (server.getServerConfig().hasPassword() == false || (server.getServerConfig().hasPassword() == true
					&& player.getPlayerServerData().getServerPasswordPassed())) {

				switch (packetID) {

				case 3:// player information

					// Now read the playerdata from the packets and refresh the

					// read playername
					int nameLength = BufferConverter.buffer_read_u8(input);
					String name = BufferConverter.buffer_read_chars_u16(input, nameLength);

					// set Playername
					player.getPlayerProfileData().setPlayerName(name);

					// steamID
					if (player.getPlayerServerData().getPlayerIsOnline()) {
						long steamID = BufferConverter.buffer_read_u64(input);
						String steamIDstring = Long.toUnsignedString(steamID);

						player.getPlayerProfileData().setSteamID(steamIDstring);
					}

					// reads player info
					PlayerSkin skin = player.getPlayerSkinCopy();
					// color information

					skin.skinID = (byte) BufferConverter.buffer_read_u8(input);
					skin.skin_R = (byte) BufferConverter.buffer_read_u8(input);
					skin.skin_G = (byte) BufferConverter.buffer_read_u8(input);
					skin.skin_B = (byte) BufferConverter.buffer_read_u8(input);
					skin.armor_R = (byte) BufferConverter.buffer_read_u8(input);
					skin.armor_G = (byte) BufferConverter.buffer_read_u8(input);
					skin.armor_B = (byte) BufferConverter.buffer_read_u8(input);
					skin.eye_R = (byte) BufferConverter.buffer_read_u8(input);
					skin.eye_G = (byte) BufferConverter.buffer_read_u8(input);
					skin.eye_B = (byte) BufferConverter.buffer_read_u8(input);

					// apply player skin
					player.applyPlayerSkin(skin);

					// -------------------------------------------------------

					player.getPlayerServerData().setSigned(true);// player is now officially in the game

					// confirm

					ByteBuffer tcpOut = ByteBuffer.allocate(3);
					BufferConverter.buffer_write_u8(tcpOut, player.getID());
					BufferConverter.buffer_write_u16(tcpOut, player.getUdpKey());

					server.sendTcpMessageLocked(player, TcpServerMessage.CLIENT_INFO_CONFIRM, tcpOut);// send the
																										// package

					player.sendPlayerJoinedToListener();

					player.getServer().sendTcpBroadcastChatMessage(ChatMessageColors.serverNotificationColor,
							"Player " + '"' + name + '"' + "[" + player.getID() + "]" + " connected.");

					server.consolePrintln("Player " + '"' + name + '"' + "[" + player.getID() + "] connected");

					server.addLeaderboardEntry(
							new LeaderboardEntry(player.getID(), name, player.getPlayerProfileData().getBadgeID()));

					if (server.getGameState().isCupMode()) {
						CupState.Settings cupSettings = server.getGameState().getCup().getSettings();
						server.getGameState().getCup().playerJoined(player.getID(), player.getPlayerProfileData().getSteamID(), name,
								player.getPlayerProfileData().getBadgeID());
						server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
								"CUP MODE: reach " + cupSettings.pointsLimit + " points to become finalist, then win a round to win the cup!");
						server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
																"Points per rank: " + cupSettings.repartitionToString() + ". Respawn at checkpoint allowed, complete reset = spectator. Type /cup for the ranking.");
						server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
								"Casters: type /spec to watch the cup without racing (/play to race again).");
						server.cupSendState();
					}

					break;

				case 206:// cup mod: clock synchronisation (u32 client time, echoed with the reception and sending times of the server)
				{
					long clientTime = BufferConverter.buffer_read_u32(input) & 0xFFFFFFFFL;
					server.sendTcpChatMessage(player, ChatMessageColors.defaultColor, "\u0001CUPCLK " + clientTime + " "
							+ ServerClock.format(message.getCreationClockMS()) + " " + ServerClock.format(ServerClock.nowMS()));
				}
					break;

					case 209:// cup mod: the map is loaded and displayed
					BufferConverter.buffer_read_u8(input);
					player.getPlayerServerData().setModReady();
					break;

				case 208:// cup mod: observer mode (u8 1 = observer, 0 = player)
					server.cupSetObserver(player, BufferConverter.buffer_read_u8(input) == 1);
					break;

				case 205:// cup mod: the client runs the mod (sent with the player metadata, before the UDP check)
					player.setModVersion(BufferConverter.buffer_read_u8(input));
					DebugIO.println("PLAYER " + player.getID() + " RUNS THE CUP MOD " + player.getModVersion());
					server.modPlayerAnnounced(player);
					// hidden message (not displayed by the mod): this server supports the mod, the mod disables the Steam uploads
					server.sendTcpChatMessage(player, ChatMessageColors.defaultColor, "CUPMOD");
					break;

				// player ranking status (badge, multiplayer experience, etc...)
				case 4:
					int multiplayer_experience = BufferConverter.buffer_read_u32(input);
					byte badge_index = (byte) BufferConverter.buffer_read_u8(input);

					player.getPlayerProfileData().setExperience(multiplayer_experience);
					player.getPlayerProfileData().setBadgeID(badge_index);

					if (server.getGameState().isCupMode()) {
						server.getGameState().getCup().setBadge(player.getID(), badge_index);
					}

					player.getServer().addLeaderboardEntry(LeaderboardState.LEADERBOARDADDTYPE.OVERWRITE_BADGE_ONLY,
							new LeaderboardEntry(player.getID(), player.getPlayerProfileData().getPlayerName(),
									badge_index));

					break;

				case 6:
					// player finished loading the map
					DebugIO.println("PLAYER FINISHED MAP LOADING");
					player.getPlayerServerData().setMapLoaded(true);
					player.getPlayerServerData().setPlayerReady(true);// Player is verified and is ready to play (client
																		// loaded the map and player can now move
																		// around)

					break;

				case 120:// current leaderboard requested
					int leaderboardRequestType = BufferConverter.buffer_read_u8(input);
					int leaderboardRequestOffset = BufferConverter.buffer_read_u8(input);
					int leaderboardRequestSize = BufferConverter.buffer_read_u8(input);
					int leaderBoardownEntry = BufferConverter.buffer_read_u8(input);
					boolean leaderboardAddOwnEntry = false;
					if (leaderBoardownEntry == 1) {
						leaderboardAddOwnEntry = true;
					}

					if (player.getPlayerServerData().getUdpTestPacketRecieved() == true
							&& player.getPlayerServerData().getUdpConnectionEstablished() == true) {
						player.getPlayerServerData()
								.addLeaderboardRequest(new LeaderboardRequest(leaderboardRequestType,
										leaderboardRequestOffset, leaderboardRequestSize, leaderboardAddOwnEntry));
					}

					break;

				default:

					if (player.getPlayerServerData().getUdpTestPacketRecieved() == true
							&& player.getPlayerServerData().getUdpConnectionEstablished() == true) {

						switch (packetID) {
						case 10:// player request
							int requestedPlayerID = BufferConverter.buffer_read_u8(input);
							player.getRequestedPlayerIDs().offerConcurrent(requestedPlayerID);
							break;

						case 150:
							int saltID = BufferConverter.buffer_read_u8(input);

							String salt = Util.generateRandomString(10);

							player.getPlayerServerData().setPasswordSalt(salt);

							var len = 2 + salt.length();

							ByteBuffer outbuffer = ByteBuffer.allocate(len);
							BufferConverter.buffer_write_u8(outbuffer, saltID);
							BufferConverter.buffer_write_u8(outbuffer, salt.length());

							BufferConverter.buffer_write_chars_u8(outbuffer, salt);

							server.sendTcpMessageLocked(player, TcpServerMessage.PASSWORD_CRYPT_DATA, outbuffer);

							break;

						case 151:

							saltID = BufferConverter.buffer_read_u8(input);// should be 0;
							int passwordLen = BufferConverter.buffer_read_u8(input);

							String cryptedPW = BufferConverter.buffer_read_chars_u8(input, passwordLen);

							String comparePW = "";
							String emptyPW = "";
							try {
								comparePW = Util.sha1(server.getServerConfig().getAdminPassword()
										+ player.getPlayerServerData().getPasswordSalt());
								emptyPW = Util.sha1("");

							} catch (NoSuchAlgorithmException e) {
								// TODO Auto-generated catch block
								player.closePlayer(TCPDISCONNECTTYPE.PROCESSINGERROR);
								break;
							}

							if (cryptedPW.equals(emptyPW)) {
								this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
										"Login failed: Admin-mode not available.");
							} else {
								if (cryptedPW.equals(comparePW)) {
									this.server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
											"Login successful! You are now an Admin.");

									// NOTE: Look into possible synchronisation locks

									player.getPlayerServerData().setAdmin(true);

								} else {
									this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor,
											"Login failed: Incorrect password.");
								}
							}

							break;

						// further Mappackets were requested.
						case 199:

							int requestedMapOrderID = BufferConverter.buffer_read_u16(input);

							// sending map
							if (player.getServer().getRessourceHandler().getMapHandler().mapReady()) {
								if (requestedMapOrderID == player.getServer().getRessourceHandler().getMapHandler()
										.getMapMagicID()) {
									// It can happen that while the player requests a new packet from a map, the
									// server already loaded a new map (which makes this packet invalid)
									// The ID check prevents this from happening
									player.getMapTransmitter().sendPacket();
								}
							}

							break;

						case 200:// chatmessage

							String playerName = player.getPlayerProfileData().getPlayerName();

							String chatMessage = BufferConverter.buffer_read_chars_u8(input, input.capacity());
							// send TCP broadcast

							if (chatMessage.length() > 0) {
								if (chatMessage.charAt(0) == '/') {
									// possible console command
									processConsoleCommand(player, chatMessage);
								} else {
									// normal chat message (send to other clients as a broadcast)
									player.getServer().sendTcpBroadcastChatMessage(
											'"' + playerName + '"' + "[" + player.getID() + "]: " + chatMessage);
								}
							}

							break;

						case 201:
							// reset run
							player.getCheckpointQueue().clearConcurrent();
							if (server.getGameState().isCupMode()) {
								server.cupRespawnAtStart(player);// respawn key without checkpoint (DNF if cup_respawnAtStartDNF)
							} else {
								server.taRunRestarted(player);
							}

							break;

						case 212:// mod: the timer of the run restarted (new run)
							BufferConverter.buffer_read_u8(input);
							server.taRunRestarted(player);
							break;

						case 213:// mod: restart key (complete reset of the run, DNF in cup mode if cup_forceRestartDNF)
							BufferConverter.buffer_read_u8(input);
							server.cupForceRestart(player);
							break;

						case 202:// checkpoint activated

							// recieve player time
							int time = BufferConverter.buffer_read_u32(input);
							player.getCheckpointQueue().offerConcurrent(time);
							server.cupCheckpoint(player, player.getCheckpointQueue().sizeConcurrent(), player.pollCheckpointTimeMS(time));

							break;

							case 207:// cup mod: precise time (ms) of the following checkpoint packet
							player.setPendingCheckpointMS(BufferConverter.buffer_read_u32(input) & 0xFFFFFFFFL);
							break;

						case 204:// cup mod: precise time (ms) of the following goal packet
							player.setPendingGoalMS(BufferConverter.buffer_read_u32(input) & 0xFFFFFFFFL);
							break;

						case 203:// goal activated

							long goalTime = BufferConverter.buffer_read_u32(input);
							long goalTimeMS = player.pollGoalTimeMS(goalTime);// milliseconds if the client runs the cup mod

							if (server.getGameState().isCupMode()) {
								player.getCheckpointQueue().clearConcurrent();
								processCupGoal(player, goalTime, goalTimeMS, message.getCreationTimeMS());
								break;
							}
							// TODO: check if checkpoint count is valid
							// TODO: Better hashcode for player identification
							byte medal = server.getRessourceHandler().getMapHandler().getMedalIDfromTime(goalTime);

							LeaderboardEntry goalEntry = new LeaderboardEntry(player.getID(),
									player.getPlayerProfileData().getPlayerName(), goalTime, medal,
									player.getPlayerProfileData().getBadgeID());
							goalEntry.setPreciseTimeMS(goalTimeMS);// official time (ticks) kept, milliseconds for the ranking tie break
							player.getServer().addLeaderboardEntry(goalEntry);
							server.taFinished(player, goalTimeMS);
							if (server.getGameState().isTimeAttackWarmup() == false) {
								addServerRecord(player, goalTime, goalTimeMS);// warmup: times don't count
							}

							// clear checkpoint list
							player.getCheckpointQueue().clearConcurrent();

							ByteBuffer b = ByteBuffer.allocate(0);
							TcpMessage updateLeaderboardMessage = new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE,
									b);// leaderboard update
							player.getServer().sendTcpBroadcast(updateLeaderboardMessage);// Send leaderboard update
																							// message
							// NOTE: The checkpoint list is currently unused.
							/**
							 * The idea was to use the checkpointlist to validate the goaltime. This could
							 * be done (in order to avoid blocking this thread) by moving the calculation to
							 * the "playerRealtimeThread".
							 */

							break;

						}

					}

				}

			} else {
				player.closePlayer(TCPDISCONNECTTYPE.PROTOCOL_VIOLATION);
			}

		}

			break;

		}
	}

}
