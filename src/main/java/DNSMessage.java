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
        while (true) {
            int length = buffer.get() & 0xFF;
            if (length == 0) break;
            if ((length & 0xC0) == 0xC0) { // Handle compressed name
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

        short responseId = resolverBuffer.getShort();
        short responseFlags = resolverBuffer.getShort();
        short qdCount = resolverBuffer.getShort();
        short anCount = resolverBuffer.getShort();
        short nsCount = resolverBuffer.getShort();
        short arCount = resolverBuffer.getShort();

        ByteBuffer responseBuffer = ByteBuffer.allocate(512);
        responseBuffer.putShort(transactionId); // Maintain the original ID
        responseBuffer.putShort(responseFlags);
        responseBuffer.putShort(qdCount);
        responseBuffer.putShort(anCount);
        responseBuffer.putShort(nsCount);
        responseBuffer.putShort(arCount);

        // Copy question section from original request
        for (Question question : questions) {
            responseBuffer.put(question.toBytes());
        }

        // Copy answer section from resolver
        int remainingBytes = resolverBuffer.remaining();
        if (remainingBytes > 0) {
            byte[] answerData = new byte[remainingBytes];
            resolverBuffer.get(answerData);
            responseBuffer.put(answerData);
        }

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.rewind();
        responseBuffer.get(response);
        return response;
    }
}
