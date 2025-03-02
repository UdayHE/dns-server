import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MS = 2000;

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equals("--resolver")) {
            System.out.println("Usage: java Main --resolver <resolver_ip:port>");
            return;
        }

        String resolverAddress = args[1];
        System.out.println("Resolver: " + resolverAddress);

        try {
            DatagramSocket udpSocket = new DatagramSocket(2053);
            byte[] buffer = new byte[512];

            InetSocketAddress resolverSocketAddress = parseResolverAddress(resolverAddress);
            DatagramSocket resolverSocket = new DatagramSocket();
            resolverSocket.connect(resolverSocketAddress);
            resolverSocket.setSoTimeout(TIMEOUT_MS);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                System.out.println("Received " + packet.getLength() + " bytes from " + packet.getSocketAddress());
                System.out.println(bytesToHex(buffer, packet.getLength()));

                if (packet.getLength() < 12) {
                    System.err.println("Invalid packet received (too short). Ignoring.");
                    continue;
                }

                DnsPacketHeader recHeader = DnsPacketHeader.fromBytes(buffer);
                System.out.println(recHeader);

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

                if (response.length > 0) {
                    byte[] resolverResponse = Arrays.copyOf(response, response.length);
                    byte[] modifiedResponse = setQRFlag(resolverResponse, response.length);

                    System.out.println("Forwarding actual resolver response to client: " + bytesToHex(modifiedResponse, modifiedResponse.length));
                    DatagramPacket responsePacket = new DatagramPacket(modifiedResponse, modifiedResponse.length, packet.getSocketAddress());
                    udpSocket.send(responsePacket);
                }


                // Modify the header to ensure QR = 1 (Response)
                byte[] modifiedResponse = setQRFlag(response, response.length);

                System.out.println("Forwarding modified response to client: " + bytesToHex(modifiedResponse, modifiedResponse.length));
                DatagramPacket responsePacket = new DatagramPacket(modifiedResponse, modifiedResponse.length, packet.getSocketAddress());
                udpSocket.send(responsePacket);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static byte[] setQRFlag(byte[] response, int length) {
        if (length < 2) {
            return response; // Safety check to ensure the packet is not malformed
        }

        // Set QR flag (bit 7 of first byte)
        response[2] = (byte) (response[2] | 0x80);

        return response;
    }

    private static InetSocketAddress parseResolverAddress(String resolverAddress) throws UnknownHostException {
        String[] parts = resolverAddress.split(":");
        InetAddress ip = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);
        return new InetSocketAddress(ip, port);
    }

    static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

}



class DnsPacketHeader {
    private int id; // Identifier
    private int qrIndicator; // Query/Response indicator (0 = query, 1 = response)
    private int opcode; // Operation code (4 bits)
    private int recursionDesired; // Recursion desired flag (1 bit)
    private int rcode; // Response code (4 bits)
    private int questionCount; // Number of questions
    private int answerCount; // Number of answers

    // Primary constructor for all fields
    public DnsPacketHeader(int id, int qrIndicator, int opcode, int recursionDesired, int rcode, int questionCount, int answerCount) {
        this.id = id;
        this.qrIndicator = qrIndicator;
        this.opcode = opcode;
        this.recursionDesired = recursionDesired;
        this.rcode = rcode;
        this.questionCount = questionCount;
        this.answerCount = answerCount;
    }

    // Simplified constructor for common use cases
    public DnsPacketHeader(int id, int opcode, int recursionDesired, int rcode, int questionCount, int answerCount) {
        this(id, 1, opcode, recursionDesired, rcode, questionCount, answerCount); // Default qrIndicator to 1 (response)
    }

    public int getId() {
        return id;
    }

    public int getOpcode() {
        return opcode;
    }

    public int getRecursionDesired() {
        return recursionDesired;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getAnswerCount() {
        return answerCount;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12);

        // ID (2 bytes)
        buffer.putShort((short) id);

        // Flags (2 bytes)
        byte flags1 = (byte) ((qrIndicator << 7) | (opcode << 3) | (recursionDesired & 0x01));
        byte flags2 = (byte) rcode;
        buffer.put(flags1);
        buffer.put(flags2);

        // Counts (8 bytes)
        buffer.putShort((short) questionCount);
        buffer.putShort((short) answerCount);
        buffer.putShort((short) 0); // Authority count (not used in this implementation)
        buffer.putShort((short) 0); // Additional count (not used in this implementation)

        return buffer.array();
    }

    public static DnsPacketHeader fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // ID (2 bytes)
        int id = buffer.getShort() & 0xFFFF;

        // Flags (2 bytes)
        byte flags1 = buffer.get();
        byte flags2 = buffer.get();
        int qrIndicator = (flags1 >> 7) & 0x01;
        int opcode = (flags1 >> 3) & 0x0F;
        int recursionDesired = flags1 & 0x01;
        int rcode = flags2 & 0x0F;

        // Counts (8 bytes)
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;
        buffer.getShort(); // Skip authority count
        buffer.getShort(); // Skip additional count

        return new DnsPacketHeader(id, qrIndicator, opcode, recursionDesired, rcode, questionCount, answerCount);
    }
}

class Question {
    private String name;
    private int recordType;
    private int classType;

    public Question(String name, int recordType, int classType) {
        this.name = name;
        this.recordType = recordType;
        this.classType = classType;
    }

    public String getName() {
        return name;
    }

    public byte[] toBytesUncompressed() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        byte[] nameBytes = domainToBytes(name);

        buffer.put(nameBytes);
        buffer.putShort((short) recordType);
        buffer.putShort((short) classType);

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
            if (endOffset == -1) {
                return questions;
            }
            currentOffset = endOffset + 1;
            int recordType = ((bytes[currentOffset] & 0xFF) << 8) | (bytes[currentOffset + 1] & 0xFF);
            currentOffset += 2;
            int classType = ((bytes[currentOffset] & 0xFF) << 8) | (bytes[currentOffset + 1] & 0xFF);
            currentOffset += 2;

            questions.add(new Question(name, recordType, classType));
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
        buffer.put((byte) 0); // NULL terminator
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    private static String expandCompressedName(byte[] bytes, int offset) {
        StringBuilder name = new StringBuilder();
        int i = offset;
        int jumps = 0;

        while (i < bytes.length) {
            int length = bytes[i] & 0xFF;
            if (length == 0) {
                break;
            }

            if ((length & 0xC0) == 0xC0) {
                if (jumps++ > 10) {
                    throw new RuntimeException("Too many compression pointer jumps.");
                }
                int pointer = ((length & 0x3F) << 8) | (bytes[i + 1] & 0xFF);
                name.append(expandCompressedName(bytes, pointer));
                break;
            } else {
                if (name.length() > 0) {
                    name.append(".");
                }
                i++;
                name.append(new String(bytes, i, length, StandardCharsets.UTF_8));
                i += length;
            }
        }

        return name.toString();
    }

    private static int findEndOfName(byte[] bytes, int offset) {
        for (int i = offset; i < bytes.length; i++) {
            if (bytes[i] == 0 || (bytes[i] & 0xC0) == 0xC0) {
                return i;
            }
        }
        return -1;
    }

    public int getByteSize() {
        return domainToBytes(name).length + 4;
    }
}
