import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {
    private short transactionId;
    private short flags;
    private short questionCount;
    private short answerCount;
    private short authorityCount;
    private short additionalCount;
    private List<Question> questions;

    public DNSMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        this.transactionId = buffer.getShort();
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
        int position = buffer.position();

        while (true) {
            int length = buffer.get() & 0xFF;
            if (length == 0) break;

            // Handle name compression (first two bits set to 11)
            if ((length & 0xC0) == 0xC0) {
                int pointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                int originalPosition = buffer.position();
                buffer.position(pointer);
                domainName.append(parseDomainName(buffer, data));
                buffer.position(originalPosition);
                break;
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

        short responseId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        short nsCount = resolverBuffer.getShort();
        short arCount = resolverBuffer.getShort();

        int estimatedSize = resolverResponse.length + 100;  // Ensure buffer is larger
        if (estimatedSize > 512) estimatedSize = 512;  // Truncate oversized responses

        ByteBuffer responseBuffer = ByteBuffer.allocate(estimatedSize);
        responseBuffer.putShort(transactionId); // Maintain the original ID
        responseBuffer.putShort(responseFlags);
        responseBuffer.putShort(qdCount);
        responseBuffer.putShort(anCount);
        responseBuffer.putShort(nsCount);
        responseBuffer.putShort(arCount);

        // Copy question section from original request
        for (Question question : questions) {
            byte[] questionBytes = question.toBytes();
            if (responseBuffer.remaining() < questionBytes.length) break; // Prevent overflow
            responseBuffer.put(questionBytes);
        }

        // Copy answer section from resolver (ensure all answers are included)
        int remainingBytes = resolverBuffer.remaining();
        if (remainingBytes > 0) {
            byte[] answerData = new byte[Math.min(remainingBytes, responseBuffer.remaining())]; // Prevent overflow
            resolverBuffer.get(answerData);
            responseBuffer.put(answerData);
        }

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.rewind();
        responseBuffer.get(response);
        return response;
    }
}
