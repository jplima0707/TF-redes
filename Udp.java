import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Udp implements Runnable{

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
        this.inSocket = new DatagramSocket(port, InetAddress.getByName(inIP));
        this.outSocket = new DatagramSocket(port, InetAddress.getByName(outIP));
        this.selfNome = self;
        this.ring = ring;
        this.port = port;
    }
    public Udp(int port, String self, Ring ring) throws Exception
    {
        this.bcSocket = new DatagramSocket(port);
        this.bcSocket.setBroadcast(true);
        this.inSocket = null;
        this.outSocket = null;
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
    public synchronized boolean sendBroadcast(DatagramPacket p)
    {
        try {
            bcSocket.send(p);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
        return true;
    }
    // synchronized evita acesso simultâneo
    public synchronized boolean sendPacket(DatagramPacket p)
    {
        try {
            outSocket.send(p);
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
                switch (recebido.tipo) {
                    case 10:
                        // Pede pra Thread principal enviar o hello e atualizar a topologia 
                        //this.ring.chegouUmDiscover();
                        break;
                    case 20:
                        // Heartbeat
                        //this.ring.chegouUmHello();
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
                            this.ring.chegouMensagem();
                        }
                        else
                        {
                            // É para outra máquina
                            if (recebido.valid) {
                                String toSend = Packet.data(recebido.origem, recebido.origem, recebido.flag, recebido.sequencia, recebido.ttl-1, recebido.mensagem);
                                this.sendPacket(new DatagramPacket(toSend.getBytes(), toSend.getBytes().length));
                            }
                            // Se não é válido fazemos o que?
                        }
                    default:
                        break;
                }

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    public void setInSocket(String inIP) throws Exception {
        this.inSocket = new DatagramSocket(this.port, InetAddress.getByName(inIP));
    }
    public void setOutSocket(String outIP) throws Exception {
        this.outSocket = new DatagramSocket(this.port, InetAddress.getByName(outIP));
    }

}