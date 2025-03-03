import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DnsAnswer {
    private short QTYPE;
    private short QCLASS;
    private int TTL;
    private short RDLENGTH;
    private String question;
    private String answer;
    private int length;

    public DnsAnswer(String question, short QTYPE, short QCLASS, int TTL, short RDLENGTH, String answer) {
        this.question = question;
        this.QTYPE = QTYPE;
        this.QCLASS = QCLASS;
        this.TTL = 60;
        this.RDLENGTH = RDLENGTH;
        this.answer = answer;
    }

    public byte[] getAnswer() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(encodeDomainName(question));
        buffer.putShort(QTYPE);
        buffer.putShort(QCLASS);
        buffer.putInt(TTL);
        buffer.putShort(RDLENGTH);
        buffer.put(encodeIpAddress(answer));
        this.length = buffer.position();
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    public int getAnswerLength() {
        return length;
    }

    public String toString() {
        return "Answer: " + question + ", " + QTYPE + ", " + QCLASS + ", " + answer;
    }

    private byte[] encodeIpAddress(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (s.length() == 0) return out.toByteArray();
        for (String octet : s.split("\\.")) {
            out.write(Integer.parseInt(octet));
        }
        return out.toByteArray();
    }

    private byte[] encodeDomainName(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : s.split("\\.")) {
            int len = label.length();
            out.write(len);
            out.writeBytes(label.getBytes());
        }
        out.write(0);
        return out.toByteArray();
    }
}