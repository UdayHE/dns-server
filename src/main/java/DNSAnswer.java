import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSAnswer {

    // Fields representing the type, class, time-to-live, resource data length, question, answer, and length of the DNS answer
    private short qType;
    private short qClass;
    private int ttl;
    private short rdLength;
    private String question;
    private String answer;
    private int length;

    // Constructor to initialize a DNSAnswer object with question, qType, qClass, rdLength, and answer
    public DNSAnswer(String question, short qType, short qClass, short rdLength, String answer) {
        this.question = question;
        this.qType = qType;
        this.qClass = qClass;
        this.ttl = 60;
        this.rdLength = rdLength;
        this.answer = answer;
    }

    // Method to get the byte array representation of the DNS answer
    public byte[] getAnswer() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(encodeDomainName(question));
        buffer.putShort(qType);
        buffer.putShort(qClass);
        buffer.putInt(ttl);
        buffer.putShort(rdLength);
        buffer.put(encodeIpAddress(answer));
        this.length = buffer.position();
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    // Method to get the length of the DNS answer
    public int getAnswerLength() {
        return length;
    }

    // Method to return a string representation of the DNS answer
    public String toString() {
        return "Answer: " + question + ", " + qType + ", " + qClass + ", " + answer;
    }

    // Private method to encode an IP address into a byte array
    private byte[] encodeIpAddress(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (s.length() == 0) return out.toByteArray();
        for (String octet : s.split("\\.")) {
            out.write(Integer.parseInt(octet));
        }
        return out.toByteArray();
    }

    // Private method to encode a domain name into a byte array
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
