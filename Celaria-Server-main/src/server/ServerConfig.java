package server;

import io.ConfFileReader;
import server.game.CupState;

public class ServerConfig {
	private int maxPlayers;
	private float playerUdpTimeoutSec;
	private float playerTcpTimeoutSec;

	private int networkPort;
	
	private String server_online_name;

	private String hostAddress;


	String mapDirectory;

	private double roundTimeSec;

	private String[] mapList;//example: "test.cmap";


	private String adminPassword;

	private boolean server_online;

	private final long tcpStillAliveTimePeriodMS = 15L*1000L;//10 seconds


	private final int client_UDP_refreshrate = 10;//how many times a second the player updates it's data with the server (client data like position,animation,rotation, etc...)
	//this is mererly a suggestion. The client can still ignore this.
	
	private String password;

	public static final int GAMEMODE_TIMEATTACK = 0;
	public static final int GAMEMODE_CUP = 1;

	private int gameMode;

	private CupState.Settings cupSettings;

	public ServerConfig(){
		mapDirectory = "./maps/";
		maxPlayers = 1;
		playerUdpTimeoutSec = 30f;
		playerTcpTimeoutSec = 30f;

		networkPort = 6510;
		
		roundTimeSec = 60*15;//10 minutes;

		hostAddress = null;

		adminPassword = null;

		mapList = null;

	
		
		password = null;

		gameMode = GAMEMODE_TIMEATTACK;
		cupSettings = new CupState.Settings();

		setServer_online_name("<default>");
		setServer_online(false);
	}


	public void apply(ConfFileReader reader){
		if(reader.exists("maxPlayerCount")){
			setMaxPlayerCount(Integer.parseInt(reader.get("maxPlayerCount")));
		}

		if(reader.exists("roundTime")){
			setRoundTimeSec(Integer.parseInt(reader.get("roundTime")));
		}

		if(reader.exists("port")){
			setPort(Integer.parseInt(reader.get("port")));
		}
		
		if(reader.exists("mapDirectory")){
			setMapDirectory(reader.get("mapDirectory"));
		}

		if(reader.exists("adminPassword")){
			setAdminPassword(reader.get("adminPassword"));
		}


		
		if(reader.exists("password")){
			String key = reader.get("password");
			if(key.equals("") == false) {
				setPassword(reader.get("password"));
			}
		}
		
		if(reader.exists("serverList_register")){
			String onlineVal = reader.get("serverList_register");

			onlineVal = onlineVal.toLowerCase();
			if(onlineVal.equals("true")) {
				setServer_online(true);				
			}else {
				setServer_online(false);
			}
		}
		if(reader.exists("onlineName")){
			setServer_online_name(reader.get("onlineName"));
		}

		if(reader.exists("gameMode")){
			String mode = reader.get("gameMode").toLowerCase();
			if(mode.equals("cup")) {
				gameMode = GAMEMODE_CUP;
			}else {
				gameMode = GAMEMODE_TIMEATTACK;
			}
		}

		if(reader.exists("cup_pointsLimit")){
			cupSettings.pointsLimit = Math.max(1, Integer.parseInt(reader.get("cup_pointsLimit")));
		}
		if(reader.exists("cup_roundsPerMap")){
			cupSettings.roundsPerMap = Math.max(1, Integer.parseInt(reader.get("cup_roundsPerMap")));
		}
		if(reader.exists("cup_nbWinners")){
			cupSettings.nbWinners = Math.max(1, Integer.parseInt(reader.get("cup_nbWinners")));
		}
		if(reader.exists("cup_finishTimeout")){
			cupSettings.finishTimeoutSec = Integer.parseInt(reader.get("cup_finishTimeout"));
		}
		if(reader.exists("cup_bots")){
			cupBots = Math.max(0, Integer.parseInt(reader.get("cup_bots").trim()));
		}
		if(reader.exists("cup_roundTimeLimit")){
			cupSettings.roundTimeLimitSec = Math.max(10, Integer.parseInt(reader.get("cup_roundTimeLimit")));
		}
		if(reader.exists("cup_respawnAtStartDNF")){
			cupSettings.respawnAtStartDNF = reader.get("cup_respawnAtStartDNF").trim().equalsIgnoreCase("true");
		}
		if(reader.exists("cup_forceRestartDNF")){
			cupSettings.forceRestartDNF = reader.get("cup_forceRestartDNF").trim().equalsIgnoreCase("true");
		}
		if(reader.exists("cup_endRoundDelay")){
			cupSettings.endRoundDelaySec = Math.max(1, parseSeconds(reader.get("cup_endRoundDelay")));
		}
		if(reader.exists("cup_endMatchDelay")){
			cupSettings.endMatchDelaySec = Math.max(1, parseSeconds(reader.get("cup_endMatchDelay")));
		}
		if(reader.exists("cup_allFinishedDelay")){
			cupSettings.allFinishedDelaySec = Math.max(0, parseSeconds(reader.get("cup_allFinishedDelay")));
		}
		if(reader.exists("cup_startDelay")){
			cupSettings.startDelaySec = Math.max(0.5, parseSeconds(reader.get("cup_startDelay")));
		}
		if(reader.exists("cup_warmupRounds")){
			cupSettings.warmupRounds = Math.max(0, Integer.parseInt(reader.get("cup_warmupRounds").trim()));
		}
		if(reader.exists("timeattack_warmup")){
			timeAttackWarmupSec = Math.max(0, parseSeconds(reader.get("timeattack_warmup")));
		}
		if(reader.exists("timeattack_endRoundDelay")){
			timeAttackEndRoundDelaySec = Math.max(1, parseSeconds(reader.get("timeattack_endRoundDelay")));
		}
		if(reader.exists("timeattack_startDelay")){
			timeAttackStartDelaySec = Math.max(1, parseSeconds(reader.get("timeattack_startDelay")));
		}
		if(reader.exists("udpRelayRate")){
			udpRelayRate = Math.max(5, Math.min(120, Integer.parseInt(reader.get("udpRelayRate").trim())));
		}
		if(reader.exists("cup_pointsRepartition")){
			String[] parts = reader.get("cup_pointsRepartition").split(",");
			int[] points = new int[parts.length];
			for(int i = 0;i<parts.length;i++) {
				points[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
			}
			cupSettings.pointsRepartition = points;
		}




	}
	
	private static double parseSeconds(String value) {
		return Double.parseDouble(value.trim().replace(',', '.'));
	}

	// time attack: leaderboard display at the end of a round, delay before the start of a round (seconds)
	private double timeAttackEndRoundDelaySec = 10;
	private double timeAttackStartDelaySec = 5;
	private double timeAttackWarmupSec = 60;// warmup at the start of every map (times don't count), 0 = none

	public double getTimeAttackWarmupSec() {
		return timeAttackWarmupSec;
	}
	// positions of the players relayed per second (both modes, LAN servers)
	private int udpRelayRate = 60;

	public double getTimeAttackEndRoundDelaySec() {
		return timeAttackEndRoundDelaySec;
	}

	public double getTimeAttackStartDelaySec() {
		return timeAttackStartDelaySec;
	}

	public int getUdpRelayRate() {
		return udpRelayRate;
	}

	public int getGameMode() {
		return gameMode;
	}

	public boolean isCupMode() {
		return gameMode == GAMEMODE_CUP;
	}

	public CupState.Settings getCupSettings() {
		return cupSettings;
	}

	public void setPort(int port) {
		this.networkPort = port;
	}

	/**
	 * Sent to the client in SERVER_INFO. The client uses it as the interval (in game steps) between two UDP position
	 * updates (clamped to 10 at most by the client). The cup mode uses a smaller interval: the other players are displayed
	 * with less delay.
	 */
	public int getClientUdpRefreshRate() {
		return 3;// both modes: LAN servers, the spectator view of the mod follows the other players closely
	}

	//TCP and UDP use always the same port (for simplicity purposes)
	private int cupBots;// cup mode: bots started with the server

	public int getCupBots() {
		return cupBots;
	}

	public int getUdpPort() {
		return networkPort;
	}

	public int getTcpPort() {
		return networkPort;
	}

	public String getAdminPassword(){
		return adminPassword;
	}

	public void setAdminPassword(String password){
		adminPassword = password;
	}
	
	
	public boolean hasPassword() {
		return password != null;
	}

	public String getPassword(){
		return password;
	}

	public void setPassword(String password){
		this.password = password;
	}




	public String getMapDirectory(){
		return mapDirectory;
	}

	public void setHostAddress(String address){
		this.hostAddress = address;
	}

	public String getHostAddress(){
		return this.hostAddress;
	}

	public float getUdpTimeout(){
		return playerUdpTimeoutSec;
	}

	public float getTcpTimeout(){
		return playerTcpTimeoutSec; 
	}


	public long getTcpStillAliveIntervallMS(){
		return tcpStillAliveTimePeriodMS;
	}


	public void setMapDirectory(String directory){
		mapDirectory = directory;
	}

	public void setTcpTimeout(float seconds){
		playerTcpTimeoutSec = seconds;
	}

	public void setUdpTimeout(float seconds){
		playerUdpTimeoutSec = seconds;
	}

	public void setMapList(String[] maplist){
		this.mapList = maplist;
	}

	public void setMaxPlayerCount(int playercount){
		this.maxPlayers = playercount;
		if(this.maxPlayers>=32){
			this.maxPlayers = 32;
		}
		if(this.maxPlayers<=1){
			this.maxPlayers = 1;
		}
	}

	public int getMaxPlayerCount(){
		return maxPlayers;
	}

	public double getRoundTimeSec(){
		return roundTimeSec;
	}

	public void setRoundTimeSec(double seconds){
		this.roundTimeSec = seconds;
	}

	public String[] getMapList(){
		return mapList;
	}


	public boolean onlineMode() {
		return server_online;
	}


	public void setServer_online(boolean server_online) {
		this.server_online = server_online;
	}


	public String getServer_online_name() {
		return server_online_name;
	}


	public void setServer_online_name(String server_online_name) {
		this.server_online_name = server_online_name;
	}

}
