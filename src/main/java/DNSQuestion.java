import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSQuestion {
    private final short qType;
    private final short qClass;
    private final String question;

    public DNSQuestion(String question, short qType, short qClass) {
        if (question == null || question.isEmpty()) {
            throw new IllegalArgumentException("Question cannot be null or empty");
        }
        this.question = question;
        this.qType = qType;
        this.qClass = qClass;
    }

    public byte[] getQuestion() {
        byte[] encodedDomainName = encodeDomainName(question);
        ByteBuffer buffer = ByteBuffer.allocate(encodedDomainName.length + 4); // 4 bytes for qType and qClass
        buffer.put(encodedDomainName);
        buffer.putShort(qType);
        buffer.putShort(qClass);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    public String toString() {
        return "Question : " + question + ", Type: " + qType + ", Class: " + qClass;
    }

    private byte[] encodeDomainName(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : s.split("\\.")) {
            int len = label.length();
            if (len > 63) {
                throw new IllegalArgumentException("Label in domain name cannot be more than 63 characters");
            }
            out.write((byte) len);
            try {
                out.writeBytes(label.getBytes("UTF-8")); // Specify encoding to avoid platform dependency
            } catch (IOException e) {
                throw new RuntimeException("Error encoding domain name", e);
            }
        }
        out.write(0);
        return out.toByteArray();
    }
}
