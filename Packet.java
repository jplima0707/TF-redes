import java.util.zip.CRC32;

public class Packet {
    
    public int tipo;
    public String origem;
    public String destino;
    public String ipOrigem;
    public String ipDestino;
    public long crc = Integer.MIN_VALUE;
    public String flag;
    public int ttl = Integer.MIN_VALUE;
    public int sequencia = Integer.MIN_VALUE;
    public String mensagem;
    public boolean valid; // Informa se o crc está de acordo com o esperado

    public Packet(){}

    public Packet(byte[] packet, int size)
    {
        int i = 0;
        String tipoString = "";
        for (byte b : packet) {
            i++;
            if ((char)b == ':') break;
            tipoString += (char) b;
        }
        System.out.println(tipoString);
        this.tipo = Integer.parseInt(tipoString);

        if (this.tipo == 1000)
        {
            this.valid = i == size;
            return;
        }


        this.origem = read(packet, i++, size);
        i += origem.length();

        int indexCRC = -1;

        switch (this.tipo) {
            case 10:    
            case 20:
                this.ipOrigem = read(packet, i++, size);
                i += this.ipOrigem.length();
                if (this.tipo == 10) {this.valid = true; return;}
                indexCRC = i;
                String crcString = read(packet, i++, size);
                i += crcString.length();
                this.crc = Long.parseLong(crcString);
                break;
            case 2000:
                this.flag = read(packet, i++, size);
                i += this.flag.length();
                String seq = read(packet, i++, size);
                i += seq.length();
                this.sequencia = Integer.parseInt(seq);
                String ttlString = read(packet, i++, size);
                i += ttlString.length();
                this.ttl = Integer.parseInt(ttlString);
                this.mensagem = read(packet, i++, size);
                i += this.mensagem.length();
                indexCRC = i;
                String crcString2 = read(packet, i++, size);
                i += crcString2.length();
                this.crc = Long.parseLong(crcString2);
                break;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(packet, 0, indexCRC);
        
        this.valid = crc32.getValue() == this.crc;
    }

    public static String read(byte[] packet, int start, int max)
    {
        String out = "";
        while (true) {
            if (start == max) break;
            byte b = packet[start++];
            if ((char)b == ':') break;
            out += (char)b;
        }
        return out;
    }

    public static String discover(String origem, String origemIP)
    {
        return "10:"+origem+":"+origemIP;
    }

    public static String hello(String origem, String origemIP)
    {
        CRC32 crc = new CRC32();
        String s = "20:"+origem+":"+origemIP+":";
        crc.update(s.getBytes());
        return s+crc.getValue();
    }

    public static String token()
    {
        return "1000";
    }

    public static String data(String origem, String destino, String flag, int numSequencia, int TTL, String mensagem)
    {
        CRC32 crc = new CRC32();
        String s = "2000:"+origem+":"+destino+":"+flag+":"+numSequencia+":"+TTL+":"+mensagem+":";
        crc.update(s.getBytes());
        return s+crc.getValue();
    }

    public static byte[] toBytes(Packet packet)
    {        
        return packet.toString().getBytes();
    }

    public static void main(String[] args) {
        Packet p = new Packet("1000".getBytes(),"1000".getBytes().length);
        System.out.println(Packet.discover("A", "192.168.0.1"));
        System.out.println(Packet.hello("B", "10.32.143.21"));
        System.out.println(Packet.token());
        System.out.println(Packet.data("A", "B", "maquinainexistente", 0, 8, "Oi"));
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
        if (this.ipDestino != null) {
            sb.append(":"+this.ipDestino);
        }
        if (this.flag != null) {
            sb.append(":"+this.flag);
        }
        if (this.ttl != Integer.MIN_VALUE) {
            sb.append(":"+this.ttl);
        }
        if (this.sequencia != Integer.MIN_VALUE) {
            sb.append(":"+this.sequencia);
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
