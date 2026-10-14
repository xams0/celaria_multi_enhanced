package server.game;

import debug.DebugIO;
import server.ServerConfig;
import server.leaderboard.LeaderboardState;
import server.records.ServerRecords;


public class GameState {

	//time of a round
	long roundTimeMS;
	volatile long roundTargetTimeMS;//volatile: modified by the player processor in the cup mode (finish timeout)
	
	//delay before round start
	long delayTimeMS;
	long delayTargetTimeMS;
	
	
	long endRoundTimeMS;
	long endRoundTargetTimeMS;
	
	//flags
	boolean delayStarted;
	boolean roundStarted;
	boolean endRoundTimerStarted;
	
	private int gameMode;
	
	private boolean switchToNextMap;
	
	
	
	private LeaderboardState highscore;

	//best times of the players on every map (records.json in the server folder)
	private final ServerRecords records = new ServerRecords(new java.io.File("records.json"));
	private final server.bots.GhostRecorder ghosts = new server.bots.GhostRecorder();// best run of every map (bots)
	private volatile String currentMapName = "";
	
	private CupState cup;//null if the server doesn't run the cup mode
	private final TimeAttackModState timeAttackMod = new TimeAttackModState();// time attack: features of the mod
	
	private long cupRoundTimeMS;
	
	//time when the finish timeout of the cup mode ends (0 = not started)
	private volatile long finishTimeoutTargetMS;
	
	//cup mode: skip the remaining rounds of the current map (admin command "nextmap")
	private volatile boolean cupSkipMap;

	//cup mode: synchronized start (time of the map load and end of the start countdown, 0 = countdown not started)
	private long cupMapLoadTimeMS;
	private double cupStartClockMS;//scheduled start of the round (ServerClock), 0 = countdown not started
	
	public GameState(ServerConfig config){
		gameMode = 1;
		
		
		roundTimeMS = (long) (config.getRoundTimeSec()*1000L);
		
		if(config.isCupMode()) {
			cup = new CupState(config.getCupSettings());
			cupRoundTimeMS = config.getCupSettings().roundTimeLimitSec*1000L;
		}else {
			cup = null;
		}
		finishTimeoutTargetMS = 0;
		roundTargetTimeMS = System.currentTimeMillis();
		
		timeAttackWarmupMS = Math.round(config.getTimeAttackWarmupSec()*1000);
		delayTimeMS = Math.round(config.getTimeAttackStartDelaySec()*1000);//10 second delay before the round starts. (players can't move until then) 
		//>> little delay to wait so that the majority of clients synchronized with the game (maptransfer,etc..)
		delayTargetTimeMS = System.currentTimeMillis();
		

		
		endRoundTimeMS = Math.round(config.getTimeAttackEndRoundDelaySec()*1000);//15 second time delay at the end of a round (displaying leaderboards)
		endRoundTargetTimeMS = System.currentTimeMillis();
		
		
		
		//Flags
		endRoundTimerStarted = false;
		delayStarted = false;
		roundStarted = false;
		
		switchToNextMap = false;
		
		highscore = new LeaderboardState();
	}
	
	public void reset(){
		highscore = new LeaderboardState();
		if(cup != null) {
			cup.resetMatch();
		}
	}
	
	/**
	 * @return true (once) if the remaining cup rounds of the current map have to be skipped.
	 */
	public boolean pollCupSkipMap() {
		boolean skip = cupSkipMap;
		cupSkipMap = false;
		return skip;
	}
	
	public long getCupMapLoadTimeMS() {
		return cupMapLoadTimeMS;
	}

	public double getCupStartClockMS() {
		return cupStartClockMS;
	}

	public void setCupStartClockMS(double startClock) {
		cupStartClockMS = startClock;
	}

	public server.bots.GhostRecorder getGhosts() {
		return ghosts;
	}

	public ServerRecords getRecords() {
		return records;
	}

	public String getCurrentMapName() {
		return currentMapName;
	}

	public void setCurrentMapName(String name) {
		currentMapName = name;
	}

	public boolean isCupMode() {
		return cup != null;
	}
	
	public TimeAttackModState getTimeAttackMod() {
		return timeAttackMod;
	}

	public CupState getCup() {
		return cup;
	}
	
	public LeaderboardState getHighscoreState(){
		return highscore;
	}

	public void addRoundTime(int seconds) {
		roundTargetTimeMS+=seconds*1000L;
	}
	
	public boolean nextMapRequested(){
		return switchToNextMap;
	}
	
	public void resetNextMapRequest(){
		switchToNextMap = false;
	}
	
	public boolean requestNextMapRotation(){
		if(isDelayFinished() && isRoundFinished() == false && isEndRoundTimerFinished()==true){
			switchToNextMap = true;
			cupSkipMap = true;
			return true;
		}else{
			return false;
		}
	}
	
	
	public int getGameMode(){
		return gameMode;
	}
	
	
	public boolean getEndRoundTimerStarted(){
		return endRoundTimerStarted;
	}
	
	public void setEndRoundTimerStarted(boolean set){
		this.endRoundTimerStarted = set;
	}
	
	
	public void startEndRoundTimer(){
		startEndRoundTimer(endRoundTimeMS);
	}
	
	public void startEndRoundTimer(long durationMS){
		setEndRoundTimerStarted(true);
		endRoundTargetTimeMS = System.currentTimeMillis()+durationMS;
	}
	

	
	public void setRoundRunning(boolean set){
		this.roundStarted = set;
	}
	
	public boolean getRoundRunning(){
		return roundStarted;
	}
	
	public void startNewRoundDelay(){
		setDelayStarted(true);
		cupMapLoadTimeMS = System.currentTimeMillis();
		cupStartClockMS = 0;
		
		delayTargetTimeMS = System.currentTimeMillis()+delayTimeMS;
		DebugIO.println("STARTING NEW ROUND DELAY!");
	}

	
	// time attack: warmup at the start of every map (a first round of timeattack_warmup seconds, then the map is reloaded)
	private long timeAttackWarmupMS;
	private volatile boolean timeAttackWarmupPending;// the next round of the map is the warmup
	private volatile boolean timeAttackWarmup;// the current round is the warmup

	public void setTimeAttackWarmupPending(boolean pending){
		timeAttackWarmupPending = pending && timeAttackWarmupMS > 0;
	}

	public boolean isTimeAttackWarmup(){
		return timeAttackWarmup;
	}

	public boolean isTimeAttackWarmupPending(){
		return timeAttackWarmupPending;
	}

	public void endTimeAttackWarmup(){
		timeAttackWarmup = false;
	}

	public long getRemainingRoundMS(){
		return Math.max(0, roundTargetTimeMS-System.currentTimeMillis());
	}

	public void startNewRound(){
		finishTimeoutTargetMS = 0;
		if(cup != null) {
			cup.startRound(System.currentTimeMillis());
			// warmup with a duration: the round lasts the warmup time
			roundTargetTimeMS = System.currentTimeMillis()+(cup.isTimedWarmupRound() ? cup.getSettings().warmupTimeSec*1000L : cupRoundTimeMS);
		}else if(timeAttackWarmupPending) {
			timeAttackWarmupPending = false;
			timeAttackWarmup = true;
			roundTargetTimeMS = System.currentTimeMillis()+timeAttackWarmupMS;
		}else {
			timeAttackWarmup = false;
			roundTargetTimeMS = System.currentTimeMillis()+roundTimeMS;
		}
		setRoundRunning(true);
		DebugIO.println("STARTING NEW ROUND!");
	}
	
	/**
	 * Ends the round after the given time (if the round would end later).
	 * @return true if the end of the round was moved forward.
	 */
	/** cup mode: end of the round once every player finished (setting cup_allFinishedDelay) */
	public boolean shortenRoundAllFinished(){
		if (cup != null && cup.isTimedWarmupRound()) {
			return false;// warmup with a duration: it lasts its time even if everybody finished
		}
		return shortenRound(cup != null ? Math.round(cup.getSettings().allFinishedDelaySec*1000) : 3000);
	}

	public boolean shortenRound(long remainingMS){
		long target = System.currentTimeMillis()+remainingMS;
		if(target < roundTargetTimeMS) {
			roundTargetTimeMS = target;
			return true;
		}
		return false;
	}
	
	/**
	 * Cup mode: starts the finish timeout (once the first player finished the round).
	 */
	public void startFinishTimeout(long timeoutMS){
		shortenRound(timeoutMS);
		finishTimeoutTargetMS = roundTargetTimeMS;
	}
	
	/**
	 * @return remaining milliseconds of the finish timeout, or -1 if no finish timeout is running.
	 */
	public long getFinishTimeoutRemainingMS(){
		if(finishTimeoutTargetMS == 0 || roundStarted == false) {
			return -1;
		}
		return Math.max(0, roundTargetTimeMS-System.currentTimeMillis());//the round can still be shortened after the start of the timeout (all players finished)
	}
	
	public void finishRoundEarly(){
		roundTargetTimeMS = System.currentTimeMillis()+5000;//5 seconds
	}
	
	public boolean getDelayStarted(){
		return delayStarted;
	}
	
	public void setDelayStarted(boolean set){
		this.delayStarted = set;
	}
	
	public boolean isDelayFinished(){
		if(System.currentTimeMillis() >= delayTargetTimeMS){
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isEndRoundTimerFinished(){
		
		if(System.currentTimeMillis() >= endRoundTargetTimeMS){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * Returns the remaining time (in ticks) of the round.
	 * 1 second = 60 ticks.
	 * 
	 * @return
	 */
	public int getRemainingRoundTimeTicks(){
		float diffSeconds = (((roundTargetTimeMS-System.currentTimeMillis()))/1000);
		int ticks = (int) Math.ceil(diffSeconds*100.0f);
		if(ticks <=0){ticks = 0;}
		return ticks;
	}
	
	public boolean isRoundFinished(){
		if(System.currentTimeMillis() >= roundTargetTimeMS){
			return true;
		}else{
			return false;
		}
	}
}
