import java.util.zip.CRC32;

public class Packet {

    public int tipo = -1;
    public String origem;
    public String destino;
    public String ipOrigem;
    public long crc = Integer.MIN_VALUE;
    public String flag;
    public int ttl = Integer.MIN_VALUE;
    public int sequencia = Integer.MIN_VALUE;
    public String mensagem;
    public boolean valid; // Informa se o crc está de acordo com o esperado

    public Packet(){}

    public Packet(byte[] packet, int size) {
        if (packet == null || size <= 0) {
            this.valid = false;
            return;
        }

        String raw = new String(packet, 0, size);
        String[] fields = raw.split(":", -1);

        try {
            this.tipo = Integer.parseInt(fields[0]);
        } catch (NumberFormatException e) {
            this.valid = false;
            this.tipo = -1;
            return;
        }

        if (this.tipo == 1000) {
            this.valid = fields.length == 1;
            return;
        }

        try {
            switch (this.tipo) {
                case 10:
                    if (fields.length != 3) {
                        this.valid = false;
                        return;
                    }
                    this.origem = fields[1];
                    this.ipOrigem = fields[2];
                    this.valid = true;
                    break;
                case 20:
                    if (fields.length != 4) {
                        this.valid = false;
                        return;
                    }
                    this.origem = fields[1];
                    this.ipOrigem = fields[2];
                    this.crc = Long.parseLong(fields[3]);
                    this.valid = crc32("20:" + this.origem + ":" + this.ipOrigem + ":") == this.crc;
                    break;
                case 2000:
                    if (fields.length != 8) {
                        preencherDadosParciais(fields);
                        this.valid = false;
                        return;
                    }
                    this.origem = fields[1];
                    this.destino = fields[2];
                    this.flag = fields[3];
                    this.sequencia = Integer.parseInt(fields[4]);
                    this.ttl = Integer.parseInt(fields[5]);
                    this.mensagem = fields[6];
                    this.crc = Long.parseLong(fields[7]);
                    this.valid = crc32("2000:" + this.origem + ":" + this.destino + ":" + this.flag + ":" + this.sequencia + ":" + this.ttl + ":" + this.mensagem + ":") == this.crc;
                    break;
                default:
                    this.valid = false;
                    return;
            }
        } catch (NumberFormatException e) {
            this.valid = false;
        }
    }

    public static String read(byte[] packet, int start, int max)
    {
        String out = "";
        while (start < max) {
            byte b = packet[start++];
            if ((char)b == ':') break;
            out += (char)b;
        }
        return out;
    }

    public static String discover(String origem, String origemIP)
    {
        return "10:"+origem.trim()+":"+origemIP.trim();
    }

    public static String hello(String origem, String origemIP)
    {
        String s = "20:"+origem.trim()+":"+origemIP.trim()+":";
        return s+crc32(s);
    }

    public static String token()
    {
        return "1000";
    }

    public static String data(String origem, String destino, String flag, int numSequencia, int TTL, String mensagem)
    {
        String s = "2000:"+campo(origem)+":"+campo(destino)+":"+campo(flag)+":"+numSequencia+":"+TTL+":"+campo(mensagem)+":";
        return s+crc32(s);
    }

    private static String campo(String valor)
    {
        return valor == null ? "" : valor.trim();
    }

    private void preencherDadosParciais(String[] fields)
    {
        if (fields.length > 1) this.origem = fields[1];
        if (fields.length > 2) this.destino = fields[2];
        if (fields.length > 3) this.flag = fields[3];
        if (fields.length > 4) {
            try {
                this.sequencia = Integer.parseInt(fields[4]);
            } catch (NumberFormatException e) {
                this.sequencia = Integer.MIN_VALUE;
            }
        }
    }

    private static long crc32(String value)
    {
        CRC32 crc = new CRC32();
        crc.update(value.getBytes());
        return crc.getValue();
    }

    public static byte[] toBytes(Packet packet)
    {        
        return packet.toString().getBytes();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.tipo);
        if (this.origem != null) {
            sb.append(":"+this.origem);
        }
        if (this.destino != null) {
            sb.append(":"+this.destino);
        }
        if (this.ipOrigem != null) {
            sb.append(":"+this.ipOrigem);
        }
        if (this.flag != null) {
            sb.append(":"+this.flag);
        }
        if (this.sequencia != Integer.MIN_VALUE) {
            sb.append(":"+this.sequencia);
        }
        if (this.ttl != Integer.MIN_VALUE) {
            sb.append(":"+this.ttl);
        }
        if (this.mensagem != null) {
            sb.append(":"+this.mensagem);
        }
        if (this.crc != Integer.MIN_VALUE) {
            sb.append(":"+this.crc);
        }
        return sb.toString();
    }
}
