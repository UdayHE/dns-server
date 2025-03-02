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
        int responseSize = Math.min(resolverResponse.length, MAX_UDP_SIZE);
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseSize);


        // Ensure transaction ID matches original request
        responseBuffer.putShort(transactionId); // Set the correct transaction ID
        responseBuffer.putShort(responseFlags);
        responseBuffer.putShort(qdCount);
        responseBuffer.putShort(anCount);
        responseBuffer.putShort(nsCount);
        responseBuffer.putShort(arCount);

        // Copy original question section (avoid mismatches)
        for (Question question : questions) {
            byte[] questionBytes = question.toBytes();
            if (responseBuffer.remaining() < questionBytes.length) break;
            responseBuffer.put(questionBytes);
        }

        // Copy resolver answer section (ensuring no truncation)
        int answerSectionSize = Math.min(resolverBuffer.remaining(), responseBuffer.remaining());
        if (answerSectionSize > 0) {
            byte[] answerData = new byte[answerSectionSize];
            resolverBuffer.get(answerData, 0, answerSectionSize);

            // Only copy non-zero bytes to avoid unnecessary padding
            int lastNonZeroIndex = answerData.length - 1;
            while (lastNonZeroIndex > 0 && answerData[lastNonZeroIndex] == 0) {
                lastNonZeroIndex--;
            }

            byte[] trimmedAnswerData = new byte[lastNonZeroIndex + 1];
            System.arraycopy(answerData, 0, trimmedAnswerData, 0, trimmedAnswerData.length);
            responseBuffer.put(trimmedAnswerData);
        }


        // Ensure response doesn't exceed 512 bytes
        if (responseBuffer.position() > MAX_UDP_SIZE) {
            System.out.println("[Warning] Response exceeded 512 bytes, setting truncation flag.");
            responseBuffer.putShort(2, (short) (responseBuffer.getShort(2) | 0x0200)); // Set TC flag
        }

        // Debugging Output
        System.out.println("[Debug] Response Size: " + responseBuffer.position());
        System.out.println("[Debug] Transaction ID: " + transactionId);
        System.out.println("[Debug] Response Flags (Unsigned): " + (responseFlags & 0xFFFF));
        System.out.println("[Debug] Question Count: " + qdCount);
        System.out.println("[Debug] Answer Count: " + anCount);
        System.out.println("[Debug] Raw Resolver Response: " + java.util.Arrays.toString(resolverResponse));

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.rewind();
        responseBuffer.get(response, 0, response.length);

        // Trim extra padding if response is exactly 512 bytes (potential issue)
        if (response.length == MAX_UDP_SIZE) {
            int lastNonZeroIndex = response.length - 1;
            while (lastNonZeroIndex > HEADER_SIZE && response[lastNonZeroIndex] == 0) {
                lastNonZeroIndex--;
            }
            byte[] trimmedResponse = new byte[lastNonZeroIndex + 1];
            System.arraycopy(response, 0, trimmedResponse, 0, trimmedResponse.length);
            response = trimmedResponse;
        }

        System.out.println("[Debug] Final Trimmed Response Size: " + response.length);
        System.out.println("[Debug] First 50 Bytes of Trimmed Response: " + java.util.Arrays.toString(java.util.Arrays.copyOf(response, Math.min(50, response.length))));
        return response;
    }
}
