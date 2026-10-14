package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import DataStructures.TcpMessage;
import DataStructures.ThreadedQueue;
import masterserver.HttpConnection;
import server.game.CupState;
import server.game.GameState;
import server.leaderboard.LeaderboardEntry;
import server.leaderboard.LeaderboardState;
import server.player.Player;
import server.player.PlayerProcessor.TcpServerMessage;
import server.util.BufferConverter;
import server.util.FileUtil;
import server.util.ServerClock;
import server.util.Util;

public class ServerProcessor implements Runnable{
	
	


	public static class LeaderboardAdd{
		private LeaderboardState.LEADERBOARDADDTYPE type;
		private LeaderboardEntry entry;
		public LeaderboardAdd(LeaderboardState.LEADERBOARDADDTYPE type,LeaderboardEntry entry) {
			this.type = type;
			this.entry = entry;
		}
		
		public LeaderboardState.LEADERBOARDADDTYPE getType() {
			return type;
		}
		
		public LeaderboardEntry getEntry() {
			return entry;
		}
	};
	
	public boolean run;

	private int sleepTime = 1000/ServerCore.TICKS_PER_SECOND;//10;
	ServerCore core;

	String mapDirectory = "./maps/";
	int currentMapIndex = -1;

	int lastPlayerCount;
	long refreshPlayerCountTime;


	
	public long httpRefreshTimeStep = 90L*1000L;//60 second HTTP refresh. DO NOT MODIFY!
	
	public long playerCountHTTPsendDelay = 6L*1000;
	
	long lastHTTPrefresh;

	private ThreadedQueue<LeaderboardAdd> newLeaderboardEntryList;//only used for checking and displaying chatmessages for the leaderboardstate

	private ThreadedQueue<Integer> extendTimeRequest;
	


	boolean sendRefreshPlayerCount;
	
	int lastFinishTimeoutAnnounce;//cup mode: last announced second of the finish timeout

	// map change requested from the server console (launcher): 1 = next map, 2 = restart the map, 3 = map by name
	private volatile int mapRequest;
	private volatile String mapRequestName;
	private boolean forcedMapLoad;
	private int forcedMapIndex;
	private boolean taReplayAfterWarmup;// time attack: the warmup is over, the map is reloaded for the real round

	/** server console: next map (1), restart the map (2), map by file name (3) */
	public void requestMap(int kind, String name) {
		mapRequestName = name;
		mapRequest = kind;
	}

	public int getCurrentMapIndex() {
		return currentMapIndex;
	}

	private void processMapRequest(GameState state) {
		int kind = mapRequest;
		if (kind == 0) {
			return;
		}
		mapRequest = 0;
		String[] list = FileUtil.getMapFileList(mapDirectory);
		if (list.length == 0) {
			core.consolePrintln("No map in " + mapDirectory);
			return;
		}
		core.getRessourceHandler().setMapFileList(list);
		int index = -1;
		String current = state.getCurrentMapName();
		for (int i = 0; i < list.length; i++) {
			if (kind == 3 ? list[i].equalsIgnoreCase(mapRequestName) : list[i].equals(current)) {
				index = i;
			}
		}
		if (kind == 3 && index < 0) {
			core.consolePrintln("Unknown map: " + mapRequestName);
			return;
		}
		if (kind == 1) {
			index = (index + 1) % list.length;
		} else if (index < 0) {
			index = 0;
		}
		if (state.getRoundRunning()) {
			state.setRoundRunning(false);
			if (state.isCupMode()) {
				state.getCup().cancelRound();// no points for the interrupted round
			}
		}
		state.setDelayStarted(false);
		state.setEndRoundTimerStarted(false);
		state.endTimeAttackWarmup();
		taReplayAfterWarmup = false;
		forcedMapLoad = true;
		forcedMapIndex = index;
		core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, (kind == 2 ? "Map restarted: " : "Map: ") + list[index]);
	}
	
	

	
	public ServerProcessor(ServerCore core){
		run = true;

		this.core = core;

		lastPlayerCount = 0;
		
		newLeaderboardEntryList = new ThreadedQueue<LeaderboardAdd>();
		extendTimeRequest = new ThreadedQueue<Integer>();


		sendRefreshPlayerCount = false;

		refreshPlayerCountTime = 0;
	}
	
	
	//=============== HTTP ==============
	private synchronized HttpConnection.ResponseMap sendHTTPregister() {
		
		lastHTTPrefresh = System.currentTimeMillis();

		ServerConfig serverConfig = core.getServerConfig();
		return HttpConnection.registerOnline(serverConfig.getServer_online_name(), serverConfig.getUdpPort(),core.getPlayerCount(),serverConfig.getMaxPlayerCount(),serverConfig.getPassword() != null,ServerCore.SERVER_PROTOCOL_VERSION);
	}


	public synchronized HttpConnection.ResponseMap sendHTTPregisterTimed() {
		return sendHTTPregister();
	}

	
	public boolean parseRegisterResponse(HttpConnection.ResponseMap response) {
		boolean error = false;
		if(response.hasKey("refreshTimestep") && response.hasKey("playerCountRefreshDelay")){
			int seconds = (Integer.parseInt(response.getValue("refreshTimestep")));
			//hardcoded limit (DO NOT MODIFY)
			seconds = Math.max(seconds,45);
			httpRefreshTimeStep = seconds*1000L;
			
			
			int delay = Integer.parseInt(response.getValue("playerCountRefreshDelay"));
			delay = Math.max(delay, 3);//DO NOT MODIFY
			playerCountHTTPsendDelay = delay*1000L;
			
			
		}else {
			error = true;
		}
		return error;
	}
	//==========================================
	
	
	

	void extendTime(int seconds) {
		extendTimeRequest.offerConcurrent(seconds);
	}
	
	public ThreadedQueue<LeaderboardAdd> getNewLeaderboardEntryList(){
		return newLeaderboardEntryList;
	}


	public void reset(){
		currentMapIndex = -1;
	}

	public void setRunning(boolean run){
		this.run = run;
	}


	//cup mode synchronized start
	private static final long CUP_START_MAX_WAIT_MS = 20*1000L;//don't wait longer for players who are still loading
		// countdown of the game (3, 2, 1, Go!) started by the ROUND_START message (initial countdown of the default settings,
	// the mod forces it): the race starts CUP_GAME_COUNTDOWN_MS after the ROUND_START message
	public static final long CUP_GAME_COUNTDOWN_MS = 3*1000L;
	private static final long CUP_ANNOUNCE_ADVANCE_MS = 700;// time for the scheduled start message to reach the clients
	int lastStartCountdownAnnounce;
	long lastPlayerDelaysSent;

	/**
	 * @return 0 = nobody on the server, 1 = some players are ready, 2 = every player is ready, 3 = nobody is ready yet
	 */
	private int playersReadyState() {
		int ready = 0, total = 0;
		long now = System.currentTimeMillis();
		for (Player p : core.getPlayers().values()) {
			if (p.getPlayerServerData().isSigned() && core.getGameState().getCup().canRace(p.getID())) {// winners and observers don't race
				total++;
				if (p.getPlayerServerData().isReadyForCupStart(now)) {
					ready++;
				}
			}
		}
		if (ready == 0) {
			return total > 0 ? 3 : 0;
		}
		return ready == total ? 2 : 1;
	}

	// the start messages are sent a bit before the scheduled start (latency compensation, this loop runs every 100 ms)
	private static final double CUP_START_SEND_ADVANCE_MS = 150;

	private void processCupStart(GameState state) {
		long now = System.currentTimeMillis();
		double nowClock = ServerClock.nowMS();
		if (state.getCupStartClockMS() == 0) {
			long waited = now - state.getCupMapLoadTimeMS();
			int ready = playersReadyState();// nobody on the server or still loading: the round doesn't start
			long minWait = Math.round(state.getCup().getSettings().startDelaySec*1000);
			if (waited >= minWait && (ready == 2 || (ready == 1 && waited >= CUP_START_MAX_WAIT_MS)
					|| (ready == 3 && waited >= 2 * CUP_START_MAX_WAIT_MS))) {
				double startClock = nowClock + CUP_ANNOUNCE_ADVANCE_MS + CUP_GAME_COUNTDOWN_MS;// race start (end of the countdown)
				state.setCupStartClockMS(startClock);
				lastStartCountdownAnnounce = -1;
				core.cupAnnounceScheduledStart(startClock);// clients with the mod start by themselves at this time
			}
			return;
		}
		double remaining = state.getCupStartClockMS() - CUP_GAME_COUNTDOWN_MS - nowClock;// until the start of the countdown
		if (remaining > CUP_START_SEND_ADVANCE_MS) {
			return;// the countdown is displayed by the game (3, 2, 1, Go!)
		}
		//countdown of every player who loaded the map: the race starts at the scheduled time
		state.setDelayStarted(false);
		core.getGameState().getHighscoreState().resetTimes();
		state.startNewRound();
		core.cupSynchronizedStart(state.getCupStartClockMS());
		lastFinishTimeoutAnnounce = -1;
		CupState cup = state.getCup();
		if (cup.isWarmup()) {
			core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "WARMUP "+cup.getWarmupIndex()+"/"+cup.getSettings().warmupRounds+" on this map: no points.");
		} else {
			core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Cup - round "+(cup.getRoundsPlayedOnMap()+1)+"/"+cup.getSettings().roundsPerMap+" on this map. Points limit: "+cup.getSettings().pointsLimit);
		}
		core.sendTcpBroadcast(new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
	}

	@Override
	public void run() {

		while(run == true){
			GameState state = core.getGameState();
			//sends HTTP refresh in intervals to the server

			if(core.getServerConfig().onlineMode() == true){

				int currentPlayerCount = core.getPlayerCount();
				if(lastPlayerCount != currentPlayerCount) {
					lastPlayerCount = currentPlayerCount;

					sendRefreshPlayerCount = true;

					if(refreshPlayerCountTime == 0) {
						refreshPlayerCountTime = System.currentTimeMillis() + playerCountHTTPsendDelay;//DO NOT MODIFY!
					}
				}
				
				
				if((lastHTTPrefresh+httpRefreshTimeStep) <System.currentTimeMillis()) {
					HttpConnection.ResponseMap response = sendHTTPregister();
					if(response != null) {
						if(response.getSuccess() == false) {
							parseRegisterResponse(response);
							
							sendRefreshPlayerCount = false;
							refreshPlayerCountTime = 0;
						}
					}
				}
							
				if(sendRefreshPlayerCount) {
					if(refreshPlayerCountTime <= System.currentTimeMillis()) {
						sendHTTPregister();
						sendRefreshPlayerCount = false;
						refreshPlayerCountTime = 0;
					}
				}
				
				
			}
			
			
			//process extend time refresh
			if(extendTimeRequest.isEmptyConcurrent() == false) {
				int timeAdd = extendTimeRequest.pollConcurrent();//retrieves and removes
				core.getGameState().addRoundTime(timeAdd);
				
				core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Extended time by "+timeAdd+" seconds.");
				
				//send visual time extend TCP message!
				
				ByteBuffer b = ByteBuffer.allocate(4);
				BufferConverter.buffer_write_u32(b, timeAdd);
				
				core.sendTcpBroadcast(new TcpMessage(TcpServerMessage.ROUNDTIME_EXTEND,b));
				
				
				
			}

			//searches the mapdirectory if maplist doesnt exist
			if(core.getRessourceHandler().getMapFileList() == null){
				core.consolePrintln("Searching map-directory...");
				String[] mapFileList = FileUtil.getMapFileList(mapDirectory);

				core.getRessourceHandler().setMapFileList(mapFileList);
				core.consolePrintln("Search complete. Found "+mapFileList.length+" .cmap files.");

			}

			processMapRequest(state);

			//loads a new map
			if(core.getRessourceHandler().getMapFileList() != null){
				if(core.getRessourceHandler().getMapFileList().length>0){

					if(core.getRessourceHandler().getMapHandler().mapDataIsNull() == true || (state.isEndRoundTimerFinished()==true && state.getEndRoundTimerStarted() == true) || forcedMapLoad){
						boolean replayMap = false;
						boolean afterWarmup = taReplayAfterWarmup;
						boolean forcedNow = forcedMapLoad;
						taReplayAfterWarmup = false;
						if (forcedMapLoad) {
							// restart / change of the map from the console: rounds and warmup of the map start again
							forcedMapLoad = false;
							if (state.isCupMode()) {
								state.getCup().mapChanged();
							}
							currentMapIndex = forcedMapIndex - 1;
						} else if (state.isCupMode() == false && afterWarmup) {
							replayMap = true;// time attack: same map after the warmup
						} else if(state.isCupMode()) {
							CupState cup = state.getCup();
							boolean skipMap = state.pollCupSkipMap();
							if(cup.isMatchOver()) {
								cup.resetMatch();
								core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "A new cup begins! Points limit: "+cup.getSettings().pointsLimit);
							}else if(core.getRessourceHandler().getMapHandler().mapDataIsNull() == false && skipMap == false) {
								replayMap = cup.replayCurrentMap();
							}
							if(replayMap == false) {
								cup.mapChanged();
							}
						}
						
						// the maps of the folder are read again (maps added or removed with the launcher), the current map keeps its place
						String[] freshList = FileUtil.getMapFileList(mapDirectory);
						if (freshList.length > 0) {
							core.getRessourceHandler().setMapFileList(freshList);
							for (int i = 0; i < freshList.length && forcedNow == false; i++) {
								if (freshList[i].equals(state.getCurrentMapName())) {
									currentMapIndex = i;
								}
							}
						}
						if(replayMap == false || currentMapIndex < 0) {
							currentMapIndex++;
						}
						state.setTimeAttackWarmupPending(state.isCupMode() == false && afterWarmup == false);
						if(currentMapIndex >= core.getRessourceHandler().getMapFileList().length){
							currentMapIndex = 0;
						}

						core.consolePrintln("Loading Map with index ["+currentMapIndex+"] "+'"'+core.getRessourceHandler().getMapFileList()[currentMapIndex]+'"'+" ...");

						try {

							byte[] mapData = FileUtil.loadBinaryFile(mapDirectory+core.getRessourceHandler().getMapFileList()[currentMapIndex]);

							ByteBuffer mapBuffer = ByteBuffer.wrap(mapData);
							boolean success = core.getRessourceHandler().getMapHandler().setMap(mapBuffer);

							if(success == true){
								state.setCurrentMapName(core.getRessourceHandler().getMapFileList()[currentMapIndex]);
								core.modMapChanged();

								//Iterate through all players and initialise new map loading
								ConcurrentHashMap<Integer, Player> map = core.getPlayers();
								Iterator<Integer> it = map.keySet().iterator();

								while(it.hasNext()){
									Integer key = (Integer) it.next();
									Player player = (Player) map.get(key);
									if(player != null){//safety check
										player.initNewMapLoading();
										//clear checkpoints

										player.getCheckpointQueue().clearConcurrent();

									}
								}

								state.setRoundRunning(false);
								state.setEndRoundTimerStarted(false);


								it = map.keySet().iterator();

								while(it.hasNext()){
									Integer key = (Integer) it.next();
									Player player = (Player) map.get(key);
									if(player != null){//safety check
										player.getPlayerServerData().setSendStartRoundMessage(false);//will be sent/set to true from the playerRealtimeHandler

									}
								}

								//starts end round timer which displays the highscore on the client for the given time before the new round starts
								state.startNewRoundDelay();

								core.consolePrintln("Map Loaded ...");

							}else{
								core.consolePrintln("Unable to load map: "+'"'+core.getRessourceHandler().getMapFileList()[currentMapIndex]+'"'+" : invalid map format!");
							}

						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}

					//process map switch request
					if(state.nextMapRequested()){
						state.resetNextMapRequest();
						state.finishRoundEarly();
					}

					//cup mode: round without player still racing (everybody left, finished or was eliminated)
					if(state.isCupMode() && state.getRoundRunning() && state.getCup().allParticipantsFinished()) {
						state.shortenRoundAllFinished();
					}

					//cup mode: complete resets of the clients without the mod
					core.cupProcessPendingResets();

					//mod: delays of the other players (prediction) and state of the players, every 2 seconds (both modes)
					if(System.currentTimeMillis() - lastPlayerDelaysSent >= 2000) {
												lastPlayerDelaysSent = System.currentTimeMillis();
						core.cupSendPlayerDelays();
						core.cupSendState();
					}

					//cup mode: finish timeout countdown
					if(state.isCupMode() && state.getRoundRunning()) {
						long remaining = state.getFinishTimeoutRemainingMS();
						if(remaining >= 0) {
							int sec = (int) Math.ceil(remaining/1000.0);
							if(sec != lastFinishTimeoutAnnounce && (sec == 10 || sec == 5 || sec == 3 || sec == 2 || sec == 1)) {
								lastFinishTimeoutAnnounce = sec;
								core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Round ends in "+sec+"...");
							}
						}
					}

					//Ends a round if the time is over (gameplay will stop for around 10-15 seconds in order to examine the leaderboard)
					if(state.isRoundFinished() ==true && state.getRoundRunning() == true){

						state.setRoundRunning(false);
						
						if(state.isCupMode()) {
							CupState cup = state.getCup();
							//points have to be distributed before the clients get the ROUND_END message (they request the end leaderboard right after)
							for(String m : cup.endRound()) {
								core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, m);
							}
							state.startEndRoundTimer(Math.round((cup.isMatchOver() ? cup.getSettings().endMatchDelaySec : cup.getSettings().endRoundDelaySec)*1000));
							core.cupSendState();
							core.sendTcpBroadcast(new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
						}else if (state.isTimeAttackWarmup()) {
							// end of the warmup: short end screen, then the map is reloaded for the real round
							state.endTimeAttackWarmup();
							taReplayAfterWarmup = true;
							state.startEndRoundTimer(3000);
							core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "End of the warmup! The map restarts: the times count now.");
						}else {
							state.startEndRoundTimer();
						}



						ConcurrentHashMap<Integer, Player> map = core.getPlayers();
						Iterator<Integer> it = map.keySet().iterator();

						while(it.hasNext()){
							Integer key = (Integer) it.next();
							Player player = (Player) map.get(key);
							if(player != null){//safety check
								player.sendRoundEnded();
							}
						}

						//clients will send leaderboard requests to the server in order to display the leaderboard on the client

					}

					//sort highscoreState

					if(newLeaderboardEntryList.isEmptyConcurrent() == false) {//prevent unnessecary write lock in poll() if list is empty
						
						LeaderboardAdd entryAdd = newLeaderboardEntryList.pollConcurrent();
						LeaderboardEntry entry = entryAdd.getEntry();
						while(entryAdd != null){

							if(entry.timeSet() == true && state.isCupMode() == false){
								LeaderboardEntry topEntry = core.getGameState().getHighscoreState().getTopEntry();


								if((entry.timeSet() == true && topEntry.timeSet() == true && entry.isBetterThan(topEntry))
										|| (entry.timeSet() == true && topEntry.timeSet() == false)
										){
									core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "Player ["+entry.getPlayername()+"] is now leading! Time to beat: "+Util.timeFormatMS(entry.getTimeMS()));
								}
							}
							
							
							core.getGameState().getHighscoreState().addEntry(entryAdd.type,entry);

							entryAdd = newLeaderboardEntryList.pollConcurrent();
						}
					}


					//cup mode: synchronized start once every player loaded the map
					if(state.isCupMode() && state.getDelayStarted() == true){
						processCupStart(state);
					}

					if(state.isCupMode() == false && state.isDelayFinished() ==true && state.getDelayStarted() == true){
						state.setDelayStarted(false);
						core.getGameState().getHighscoreState().resetTimes();
						state.startNewRound();
						if (state.isTimeAttackWarmup()) {
							core.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "WARMUP: " + Math.round(state.getRemainingRoundMS() / 1000.0) + " seconds, the times don't count.");
						}
						core.cupSendState();
					}


				}
			}


			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}
