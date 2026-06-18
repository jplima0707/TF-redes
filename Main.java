import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Main {

    public static void main(String[] args) throws Exception{

            DatagramSocket socket = new DatagramSocket(6011);
            socket.setBroadcast(true);
            //socket.bind(null);

            String pac = Packet.discover("A", "10.32.160.153");
            System.out.println(Inet4Address.getLocalHost().toString());
            socket.send(
                    new DatagramPacket(pac.getBytes(), pac.getBytes().length, InetAddress.getByName("255.255.255.255"),
                            6011));

            System.out.println(pac);

            socket.setSoTimeout(1000);

            long start_time = System.currentTimeMillis();

            
            ArrayList<DatagramPacket> hellos = new ArrayList<>();
            while (System.currentTimeMillis() < start_time + 1000) {
                DatagramPacket dp = new DatagramPacket(new byte[255], 255);
                try {
                    socket.receive(dp);
                } catch (SocketTimeoutException e) {
                    // TODO: handle exception
                    break;
                }
                hellos.add(dp);
            }

            for (DatagramPacket hello : hellos) {
                Packet p = new Packet(hello.getData(), hello.getLength());
                System.out.printf("%d:%s:%s:%d%n",p.tipo,p.origem,p.ipOrigem,p.crc);
                if (p.tipo != 20)
                {
                    System.out.println("Ignorando não Hello");
                    continue;
                }
            }


            socket.close();

            List<String> teste = new ArrayList<String>();

    }

}
