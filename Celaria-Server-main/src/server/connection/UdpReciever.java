package server.connection;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;

import debug.DebugIO;
import server.ServerCore;
import server.player.Player;
import server.player.data.PlayerRuntimeUdpData;
import server.util.ServerClock;
import server.util.StreamConverter;


/**
 * Class which recieves UDP packes from the game clients.
 * The sender of ths message can be identified by the playerID which is included in each UDP packet.
 * 
 * 
 *
 */

public class UdpReciever implements Runnable{

	private ServerCore server;
	boolean running;



	public UdpReciever(ServerCore s){
		server = s;
		running = true;

	}


	@Override
	public void run() {
		while(running){//Start a do while loop while run is true
			DebugIO.printRunning("UDP Reciever");

			checkForMessagesUDP();// receive() blocks until a packet arrives (no sleep: it would delay the queued packets)
		}
	}

	public void checkForMessagesUDP(){		
		try {
			DatagramPacket packet = new DatagramPacket( new byte[1024], 1024 );

			server.getServerSocketUDP().receive( packet );

			/*InetAddress address = packet.getAddress();
		      int         port    = packet.getPort();
		      int         len     = packet.getLength();
		      byte[]      data    = packet.getData();*/
			byte[] data = packet.getData();


			ByteArrayInputStream byteOut = new ByteArrayInputStream(data);
			DataInputStream input = new DataInputStream(byteOut);

			int packetID = -1;
			int player_id = -1;
			int player_udpKey = -1;

			boolean isValidPacket = false;
			//check header
			int c1 = StreamConverter.buffer_read_u8(input);//C
			int c2 = StreamConverter.buffer_read_u8(input);//C
			int c3 = StreamConverter.buffer_read_u8(input);//P
			int c4 = StreamConverter.buffer_read_u8(input);//U

			if(c1 == 67 && c2 == 67 && c3 == 80 && c4 == 85){

				player_id = StreamConverter.buffer_read_u8(input);
				packetID = StreamConverter.buffer_read_u8(input);
				player_udpKey = StreamConverter.buffer_read_u16(input);

				isValidPacket = true;

			}else{

				DebugIO.println("WRONG HEADER UDP! "+c1+"/"+c2+"/"+c3+"/"+c4);

			}



			boolean packetFromValidSource = false;

			if(isValidPacket){



				packet.getAddress();
				packet.getPort();

				//TODO: change hashmap for own implementation for possible performanceimprovements during reads!
				if(server.getPlayers().containsKey(new Integer(player_id))){//check if the player_id exists in the database

					//update the udp internetadress and port if they don't match

					Player p = server.getPlayers().get(new Integer(player_id));
					//check if player has set InternetAddress
					if(p.getInternetAddress() != null){
						//if the player has an InternetAddress
						if(player_udpKey == p.getUdpKey()){//check the keys
							packetFromValidSource = true;
						}


					}else{
						//if the player doesn't have an internetAddress
						if(player_udpKey == p.getUdpKey()){//check keys
							p.setInternetAddress(packet.getAddress());//set the adress of the new player
							packetFromValidSource = true;
						}else{
							DebugIO.println("WRONG KEY: "+player_udpKey+"/"+p.getUdpKey());
						}
					}


					if(packetFromValidSource == true){//Proceed only if the packet was validated from the right player!

						if(p.getUdpPort() != packet.getPort()){
							p.setUdpPort(packet.getPort());
						}
						//------------------------------------------

						switch(packetID){
						case 1:
							int updateNumber = StreamConverter.buffer_read_u8(input);
							int respawnNumber = StreamConverter.buffer_read_u8(input);


							double x = StreamConverter.buffer_read_f32(input);
							double y = StreamConverter.buffer_read_f32(input);
							double z = StreamConverter.buffer_read_f32(input);

							float nx =  ((((float)(StreamConverter.buffer_read_u8(input)))/255.0f)*2.0f)-1.0f;
							float ny =  ((((float)(StreamConverter.buffer_read_u8(input)))/255.0f)*2.0f)-1.0f;
							float nz =  ((((float)(StreamConverter.buffer_read_u8(input)))/255.0f)*2.0f)-1.0f;
							
							float nLen =  (((float)(StreamConverter.buffer_read_u8(input)))/255.0f)*3.0f;
							
							float movX = nx*nLen;
							float movY = ny*nLen;
							float movZ = nz*nLen;

							float rotationZ = (StreamConverter.buffer_read_u8(input)/255f)*360f;

							int animationID = StreamConverter.buffer_read_u8(input);


							float animationStep =(float)(StreamConverter.buffer_read_u8(input))/255f;// /65535f
							
							PlayerRuntimeUdpData playerData = p.getPlayerDataCopy();
							
							int lastUpdateNumber = playerData.getUpdateNumber();
							
							boolean applyRegardless = false;
							if((updateNumber>>7) != (lastUpdateNumber>>7)) {
								applyRegardless = true;
							}
							
							if(lastUpdateNumber<updateNumber || applyRegardless) {


								playerData.setRespawnNumber(respawnNumber);
								playerData.setX(x);
								playerData.setY(y);
								playerData.setZ(z);
								playerData.setMovX(movX);
								playerData.setMovY(movY);
								playerData.setMovZ(movZ);
								playerData.setRotationZ(rotationZ);
								playerData.setAnimationID(animationID);
								playerData.setAnimationStep(animationStep);
								playerData.setNewUpdateNumber(updateNumber);
								playerData.setRaceTimeMS(readRaceTime(p, data, packet.getLength()));
								readLook(p, playerData, data, packet.getLength());
								if (playerData.getRaceTimeMS() >= 0 && server.getGameState().isCupMode() && ServerCore.isBot(p) == false) {// run recorded for the bots
									server.getGameState().getGhosts().addSample(p.getID(), playerData.getRaceTimeMS(), x, y, z, movX, movY, movZ,
											rotationZ, animationID, animationStep, respawnNumber);
								}
								
								p.applyPlayerData(playerData);

								if (server.getGameState().isCupMode() && p.getModVersion() < 2 && server.getGameState().getCup().playerStateUpdate(p.getID(), respawnNumber, x, y, z)) {
									server.cupCompleteResetDetected(p);// complete reset detected (respawn on the start position, client without the mod)
								}
																
							}else{

								DebugIO.println("WRONG UDP update packet");
							}


							break;


						case 10:
							//if testpacket recieved, set the variable to true.
							//now the server hast to send a testpacket back (happens in the UDPbroadcaster)
							p.getPlayerServerData().setUdpTestPacketRecieved(true);
							DebugIO.println("TESTPACKET RECIEVED");
							break;

						case 210:
							
							long currentTime = System.currentTimeMillis();
							
							int pingPacketID = StreamConverter.buffer_read_u8(input);
							long lastTime = p.getNetworkCounter().getPingTime(pingPacketID);


							long diff = (currentTime-lastTime);
							if(diff < 0){diff = 0;}
							diff = Math.min(diff, 999);
							p.getTimings().setLastUdpPingTime((short)diff);
							
							break;

						}

						//RESET UDP TIMEOUT
						p.getTimings().resetUdpTimeoutCounter();
					}

				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			DebugIO.errPrintln("UDP Reciever closed");
			stop();

		}
	}

		/**
	 * Cup mod: camera of the player appended after the race time ("R" + u32 race time + u16 yaw + s16 pitch), used for the
	 * first person spectator view.
	 */
	private void readLook(Player p, PlayerRuntimeUdpData playerData, byte[] data, int length) {
		int o = POSITION_PACKET_SIZE + 5;
		if (p.getModVersion() > 0 && length >= o + 4 && (data[POSITION_PACKET_SIZE] == (byte) 0x52 || data[POSITION_PACKET_SIZE] == (byte) 0x4E)) {
			int yaw = (data[o] & 0xFF) | ((data[o + 1] & 0xFF) << 8);
			int pitch = (short) ((data[o + 2] & 0xFF) | ((data[o + 3] & 0xFF) << 8));
			playerData.setLook(yaw, pitch);
		} else {
			playerData.setLook(-1, 0);
		}
	}

	/** size of the position packet of the game (header 8 bytes + 21 bytes of data) */
	private static final int POSITION_PACKET_SIZE = 29;

	/**
	 * Cup mode: race time (ms since the synchronized start) of a position update.
	 * The cup mod appends "R" + u32 race time (ms) to the packet (the official server ignores it). For the other clients
	 * it is estimated with the reception time and half of the ping.
	 *
	 * @return -1 if unknown (no cup round running)
	 */
	private long readRaceTime(Player p, byte[] data, int length) {
		if (server.getGameState().isCupMode() == false) {
			// time attack: timer of the run of the player (mod), for the spectator view of the other clients
			if (p.getModVersion() > 0 && length >= POSITION_PACKET_SIZE + 5 && data[POSITION_PACKET_SIZE] == (byte) 0x52) {
				int o = POSITION_PACKET_SIZE + 1;
				return (data[o] & 0xFFL) | ((data[o + 1] & 0xFFL) << 8) | ((data[o + 2] & 0xFFL) << 16) | ((data[o + 3] & 0xFFL) << 24);
			}
			return -1;
		}
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
		return Math.max(0, Math.round(ServerClock.nowMS() - start - p.getTimings().getLastPingTime() / 2.0));
	}

	public void stop(){
		running = false;
	}

}
