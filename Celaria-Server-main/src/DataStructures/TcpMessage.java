package DataStructures;

import java.nio.ByteBuffer;

public class TcpMessage {

	private int messageID;
	private ByteBuffer buffer;
	private long creationTimeMS;//incoming messages are created by the TCP reader as soon as they are recieved (used by the cup mode to rank finish times precisely)
	private double creationClockMS;//same moment on the high resolution clock of the server (clock synchronisation of the cup mod)
	public TcpMessage(int messageID,ByteBuffer buffer){
		this.messageID = messageID;
		this.buffer = buffer;
		this.creationTimeMS = System.currentTimeMillis();
		this.creationClockMS = server.util.ServerClock.nowMS();
	}


	public int getID(){
		return messageID;
	}

	public ByteBuffer getBuffer(){
		return buffer;
	}

	public double getCreationClockMS(){
		return creationClockMS;
	}

	public long getCreationTimeMS(){
		return creationTimeMS;
	}
}
