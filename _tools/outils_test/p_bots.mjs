import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "game/GameState.java", [
[`	private final ServerRecords records = new ServerRecords(new java.io.File("records.json"));`,
 `	private final ServerRecords records = new ServerRecords(new java.io.File("records.json"));
	private final server.bots.GhostRecorder ghosts = new server.bots.GhostRecorder();// best run of every map (bots)`],
[`	public ServerRecords getRecords() {`, `	public server.bots.GhostRecorder getGhosts() {
		return ghosts;
	}

	public ServerRecords getRecords() {`],
]);
patch(S + "ServerCore.java", [
[`	public void cupSendState() {`, `	private server.bots.BotManager bots;

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

	public void cupSendState() {`],
[`		String m = "\u0001CUPCP " + player.getID() + " " + index + " " + timeMS;`, `		if (isBot(player) == false) {
			getGameState().getGhosts().addCheckpoint(player.getID(), timeMS);
		}
		String m = "\u0001CUPCP " + player.getID() + " " + index + " " + timeMS;`],
[`		getGameState().getCup().setRoundStartTime(startTime);`, `		getGameState().getCup().setRoundStartTime(startTime);
		getGameState().getGhosts().startRound();`],
]);
patch(S + "connection/UdpReciever.java", [
[`								readLook(p, playerData, data, packet.getLength());`, `								readLook(p, playerData, data, packet.getLength());
								if (playerData.getRaceTimeMS() >= 0 && ServerCore.isBot(p) == false) {// run recorded for the bots
									server.getGameState().getGhosts().addSample(p.getID(), playerData.getRaceTimeMS(), x, y, z, movX, movY, movZ,
											rotationZ, animationID, animationStep, respawnNumber);
								}`],
]);
patch(S + "player/PlayerProcessor.java", [
[`		addServerRecord(player, (result.officialTime + 9) / 10, result.time);`, `		addServerRecord(player, (result.officialTime + 9) / 10, result.time);
		if (ServerCore.isBot(player) == false && state.getGhosts().finished(player.getID(), player.getPlayerProfileData().getPlayerName(),
				state.getCurrentMapName(), result.time)) {
			server.sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, "New run for the bots on this map: "
					+ player.getPlayerProfileData().getPlayerName() + " " + Util.timeFormatMS(result.time) + " (/bots <count>).");
		}`],
[`				case "spec":
				case "observer":`, `				case "bots":
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
				case "observer":`],
]);
