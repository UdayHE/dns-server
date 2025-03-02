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

        int questionSectionSize = request.questionSections.stream().mapToInt(q -> q.length + 4).sum();
        resolverBuffer.position(12 + questionSectionSize);

        List<byte[]> answers = new ArrayList<>();
        if (anCount > 0) {
            for (int i = 0; i < anCount; i++) {
                int remainingBytes = resolverBuffer.remaining();
                if (remainingBytes < 12) {
                    System.out.println("❌ Not enough bytes left for answer section! Skipping.");
                    break;
                }

                int answerStart = resolverBuffer.position();

                byte[] nameField = new byte[2];
                resolverBuffer.get(nameField);
                if ((nameField[0] & 0xC0) == 0xC0) { // Handle compressed names
                    int pointer = ((nameField[0] & 0x3F) << 8) | (nameField[1] & 0xFF);
                    System.out.println("📌 Compressed Name Pointer: " + pointer);
                }

                short answerType = resolverBuffer.getShort();
                short answerClass = resolverBuffer.getShort();
                int ttl = resolverBuffer.getInt();
                int dataLength = resolverBuffer.getShort() & 0xFFFF;

                if (resolverBuffer.remaining() < dataLength) {
                    System.out.println("❌ Not enough bytes left for answer data! Expected: " + dataLength + ", Available: " + resolverBuffer.remaining());
                    break;
                }

                byte[] answerData = new byte[dataLength];
                resolverBuffer.get(answerData);

                if (answerType == 1 && dataLength == 4) { // Extract IPv4
                    System.out.println("✅ Extracted IPv4 Address: " +
                            (answerData[0] & 0xFF) + "." + (answerData[1] & 0xFF) + "." +
                            (answerData[2] & 0xFF) + "." + (answerData[3] & 0xFF));
                }

                ByteBuffer answerBuffer = ByteBuffer.allocate(resolverBuffer.position() - answerStart);
                resolverBuffer.position(answerStart);
                resolverBuffer.get(answerBuffer.array());
                answers.add(answerBuffer.array());
            }
        } else {
            System.out.println("⚠ No answers in resolver response.");
        }

        int responseSize = 12 + questionSectionSize + answers.stream().mapToInt(a -> a.length).sum();
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        responseBuffer.putShort(transactionId);
        responseBuffer.putShort(responseFlags);
        responseBuffer.putShort(qdCount);
        responseBuffer.putShort((short) answers.size());
        responseBuffer.putShort(nsCount);
        responseBuffer.putShort(arCount);

        for (byte[] question : request.questionSections) {
            responseBuffer.put(question);
            responseBuffer.putShort((short) 1);
            responseBuffer.putShort((short) 1);
        }

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
