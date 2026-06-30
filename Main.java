import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Main {

    private static PrintWriter outputLog;

    public static void main(String[] args) throws Exception{

            Main.outputLog = new PrintWriter(new FileWriter("out.log", false), true);

            Config conf = Config.lerArquivo("config.txt");
            String ip = conf.getIp();
            int port = 6011;

            Ring ring = new Ring(conf, ip, port);

            Udp network = new Udp(port, conf.getNome(), ring);
            ring.setSocket(network);
            Thread t = new Thread(() -> {try {
                network.run();
            } catch (Exception e) {
                e.printStackTrace();
            }});
            t.start();
            

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

}
