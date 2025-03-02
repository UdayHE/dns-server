//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.List;
//
//class Question {
//
//    private final String name;
//
//    public Question(String name) {
//        this.name = name;
//    }
//
//    public String getName() {
//        return name;
//    }
//
//    public int getByteSize() {
//        return domainToBytes(name).length + 4;  // Domain name bytes + 2 (Type) + 2 (Class)
//    }
//
//
//    public byte[] toBytesUncompressed() {
//        ByteBuffer buffer = ByteBuffer.allocate(512);
//        byte[] nameBytes = domainToBytes(name);
//        buffer.put(nameBytes);
//        buffer.putShort((short) 1);
//        buffer.putShort((short) 1);
//        byte[] result = new byte[buffer.position()];
//        buffer.flip();
//        buffer.get(result);
//        return result;
//    }
//
//    public static List<Question> fromBytes(int count, byte[] bytes, int offset) {
//        List<Question> questions = new ArrayList<>();
//        int currentOffset = offset;
//
//        for (int i = 0; i < count; i++) {
//            String name = expandCompressedName(bytes, currentOffset);
//            int endOffset = findEndOfName(bytes, currentOffset);
//            currentOffset = endOffset + 1;
//            currentOffset += 4;
//            questions.add(new Question(name));
//        }
//
//        return questions;
//    }
//
//    public static byte[] domainToBytes(String domain) {
//        ByteBuffer buffer = ByteBuffer.allocate(512);
//        String[] labels = domain.split("\\.");
//        for (String label : labels) {
//            buffer.put((byte) label.length());
//            buffer.put(label.getBytes(StandardCharsets.UTF_8));
//        }
//        buffer.put((byte) 0);
//        byte[] result = new byte[buffer.position()];
//        buffer.flip();
//        buffer.get(result);
//        return result;
//    }
//
//    public static String expandCompressedName(byte[] bytes, int offset) {
//        StringBuilder name = new StringBuilder();
//        int currentOffset = offset;
//        boolean isCompressed = false;
//
//        while (true) {
//            int length = bytes[currentOffset] & 0xFF;
//
//            if (length == 0) {
//                break;  // End of domain name
//            }
//
//            if ((length & 0xC0) == 0xC0) {  // Compression pointer
//                int pointer = ((length & 0x3F) << 8) | (bytes[currentOffset + 1] & 0xFF);
//                if (!isCompressed) {
//                    isCompressed = true;
//                }
//                name.append(expandCompressedName(bytes, pointer));
//                break;
//            } else {
//                if (!name.isEmpty()) {
//                    name.append(".");
//                }
//                name.append(new String(bytes, currentOffset + 1, length, StandardCharsets.UTF_8));
//                currentOffset += length + 1;
//            }
//        }
//
//        return name.toString();
//    }
//
//    public static int findEndOfName(byte[] bytes, int offset) {
//        int currentOffset = offset;
//
//        while (true) {
//            int length = bytes[currentOffset] & 0xFF;
//
//            if (length == 0) {
//                return currentOffset;
//            }
//
//            if ((length & 0xC0) == 0xC0) { // Compression pointer
//                return currentOffset + 1;
//            }
//
//            currentOffset += length + 1;
//        }
//    }
//
//}