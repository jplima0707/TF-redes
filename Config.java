public class Config {
    private String nomeDaMaquina;
    private int delayDoToken;
    private double probErro;
    private int timeoutToken;
    private int tempoMinimoToken;

    public Config(){
        nomeDaMaquina = "B";
        delayDoToken = 300;
        probErro = 10;
        timeoutToken = 10;
        tempoMinimoToken = 2;
    }

    public Config(String nome, int delay, int probErroBase100, int timeout, int tempoMinimo){
        if(probErroBase100 > 100){
            probErroBase100 = 100;
        }
        nomeDaMaquina = nome;
        delayDoToken = delay;
        probErro = probErroBase100/100;
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
