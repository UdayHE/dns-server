import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSMessage {
    private final byte[] rawData;
    private final short transactionId;
    private final byte[] questionSection;
    private final int questionEndIndex;

    public DNSMessage(byte[] data) {
        this.rawData = data;
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();  // ID
        buffer.getShort(); // Flags
        buffer.getShort(); // QDCOUNT
        buffer.getShort(); // ANCOUNT
        buffer.getShort(); // NSCOUNT
        buffer.getShort(); // ARCOUNT

        // Extract question section (Variable-length domain name)
        int index = 12; // Start after the header
        while (data[index] != 0) { index++; } // Domain name ends with 0x00
        index += 5; // Move past null byte + QTYPE (2 bytes) + QCLASS (2 bytes)
        this.questionEndIndex = index;

        this.questionSection = Arrays.copyOfRange(data, 12, questionEndIndex);
    }

    public static byte[] createResponse(DNSMessage request) {
        int questionLength = request.questionSection.length;
        int answerLength = questionLength + 10 + 4; // Name + Type + Class + TTL + Length + IP
        int responseSize = 12 + questionLength + answerLength;

        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort((short) 1234); // Transaction ID
        responseBuffer.putShort((short) 0x8180); // Flags: Response, No error
        responseBuffer.putShort((short) 1); // QDCOUNT (1 question)
        responseBuffer.putShort((short) 1); // ANCOUNT (1 answer)
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        responseBuffer.put(request.questionSection);

        responseBuffer.put(request.questionSection); // Name (Same as question)
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
