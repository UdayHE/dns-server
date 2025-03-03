import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSQuestion {
    private final short qType;
    private final short qClass;
    private int length;
    private final String question;

    public DNSQuestion(String question, short qType, short qClass) {
        this.question = question;
        this.qType = qType;
        this.qClass = qClass;
    }

    public byte[] getQuestion() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(encodeDomainName(question));
        buffer.putShort(qType);
        buffer.putShort(qClass);
        this.length = buffer.position();
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    public String toString() {
        return "Question : " + question + ", " + qType + ", " + qClass;
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