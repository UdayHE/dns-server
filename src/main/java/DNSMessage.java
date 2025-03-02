import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSMessage {
    private final short transactionId;
    private final short flags;
    private final byte opcode;
    private final boolean recursionDesired;
    private final byte responseCode;
    private final byte[] questionSection;
    private final int questionEndIndex;

    public DNSMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();  // Extract ID
        this.flags = buffer.getShort(); // Extract Flags

        this.opcode = (byte) ((this.flags >> 11) & 0x0F); // Extract OPCODE (4 bits)
        this.recursionDesired = ((this.flags >> 8) & 1) == 1; // Extract RD flag
        this.responseCode = (this.opcode == 0) ? (byte) 0 : (byte) 4; // RCODE: 0 (Success) or 4 (Not Implemented)

        buffer.getShort(); // Skip QDCOUNT
        buffer.getShort(); // Skip ANCOUNT
        buffer.getShort(); // Skip NSCOUNT
        buffer.getShort(); // Skip ARCOUNT

        // Extract question section
        int index = 12; // Start after header
        while (data[index] != 0) { index++; } // Find end of domain name
        index += 5; // Move past null byte + QTYPE (2 bytes) + QCLASS (2 bytes)
        this.questionEndIndex = index;

        this.questionSection = Arrays.copyOfRange(data, 12, questionEndIndex);
    }

    public static byte[] createResponse(DNSMessage request) {
        int questionLength = request.questionSection.length;
        int answerLength = questionLength + 10 + 4; // Name + Type + Class + TTL + Length + IP
        int responseSize = 12 + questionLength + answerLength; // Header + Question + Answer

        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        // 🔹 Header Section (12 bytes)
        responseBuffer.putShort(request.transactionId); // ✅ Copy ID from request
        short responseFlags = (short) (0x8000 | (request.opcode << 11) | (request.recursionDesired ? 0x0100 : 0) | request.responseCode);
        responseBuffer.putShort(responseFlags); // ✅ Flags: QR=1, OPCODE mirrored, RD mirrored, RCODE set
        responseBuffer.putShort((short) 1); // ✅ QDCOUNT (1 question)
        responseBuffer.putShort((short) 1); // ✅ ANCOUNT (1 answer)
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        // 🔹 Question Section (Copy from request)
        responseBuffer.put(request.questionSection);

        // 🔹 Answer Section (codecrafters.io -> 8.8.8.8)
        responseBuffer.put(request.questionSection); // Name (Use the same as in the question)
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
