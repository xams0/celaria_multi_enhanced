import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "bots/BotClient.java", [
[`			socket = new Socket();
			socket.connect(new InetSocketAddress(server, port), 5000);`, `			for (int attempt = 0;; attempt++) {// the server may still be starting
				try {
					socket = new Socket();
					socket.connect(new InetSocketAddress(server, port), 2000);
					break;
				} catch (IOException e) {
					if (attempt >= 10 || running == false) {
						throw e;
					}
					Thread.sleep(1000);
				}
			}`],
]);
