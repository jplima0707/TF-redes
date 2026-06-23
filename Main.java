import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception{

            Config conf = new Config();
            String ip = "10.32.160.153";
            int port = 6011;

            Ring ring = new Ring(conf, ip, port);

            // Java não tem recebimento de pacote não bloqueante, então tem que ter uma Thread a parte para receber
            Udp network = new Udp(port, conf.getNome(), ring);
            ring.setSocket(network);
            Thread t = new Thread(() -> {try {
                network.initialize();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }});
            t.start();
            

            // Inicia o anel, se estiver sozinho espera 10s e tenta de novo
            while (!ring.initialize()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            t.interrupt();
            // Configura inSocket e outSocket
            network.setInSocket(ring.getPrevIP());
            network.setOutSocket(ring.getNextIP());

            //Em algum lugar, tem que mandar o heartbeat de 10 em 10 segundos

            //Recebe input do terminal
            String input = "";
            Scanner s = new Scanner(System.in);
            while (true) {
                input = s.nextLine();
                if (input == "quit") {
                    break;
                }
                if (input.indexOf(':') != -1) {
                    System.out.println("':' encontrado na mensagem, ignorando");
                    continue;
                }
                ring.novaMensagemParaEnviar(input);
            }
            s.close();
    }

}
