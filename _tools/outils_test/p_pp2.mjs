import { patch } from "./wsrep.mjs";
patch("player/PlayerProcessor.java", [
[`				case "records":
					sendServerRecords(player);
					break;`, `				case "records":
					sendServerRecords(player);
					break;

				case "spec":
				case "observer":
				case "play":
					if (server.getGameState().isCupMode()) {
						server.cupSetObserver(player, command.equals("play") == false);
					} else {
						this.server.sendTcpChatMessage(player, ChatMessageColors.errorColor, "The server is not running the cup mode.");
					}
					break;`],
[`		if (result.allFinished) {
			state.shortenRound(3000);// everybody finished: end the round
		}

		server.sendTcpBroadcast(new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
	}`, `		if (result.allFinished) {
			state.shortenRound(3000);// everybody finished: end the round
		}

		server.sendTcpBroadcast(new TcpMessage(TcpServerMessage.LEADERBOARD_UPDATE, ByteBuffer.allocate(0)));
		server.cupSendState();
	}`],
[`								"Points per rank: " + cupSettings.repartitionToString() + ". Respawn at checkpoint allowed, complete reset = spectator. Type /cup for the ranking.");`,
 `								"Points per rank: " + cupSettings.repartitionToString() + ". Respawn at checkpoint allowed, complete reset = spectator. Type /cup for the ranking.");
						server.sendTcpChatMessage(player, ChatMessageColors.serverNotificationColor,
								"Casters: type /spec to watch the cup without racing (/play to race again).");
						server.cupSendState();`],
]);
