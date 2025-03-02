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
    private final int questionEndIndex;

    public DNSMessage(byte[] data) {
        this.rawData = data;
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();  // ID
        this.flags = buffer.getShort();         // Flags
        this.questionCount = buffer.getShort(); // QDCOUNT
        this.answerCount = buffer.getShort();   // ANCOUNT
        this.authorityCount = buffer.getShort(); // NSCOUNT
        this.additionalCount = buffer.getShort(); // ARCOUNT

        // Extract question section
        int index = 12; // Start after header
        while (data[index] != 0) { index++; } // Domain name ends with 0x00
        index += 5; // Move past null byte + QTYPE (2 bytes) + QCLASS (2 bytes)
        this.questionEndIndex = index;

        this.questionSection = Arrays.copyOfRange(data, 12, questionEndIndex);
    }

    public static byte[] createResponse(DNSMessage request) {
        int responseSize = request.questionEndIndex + 16;
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort((short) 1234); // Transaction ID
        responseBuffer.putShort((short) 0x8180); // Flags: Response, No error
        responseBuffer.putShort((short) 1); // QDCOUNT (1 question)
        responseBuffer.putShort((short) 1); // ANCOUNT (1 answer)
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT


        responseBuffer.put(request.questionSection);

        responseBuffer.put(request.questionSection); // Name (use same format from question)
        responseBuffer.putShort((short) 1); // Type (A)
        responseBuffer.putShort((short) 1); // Class (IN)
        responseBuffer.putInt(60); // TTL (60 seconds)
        responseBuffer.putShort((short) 4); // Length (IPv4 address size)
        responseBuffer.put(new byte[]{8, 8, 8, 8}); // Data (IP: 8.8.8.8)

        return responseBuffer.array();
    }

    public short getTransactionId() {
        return transactionId;
    }
}
