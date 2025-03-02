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

        // Extract Transaction ID and Flags
        short transactionId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();

        // Extract QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        short nsCount = resolverBuffer.getShort();
        short arCount = resolverBuffer.getShort();

        // Debug: Print extracted counts
        System.out.println("ANCOUNT: " + anCount + ", NSCOUNT: " + nsCount + ", ARCOUNT: " + arCount);

        // Skip the question section
        int questionSectionSize = request.questionSections.stream().mapToInt(q -> q.length + 4).sum();
        resolverBuffer.position(12 + questionSectionSize);

        // Extract the Answer Section (Only if ANCOUNT > 0)
        List<byte[]> answers = new ArrayList<>();
        if (anCount > 0) {
            for (int i = 0; i < anCount; i++) {
                int remainingBytes = resolverBuffer.remaining();
                if (remainingBytes < 12) {  // Minimum size of an answer
                    System.out.println("❌ Not enough bytes left for answer section! Skipping.");
                    break;
                }

                int answerStart = resolverBuffer.position();  // Track the answer section start

                // Read Name (Handling Compression)
                byte[] nameField = new byte[2];
                resolverBuffer.get(nameField);
                if ((nameField[0] & 0xC0) == 0xC0) {
                    int pointer = ((nameField[0] & 0x3F) << 8) | (nameField[1] & 0xFF);
                    System.out.println("📌 Compressed Name Pointer: " + pointer);
                }

                short answerType = resolverBuffer.getShort();
                short answerClass = resolverBuffer.getShort();
                int ttl = resolverBuffer.getInt();
                int dataLength = resolverBuffer.getShort() & 0xFFFF; // Convert unsigned

                if (resolverBuffer.remaining() < dataLength) {
                    System.out.println("❌ Not enough bytes left for answer data! Expected: " + dataLength + ", Available: " + resolverBuffer.remaining());
                    break;
                }

                byte[] answerData = new byte[dataLength];
                resolverBuffer.get(answerData);

                // Debug: Print extracted Answer IP
                if (answerType == 1 && dataLength == 4) { // IPv4 Only
                    System.out.println("Extracted IPv4 Address: " +
                            (answerData[0] & 0xFF) + "." + (answerData[1] & 0xFF) + "." +
                            (answerData[2] & 0xFF) + "." + (answerData[3] & 0xFF));
                }

                // Store full answer
                ByteBuffer answerBuffer = ByteBuffer.allocate(resolverBuffer.position() - answerStart);
                resolverBuffer.position(answerStart);
                resolverBuffer.get(answerBuffer.array());
                answers.add(answerBuffer.array());
            }
        } else {
            System.out.println("⚠ No answers in resolver response.");
        }

        // Correctly Allocate Response Buffer
        int responseSize = 12 + questionSectionSize + answers.stream().mapToInt(a -> a.length).sum();
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort(transactionId); // Copy ID
        responseBuffer.putShort(responseFlags); // Copy Flags

        responseBuffer.putShort(qdCount); // QDCOUNT
        responseBuffer.putShort((short) answers.size()); // Correct ANCOUNT
        responseBuffer.putShort(nsCount); // Copy NSCOUNT
        responseBuffer.putShort(arCount); // Copy ARCOUNT

        // Add the Question Section
        for (byte[] question : request.questionSections) {
            responseBuffer.put(question);
            responseBuffer.putShort((short) 1); // Type (A)
            responseBuffer.putShort((short) 1); // Class (IN)
        }

        // Add the Correct Answer Section
        for (byte[] answer : answers) {
            responseBuffer.put(answer);
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
