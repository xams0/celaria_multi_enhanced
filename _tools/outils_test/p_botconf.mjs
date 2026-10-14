import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "ServerConfig.java", [
[`		if(reader.exists("cup_roundTimeLimit")){`, `		if(reader.exists("cup_bots")){
			cupBots = Math.max(0, Integer.parseInt(reader.get("cup_bots").trim()));
		}
		if(reader.exists("cup_roundTimeLimit")){`],
[`	public int getUdpPort() {`, `	private int cupBots;// cup mode: bots started with the server

	public int getCupBots() {
		return cupBots;
	}

	public int getUdpPort() {`],
]);
patch(S + "ServerCore.java", [
[`		this.consolePrintln("=== Server Started ===");// Display the message that the server started`, `		this.consolePrintln("=== Server Started ===");// Display the message that the server started
		if (getGameState().isCupMode() && getServerConfig().getCupBots() > 0) {
			getBots().setCount(getServerConfig().getCupBots());
			this.consolePrintln("Bots: " + getServerConfig().getCupBots());
		}`],
]);
