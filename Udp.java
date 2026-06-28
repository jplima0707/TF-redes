import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Udp implements Runnable {

    // Nao fazer dois sockets diferentes (so referencias diferentes pro mesmo socket)
    public DatagramSocket inSocket;
    public DatagramSocket outSocket;
    private DatagramSocket bcSocket;
    public String selfNome;
    private Ring ring;
    private int port;

    public Udp(int port, String inIP, String outIP, String self, Ring ring) throws Exception {
        this(port, self, ring);
    }

    public Udp(int port, String self, Ring ring) throws Exception {
        this.bcSocket = new DatagramSocket(port);
        this.bcSocket.setBroadcast(true);
        this.inSocket = bcSocket;
        this.outSocket = bcSocket;
        this.selfNome = self;
        this.ring = ring;
        this.port = port;
    }

    public synchronized boolean sendBroadcast(String p) {
        Main.log(String.format("Enviando broadcast: %s%n", p));
        try {
            bcSocket.send(new DatagramPacket(p.getBytes(), p.getBytes().length, InetAddress.getByName("255.255.255.255"), this.port));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    // synchronized evita acesso simultaneo
    public synchronized boolean sendPacket(String p, String ipDestino) {
        Main.log(String.format("Enviando pacote: %s para %s%n", p, ipDestino));
        try {
            outSocket.send(new DatagramPacket(p.getBytes(), p.getBytes().length, InetAddress.getByName(ipDestino), this.port));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        DatagramPacket p = new DatagramPacket(new byte[255], 255);
        while (true) {
            try {
                inSocket.receive(p);
                Packet recebido = new Packet(p.getData(), p.getLength());
                Main.log(String.format("Pacote recebido: %s%n", recebido.toString()));

                switch (recebido.tipo) {
                    case 10:
                        this.ring.chegouUmDiscover(recebido);
                        break;
                    case 20:
                        this.ring.chegouUmHello(recebido);
                        break;
                    case 1000:
                        if (recebido.valid) {
                            this.ring.chegouToken();
                        } else {
                            Main.log("Token invalido descartado");
                        }
                        break;
                    case 2000:
                        if (recebido.destino.equals(selfNome)) {
                            this.ring.chegouMensagem(recebido);
                        } else if (recebido.origem.equals(selfNome)) {
                            this.ring.chegouResposta(recebido);
                        } else {
                            this.ring.encaminharMensagem(recebido);
                        }
                        break;
                    default:
                        Main.log("Pacote com tipo desconhecido descartado");
                        break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
