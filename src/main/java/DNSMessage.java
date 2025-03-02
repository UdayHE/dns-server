import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNSMessage {
    private final byte[] rawData;
    private final short transactionId;
    private final short flags;
    private final short questionCount;
    private final short answerCount;
    private final short authorityCount;
    private final short additionalCount;
    
    public DNSMessage(byte[] data) {
        this.rawData = data;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        this.transactionId = buffer.getShort();
        this.flags = buffer.getShort();
        this.questionCount = buffer.getShort();
        this.answerCount = buffer.getShort();
        this.authorityCount = buffer.getShort();
        this.additionalCount = buffer.getShort();
    }

    public static byte[] createResponse(DNSMessage request) {
        byte[] response = Arrays.copyOf(request.rawData, request.rawData.length);

        response[2] |= (byte) (1 << 7);
        response[3] = 0;

        return response;
    }

    public short getTransactionId() {
        return transactionId;
    }

    public short getFlags() {
        return flags;
    }

    public short getQuestionCount() {
        return questionCount;
    }
}
