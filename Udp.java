import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

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
        try {
            byte[] bytes = p.getBytes();
            for (InetAddress destino : listarEnderecosDeBroadcast()) {
                Main.log(String.format("Enviando broadcast para %s: %s%n", destino.getHostAddress(), p));
                bcSocket.send(new DatagramPacket(bytes, bytes.length, destino, this.port));
            }
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
            outSocket.send(new DatagramPacket(p.getBytes(), p.getBytes().length, InetAddress.getByName(ipDestino.trim()), this.port));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        DatagramPacket p = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                p.setLength(buffer.length);
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
                        if (recebido.destino.equalsIgnoreCase(selfNome)) {
                            this.ring.chegouMensagem(recebido);
                        } else if (recebido.origem.equalsIgnoreCase(selfNome)) {
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

    private Set<InetAddress> listarEnderecosDeBroadcast() throws IOException {
        Set<InetAddress> enderecos = new LinkedHashSet<>();
        enderecos.add(InetAddress.getByName("255.255.255.255"));

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }

            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress broadcast = interfaceAddress.getBroadcast();
                if (broadcast != null) {
                    enderecos.add(broadcast);
                }
            }
        }

        return enderecos;
    }
}
