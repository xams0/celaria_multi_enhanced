import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "player/data/PlayerRuntimeUdpData.java", [
[`	private long raceTimeMS = -1;// cup mod: race time (ms since the start of the round) of this position, -1 = unknown`,
 `	private long raceTimeMS = -1;// cup mod: race time (ms since the start of the round) of this position, -1 = unknown
	private int lookYaw = -1;// cup mod: camera of the player (yaw 0..65535 = 0..360 degrees, pitch in 1/100 degree), -1 = unknown
	private int lookPitch;`],
[`		this.raceTimeMS = source.raceTimeMS;`, `		this.raceTimeMS = source.raceTimeMS;
		this.lookYaw = source.lookYaw;
		this.lookPitch = source.lookPitch;`],
[`	public long getRaceTimeMS(){`, `	public int getLookYaw(){
		return lookYaw;
	}

	public int getLookPitch(){
		return lookPitch;
	}

	public void setLook(int yaw, int pitch){
		this.lookYaw = yaw;
		this.lookPitch = pitch;
	}

	public long getRaceTimeMS(){`],
]);
patch(S + "connection/UdpReciever.java", [
[`								playerData.setRaceTimeMS(readRaceTime(p, data, packet.getLength()));`,
 `								playerData.setRaceTimeMS(readRaceTime(p, data, packet.getLength()));
								readLook(p, playerData, data, packet.getLength());`],
[`	/** size of the position packet of the game (header 8 bytes + 21 bytes of data) */`,
 `	/**
	 * Cup mod: camera of the player appended after the race time ("R" + u32 race time + u16 yaw + s16 pitch), used for the
	 * first person spectator view.
	 */
	private void readLook(Player p, PlayerRuntimeUdpData playerData, byte[] data, int length) {
		int o = POSITION_PACKET_SIZE + 5;
		if (p.getModVersion() > 0 && length >= o + 4 && data[POSITION_PACKET_SIZE] == (byte) 0x52) {
			int yaw = (data[o] & 0xFF) | ((data[o + 1] & 0xFF) << 8);
			int pitch = (short) ((data[o + 2] & 0xFF) | ((data[o + 3] & 0xFF) << 8));
			playerData.setLook(yaw, pitch);
		} else {
			playerData.setLook(-1, 0);
		}
	}

	/** size of the position packet of the game (header 8 bytes + 21 bytes of data) */`],
]);
patch(S + "connection/BroadcasterUDP.java", [
[`														packetRaceTimes.add(new long[] { playerKey.intValue(), requestedPlayerData.getRaceTimeMS() });`,
 `														packetRaceTimes.add(new long[] { playerKey.intValue(), requestedPlayerData.getRaceTimeMS(), requestedPlayerData.getLookYaw(), requestedPlayerData.getLookPitch() });`],
[`	 * clients without the mod). Read from the end of the packet: [id u8, race time u32] * count, count u8, "RX".`,
 `	 * clients without the mod). Read from the end of the packet: [id u8, race time u32, yaw u16, pitch s16] * count, count u8,
	 * "SX" (yaw 0xFFFF = unknown).`],
[`			StreamConverter.buffer_write_u32(output, (int) t[1]);
		}
		StreamConverter.buffer_write_u8(output, raceTimes.size());
		StreamConverter.buffer_write_u8(output, 0x52);// R`,
 `			StreamConverter.buffer_write_u32(output, (int) t[1]);
			StreamConverter.buffer_write_u16(output, t[2] < 0 ? 0xFFFF : (int) t[2]);
			StreamConverter.buffer_write_u16(output, (int) t[3]);
		}
		StreamConverter.buffer_write_u8(output, raceTimes.size());
		StreamConverter.buffer_write_u8(output, 0x53);// S`],
]);
