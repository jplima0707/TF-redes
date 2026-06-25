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
    private Thread timeout;
    private long lastTokenTime;
    private volatile boolean duplicata = false;


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
        Main.log("Começando inicializacao");
        //this.hellos = new ArrayList<>();
        Packet p = new Packet();
        p.tipo = 20;
        p.origem = nomeDaMaquina;
        p.ipOrigem = selfIP;
        this.hellos.add(p);
        String pac = Packet.discover(this.nomeDaMaquina, this.selfIP);
        this.socket.sendBroadcast(pac);

        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + 1000) {
            
        }
        Main.log(String.format("Espera inicial terminada%n"));
        atualizarTopologia();

        if (this.anel.size() == 1) {
            // Estamos sozinhos =(
            return false;
        }

        return true;
    }

    public synchronized void startHeartbeat()
    {
        new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {}
            Heatbeat();
        }).start();
    }

    private synchronized void Heatbeat()
    {
        this.socket.sendBroadcast(Packet.hello(nomeDaMaquina, selfIP));

        ArrayList<Packet> toRemove = new ArrayList<>();

        for (Packet packet : anel) {
            if (heartBeats.get(packet.origem) > System.currentTimeMillis() - 30000) {
                // Está morto
                Main.log(String.format("Host removido por inatividade: %s%n", packet.origem));
                toRemove.add(packet);
            }
        }
        for (Packet packet : toRemove) {
            this.anel.remove(packet);
        }
        if (toRemove.size() > 0) {
            atualizarTopologia();
        }

        new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {}
            Heatbeat();
        }).start();
    }
    @Override
    public void run() {
        // Cuida do token (por enquanto só envia de início, mas tem que cuidar dos timeouts tbm)
        Main.log("Iniciando Token");
        this.socket.sendPacket(Packet.token(),this.nextIP);
        
        timeout = new Thread(() -> {
            try {
                Thread.sleep(this.timeoutToken);
            } catch (InterruptedException e) { }
            timeoutToken();
        });
        timeout.start();
    }

    private synchronized void timeoutToken()
    {
        if (this.lastTokenTime + this.timeoutToken < System.currentTimeMillis()) {
            Main.log("TIMEOUT do Token, enviando novo");
            this.socket.sendPacket(Packet.token(),this.nextIP);
        }
        if (this.lastTokenTime + this.tempoMinimoToken > System.currentTimeMillis()) {
            Main.log("Token Duplicado, removendo");
            this.duplicata = true;
        }
        timeout = new Thread(() -> {
            try {
                Thread.sleep(this.timeoutToken);
            } catch (InterruptedException e) { }
            timeoutToken();
        });
        timeout.start();
    }

    public synchronized void chegouUmHello(Packet p) {
        Main.log("Chegou um hello");
        // Pode ser um heartbeat ou uma resposta a um Discover (durante execução)
        hellos.add(p);
        heartBeats.put(p.origem, System.currentTimeMillis());
    }

    public synchronized void chegouToken() {
        Main.log("Chegou o token");
        this.lastTokenTime = System.currentTimeMillis();
        if (timeout != null)
        {
            timeout.interrupt();
            timeout = new Thread(() -> {
                try {
                    Thread.sleep(this.timeoutToken);
                } catch (InterruptedException e) { }
                timeoutToken();
            });
            timeout.start();
        }
        try {
            Thread.sleep(this.delayDoToken);
        } catch (InterruptedException e) {}
        if (this.listaMensagens.isEmpty()) {

            //Só passa o token pra frente depois de esperar o delay do Token
            if (this.duplicata) {
                this.duplicata = false;
                return;
            }
            this.socket.sendPacket(Packet.token(),this.nextIP);
            return;
        }
        // Começamos a mandar uma mensagem
        Mensagem m = this.listaMensagens.getFirst();
        this.socket.sendPacket(Packet.data(this.nomeDaMaquina, m.destino, "maquinainexistente", m.indice, this.anel.size()*2, m.texto), nextIP);
        // Agora temos que espera o ACK/NACK ou timeout, mas vai ser tratado nas outras funções
    }

    public synchronized void chegouMensagem(Packet p) {
        Main.log("Chegou uma mensagem");
        //  Se chegou aqui sabemos que é destinado pra essa máquina
        if (!p.valid) {
            // Se inválido, marcar a flag como NAK, recomputar o CRC e reenviar.
            this.socket.sendPacket(Packet.data(p.origem,p.destino,"NAK",p.sequencia,this.anel.size()*2,p.mensagem),
                                   this.nextIP);
        }
        // verificar o número de sequência. Cada máquina mantém o próximo número de sequência esperado para cada origem:
        if (p.sequencia == proximaMensagemEsperada.get(p.origem)) {
            //Se for o esperado: imprimir o apelido da origem e a mensagem, avançar o contador esperado, marcar flag como ACK.
            Main.log(String.format("Mensagem de %s: %s%n",p.origem,p.mensagem));
            System.out.printf("Mensagem recebida de %s: %s%n",p.origem,p.mensagem);
            proximaMensagemEsperada.put(p.origem, proximaMensagemEsperada.get(p.origem)+1);
        }
        //Se o número já foi recebido (duplicata): descartar o conteúdo, responder com ACK.
        this.socket.sendPacket(Packet.data(p.origem,p.destino,"ACK",p.sequencia,this.anel.size()*2,p.mensagem),
                                   this.nextIP);        
    }
    
    public synchronized void chegouResposta(Packet recebido) {
        Main.log("Chegou uma resposta");
        if (!recebido.valid || recebido.flag.equals("NAK")) {
            // a entrega falhou. Exibir mensagem na tela. Manter a mensagem na fila com o mesmo número de sequência e retransmitir na próxima passagem do token (encaminhar o token agora).
            Main.log("Entrega falha ou ACK corrompido");
        }
        else if (recebido.flag.equals("maquinainexistente")) {
            // a máquina destino não existe ou está inativa. Exibir mensagem na tela, retirar a mensagem da fila, encaminhar o token.
            Main.log("Máquina inexistente");
            this.listaMensagens.remove(0);
            
        }
        else if (recebido.flag.equals("ACK")) {
            // exibir mensagem na tela, retirar a mensagem da fila, encaminhar o token para o sucessor.
            Main.log(String.format("Mensagem enviada para %s com sucesso: %s%n",recebido.destino,recebido.mensagem));
            System.out.printf("Mensagem enviada para %s com sucesso: %s%n",recebido.destino,recebido.mensagem);
            this.listaMensagens.remove(0);
        }
        else 
        {
            Main.log("Algo está muito errado em chegouResposta");
        }
        Main.log("Enviando Token");
        this.socket.sendPacket(Packet.token(),this.nextIP);
    }

    public synchronized void chegouUmDiscover(Packet p) {
        Main.log("Chegou um discover");
        this.socket.sendBroadcast(Packet.hello(nomeDaMaquina, selfIP));
        // Tem que reconstruir a topologia incluindo essa nova máquina

        this.hellos.add(p);
        atualizarTopologia();
    }

    public synchronized void atualizarTopologia()
    {
        Main.log(String.format("Atualizando Topologia%n"));
        this.anel = new ArrayList<>();

        for (Packet p : this.hellos) {
            Main.log(String.format("Processando pacote de topologia: %s%n",p));
            if (p.tipo != 20)
            {
                Main.log("Ignorando não Hello");
                continue;
            }
            // Verifica se não é repetido - também impede nomes repetidos com ips diferentes, mas falha sileciosamente
            if (this.anel.stream().noneMatch(x -> x.origem.equals(p.origem))) {
                this.anel.add(p);
            }
            else Main.log(String.format("Nome repetido no anel: %s%n",p.toString()));
        }
        Main.log(String.format("Quantidade de Hosts encontrados: %d%n",this.anel.size()));
        // Temos que ordenar por ordem alfabética (testar e ver se funciona como esperado)
        this.anel.sort((x,y) -> x.origem.compareToIgnoreCase(y.origem));

        String s = "";
        for (Packet packet : anel) {
            // Pode dar problema se um host com nome X sair e outro com o mesmo nome X entrar depois
            s += String.format("%s -> ",packet.origem);
            proximaMensagemEsperada.putIfAbsent(packet.origem, 0);
            heartBeats.putIfAbsent(packet.origem, System.currentTimeMillis());
        }
        Main.log(String.format("Topologia formada: %s%n", s.substring(0, s.length()-3)));

        // Agora que sabemos o anel, podemos fazer os sockets corretamente pro próximo e anterior do anel
        Packet self = this.anel.stream().filter(x -> x.origem.equalsIgnoreCase(this.nomeDaMaquina)).findFirst().get();
        int selfIndex = this.anel.indexOf(self);
        Packet next = this.anel.get(selfIndex+1 >= this.anel.size() ? 0 : selfIndex+1);
        Packet prev = this.anel.get(selfIndex-1 < 0 ? this.anel.size() - 1 : selfIndex-1);
        
        Main.log(String.format("Host Anterior encontrados: %s%n",prev.origem));
        Main.log(String.format("Host Próximo encontrados: %s%n",next.origem));


        this.nextIP = next.ipOrigem;
        this.prevIP = prev.ipOrigem;
        
    }

    public boolean novaMensagemParaEnviar(String mensagem, String destino) 
    {
        Integer i = this.proximaMensagemEsperada.get(destino);
        if (i == null) {
            // Não existe o destino digitado
            return false;
        }
        this.listaMensagens.add(new Mensagem(mensagem, destino, i));
        this.proximaMensagemEsperada.put(destino, i+1);

        return true;
    }


    public String getCurrentHosts()
    {
        String s = "";
        for (Packet packet : anel) {
            s += String.format("%s -> ",packet.origem);
        }

        return s.substring(0,s.length()-3);
    }

    public String getPrevIP() {
        return prevIP;
    }
    public String getNextIP() {
        return nextIP;
    }
    public boolean isFirst()
    {
        Packet self = this.anel.stream().filter(x -> x.origem.equalsIgnoreCase(this.nomeDaMaquina)).findFirst().get();
        return this.anel.indexOf(self) == 0;
    }

    public void setSocket(Udp socket) {
        this.socket = socket;
    }
}
