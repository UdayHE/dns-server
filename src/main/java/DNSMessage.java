import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {
    private final short transactionId;
    private final short flags;
    private final byte opcode;
    private final boolean recursionDesired;
    private final byte responseCode;
    private final List<byte[]> questionSections; // List of all question sections
    private final int answerCount;

    public DNSMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();  // Extract ID
        this.flags = buffer.getShort(); // Extract Flags

        this.opcode = (byte) ((this.flags >> 11) & 0x0F); // Extract OPCODE (4 bits)
        this.recursionDesired = ((this.flags >> 8) & 1) == 1; // Extract RD flag
        this.responseCode = (this.opcode == 0) ? (byte) 0 : (byte) 4; // RCODE: 0 (Success) or 4 (Not Implemented)

        int qdCount = buffer.getShort() & 0xFFFF; // QDCOUNT
        this.answerCount = qdCount; // ANCOUNT should match QDCOUNT
        buffer.getShort(); // Skip NSCOUNT
        buffer.getShort(); // Skip ARCOUNT

        this.questionSections = new ArrayList<>();
        int offset = 12; // Start after header

        for (int i = 0; i < qdCount; i++) {
            byte[] question = parseDomainName(data, offset);
            offset += question.length + 4; // Skip QTYPE + QCLASS
            this.questionSections.add(question);
        }
    }

    private static byte[] parseDomainName(byte[] data, int offset) {
        List<Byte> name = new ArrayList<>();
        while (data[offset] != 0) {
            if ((data[offset] & 0xC0) == 0xC0) { // Compressed name
                int pointer = ((data[offset] & 0x3F) << 8) | (data[offset + 1] & 0xFF);
                name.addAll(byteArrayToList(parseDomainName(data, pointer)));
                return listToByteArray(name);
            } else {
                name.add(data[offset]);
                for (int i = 0; i < data[offset]; i++) {
                    name.add(data[offset + 1 + i]);
                }
                offset += data[offset] + 1;
            }
        }
        name.add((byte) 0);
        return listToByteArray(name);
    }

    public static byte[] createResponse(DNSMessage request) {
        int responseSize = 12 + request.questionSections.stream().mapToInt(q -> q.length + 4).sum()
                + request.answerCount * (request.questionSections.get(0).length + 16);

        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort(request.transactionId); // Copy ID from request

        // Set QR bit to 1 (response) and other flags
        short responseFlags = (short) (0x8000 | (request.opcode << 11) | (request.recursionDesired ? 0x0100 : 0) | request.responseCode);
        responseBuffer.putShort(responseFlags); // Flags: QR=1, OPCODE mirrored, RD mirrored, RCODE set

        responseBuffer.putShort((short) request.questionSections.size()); // QDCOUNT
        responseBuffer.putShort((short) request.answerCount); // ANCOUNT (same as QDCOUNT)
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        for (byte[] question : request.questionSections) {
            responseBuffer.put(question);
            responseBuffer.putShort((short) 1); // Type (A)
            responseBuffer.putShort((short) 1); // Class (IN)
        }

        for (byte[] question : request.questionSections) {
            responseBuffer.put(question); // Name (Uncompressed)
            responseBuffer.putShort((short) 1); // Type (A)
            responseBuffer.putShort((short) 1); // Class (IN)
            responseBuffer.putInt(60); // TTL (60 seconds)
            responseBuffer.putShort((short) 4); // Length (IPv4 address size)
        }

        return responseBuffer.array();
    }

    private static List<Byte> byteArrayToList(byte[] array) {
        List<Byte> list = new ArrayList<>();
        for (byte b : array) list.add(b);
        return list;
    }

    private static byte[] listToByteArray(List<Byte> list) {
        byte[] array = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) array[i] = list.get(i);
        return array;
    }

}
