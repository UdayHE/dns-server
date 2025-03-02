import java.nio.ByteBuffer;

public class Question {
    private String name;
    private short recordType;
    private short recordClass;

    public Question(String name, short recordType, short recordClass) {
        this.name = name;
        this.recordType = recordType;
        this.recordClass = recordClass;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(domainToBytes(name));
        buffer.putShort(recordType);
        buffer.putShort(recordClass);
        byte[] result = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(result);
        return result;
    }

    private byte[] domainToBytes(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        String[] labels = domain.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes());
        }
        buffer.put((byte) 0); // End of domain
        byte[] result = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(result);
        return result;
    }
}
