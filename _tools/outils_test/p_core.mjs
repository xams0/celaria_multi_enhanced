import { patch } from "./wsrep.mjs";
const SOH = String.fromCharCode(1);
patch("ServerCore.java", [
[`				if (p.getModVersion() > 0 && p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now)) {`,
 `				if (p.getModVersion() > 0 && p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now)
						&& getGameState().getCup().canRace(p.getID())) {`],
[`				if (p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now)) {
					ready.add(p);
				} else if (p.getPlayerServerData().isSigned()) {`,
 `				if (getGameState().getCup().canRace(p.getID()) == false) {
					continue;// winners and observers are spectators
				}
				if (p.getPlayerServerData().isSigned() && p.getPlayerServerData().isReadyForCupStart(now)) {
					ready.add(p);
				} else if (p.getPlayerServerData().isSigned()) {`],
[`		DebugIO.println("CUP ROUND STARTED WITH " + ready.size() + " PLAYERS (latency compensation " + maxDelay + " ms)");
	}`,
 `		DebugIO.println("CUP ROUND STARTED WITH " + ready.size() + " PLAYERS (latency compensation " + maxDelay + " ms)");
		cupSendState();
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
	public void cupSendState() {
		if (getGameState().isCupMode() == false) {
			return;
		}
		java.util.List<String> messages = getGameState().getCup().buildStateMessages();
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
		if (getGameState().isCupMode() == false || getGameState().getRoundRunning() == false
				|| getGameState().getCup().isParticipant(player.getID()) == false) {
			return;
		}
		String m = "${SOH}CUPCP " + player.getID() + " " + index + " " + timeMS;
		for (Player p : modPlayers()) {
			sendTcpChatMessage(p, ChatMessageColors.defaultColor, m);
		}
	}

	/**
	 * Cup mode: observer (caster) mode. The player doesn't race and isn't ranked; the mod shows the spectator view.
	 */
	public void cupSetObserver(Player player, boolean observer) {
		if (getGameState().isCupMode() == false || getGameState().getCup().setObserver(player.getID(), observer) == false) {
			return;
		}
		String name = player.getPlayerProfileData().getPlayerName();
		sendTcpBroadcastChatMessage(ChatMessageColors.gamestateColor, observer ? "[" + name + "] is now observer." : "[" + name + "] will race from the next round.");
		if (getGameState().getCup().allParticipantsFinished()) {
			getGameState().shortenRound(3000);
		}
		cupSendState();
		sendTcpBroadcast(new TcpMessage(PlayerProcessor.TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
	}`],
[`		player.initNewMapLoading();
		if (getGameState().getCup().allParticipantsFinished()) {
			getGameState().shortenRound(3000);
		}`,
 `		player.initNewMapLoading();
		if (getGameState().getCup().allParticipantsFinished()) {
			getGameState().shortenRound(3000);
		}
		cupSendState();`],
]);
