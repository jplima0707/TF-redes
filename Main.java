import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception{

            Config conf = new Config();
            String ip = "192.168.0.74";
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
