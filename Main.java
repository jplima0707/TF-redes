import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception{

            Config conf = new Config();
            String ip = "10.32.160.153";
            int port = 6011;

            DatagramSocket socket = new DatagramSocket(port);
            socket.setBroadcast(true);
            //socket.bind(null);

            String pac = Packet.discover(conf.getNome(), ip);
            System.out.println(Inet4Address.getLocalHost().toString());
            socket.send(
                    new DatagramPacket(pac.getBytes(), pac.getBytes().length, InetAddress.getByName("255.255.255.255"),
                            port));

            System.out.println(pac);

            socket.setSoTimeout(1000);

            long start_time = System.currentTimeMillis();
            
            ArrayList<DatagramPacket> hellos = new ArrayList<>();
            while (System.currentTimeMillis() < start_time + 1000) {
                DatagramPacket dp = new DatagramPacket(new byte[255], 255);
                try {
                    socket.receive(dp);
                } catch (SocketTimeoutException e) {
                    break;
                }
                hellos.add(dp);
            }

            List<Packet> anel = new LinkedList<>();

            // Coisa feia para incluir esse processo no mapa do anel
            Packet self = new Packet(Packet.hello(conf.getNome(), ip).getBytes(),Packet.hello(conf.getNome(), ip).getBytes().length);

            anel.add(self);

            for (DatagramPacket hello : hellos) {
                Packet p = new Packet(hello.getData(), hello.getLength());
                System.out.printf("%d:%s:%s:%d%n",p.tipo,p.origem,p.ipOrigem,p.crc);
                if (p.tipo != 20)
                {
                    System.out.println("Ignorando não Hello");
                    continue;
                }
                // Verifica se não é repetido - também impede nomes repetidos com ips diferentes, mas falha sileciosamente
                if (anel.stream().noneMatch(x -> x.origem == p.origem)) {
                    anel.add(p);
                }
            }
            socket.close();
            // Temos que ordenar por ordem alfabética (testar e ver se funciona como esperado)
            anel.sort((x,y) -> x.origem.compareToIgnoreCase(y.origem));


            // Agora que sabemos o anel, podemos fazer os sockets corretamente pro próximo e anterior do anel
            int selfIndex = anel.indexOf(self);
            Packet next = anel.get(selfIndex+1 > anel.size() ? 0 : selfIndex+1);
            Packet prev = anel.get(selfIndex-1 < 0 ? anel.size() : selfIndex-1);
            
            // Java não tem recebimento de pacote não bloqueante, então tem que ter uma Thread a parte para receber
            Udp network = new Udp(port, prev.origem, next.origem, self);
            network.run();
            
            // Se somos o primeiro do anel temos que criar e manejar o Token (por enquanto só manda o inicial)
            if (anel.indexOf(self) == 0) {
                //outSocket.send(new DatagramPacket(Packet.token().getBytes(), Packet.token().getBytes().length));
                long time = System.currentTimeMillis(); // Para cuidar dos timeouts
            }
            

            //List<String> teste = new ArrayList<String>();
            
    }

}
