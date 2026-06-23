import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Udp implements Runnable{

    public DatagramSocket inSocket;
    public DatagramSocket outSocket;
    public String selfNome;
    private Ring ring;

    public Udp(int port, String inIP, String outIP, String self, Ring ring) throws Exception
    {
        this.inSocket = new DatagramSocket(port, InetAddress.getByName(inIP));
        this.outSocket = new DatagramSocket(port, InetAddress.getByName(outIP));
        this.selfNome = self;
        this.ring = ring;
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
                        this.ring.chegouUmHello();
                        break;
                    case 20:
                        // Heartbeat
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


}