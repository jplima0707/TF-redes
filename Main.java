import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {

        try {
            DatagramSocket socket = new DatagramSocket(6000);
            socket.setBroadcast(true);

            String pac = Packet.discover("A", Inet4Address.getLocalHost().toString());

            socket.send(
                    new DatagramPacket(pac.getBytes(), pac.getBytes().length, InetAddress.getByName("255.255.255.255"),
                            6000));

            socket.close();

            System.out.println(pac);

            socket.setSoTimeout(1000);

            List<String> teste = new ArrayList<String>();

            

        } catch (Exception ex) {
            System.out.println(ex);
        }

    }

}
