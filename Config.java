import java.io.FileInputStream;
import java.util.Properties;

public class Config {
    private String nomeDaMaquina;
    private String ip;
    private int delayDoToken;
    private double probErro;
    private int timeoutToken;
    private int tempoMinimoToken;

    public Config(String nome, String ip, int delay, int probErroBase100, int timeout, int tempoMinimo){
        if(probErroBase100 > 100) probErroBase100 = 100;
        this.nomeDaMaquina = nome;
        this.ip = ip;
        this.delayDoToken = delay;
        this.probErro = probErroBase100 / 100.0;
        this.timeoutToken = timeout;
        this.tempoMinimoToken = tempoMinimo;
    }

    public static Config lerArquivo(String caminho) throws Exception {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(caminho)) {
            props.load(fis);
        }
        String nome         = props.getProperty("nome", "A");
        String ip           = props.getProperty("ip", "127.0.0.1");
        int delay           = Integer.parseInt(props.getProperty("delay", "100"));
        int probErroBase100 = Integer.parseInt(props.getProperty("probErro", "0"));
        int timeout         = Integer.parseInt(props.getProperty("timeout", "5000"));
        int tempoMinimo     = Integer.parseInt(props.getProperty("tempoMinimo", "500"));
        return new Config(nome, ip, delay, probErroBase100, timeout, tempoMinimo);
    }

    public String getNome()              { return this.nomeDaMaquina; }
    public String getIp()                { return this.ip; }
    public int getDelayToken()           { return this.delayDoToken; }
    public double getProbabilidadeErro() { return this.probErro; }
    public int getTimeoutToken()         { return this.timeoutToken; }
    public int getTempoMinimoToken()     { return this.tempoMinimoToken; }
}
