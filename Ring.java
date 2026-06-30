import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

// Mantem o estado do anel e as decisoes de encaminhamento.
public class Ring implements Runnable {
    private static final int HEARTBEAT_INTERVAL_MS = 10000;
    private static final int HOST_TIMEOUT_MS = 30000;
    private static final int MAX_FILA = 10;

    private final String nomeDaMaquina;
    private final int delayDoToken;
    private final int timeoutToken;
    private final int tempoMinimoToken;
    private final double probErro;
    private final String selfIP;
    private final int port;
    private final Map<String, Packet> hostsConhecidos;
    private final LinkedList<Mensagem> listaMensagens;
    private final HashMap<String, Integer> proximaMensagemEsperada;
    private final HashMap<String, Long> heartBeats;

    private String nextIP;
    private String prevIP;
    private LinkedList<Packet> anel;
    private Udp socket;
    private Thread timeoutThread;
    private Thread heartbeatThread;
    private boolean heartbeatAtivo;
    private boolean souControladora;
    private int tamanhoAnteriorAnel;
    private int proximaSequenciaLocal;
    private long lastTokenSeenTime;
    private long lastTokenTimeoutBaseTime;

    public Ring(Config conf, String ip, int port) {
        this.nomeDaMaquina = conf.getNome();
        this.delayDoToken = conf.getDelayToken();
        this.timeoutToken = conf.getTimeoutToken();
        this.tempoMinimoToken = conf.getTempoMinimoToken();
        this.probErro = conf.getProbabilidadeErro();
        this.selfIP = ip;
        this.port = port;
        this.hostsConhecidos = new LinkedHashMap<>();
        this.listaMensagens = new LinkedList<>();
        this.anel = new LinkedList<>();
        this.proximaMensagemEsperada = new HashMap<>();
        this.heartBeats = new HashMap<>();
        this.tamanhoAnteriorAnel = 0;
        this.lastTokenSeenTime = Long.MIN_VALUE;
        this.lastTokenTimeoutBaseTime = Long.MIN_VALUE;
    }

    public synchronized boolean initialize() {
        Main.log("Começando inicializacao");
        registrarHostLocal();
        startHeartbeat();
        this.socket.sendBroadcast(Packet.discover(this.nomeDaMaquina, this.selfIP));

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < startTime + 1000) {
            try {
                wait(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        atualizarTopologia();
        return this.anel.size() > 1;
    }

    private void registrarHostLocal() {
        Packet self = new Packet();
        self.tipo = 20;
        self.origem = this.nomeDaMaquina;
        self.ipOrigem = this.selfIP;
        self.valid = true;
        this.hostsConhecidos.put(this.nomeDaMaquina, self);
        this.heartBeats.put(this.nomeDaMaquina, System.currentTimeMillis());
        this.proximaMensagemEsperada.putIfAbsent(this.nomeDaMaquina, 0);
    }

    public synchronized void startHeartbeat() {
        if (this.heartbeatAtivo) {
            return;
        }
        this.heartbeatAtivo = true;
        this.heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                enviarHeartbeat();
            }
        }, "heartbeat-" + this.nomeDaMaquina);
        this.heartbeatThread.setDaemon(true);
        this.heartbeatThread.start();
    }

    private synchronized void enviarHeartbeat() {
        this.socket.sendBroadcast(Packet.hello(this.nomeDaMaquina, this.selfIP));
        removerHostsInativos();
    }

    private void removerHostsInativos() {
        long agora = System.currentTimeMillis();
        boolean mudou = false;
        ArrayList<String> removidos = new ArrayList<>();

        for (Map.Entry<String, Long> entry : this.heartBeats.entrySet()) {
            String host = entry.getKey();
            if (host.equals(this.nomeDaMaquina)) {
                continue;
            }
            if (agora - entry.getValue() >= HOST_TIMEOUT_MS) {
                removidos.add(host);
            }
        }

        for (String host : removidos) {
            Main.log("Host removido por inatividade: " + host);
            this.heartBeats.remove(host);
            this.hostsConhecidos.remove(host);
            this.proximaMensagemEsperada.remove(host);
            mudou = true;
        }

        if (mudou) {
            atualizarTopologia();
        }
    }

    @Override
    public synchronized void run() {
        atualizarTopologia();
    }

    private synchronized void timeoutToken() {
        if (!this.souControladora || this.anel.size() < 2) {
            return;
        }

        long agora = System.currentTimeMillis();
        long expiracao = this.lastTokenTimeoutBaseTime + this.timeoutToken;
        if (this.lastTokenTimeoutBaseTime == Long.MIN_VALUE || agora >= expiracao) {
            Main.log("TIMEOUT do token, gerando novo token");
            enviarToken("timeout");
            return;
        }

        agendarTimeoutToken(expiracao - agora);
    }

    private void agendarTimeoutToken(long atraso) {
        if (this.timeoutThread != null) {
            this.timeoutThread.interrupt();
        }

        this.timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(Math.max(1L, atraso));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            timeoutToken();
        }, "token-timeout-" + this.nomeDaMaquina);
        this.timeoutThread.setDaemon(true);
        this.timeoutThread.start();
    }

    private void cancelarTimeoutToken() {
        if (this.timeoutThread != null) {
            this.timeoutThread.interrupt();
            this.timeoutThread = null;
        }
    }

    private void atualizarEstadoControladora(int tamanhoAnterior) {
        boolean eraControladora = this.souControladora;
        boolean agoraSouControladora = isFirst();

        this.souControladora = agoraSouControladora;

        if (agoraSouControladora && (!eraControladora || tamanhoAnterior < 2)) {
            Main.log("Assumindo controle do token");
            if (this.anel.size() > 1) {
                enviarToken(tamanhoAnterior < 2 ? "anel-formado" : "nova-controladora");
            }
        } else if (!agoraSouControladora && eraControladora) {
            Main.log("Deixando de ser controladora");
            cancelarTimeoutToken();
        } else if (agoraSouControladora && this.anel.size() < 2) {
            cancelarTimeoutToken();
        }
    }

    private void enviarToken(String motivo) {
        if (this.nextIP == null || this.anel.size() < 2) {
            return;
        }
        Main.log("Enviando token (" + motivo + ") para " + this.nextIP);
        if (this.souControladora) {
            this.lastTokenTimeoutBaseTime = System.currentTimeMillis();
        }
        this.socket.sendPacket(Packet.token(), this.nextIP);
        if (this.souControladora) {
            agendarTimeoutToken(this.timeoutToken);
        }
    }

    private void encaminharTokenOuMensagem() {
        try {
            Thread.sleep(this.delayDoToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (this.listaMensagens.isEmpty()) {
            enviarToken("passagem-normal");
            return;
        }

        Mensagem mensagem = this.listaMensagens.peekFirst();
        int ttlInicial = Math.max(2, this.anel.size() * 2);
        String pacote = Packet.data(
            this.nomeDaMaquina,
            mensagem.destino,
            "maquinainexistente",
            mensagem.indice,
            ttlInicial,
            mensagem.texto
        );

        if (deveCorromperPacote()) {
            pacote = corromperMensagem(pacote);
        }

        Main.log("Enviando mensagem para " + mensagem.destino + " com sequencia " + mensagem.indice);
        this.socket.sendPacket(pacote, this.nextIP);
    }

    private boolean deveCorromperPacote() {
        return Math.random() < this.probErro;
    }

    private String corromperMensagem(String pacote) {
        String[] partes = pacote.split(":", 8);
        if (partes.length != 8) {
            return pacote;
        }
        String mensagemOriginal = partes[6];
        if (mensagemOriginal.isEmpty()) {
            partes[6] = "X";
        } else {
            char primeiro = mensagemOriginal.charAt(0);
            char substituto = primeiro == 'X' ? 'Y' : 'X';
            partes[6] = substituto + mensagemOriginal.substring(1);
        }
        return String.join(":", partes);
    }

    public synchronized void chegouUmHello(Packet p) {
        if (!p.valid || p.origem == null || p.ipOrigem == null) {
            Main.log("HELLO invalido descartado");
            return;
        }
        if (p.origem.equalsIgnoreCase(this.nomeDaMaquina)) {
            if (!p.ipOrigem.trim().equals(this.selfIP)) {
                Main.log("HELLO com apelido duplicado ignorado: " + p.origem + " em " + p.ipOrigem);
            }
            notifyAll();
            return;
        }

        Packet anterior = this.hostsConhecidos.get(p.origem);
        this.hostsConhecidos.put(p.origem, p);
        this.heartBeats.put(p.origem, System.currentTimeMillis());
        this.proximaMensagemEsperada.putIfAbsent(p.origem, 0);

        boolean mudou = anterior == null || !anterior.ipOrigem.equals(p.ipOrigem);
        if (mudou) {
            Main.log("HELLO adicionou/atualizou host " + p.origem);
            atualizarTopologia();
        }
        notifyAll();
    }

    public synchronized void chegouToken() {
        if (this.anel.size() < 2) {
            return;
        }

        long agora = System.currentTimeMillis();
        if (this.souControladora) {
            if (this.lastTokenSeenTime != Long.MIN_VALUE && agora - this.lastTokenSeenTime < this.tempoMinimoToken) {
                Main.log("Token duplicado detectado e descartado");
                return;
            }
            this.lastTokenSeenTime = agora;
            this.lastTokenTimeoutBaseTime = agora;
            agendarTimeoutToken(this.timeoutToken);
        }

        Main.log("Chegou o token");
        encaminharTokenOuMensagem();
    }

    public synchronized void chegouMensagem(Packet p) {
        Main.log("Chegou uma mensagem destinada a esta maquina");

        if (!p.valid) {
            if (p.origem == null || p.destino == null || p.sequencia == Integer.MIN_VALUE || this.nextIP == null) {
                Main.log("Pacote invalido sem campos suficientes para NAK descartado");
                return;
            }
            this.socket.sendPacket(
                Packet.data(p.origem, p.destino, "NAK", p.sequencia, ttlResetado(), p.mensagem),
                this.nextIP
            );
            return;
        }

        int esperado = this.proximaMensagemEsperada.getOrDefault(p.origem, 0);
        String flagResposta = "ACK";

        if (p.sequencia == esperado) {
            Main.log("Mensagem de " + p.origem + ": " + p.mensagem);
            System.out.printf("Mensagem recebida de %s: %s%n", p.origem, p.mensagem);
            this.proximaMensagemEsperada.put(p.origem, esperado + 1);
        } else if (p.sequencia > esperado) {
            flagResposta = "NAK";
        }

        this.socket.sendPacket(
            Packet.data(p.origem, p.destino, flagResposta, p.sequencia, ttlResetado(), p.mensagem),
            this.nextIP
        );
    }

    public synchronized void chegouResposta(Packet recebido) {
        Main.log("Chegou uma resposta");

        if (!recebido.valid || "NAK".equals(recebido.flag)) {
            Main.log("Entrega falhou, mensagem permanece na fila");
        } else if ("maquinainexistente".equals(recebido.flag)) {
            Main.log("Maquina inexistente/inativa: " + recebido.destino);
            if (!this.listaMensagens.isEmpty()) {
                this.listaMensagens.removeFirst();
            }
        } else if ("ACK".equals(recebido.flag)) {
            Main.log("Mensagem enviada para " + recebido.destino + " com sucesso: " + recebido.mensagem);
            System.out.printf("Mensagem enviada para %s com sucesso: %s%n", recebido.destino, recebido.mensagem);
            if (!this.listaMensagens.isEmpty()) {
                this.listaMensagens.removeFirst();
            }
        } else {
            Main.log("Flag de resposta desconhecida: " + recebido.flag);
        }

        enviarToken("fim-resposta");
    }

    public synchronized void encaminharMensagem(Packet recebido) {
        if (!recebido.valid) {
            Main.log("Pacote de dados com CRC invalido descartado");
            return;
        }
        if (recebido.ttl <= 0) {
            Main.log("Pacote descartado por TTL esgotado");
            return;
        }

        String toSend = Packet.data(
            recebido.origem,
            recebido.destino,
            recebido.flag,
            recebido.sequencia,
            recebido.ttl - 1,
            recebido.mensagem
        );
        this.socket.sendPacket(toSend, this.nextIP);
    }

    public synchronized void chegouUmDiscover(Packet p) {
        Main.log("Chegou um discover");
        if (p.valid && p.origem != null && p.ipOrigem != null) {
            if (p.origem.equalsIgnoreCase(this.nomeDaMaquina)) {
                if (!p.ipOrigem.trim().equals(this.selfIP)) {
                    Main.log("DISCOVER com apelido duplicado ignorado: " + p.origem + " em " + p.ipOrigem);
                }
            } else {
                Packet anterior = this.hostsConhecidos.get(p.origem);
                this.hostsConhecidos.put(p.origem, p);
                this.heartBeats.put(p.origem, System.currentTimeMillis());
                this.proximaMensagemEsperada.putIfAbsent(p.origem, 0);
                if (anterior == null || !anterior.ipOrigem.equals(p.ipOrigem)) {
                    Main.log("DISCOVER adicionou/atualizou host " + p.origem);
                    atualizarTopologia();
                }
                notifyAll();
            }
        }
        this.socket.sendBroadcast(Packet.hello(this.nomeDaMaquina, this.selfIP));
    }

    public synchronized void atualizarTopologia() {
        int tamanhoAnterior = this.tamanhoAnteriorAnel;
        registrarHostLocal();

        this.anel = new LinkedList<>(this.hostsConhecidos.values());
        this.anel.sort((x, y) -> x.origem.compareToIgnoreCase(y.origem));
        this.tamanhoAnteriorAnel = this.anel.size();

        if (this.anel.isEmpty()) {
            this.nextIP = null;
            this.prevIP = null;
            return;
        }

        for (Packet packet : this.anel) {
            this.proximaMensagemEsperada.putIfAbsent(packet.origem, 0);
        }

        Packet self = null;
        for (Packet packet : this.anel) {
            if (packet.origem.equalsIgnoreCase(this.nomeDaMaquina)) {
                self = packet;
                break;
            }
        }

        if (self == null) {
            return;
        }

        int selfIndex = this.anel.indexOf(self);
        Packet next = this.anel.get((selfIndex + 1) % this.anel.size());
        Packet prev = this.anel.get((selfIndex - 1 + this.anel.size()) % this.anel.size());

        this.nextIP = next.ipOrigem;
        this.prevIP = prev.ipOrigem;

        Main.log("Topologia formada: " + getCurrentHosts());
        Main.log("Host anterior: " + prev.origem);
        Main.log("Host proximo: " + next.origem);

        atualizarEstadoControladora(tamanhoAnterior);
    }

    private int ttlResetado() {
        return Math.max(2, this.anel.size() * 2);
    }

    public synchronized boolean novaMensagemParaEnviar(String mensagem, String destino) {
        if (mensagem == null || mensagem.isBlank() || destino == null || destino.isBlank()) {
            return false;
        }
        if (this.listaMensagens.size() >= MAX_FILA) {
            Main.log("Fila cheia, mensagem descartada");
            return false;
        }

        String destinoNormalizado = destino.trim();
        this.listaMensagens.add(new Mensagem(mensagem, destinoNormalizado, this.proximaSequenciaLocal));
        Main.log("Mensagem enfileirada para " + destinoNormalizado + " com sequencia " + this.proximaSequenciaLocal);
        this.proximaSequenciaLocal++;
        return true;
    }

    public synchronized String getCurrentHosts() {
        if (this.anel.isEmpty()) {
            return "(nenhum)";
        }

        StringBuilder builder = new StringBuilder();
        for (Packet packet : this.anel) {
            if (builder.length() > 0) {
                builder.append(" -> ");
            }
            builder.append(packet.origem);
        }
        return builder.toString();
    }

    public synchronized String getPrevIP() {
        return this.prevIP;
    }

    public synchronized String getNextIP() {
        return this.nextIP;
    }

    public synchronized boolean isFirst() {
        if (this.anel.isEmpty()) {
            return false;
        }
        return this.anel.get(0).origem.equalsIgnoreCase(this.nomeDaMaquina);
    }

    public synchronized void setSocket(Udp socket) {
        this.socket = socket;
    }
}
