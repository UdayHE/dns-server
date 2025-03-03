import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DnsQuestion {
    private short QTYPE;
    private short QCLASS;
    private int length;
    private String question;

    public DnsQuestion(String question, short QTYPE, short QCLASS) {
        this.question = question;
        this.QTYPE = QTYPE;
        this.QCLASS = QCLASS;
    }

    public byte[] getQuestion() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(encodeDomainName(question));
        buffer.putShort(QTYPE);
        buffer.putShort(QCLASS);
        this.length = buffer.position();
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    public int getQuestionLength() {
        return this.length;
    }

    public String toString() {
        return "Question : " + question + ", " + QTYPE + ", " + QCLASS;
    }

    private byte[] encodeDomainName(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : s.split("\\.")) {
            int len = label.length();
            out.write((byte) len);
            out.writeBytes(label.getBytes());
        }
        out.write(0);
        return out.toByteArray();
    }
}