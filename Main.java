import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Scanner;

public class Main {

    private static PrintWriter outputLog;

    public static void main(String[] args) throws Exception{

            Main.outputLog = new PrintWriter(new FileWriter("out.log", false), true);

            Config conf = args.length > 0 ? new Config(args[0]) : new Config();
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
            String ip = args.length > 2 ? args[2].trim() : descobrirIpLocal();
            System.out.printf("Iniciando %s em %s:%d%n", conf.getNome(), ip, port);

            Ring ring = new Ring(conf, ip, port);

            // Java não tem recebimento de pacote não bloqueante, então tem que ter uma Thread a parte para receber
            Udp network = new Udp(port, conf.getNome(), ring);
            ring.setSocket(network);
            Thread t = new Thread(() -> {try {
                network.run();
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
            //t.interrupt();
            Thread token = new Thread(() -> {ring.run();});
            token.start();
            ring.startHeartbeat();

            //Recebe input do terminal
            String input = "";
            Scanner s = new Scanner(System.in);
            while (true) {
                System.out.println("Escreva uma Mensagem:");
                input = s.nextLine();
                if (input.equalsIgnoreCase("quit")) {
                    break;
                }
                if (input.indexOf(':') != -1) {
                    System.out.println("':' encontrado na mensagem, ignorando");
                    continue;
                }
                System.out.printf("Escreva o destino da mensagem: (Hosts disponíveis: %s)%n",ring.getCurrentHosts());
                String destino = s.nextLine();
                if(!ring.novaMensagemParaEnviar(input,destino))
                {
                    System.out.println("Destino inexistente");
                }
                else System.out.println("Mensagem adicionada");
            }
            s.close();
    }

    public synchronized static void log(String message) {
        if (outputLog != null) {
            outputLog.println("[" + System.currentTimeMillis() + "] " + message);
        }
    }

    private static String descobrirIpLocal() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!interfaceValida(networkInterface)) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        return InetAddress.getLocalHost().getHostAddress();
    }

    private static boolean interfaceValida(NetworkInterface networkInterface) throws SocketException {
        return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual();
    }
}
