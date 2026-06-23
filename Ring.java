import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


// Se aqui vai ser mantido o estado do anel, quase todos métodos vão ter que ser syncronized
public class Ring implements Runnable{
    private String nomeDaMaquina;
    private int delayDoToken;
    private int timeoutToken;
    private int tempoMinimoToken;
    private String selfIP;
    private int port;
    private ArrayList<Packet> hellos = new ArrayList<>();

    // Parâmetros calculados na inicialização
    private String nextIP;
    private String prevIP; 
    private List<Packet> anel;
    private Udp socket;

    // Parâmetros da execução do loop
    private List<String> listaMensagens;


    public Ring(Config conf, String ip, int port)
    {
        this.nomeDaMaquina = conf.getNome();
        this.delayDoToken = conf.getDelayToken();
        this.timeoutToken = conf.getTimeoutToken();
        this.tempoMinimoToken = conf.getTempoMinimoToken();
        this.selfIP = ip;
        this.port = port;
    }

    public synchronized boolean initialize() throws Exception
    {
        DatagramSocket socket = new DatagramSocket(port);
        socket.setBroadcast(true);
        //socket.bind(null);

        String pac = Packet.discover(this.nomeDaMaquina, this.selfIP);
        System.out.println(Inet4Address.getLocalHost().toString());
        socket.send(
                new DatagramPacket(pac.getBytes(), pac.getBytes().length, InetAddress.getByName("255.255.255.255"),
                        port));
        socket.close();
        System.out.println(pac);

        long start_time = System.currentTimeMillis();
        
        this.anel = new LinkedList<>();

        // Coisa feia para incluir esse processo no mapa do anel
        Packet self = new Packet(Packet.hello(this.nomeDaMaquina, this.selfIP).getBytes(),Packet.hello(this.nomeDaMaquina, this.selfIP).getBytes().length);

        this.anel.add(self);

        for (Packet p : this.hellos) {
            System.out.printf("Processando: %s%n",p);
            if (p.tipo != 20)
            {
                System.out.println("Ignorando não Hello");
                continue;
            }
            // Verifica se não é repetido - também impede nomes repetidos com ips diferentes, mas falha sileciosamente
            if (this.anel.stream().noneMatch(x -> x.origem == p.origem)) {
                this.anel.add(p);
            }
            else System.out.printf("Nome repetido no anel: %s%n",p.toString());
        }
        
        // Temos que ordenar por ordem alfabética (testar e ver se funciona como esperado)
        this.anel.sort((x,y) -> x.origem.compareToIgnoreCase(y.origem));

        if (this.anel.size() == 1) {
            // Estamos sozinhos
            return false;
        }

        // Agora que sabemos o anel, podemos fazer os sockets corretamente pro próximo e anterior do anel
        int selfIndex = this.anel.indexOf(self);
        Packet next = this.anel.get(selfIndex+1 > this.anel.size() ? 0 : selfIndex+1);
        Packet prev = this.anel.get(selfIndex-1 < 0 ? this.anel.size() : selfIndex-1);
        
        this.nextIP = next.ipOrigem;
        this.prevIP = prev.ipOrigem;


        return true;
    }

    @Override
    public void run() {
        // Cuida do token (por enquanto só envia de início, mas tem que cuidar dos timeouts tbm)
        socket.sendPacket(new DatagramPacket(Packet.token().getBytes(), Packet.token().getBytes().length));
    }

    public void chegouUmHello(Packet p) {
        this.hellos.add(p);
    }

    public void chegouToken() {}

    public void chegouMensagem() {}

    public void chegouUmDiscover(Packet p) {
        this.socket.sendBroadcast(new DatagramPacket(Packet.hello(nomeDaMaquina, selfIP).getBytes(), Packet.hello(nomeDaMaquina, selfIP).getBytes().length));
    }

    public void novaMensagemParaEnviar(String mensagem) {}

    public String getPrevIP() {
        return prevIP;
    }
    public String getNextIP() {
        return nextIP;
    }
    public boolean isFirst()
    {
        Packet self = this.anel.stream().filter(x -> x.origem == this.nomeDaMaquina).findFirst().get();
        return this.anel.indexOf(self) == 0;
    }

    public void setSocket(Udp socket) {
        this.socket = socket;
    }
}
