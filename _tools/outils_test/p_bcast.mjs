import fs from "node:fs";
import { patch } from "./wsrep.mjs";
const E = []; const rep = (o, n, all) => E.push([o, n, all]);
// list of the race times of the players written in the current packet
rep(`						int packetNumber = 1;
						boolean headerInitialised = false;`, `						int packetNumber = 1;
						boolean headerInitialised = false;
						boolean raceTimes = player.getModVersion() > 0 && server.getGameState().isCupMode();
						java.util.List<long[]> packetRaceTimes = new java.util.ArrayList<long[]>();`);
rep(`													StreamConverter.buffer_write_u8(output, (int)(requestedPlayerData.getAnimationStep()*255f));`, `													StreamConverter.buffer_write_u8(output, (int)(requestedPlayerData.getAnimationStep()*255f));
													if (raceTimes && requestedPlayerData.getRaceTimeMS() >= 0) {
														packetRaceTimes.add(new long[] { playerKey.intValue(), requestedPlayerData.getRaceTimeMS() });
													}`);
// both sends: trailer after the terminator
rep(`StreamConverter.buffer_write_u8(output,0);//no additional information is available (client stops reading this packet)`, `StreamConverter.buffer_write_u8(output,0);//no additional information is available (client stops reading this packet)
writeRaceTimes(output, packetRaceTimes);`, true);
rep(`	public void sendTestPacket(Player player,InetAddress address, int udpPort){`, `	/**
	 * Cup mod: race time of every position of the packet, after the end of the data read by the game (ignored by the
	 * clients without the mod). Read from the end of the packet: [id u8, race time u32] * count, count u8, "RX".
	 */
	private static void writeRaceTimes(DataOutputStream output, java.util.List<long[]> raceTimes) throws IOException {
		if (raceTimes.isEmpty()) {
			return;
		}
		for (long[] t : raceTimes) {
			StreamConverter.buffer_write_u8(output, (int) t[0]);
			StreamConverter.buffer_write_u32(output, (int) t[1]);
		}
		StreamConverter.buffer_write_u8(output, raceTimes.size());
		StreamConverter.buffer_write_u8(output, 0x52);// R
		StreamConverter.buffer_write_u8(output, 0x58);// X
		raceTimes.clear();
	}

	public void sendTestPacket(Player player,InetAddress address, int udpPort){`);
patch("connection/BroadcasterUDP.java", E);
