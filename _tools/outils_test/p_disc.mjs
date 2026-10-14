import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "ServerCore.java", [
[`		this.consolePrintln("=== Server Started ===");// Display the message that the server started`, `		this.consolePrintln("=== Server Started ===");// Display the message that the server started
		lanDiscovery = new server.connection.LanDiscovery(this);// server browser LAN of the cup mod
		Thread discovery = new Thread(lanDiscovery, "LAN discovery");
		discovery.setDaemon(true);
		discovery.start();`],
[`		consolePrintln("=== Closing server ===");`, `		consolePrintln("=== Closing server ===");
		if (lanDiscovery != null) {
			lanDiscovery.stop();
		}`],
[`	private server.bots.BotManager bots;`, `	private server.bots.BotManager bots;
	private server.connection.LanDiscovery lanDiscovery;`],
]);
