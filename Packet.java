import java.util.zip.CRC32;

public class Packet {
    
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

    public static void main(String[] args) {
        System.out.println(Packet.discover("A", "192.168.0.1"));
        System.out.println(Packet.hello("B", "10.32.143.21"));
        System.out.println(Packet.token());
        System.out.println(Packet.data("A", "B", "maquinainexistente", 0, 8, "Oi"));
    }
}
