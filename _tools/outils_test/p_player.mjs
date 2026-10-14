import { patch } from "./wsrep.mjs";
patch("player/Player.java", [
[`	private java.util.concurrent.atomic.AtomicLong pendingGoalMS = new java.util.concurrent.atomic.AtomicLong(-1);`,
 `	private java.util.concurrent.atomic.AtomicLong pendingGoalMS = new java.util.concurrent.atomic.AtomicLong(-1);
	private java.util.concurrent.atomic.AtomicLong pendingCheckpointMS = new java.util.concurrent.atomic.AtomicLong(-1);`],
[`	public ThreadedQueue<Integer> getRequestedPlayerIDs(){`,
 `	public void setPendingCheckpointMS(long ms){
		pendingCheckpointMS.set(ms);
	}

	/**
	 * @return the precise checkpoint time (ms) sent by the mod for this checkpoint time (ticks), or ticks*10.
	 */
	public long pollCheckpointTimeMS(long ticks){
		long ms = pendingCheckpointMS.getAndSet(-1);
		if(ms >= 0 && ms >= (ticks-1L)*10L && ms <= ticks*10L){
			return ms;
		}
		return ticks*10L;
	}

	public ThreadedQueue<Integer> getRequestedPlayerIDs(){`],
]);
patch("player/PlayerProcessor.java", [
[`				case 205:// cup mod: the client runs the mod (sent with the player metadata, before the UDP check)`,
 `				case 208:// cup mod: observer mode (u8 1 = observer, 0 = player)
					server.cupSetObserver(player, BufferConverter.buffer_read_u8(input) == 1);
					break;

				case 205:// cup mod: the client runs the mod (sent with the player metadata, before the UDP check)`],
[`							int time = BufferConverter.buffer_read_u32(input);
							player.getCheckpointQueue().offerConcurrent(time);`,
 `							int time = BufferConverter.buffer_read_u32(input);
							player.getCheckpointQueue().offerConcurrent(time);
							server.cupCheckpoint(player, player.getCheckpointQueue().sizeConcurrent(), player.pollCheckpointTimeMS(time));`],
[`						case 204:// cup mod: precise time (ms) of the following goal packet`,
 `						case 207:// cup mod: precise time (ms) of the following checkpoint packet
							player.setPendingCheckpointMS(BufferConverter.buffer_read_u32(input) & 0xFFFFFFFFL);
							break;

						case 204:// cup mod: precise time (ms) of the following goal packet`],
]);
