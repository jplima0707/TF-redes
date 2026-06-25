import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Main {

    private static PrintWriter outputLog;

    public static void main(String[] args) throws Exception{

            Main.outputLog = new PrintWriter(new FileWriter("out.log", false), true);

            Config conf = new Config();
            String ip = "10.32.162.226";
            int port = 6011;

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
            Thread token;
            
            if (ring.isFirst()) {
                token = new Thread(() -> {ring.run();});
                token.start();
            }
            
            //Em algum lugar, tem que mandar o heartbeat de 10 em 10 segundos

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
