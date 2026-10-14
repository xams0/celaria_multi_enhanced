package server.player.data;

/**
 * Class which stores Player information
 * 
 * 
 *
 */

public class PlayerRuntimeUdpData {
	

	private double x;
	private double y;
	private double z;
	private float movX;
	private float movY;
	private float movZ;
	private float rotationZ;
	private int animationID;
	private float animationStep;
	

	private int updateNumber;
	private int respawnNumber;
		private long raceTimeMS = -1;// cup mod: race time (ms since the start of the round) of this position, -1 = unknown
	private int lookYaw = -1;// cup mod: camera of the player (yaw 0..65535 = 0..360 degrees, pitch in 1/100 degree), -1 = unknown
	private int lookPitch;
	
	public PlayerRuntimeUdpData(){
		
		x = 0;
		y = 0;
		z = 0;
		//movement
		setMovX(0);
		setMovY(0);
		setMovZ(0);
		rotationZ = 0;
		setAnimationID(0);
		setAnimationStep(0);
		

		updateNumber = 0;
		respawnNumber = 0;
	}
	

	
	public PlayerRuntimeUdpData(PlayerRuntimeUdpData source){
		apply(source);
	}
	
	public void apply(PlayerRuntimeUdpData source){
		this.x = source.x;
		this.y = source.y;
		this.z = source.z;
		this.movX = source.movX;
		this.movY = source.movY;
		this.movZ = source.movZ;
		this.rotationZ = source.rotationZ;
		
		this.animationID = source.animationID;
		this.animationStep = source.animationStep;		
		
		this.respawnNumber = source.respawnNumber;
		this.updateNumber = source.updateNumber;
		this.raceTimeMS = source.raceTimeMS;
		this.lookYaw = source.lookYaw;
		this.lookPitch = source.lookPitch;
	
	}
	
	
		public int getLookYaw(){
		return lookYaw;
	}

	public int getLookPitch(){
		return lookPitch;
	}

	public void setLook(int yaw, int pitch){
		this.lookYaw = yaw;
		this.lookPitch = pitch;
	}

	public long getRaceTimeMS(){
		return raceTimeMS;
	}

	public void setRaceTimeMS(long raceTimeMS){
		this.raceTimeMS = raceTimeMS;
	}

	public int getRespawnNumber(){
		return respawnNumber;
	}
	
	public int getUpdateNumber(){
		
		return updateNumber;
	}
	public void setRespawnNumber(int number){
		this.respawnNumber = number;
	}
	
	public void setNewUpdateNumber(int i){
		this.updateNumber = i;
	}
	

	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
	}
	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
	}
	public double getZ() {
		return z;
	}
	public void setZ(double z) {
		this.z = z;
	}
	public float getRotationZ() {
		return rotationZ;
	}
	public void setRotationZ(float rotationZ) {
		this.rotationZ = rotationZ;
	}

	public float getMovY() {
		return movY;
	}

	public void setMovY(float movY) {
		this.movY = movY;
	}

	public float getMovZ() {
		return movZ;
	}

	public void setMovZ(float movZ) {
		this.movZ = movZ;
	}

	public float getMovX() {
		return movX;
	}

	public void setMovX(float movX) {
		this.movX = movX;
	}

	public int getAnimationID() {
		return animationID;
	}

	public void setAnimationID(int animationID) {
		this.animationID = animationID;
	}

	public float getAnimationStep() {
		return animationStep;
	}

	public void setAnimationStep(float animationStep) {
		this.animationStep = animationStep;
	}


	
}
