import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private List<Mensagem> listaMensagens;
    private HashMap<String,Integer> proximaMensagemEsperada;
    private HashMap<String,Long> heartBeats;


    public Ring(Config conf, String ip, int port)
    {
        this.nomeDaMaquina = conf.getNome();
        this.delayDoToken = conf.getDelayToken();
        this.timeoutToken = conf.getTimeoutToken();
        this.tempoMinimoToken = conf.getTempoMinimoToken();
        this.selfIP = ip;
        this.port = port;
        this.listaMensagens = new ArrayList<>();
        this.anel = new LinkedList<>();
        this.proximaMensagemEsperada = new HashMap<>();
        this.heartBeats = new HashMap<>();
    }

    public synchronized boolean initialize() throws Exception
    {
        this.hellos = new ArrayList<>();
        Packet p = new Packet();
        p.tipo = 20;
        p.origem = nomeDaMaquina;
        p.ipOrigem = selfIP;
        this.hellos.add(p);
        String pac = Packet.discover(this.nomeDaMaquina, this.selfIP);
        this.socket.sendBroadcast(pac);
        System.out.println(pac);

        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + 1000) {
            
        }

        atualizarTopologia();

        if (this.anel.size() == 1) {
            // Estamos sozinhos =(
            return false;
        }


        return true;
    }

    @Override
    public void run() {
        // Cuida do token (por enquanto só envia de início, mas tem que cuidar dos timeouts tbm)
        this.socket.sendPacket(Packet.token(),this.nextIP);
    }

    public void chegouUmHello(Packet p) {
        System.out.println("Chegou um hello");
        // Pode ser um heartbeat ou uma resposta a um Discover (durante execução)
        hellos.add(p);
        heartBeats.put(p.origem, System.currentTimeMillis());
    }

    public void chegouToken() {
        System.out.println("Chegou o token");
        try {
            Thread.sleep(this.delayDoToken);
        } catch (InterruptedException e) {}
        if (this.listaMensagens.isEmpty()) {

            //Só passa o token pra frente depois de esperar o delay do Token
            this.socket.sendPacket(Packet.token(),this.nextIP);
            return;
        }
        // Começamos a mandar uma mensagem
        Mensagem m = this.listaMensagens.getFirst();
        this.socket.sendPacket(Packet.data(this.nomeDaMaquina, m.destino, "maquinainexistente", m.indice, this.anel.size()*2, m.texto), nextIP);
        // Agora temos que espera o ACK/NACK ou timeout, mas vai ser tratado nas outras funções
    }

    public void chegouMensagem(Packet p) {
        System.out.println("Chegou uma mensagem");
        //  Se chegou aqui sabemos que é destinado pra essa máquina
        if (!p.valid) {
            // Se inválido, marcar a flag como NAK, recomputar o CRC e reenviar.
            this.socket.sendPacket(Packet.data(p.origem,p.destino,"NAK",p.sequencia,this.anel.size()*2,p.mensagem),
                                   this.nextIP);
        }
        // verificar o número de sequência. Cada máquina mantém o próximo número de sequência esperado para cada origem:
        if (p.sequencia == proximaMensagemEsperada.get(p.origem)) {
            //Se for o esperado: imprimir o apelido da origem e a mensagem, avançar o contador esperado, marcar flag como ACK.
            System.out.printf("Mensagem de %s: %s%n",p.origem,p.mensagem);
            proximaMensagemEsperada.put(p.origem, proximaMensagemEsperada.get(p.origem)+1);
        }
        //Se o número já foi recebido (duplicata): descartar o conteúdo, responder com ACK.
        this.socket.sendPacket(Packet.data(p.origem,p.destino,"ACK",p.sequencia,this.anel.size()*2,p.mensagem),
                                   this.nextIP);        
    }
    
    public void chegouResposta(Packet recebido) {
        System.out.println("Chegou uma resposta");
        if (!recebido.valid || recebido.flag == "NAK") {
            // a entrega falhou. Exibir mensagem na tela. Manter a mensagem na fila com o mesmo número de sequência e retransmitir na próxima passagem do token (encaminhar o token agora).
            System.out.println("Entrega falha ou ACK corrompido");
        }
        else if (recebido.flag == "maquinainexistente") {
            // a máquina destino não existe ou está inativa. Exibir mensagem na tela, retirar a mensagem da fila, encaminhar o token.
            System.out.println("Máquina inexistente");
            this.listaMensagens.remove(0);
            
        }
        else if (recebido.flag == "ACK") {
            // exibir mensagem na tela, retirar a mensagem da fila, encaminhar o token para o sucessor.
            System.out.printf("Mensagem enviada para %s: %s%n",recebido.destino,recebido.mensagem);
        }
        else 
        {
            System.out.println("Algo está muito errado");
        }
        this.socket.sendPacket(Packet.token(),this.nextIP);
    }

    public void chegouUmDiscover(Packet p) {
        System.out.println("Chegou um discover");
        this.socket.sendBroadcast(Packet.hello(nomeDaMaquina, selfIP));
        // Tem que reconstruir a topologia incluindo essa nova máquina

        this.hellos.add(p);
        atualizarTopologia();
    }

    public void atualizarTopologia()
    {
        this.anel = new ArrayList<>();

        for (Packet p : this.hellos) {
            System.out.printf("Processando: %s%n",p);
            if (p.tipo != 20)
            {
                System.out.println("Ignorando não Hello");
                continue;
            }
            // Verifica se não é repetido - também impede nomes repetidos com ips diferentes, mas falha sileciosamente
            if (this.anel.stream().noneMatch(x -> x.origem.equals(p.origem))) {
                this.anel.add(p);
            }
            else System.out.printf("Nome repetido no anel: %s%n",p.toString());
        }
        System.out.printf("Hosts encontrados: %d%n",this.anel.size());
        // Temos que ordenar por ordem alfabética (testar e ver se funciona como esperado)
        this.anel.sort((x,y) -> x.origem.compareToIgnoreCase(y.origem));

        for (Packet packet : anel) {
            // Pode dar problema se um host com nome X sair e outro com o mesmo nome X entrar depois
            System.out.printf("%s -> ",packet.origem);
            proximaMensagemEsperada.putIfAbsent(packet.origem, 0);
            heartBeats.putIfAbsent(packet.origem, System.currentTimeMillis());
        }
        System.out.println();

        // Agora que sabemos o anel, podemos fazer os sockets corretamente pro próximo e anterior do anel
        Packet self = this.anel.stream().filter(x -> x.origem == this.nomeDaMaquina).findFirst().get();
        int selfIndex = this.anel.indexOf(self);
        Packet next = this.anel.get(selfIndex+1 >= this.anel.size() ? 0 : selfIndex+1);
        Packet prev = this.anel.get(selfIndex-1 < 0 ? this.anel.size() - 1 : selfIndex-1);
        
        this.nextIP = next.ipOrigem;
        this.prevIP = prev.ipOrigem;
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
