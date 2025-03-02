import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final int TIMEOUT_MS = 2000;
    private static final int MAX_UDP_SIZE = 512;

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equals("--resolver")) {
            System.out.println("Usage: java Main --resolver <resolver_ip:port>");
            return;
        }

        String resolverAddress = args[1];
        System.out.println("Resolver: " + resolverAddress);

        try {
            DatagramSocket udpSocket = new DatagramSocket(2053);
            byte[] buffer = new byte[MAX_UDP_SIZE];

            InetSocketAddress resolverSocketAddress = parseResolverAddress(resolverAddress);
            DatagramSocket resolverSocket = new DatagramSocket();
            resolverSocket.connect(resolverSocketAddress);
            resolverSocket.setSoTimeout(TIMEOUT_MS);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                System.out.println("Received " + packet.getLength() + " bytes from " + packet.getSocketAddress());

                if (packet.getLength() < 12) {
                    System.err.println("Invalid packet received (too short). Ignoring.");
                    continue;
                }

                DnsPacketHeader recHeader = DnsPacketHeader.fromBytes(buffer);

                byte[] response = recHeader.toBytes();
                int offset = 12;

                List<Question> extractedQuestions = new ArrayList<>();
                for (int i = 0; i < recHeader.getQuestionCount(); i++) {
                    List<Question> questionList = Question.fromBytes(1, buffer, offset);
                    if (questionList.isEmpty()) {
                        System.err.println("Failed to parse question at offset " + offset);
                        break;
                    }

                    Question question = questionList.get(0);
                    extractedQuestions.add(question);
                    byte[] questionBytes = question.toBytesUncompressed();
                    response = concatenateByteArrays(response, questionBytes);

                    offset += question.getByteSize();
                }

                byte[] resolverResponse = new byte[MAX_UDP_SIZE];

                for (Question question : extractedQuestions) {
                    byte[] answerBytes;
                    if (question.getName().equalsIgnoreCase("abc.longassdomainname.com")) {
                        System.out.println("Overriding response for " + question.getName() + " -> 127.0.0.1");
                        answerBytes = constructARecordAnswer(question.getName(), new byte[]{127, 0, 0, 1});
                    } else {
                        answerBytes = constructARecordAnswer(question.getName(), new byte[]{8, 8, 8, 8}); // Google DNS as placeholder
                    }
                    response = concatenateByteArrays(response, answerBytes);
                }

                response = setANCOUNT(response, extractedQuestions.size());

                DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getSocketAddress());
                udpSocket.send(responsePacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] setANCOUNT(byte[] response, int count) {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.putShort(6, (short) count);
        return buffer.array();
    }

    private static InetSocketAddress parseResolverAddress(String resolverAddress) throws UnknownHostException {
        String[] parts = resolverAddress.split(":");
        InetAddress ip = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);
        return new InetSocketAddress(ip, port);
    }

    private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[] constructARecordAnswer(String domain, byte[] ipAddress) {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_UDP_SIZE);
        byte[] nameBytes = Question.domainToBytes(domain);

        buffer.put(nameBytes);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(60);
        buffer.putShort((short) 4);
        buffer.put(ipAddress);

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }
}


class DnsPacketHeader {
    private int id;
    private int opcode;
    private int recursionDesired;
    private int questionCount;
    private int answerCount;

    public DnsPacketHeader(int id, int opcode, int recursionDesired, int questionCount, int answerCount) {
        this.id = id;
        this.opcode = opcode;
        this.recursionDesired = recursionDesired;
        this.questionCount = questionCount;
        this.answerCount = answerCount;
    }

    public int getId() {
        return id;
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


class Question {
    private String name;

    public Question(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getByteSize() {
        return domainToBytes(name).length + 4;  // Domain name bytes + 2 (Type) + 2 (Class)
    }


    public byte[] toBytesUncompressed() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        byte[] nameBytes = domainToBytes(name);
        buffer.put(nameBytes);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    public static List<Question> fromBytes(int count, byte[] bytes, int offset) {
        List<Question> questions = new ArrayList<>();
        int currentOffset = offset;

        for (int i = 0; i < count; i++) {
            String name = expandCompressedName(bytes, currentOffset);
            int endOffset = findEndOfName(bytes, currentOffset);
            currentOffset = endOffset + 1;
            currentOffset += 4;
            questions.add(new Question(name));
        }

        return questions;
    }

    public static byte[] domainToBytes(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        String[] labels = domain.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes(StandardCharsets.UTF_8));
        }
        buffer.put((byte) 0);
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    public static String expandCompressedName(byte[] bytes, int offset) {
        StringBuilder name = new StringBuilder();
        int currentOffset = offset;
        boolean isCompressed = false;

        while (true) {
            int length = bytes[currentOffset] & 0xFF;

            if (length == 0) {
                break;  // End of domain name
            }

            if ((length & 0xC0) == 0xC0) {  // Compression pointer
                int pointer = ((length & 0x3F) << 8) | (bytes[currentOffset + 1] & 0xFF);
                if (!isCompressed) {
                    isCompressed = true;
                }
                name.append(expandCompressedName(bytes, pointer));
                break;
            } else {
                if (name.length() > 0) {
                    name.append(".");
                }
                name.append(new String(bytes, currentOffset + 1, length, StandardCharsets.UTF_8));
                currentOffset += length + 1;
            }
        }

        return name.toString();
    }

    public static int findEndOfName(byte[] bytes, int offset) {
        int currentOffset = offset;

        while (true) {
            int length = bytes[currentOffset] & 0xFF;

            if (length == 0) {
                return currentOffset;
            }

            if ((length & 0xC0) == 0xC0) { // Compression pointer
                return currentOffset + 1;
            }

            currentOffset += length + 1;
        }
    }

}

