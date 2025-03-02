import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {
    private final short transactionId;
    private final short flags;
    private final short qdCount;
    private final short anCount;
    private final short nsCount;
    private final short arCount;
    private final List<byte[]> questionSections;
    private final List<byte[]> answerSections;

    public DNSMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        this.transactionId = buffer.getShort();
        this.flags = buffer.getShort();
        this.qdCount = buffer.getShort();
        this.anCount = buffer.getShort();
        this.nsCount = buffer.getShort();
        this.arCount = buffer.getShort();

        this.questionSections = new ArrayList<>();
        this.answerSections = new ArrayList<>();

        int offset = 12; // Start after the header

        // Read question section
        for (int i = 0; i < qdCount; i++) {
            byte[] question = parseDomainName(data, offset);
            offset += question.length + 4; // Skip QTYPE and QCLASS
            this.questionSections.add(question);
        }
    }

    private static byte[] parseDomainName(byte[] data, int offset) {
        List<Byte> name = new ArrayList<>();
        while (data[offset] != 0) {
            if ((data[offset] & 0xC0) == 0xC0) { // Compressed name
                int pointer = ((data[offset] & 0x3F) << 8) | (data[offset + 1] & 0xFF);
                return parseDomainName(data, pointer); // Follow the pointer
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

        // Extract Header Fields
        short transactionId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        short nsCount = resolverBuffer.getShort();
        short arCount = resolverBuffer.getShort();

        // Debug Logs
        System.out.println("ANCOUNT: " + anCount + ", NSCOUNT: " + nsCount + ", ARCOUNT: " + arCount);

        // Skip Question Section
        int questionSectionSize = request.questionSections.stream().mapToInt(q -> q.length + 4).sum();
        resolverBuffer.position(12 + questionSectionSize);

        // Extract Answer Section Properly
        List<byte[]> answers = new ArrayList<>();
        if (anCount > 0) {
            for (int i = 0; i < anCount; i++) {
                if (resolverBuffer.remaining() < 12) { // Minimum DNS record size
                    System.out.println("❌ Not enough bytes left for answer section!");
                    return buildErrorResponse(request);
                }

                int answerStart = resolverBuffer.position();
                short nameField = resolverBuffer.getShort(); // Read name (could be pointer)

                // Handle Compression Pointer
                if ((nameField & 0xC000) == 0xC000) {
                    int pointer = nameField & 0x3FFF;
                    System.out.println("📌 Processing name pointer at: " + pointer);
                }

                short answerType = resolverBuffer.getShort();
                short answerClass = resolverBuffer.getShort();
                int ttl = resolverBuffer.getInt();
                int dataLength = resolverBuffer.getShort() & 0xFFFF;

                if (resolverBuffer.remaining() < dataLength) {
                    System.out.println("❌ Error: Not enough bytes left for answer data!");
                    return buildErrorResponse(request);
                }

                byte[] answerData = new byte[dataLength];
                resolverBuffer.get(answerData);

                // Debug: Extracted IPv4 Address
                if (answerType == 1 && dataLength == 4) {
                    System.out.println(" Extracted IPv4 Address: " +
                            (answerData[0] & 0xFF) + "." + (answerData[1] & 0xFF) + "." +
                            (answerData[2] & 0xFF) + "." + (answerData[3] & 0xFF));
                }

                // Store Full Answer Section
                int answerSize = resolverBuffer.position() - answerStart;
                ByteBuffer answerBuffer = ByteBuffer.allocate(answerSize);
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

    private static byte[] buildErrorResponse(DNSMessage request) {
        ByteBuffer errorBuffer = ByteBuffer.allocate(12 + request.questionSections.stream().mapToInt(q -> q.length + 4).sum());
        errorBuffer.putShort(request.transactionId);
        errorBuffer.putShort((short) 0x8182); // Server Failure (RCODE=2)
        errorBuffer.putShort(request.qdCount);
        errorBuffer.putShort((short) 0); // ANCOUNT = 0
        errorBuffer.putShort((short) 0); // NSCOUNT = 0
        errorBuffer.putShort((short) 0); // ARCOUNT = 0

        for (byte[] question : request.questionSections) {
            errorBuffer.put(question);
            errorBuffer.putShort((short) 1); // Type (A)
            errorBuffer.putShort((short) 1); // Class (IN)
        }

        return errorBuffer.array();
    }


    private static byte[] listToByteArray(List<Byte> list) {
        byte[] array = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) array[i] = list.get(i);
        return array;
    }
}