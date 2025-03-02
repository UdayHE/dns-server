import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {
    private final short transactionId;
    private final short flags;
    private final byte opcode;
    private final boolean recursionDesired;
    private final byte responseCode;
    private final List<byte[]> questionSections;
    private final int answerCount;

    public DNSMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();  // Extract ID
        this.flags = buffer.getShort(); // Extract Flags

        this.opcode = (byte) ((this.flags >> 11) & 0x0F);
        this.recursionDesired = ((this.flags >> 8) & 1) == 1;
        this.responseCode = (this.opcode == 0) ? (byte) 0 : (byte) 4;

        int qdCount = buffer.getShort() & 0xFFFF;
        this.answerCount = buffer.getShort() & 0xFFFF;
        buffer.getShort(); // Skip NSCOUNT
        buffer.getShort(); // Skip ARCOUNT

        this.questionSections = new ArrayList<>();
        int offset = 12;

        for (int i = 0; i < qdCount; i++) {
            byte[] question = parseDomainName(data, offset);
            offset += question.length + 4; // Skip QTYPE + QCLASS
            this.questionSections.add(question);
        }
    }

    private static byte[] parseDomainName(byte[] data, int offset) {
        List<Byte> name = new ArrayList<>();
        while (data[offset] != 0) {
            if ((data[offset] & 0xC0) == 0xC0) { // Handle compression
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

        short transactionId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        short nsCount = resolverBuffer.getShort();
        short arCount = resolverBuffer.getShort();

        System.out.println("ANCOUNT: " + anCount + ", NSCOUNT: " + nsCount + ", ARCOUNT: " + arCount);

        // Skip the question section
        int questionSectionSize = request.questionSections.stream().mapToInt(q -> q.length + 4).sum();
        resolverBuffer.position(12 + questionSectionSize);

        List<byte[]> answers = new ArrayList<>();
        if (anCount > 0) {
            for (int i = 0; i < anCount; i++) {
                int answerStart = resolverBuffer.position();

                // Skip the name field (assuming it's compressed)
                resolverBuffer.getShort();

                short answerType = resolverBuffer.getShort();
                short answerClass = resolverBuffer.getShort();
                int ttl = resolverBuffer.getInt();
                short dataLength = resolverBuffer.getShort();

                if (answerType == 1 && dataLength == 4) { // IPv4 address
                    byte[] ipv4Address = new byte[4];
                    resolverBuffer.get(ipv4Address);
                    System.out.println("✅ Extracted IPv4 Address: " +
                            (ipv4Address[0] & 0xFF) + "." + (ipv4Address[1] & 0xFF) + "." +
                            (ipv4Address[2] & 0xFF) + "." + (ipv4Address[3] & 0xFF));

                    // Construct the answer section
                    ByteBuffer answerBuffer = ByteBuffer.allocate(16); // 12 bytes for header + 4 bytes for IPv4
                    answerBuffer.putShort((short) 0xC00C); // Compressed name pointer to the question section
                    answerBuffer.putShort(answerType);
                    answerBuffer.putShort(answerClass);
                    answerBuffer.putInt(ttl);
                    answerBuffer.putShort(dataLength);
                    answerBuffer.put(ipv4Address);

                    answers.add(answerBuffer.array());
                } else {
                    // Skip non-A records or invalid data lengths
                    resolverBuffer.position(resolverBuffer.position() + dataLength);
                }
            }
        } else {
            System.out.println("⚠ No answers in resolver response.");
        }

        // Construct the final response
        int responseSize = 12 + questionSectionSize + answers.stream().mapToInt(a -> a.length).sum();
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort(transactionId);
        responseBuffer.putShort(responseFlags);
        responseBuffer.putShort(qdCount);
        responseBuffer.putShort((short) answers.size());
        responseBuffer.putShort(nsCount);
        responseBuffer.putShort(arCount);

        // Add the question section
        for (byte[] question : request.questionSections) {
            responseBuffer.put(question);
            responseBuffer.putShort((short) 1); // Type A
            responseBuffer.putShort((short) 1); // Class IN
        }

        // Add the answer section
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
