import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

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
        return buffer.array(); // No need for Arrays.copyOf as buffer.array() returns the full array
    }

    public String toString() {
        return "Question: " + question + ", Type: " + qType + ", Class: " + qClass;
    }

    private byte[] encodeDomainName(String s) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String label : s.split("\\.")) {
                int len = label.length();
                if (len > 63) {
                    throw new IllegalArgumentException("Label in domain name cannot be more than 63 characters");
                }
                out.write((byte) len);
                out.write(label.getBytes("UTF-8")); // Avoid platform dependency
            }
            out.write(0); // End of domain name
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error encoding domain name", e);
        }
    }
}
