import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {
    private static final int MAX_UDP_SIZE = 512;
    private static final int HEADER_SIZE = 12;

    private short transactionId;
    private short flags;
    private short questionCount;
    private short answerCount;
    private short authorityCount;
    private short additionalCount;
    private List<Question> questions;

    public DNSMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort(); // Extract Transaction ID
        this.flags = buffer.getShort();
        this.questionCount = buffer.getShort();
        this.answerCount = buffer.getShort();
        this.authorityCount = buffer.getShort();
        this.additionalCount = buffer.getShort();

        this.questions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            this.questions.add(parseQuestion(buffer, data));
        }
    }

    private Question parseQuestion(ByteBuffer buffer, byte[] data) {
        String name = parseDomainName(buffer, data);
        short recordType = buffer.getShort();
        short recordClass = buffer.getShort();
        return new Question(name, recordType, recordClass);
    }

    private String parseDomainName(ByteBuffer buffer, byte[] data) {
        StringBuilder domainName = new StringBuilder();

        while (true) {
            int length = buffer.get() & 0xFF;
            if (length == 0) break;

            // Handle name compression (first two bits set to 11)
            if ((length & 0xC0) == 0xC0) {
                int pointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                return parseDomainName(ByteBuffer.wrap(data, pointer, data.length - pointer), data);
            }

            if (domainName.length() > 0) domainName.append(".");
            byte[] label = new byte[length];
            buffer.get(label);
            domainName.append(new String(label));
        }
        return domainName.toString();
    }

    public byte[] createResponse(byte[] resolverResponse) {
        ByteBuffer resolverBuffer = ByteBuffer.wrap(resolverResponse);

        // Extract response header
        short responseId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        short nsCount = resolverBuffer.getShort();
        short arCount = resolverBuffer.getShort();

        // Allocate buffer dynamically
        int responseSize = resolverResponse.length;
        if (responseSize > MAX_UDP_SIZE) responseSize = MAX_UDP_SIZE; // Enforce UDP limit

        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);

        // **Ensure transaction ID matches original request**
        responseBuffer.putShort(transactionId);
        responseBuffer.putShort(responseFlags);
        responseBuffer.putShort(qdCount);
        responseBuffer.putShort(anCount);
        responseBuffer.putShort(nsCount);
        responseBuffer.putShort(arCount);

        // Copy original question section (to avoid mismatches)
        for (Question question : questions) {
            byte[] questionBytes = question.toBytes();
            if (responseBuffer.remaining() < questionBytes.length) break;
            responseBuffer.put(questionBytes);
        }

        // Copy resolver answer section (truncate if needed)
        int remainingBytes = resolverBuffer.remaining();
        if (remainingBytes > 0) {
            byte[] answerData = new byte[Math.min(remainingBytes, responseBuffer.remaining())];
            resolverBuffer.get(answerData);
            responseBuffer.put(answerData);
        }

        // **Set truncation flag if response exceeds limit**
        if (responseBuffer.position() > MAX_UDP_SIZE) {
            System.out.println("[Warning] Response exceeded 512 bytes, setting truncation flag.");
            responseBuffer.putShort(2, (short) (responseBuffer.getShort(2) | 0x0200)); // Set TC flag
        }

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.rewind();
        responseBuffer.get(response);
        return response;
    }
}
