import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class Config {
    private String nomeDaMaquina;
    private int    delayDoToken;
    private double probErro;
    private int    timeoutToken;
    private int    tempoMinimoToken;

    public Config(){
        nomeDaMaquina = "B";
        delayDoToken = 100;
        probErro = 0.0;
        timeoutToken = 1000;
        tempoMinimoToken = 150;
    }

    public Config(String nome){
        this();
        if (nome != null && !nome.isBlank()) {
            nomeDaMaquina = nome.trim();
        }
    }

    public Config(BufferedReader confFile) throws Exception {
        this();
        List<String> lines = confFile.lines().toList();
        for (String string : lines) {
            if (string.contains("nomeDaMaquina:")) {
                this.nomeDaMaquina = string.substring(string.indexOf(':')+1).trim();
            }
            if (string.contains("delayDoToken:")) {
                this.delayDoToken = Integer.parseInt(string.substring(string.indexOf(':')+1).trim());
            }
            if (string.contains("probErro:")) {
                this.probErro = Double.parseDouble(string.substring(string.indexOf(':')+1).trim());
            }
            if (string.contains("timeoutToken:")) {
                this.timeoutToken = Integer.parseInt(string.substring(string.indexOf(':')+1).trim());
            }
            if (string.contains("tempoMinimoToken:")) {
                this.tempoMinimoToken = Integer.parseInt(string.substring(string.indexOf(':')+1).trim());
            }
        }
    }

    public Config(String nome, int delay, int probErroBase100, int timeout, int tempoMinimo){
        if(probErroBase100 > 100){
            probErroBase100 = 100;
        }
        nomeDaMaquina = nome;
        delayDoToken = delay;
        probErro = probErroBase100 / 100.0;
        timeoutToken = timeout;
        tempoMinimoToken = tempoMinimo;
    }

    public String getNome(){
        return this.nomeDaMaquina;
    }

    public int getDelayToken(){
        return this.delayDoToken;
    }

    public double getProbabilidadeErro(){
        return this.probErro;
    }

    public int getTimeoutToken(){
        return this.timeoutToken;
    }

    public int getTempoMinimoToken(){
        return this.tempoMinimoToken;
    }
}
