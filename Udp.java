import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Udp implements Runnable{

    // Não fazer dois sockets diferentes
    public DatagramSocket inSocket;
    public DatagramSocket outSocket;
    private DatagramSocket bcSocket;
    public String selfNome;
    private Ring ring;
    private int port;

    public Udp(int port, String inIP, String outIP, String self, Ring ring) throws Exception
    {
        this.bcSocket = new DatagramSocket(port);
        this.bcSocket.setBroadcast(true);
        this.inSocket = bcSocket;
        this.outSocket = bcSocket;
        this.selfNome = self;
        this.ring = ring;
        this.port = port;
    }
    public Udp(int port, String self, Ring ring) throws Exception
    {
        this.bcSocket = new DatagramSocket(port);
        this.bcSocket.setBroadcast(true);
        this.inSocket = bcSocket;
        this.outSocket = bcSocket;
        this.selfNome = self;
        this.ring = ring;
        this.port = port;
    }

    public void initialize() throws Exception
    {
        DatagramPacket p = new DatagramPacket(new byte[255], 255);
        while (true) {
            bcSocket.receive(p);
            Packet pa = new Packet(p.getData(), p.getLength());
            switch (pa.tipo) {
                case 10:
                    ring.chegouUmDiscover(pa);
                    break;
                case 20:
                    ring.chegouUmHello(pa);
                default:
                    break;
            }
        }
    }
    public synchronized boolean sendBroadcast(String p)
    {
        try {
            bcSocket.send(new DatagramPacket(p.getBytes(), p.getBytes().length, InetAddress.getByName("255.255.255.255"),this.port));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
        return true;
    }
    // synchronized evita acesso simultâneo
    public synchronized boolean sendPacket(String p, String ipDestino)
    {
        try {
            outSocket.send(new DatagramPacket(p.getBytes(), p.getBytes().length,InetAddress.getByName(ipDestino),this.port));
        } catch (IOException e) {
            // TODO Auto-generated catch block
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
                Packet recebido = new Packet(p.getData(),p.getLength());
                System.out.printf("Pacote recebido tipo %d%n",recebido.tipo);
                switch (recebido.tipo) {
                    case 10:
                        // Pede pra Thread principal enviar o hello e atualizar a topologia 
                        this.ring.chegouUmDiscover(recebido);
                        break;
                    case 20:
                        // Heartbeat
                        this.ring.chegouUmHello(recebido);
                        break;
                    case 1000:
                        // Avisa a Thread principal que pode enviar
                        this.ring.chegouToken();
                        break;
                    case 2000:
                        // Analisa e encaminha o pacote pra frente
                        if (recebido.destino == selfNome)
                        {
                            // É pra essa máquina
                            this.ring.chegouMensagem(recebido);
                        }
                        else if (recebido.origem == selfNome)
                        {
                            // É um ACK/NACK de uma mensagem dessa máquina
                            this.ring.chegouResposta(recebido);
                        }
                        else
                        {
                            // É para outra máquina
                            if (recebido.valid) {
                                String toSend = Packet.data(recebido.origem, recebido.origem, recebido.flag, recebido.sequencia, recebido.ttl-1, recebido.mensagem);
                                this.sendPacket(toSend,this.ring.getNextIP());
                            }
                            // Se não é válido ele é descartado, então não fazemos nada
                        }
                    default:
                        break;
                }

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}