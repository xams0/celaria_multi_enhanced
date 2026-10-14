import fs from "node:fs";
const f = "connection/UdpReciever.java"; let s = fs.readFileSync(f, "utf8");
const rep = (o, n) => { const r = typeof o === "string" ? s.includes(o) : o.test(s); if (!r) { console.error("missing " + o); process.exit(1); } s = s.replace(o, () => n); };
rep(/checkForMessagesUDP\(\);\s*try \{\s*Thread\.sleep\(10\);\s*\} catch \(InterruptedException e\) \{\s*\/\/ TODO Auto-generated catch block\s*e\.printStackTrace\(\);\s*\}/, "checkForMessagesUDP();// receive() blocks until a packet arrives (no sleep: it would delay the queued packets)");
rep("playerData.setNewUpdateNumber(updateNumber);", "playerData.setNewUpdateNumber(updateNumber);\n\t\t\t\t\t\t\t\tplayerData.setRaceTimeMS(readRaceTime(p, data, packet.getLength()));");
rep("\tpublic void stop(){", `	/** size of the position packet of the game (header 8 bytes + 21 bytes of data) */
	private static final int POSITION_PACKET_SIZE = 29;

	/**
	 * Cup mode: race time (ms since the synchronized start) of a position update.
	 * The cup mod appends "R" + u32 race time (ms) to the packet (the official server ignores it). For the other clients
	 * it is estimated with the reception time and half of the ping.
	 *
	 * @return -1 if unknown (no cup round running)
	 */
	private long readRaceTime(Player p, byte[] data, int length) {
		if (server.getGameState().isCupMode() == false || server.getGameState().getRoundRunning() == false
				|| server.getGameState().getCup().isParticipant(p.getID()) == false) {
			return -1;
		}
		if (p.getModVersion() > 0 && length >= POSITION_PACKET_SIZE + 5 && data[POSITION_PACKET_SIZE] == (byte) 0x52) {
			int o = POSITION_PACKET_SIZE + 1;
			return (data[o] & 0xFFL) | ((data[o + 1] & 0xFFL) << 8) | ((data[o + 2] & 0xFFL) << 16) | ((data[o + 3] & 0xFFL) << 24);
		}
		double start = server.getGameState().getCupStartClockMS();
		if (start <= 0) {
			return -1;
		}
		return Math.max(0, Math.round(server.util.ServerClock.nowMS() - start - p.getTimings().getLastPingTime() / 2.0));
	}

	public void stop(){`);
fs.writeFileSync(f, s); console.log("ok");
