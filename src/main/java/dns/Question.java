package dns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a DNS question section.
 */
public class Question {

    private static final String SEPARATOR = "\\.";

    private final short qType;
    private final short qClass;
    private final String name;

    /**
     * Constructs a new Question with the specified domain name, query type, and query class.
     *
     * @param name   the domain name to query
     * @param qType  the query type
     * @param qClass the query class
     */
    public Question(String name, short qType, short qClass) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("Question cannot be null or empty");
        this.name = name;
        this.qType = qType;
        this.qClass = qClass;
    }

    /**
     * Returns the byte array representation of the question, including the encoded domain name and query type and class.
     *
     * @return the byte array representation of the question
     */
    public byte[] getName() {
        byte[] encodedDomainName = encodeDomainName(name);
        ByteBuffer buffer = ByteBuffer.allocate(encodedDomainName.length + 4); // 4 bytes for qType and qClass
        buffer.put(encodedDomainName);
        buffer.putShort(qType);
        buffer.putShort(qClass);
        return buffer.array(); // No need for Arrays.copyOf as buffer.array() returns the full array
    }

    /**
     * Returns a string representation of the question, including the domain name, query type, and query class.
     *
     * @return the string representation of the question
     */
    public String toString() {
        return "Question: " + name + ", Type: " + qType + ", Class: " + qClass;
    }

    /**
     * Encodes the domain name into a byte array suitable for DNS queries.
     *
     * @param s the domain name to encode
     * @return the encoded byte array of the domain name
     */
    private byte[] encodeDomainName(String s) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String label : s.split(SEPARATOR)) {
                int len = label.length();
                if (len > 63)
                    throw new IllegalArgumentException("Label in domain name cannot be more than 63 characters");
                out.write((byte) len);
                out.write(label.getBytes(StandardCharsets.UTF_8)); // Avoid platform dependency
            }
            out.write(0); // End of domain name
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error encoding domain name", e);
        }
    }
}
