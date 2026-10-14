import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "player/PlayerServerState.java", [
[`	private AtomicLong firstLoadDurationMS;//time the client needed to load the first map (0 = unknown)`,
 `	private AtomicLong firstLoadDurationMS;//time the client needed to load the first map (0 = unknown)
	private AtomicBoolean modReady = new AtomicBoolean(false);//cup mod: the map is loaded and displayed (fade finished)
	private AtomicBoolean modReadySupported = new AtomicBoolean(false);//the mod of the client sends the ready message`],
[`		mapLoaded.set(false);
		sendNewMapInit.set(true);`, `		mapLoaded.set(false);
		modReady.set(false);
		sendNewMapInit.set(true);`],
[`	public boolean isReadyForCupStart(long now){
		if(mapSended.get() == false) {
			return false;
		}`, `	/** cup mod: the client finished loading the map and its fade in (the countdown of the game is displayed) */
	public void setModReady(){
		modReadySupported.set(true);
		modReady.set(true);
	}

	public boolean isReadyForCupStart(long now){
		if(mapSended.get() == false) {
			return false;
		}
		if(modReadySupported.get()) {
			return modReady.get();// exact (the server doesn't wait for ever: maximum wait of the cup start)
		}`],
]);
patch(S + "player/PlayerProcessor.java", [
[`				case 208:// cup mod: observer mode (u8 1 = observer, 0 = player)`, `				case 209:// cup mod: the map is loaded and displayed
					BufferConverter.buffer_read_u8(input);
					player.getPlayerServerData().setModReady();
					break;

				case 208:// cup mod: observer mode (u8 1 = observer, 0 = player)`],
]);
