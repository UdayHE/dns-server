import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSMessage {
    private final byte[] rawData;
    private final short transactionId;
    private final short flags;
    private final short questionCount;
    private final short answerCount;
    private final short authorityCount;
    private final short additionalCount;
    private final byte[] questionSection;

    public DNSMessage(byte[] data) {
        this.rawData = data;
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();  // ID
        this.flags = buffer.getShort();         // Flags
        this.questionCount = buffer.getShort(); // QDCOUNT
        this.answerCount = buffer.getShort();   // ANCOUNT
        this.authorityCount = buffer.getShort(); // NSCOUNT
        this.additionalCount = buffer.getShort(); // ARCOUNT

        // Extract question section (excluding 12-byte header)
        int questionSectionLength = data.length - 12;
        this.questionSection = Arrays.copyOfRange(data, 12, 12 + questionSectionLength);
    }

    public static byte[] createResponse(DNSMessage request) {
        byte[] domainName = new byte[]{0x0C, 'c', 'o', 'd', 'e', 'c', 'r', 'a', 'f', 't', 'e', 'r', 's', 0x02, 'i', 'o', 0x00};
        int responseSize = 12 + request.questionSection.length + domainName.length + 10; // Header + Question + Answer
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        // Header (12 bytes)
        responseBuffer.putShort((short) 1234); // Transaction ID
        responseBuffer.putShort((short) 0x8180); // Flags: Response, No error
        responseBuffer.putShort((short) 1); // QDCOUNT (1 question)
        responseBuffer.putShort((short) 1); // ANCOUNT (1 answer)
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        // Question Section (Copied from request)
        responseBuffer.put(request.questionSection);

        // Answer Section
        responseBuffer.put(domainName); // Name
        responseBuffer.putShort((short) 1); // Type (A)
        responseBuffer.putShort((short) 1); // Class (IN)
        responseBuffer.putInt(60); // TTL (60 seconds)
        responseBuffer.putShort((short) 4); // Length (4 bytes for IPv4)
        responseBuffer.put(new byte[]{8, 8, 8, 8}); // Data (8.8.8.8)

        return responseBuffer.array();
    }

    public short getTransactionId() {
        return transactionId;
    }
}
