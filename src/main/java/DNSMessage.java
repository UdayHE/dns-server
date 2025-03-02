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

    public static byte[] createResponse(DNSMessage request, byte[] resolverResponse) {
        ByteBuffer resolverBuffer = ByteBuffer.wrap(resolverResponse);

        // Extract Transaction ID and Flags from the resolver response
        short transactionId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();

        // Extract QDCOUNT and ANCOUNT
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        resolverBuffer.getShort(); // NSCOUNT (skip)
        resolverBuffer.getShort(); // ARCOUNT (skip)

        // Skip the question section
        int questionSectionSize = request.questionSections.stream().mapToInt(q -> q.length + 4).sum();
        resolverBuffer.position(12 + questionSectionSize);

        // Extract the Answer Section (Correct Offset Handling)
        byte[] namePointer = new byte[request.questionSections.get(0).length]; // Name field in answer
        resolverBuffer.get(namePointer);
        short answerType = resolverBuffer.getShort();
        short answerClass = resolverBuffer.getShort();
        int ttl = resolverBuffer.getInt();
        short dataLength = resolverBuffer.getShort();

        byte[] ipAddress = new byte[4];
        if (answerType == 1 && dataLength == 4) { // Ensure it's an IPv4 response
            resolverBuffer.get(ipAddress);
        } else {
            ipAddress = new byte[]{0, 0, 0, 0}; // Fallback if no valid IPv4 found
        }

        // Debugging: Print the extracted IPv4 address
        System.out.println("Extracted IPv4 Address: " +
                (ipAddress[0] & 0xFF) + "." + (ipAddress[1] & 0xFF) + "." +
                (ipAddress[2] & 0xFF) + "." + (ipAddress[3] & 0xFF));

        // Calculate response size correctly
        int responseSize = 12 + questionSectionSize + (namePointer.length + 16);
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort(transactionId); // Copy ID from resolver response
        responseBuffer.putShort(responseFlags); // Copy Flags from resolver response

        responseBuffer.putShort(qdCount); // QDCOUNT (number of questions)
        responseBuffer.putShort(anCount); // ANCOUNT (number of answers)
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        // Add the question section back
        for (byte[] question : request.questionSections) {
            responseBuffer.put(question);
            responseBuffer.putShort((short) 1); // Type (A)
            responseBuffer.putShort((short) 1); // Class (IN)
        }

        // Add the Answer Section
        responseBuffer.put(namePointer); // Name (Pointer or Uncompressed)
        responseBuffer.putShort(answerType); // Type (A)
        responseBuffer.putShort(answerClass); // Class (IN)
        responseBuffer.putInt(ttl); // TTL (Copied from resolver)
        responseBuffer.putShort((short) 4); // Length (IPv4 address size)
        responseBuffer.put(ipAddress); // Correct IPv4 address from resolver response

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
