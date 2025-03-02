import java.nio.ByteBuffer;

class DnsPacketHeader {

    private final int id;
    private final int opcode;
    private final int recursionDesired;
    private final int questionCount;
    private final int answerCount;

    public DnsPacketHeader(int id, int opcode, int recursionDesired, int questionCount, int answerCount) {
        this.id = id;
        this.opcode = opcode;
        this.recursionDesired = recursionDesired;
        this.questionCount = questionCount;
        this.answerCount = answerCount;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putShort((short) id);
        buffer.put((byte) ((1 << 7) | (opcode << 3) | recursionDesired));
        buffer.put((byte) 0);
        buffer.putShort((short) questionCount);
        buffer.putShort((short) answerCount);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        return buffer.array();
    }

    public static DnsPacketHeader fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int id = buffer.getShort() & 0xFFFF;
        byte flags1 = buffer.get();
        int opcode = (flags1 >> 3) & 0x0F;
        int recursionDesired = flags1 & 0x01;
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;
        buffer.getShort();
        buffer.getShort();
        return new DnsPacketHeader(id, opcode, recursionDesired, questionCount, answerCount);
    }
}