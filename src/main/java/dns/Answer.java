package dns;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

// Represents a DNS answer record with the necessary fields and methods to encode the data
public class Answer {

    private static final int BUFFER_SIZE = 512;
    private static final String SEPARATOR = "\\.";

    private final short qType;
    private final short qClass;
    private final int ttl;
    private final short rdLength;
    private final String question;
    private final String answer;
    private int length;

    // Constructor to initialize the fields of the DNS answer record
    public Answer(String question, short qType, short qClass, short rdLength, String answer) {
        this.question = question;
        this.qType = qType;
        this.qClass = qClass;
        this.ttl = 60;
        this.rdLength = rdLength;
        this.answer = answer;
    }

    // Method to get the byte array representation of the DNS answer
    public byte[] getAnswer() {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        buffer.put(encodeDomainName(question));
        buffer.putShort(qType);
        buffer.putShort(qClass);
        buffer.putInt(ttl);
        buffer.putShort(rdLength);
        buffer.put(encodeIpAddress(answer));
        this.length = buffer.position();
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    // Method to return a string representation of the DNS answer
    public String toString() {
        return "Answer: " + question + ", " + qType + ", " + qClass + ", " + answer;
    }

    // Private method to encode an IP address into a byte array
    private byte[] encodeIpAddress(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (s.length() == 0) return out.toByteArray();
        for (String octet : s.split(SEPARATOR)) {
            out.write(Integer.parseInt(octet));
        }
        return out.toByteArray();
    }

    // Private method to encode a domain name into a byte array
    private byte[] encodeDomainName(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : s.split(SEPARATOR)) {
            int len = label.length();
            out.write(len);
            out.writeBytes(label.getBytes());
        }
        out.write(0);
        return out.toByteArray();
    }
}
