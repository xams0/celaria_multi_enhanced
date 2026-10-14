import io.ConfFileReader; import server.ServerConfig;
public class ConfCheck { public static void main(String[] a){ ConfFileReader r=new ConfFileReader(); System.out.println("loaded="+r.loadFile(a[0])); ServerConfig c=new ServerConfig(); c.apply(r);
 System.out.println("cup="+c.isCupMode()+" limit="+c.getCupSettings().pointsLimit+" rounds="+c.getCupSettings().roundsPerMap+" winners="+c.getCupSettings().nbWinners+" rep="+c.getCupSettings().repartitionToString()+" timeout="+c.getCupSettings().finishTimeoutSec+" roundLimit="+c.getCupSettings().roundTimeLimitSec+" port="+c.getTcpPort()); } }
