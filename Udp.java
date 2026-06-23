import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Udp implements Runnable{

    public DatagramSocket inSocket;
    public DatagramSocket outSocket;
    public Packet self;

    public Udp(int port, String inIP, String outIP, Packet self) throws Exception
    {
        this.inSocket = new DatagramSocket(port, InetAddress.getByName(inIP));
        this.outSocket = new DatagramSocket(port, InetAddress.getByName(outIP));
        this.self = self;
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
                        break;
                    case 20:
                        // Ignora?
                        break;
                    case 1000:
                        // Avisa a Thread principal que pode enviar
                        break;
                    case 2000:
                        // Analisa e encaminha o pacote pra frente
                        if (recebido.destino == self.destino)
                        {
                            // É pra essa máquina
                        }
                        else
                        {
                            // É para outra máquina
                            if (recebido.valid) {
                                
                            }
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