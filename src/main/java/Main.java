import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

                if (packet.getSocketAddress().equals(resolverSocketAddress)) {
                    System.out.println("Got data from resolver! Ignoring self-response.");
                    continue;
                }

                if (packet.getLength() < 12) {
                    System.err.println("Invalid packet received (too short). Ignoring.");
                    continue;
                }

                DnsPacketHeader recHeader = DnsPacketHeader.fromBytes(buffer);
                System.out.println(recHeader);

                // Build request header for resolver
                DnsPacketHeader reqHeaderResolver = new DnsPacketHeader(
                        recHeader.getId(),
                        0, // QR (0 = query)
                        recHeader.getOpcode(),
                        recHeader.getRecursionDesired(),
                        0, // Response code (default to NOERROR)
                        recHeader.getQuestionCount(),
                        0  // Initially no answers
                );

                byte[] resolverRequest = reqHeaderResolver.toBytes();
                int offset = 12;  // Start after the DNS header

                List<Question> extractedQuestions = new ArrayList<>();
                for (int i = 0; i < recHeader.getQuestionCount(); i++) {
                    List<Question> questionList = Question.fromBytes(1, buffer, offset);
                    if (questionList.isEmpty()) {
                        System.err.println("Failed to parse question at offset " + offset);
                        break;
                    }

                    Question question = questionList.get(0);
                    extractedQuestions.add(question);

                    resolverRequest = concatenateByteArrays(resolverRequest, question.toBytes());

                    // Move offset properly (use domain compression if needed)
                    offset += question.getByteSize();
                }

                System.out.println("Sending to resolver: " + bytesToHex(resolverRequest, resolverRequest.length));



                System.out.println("Sending to resolver: " + bytesToHex(resolverRequest, resolverRequest.length));

                DatagramPacket resolverPacket = new DatagramPacket(resolverRequest, resolverRequest.length, resolverSocketAddress);
                resolverSocket.send(resolverPacket);

                byte[] resolverBuffer = new byte[512];
                DatagramPacket resolverResponsePacket = new DatagramPacket(resolverBuffer, resolverBuffer.length);

                boolean gotResponse = false;
                for (int i = 0; i < MAX_RETRIES; i++) {
                    try {
                        resolverSocket.receive(resolverResponsePacket);
                        gotResponse = true;
                        System.out.println("Got " + resolverResponsePacket.getLength() + " bytes from resolver " + resolverResponsePacket.getSocketAddress());
                        break;
                    } catch (SocketTimeoutException e) {
                        System.err.println("Timeout waiting for resolver response, retrying... (" + (i + 1) + "/" + MAX_RETRIES + ")");
                    }
                }

                if (!gotResponse) {
                    System.err.println("Resolver did not respond after " + MAX_RETRIES + " retries. Sending SERVFAIL.");
                    DnsPacketHeader failResponseHeader = new DnsPacketHeader(
                            recHeader.getId(), 1, recHeader.getOpcode(), recHeader.getRecursionDesired(), 2, // SERVFAIL
                            recHeader.getQuestionCount(), 0 // No answers
                    );
                    byte[] failResponse = failResponseHeader.toBytes();

                    // Extract the questions **before** using them
                    List<Question> questions = new ArrayList<>();
                    for (int i = 0; i < recHeader.getQuestionCount(); i++) {
                        List<Question> questionList = Question.fromBytes(1, buffer, offset);
                        if (!questionList.isEmpty()) {
                            Question question = questionList.get(0);
                            questions.add(question);
                            failResponse = concatenateByteArrays(failResponse, question.toBytes());
                            offset += question.getByteSize();
                        }
                    }

                    DatagramPacket responsePacket = new DatagramPacket(failResponse, failResponse.length, packet.getSocketAddress());
                    udpSocket.send(responsePacket);
                    continue;
                }


                // Send valid response back to client
                System.out.println("Forwarding response to client: " + bytesToHex(resolverBuffer, resolverResponsePacket.getLength()));
                DatagramPacket responsePacket = new DatagramPacket(resolverBuffer, resolverResponsePacket.getLength(), packet.getSocketAddress());
                udpSocket.send(responsePacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(domainToBytes(name));
        buffer.putShort((short) recordType);
        buffer.putShort((short) classType);
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    public static List<Question> fromBytes(int count, byte[] bytes, int offset) {
        List<Question> questions = new ArrayList<>();
        int afterName = offset;
        for (int i = 0; i < count; i++) {
            String name = bytesToName(bytes, afterName);
            afterName = findNullByte(bytes, afterName) + 1;
            int recordType = ((bytes[afterName] & 0xFF) << 8) | (bytes[afterName + 1] & 0xFF);
            afterName += 2;
            int classType = ((bytes[afterName] & 0xFF) << 8) | (bytes[afterName + 1] & 0xFF);
            afterName += 2;
            questions.add(new Question(name, recordType, classType));
        }
        return questions;
    }

    private static byte[] domainToBytes(String domain) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        String[] labels = domain.split("\\.");
        int position = 0;

        for (String label : labels) {
            if (label.isEmpty()) continue;  // Skip empty labels
            buffer.put((byte) label.length());
            buffer.put(label.getBytes(StandardCharsets.UTF_8));
            position += label.length() + 1;
        }

        buffer.put((byte) 0); // NULL terminator
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);

        System.out.println("Encoded Domain: " + domain + " -> " + Main.bytesToHex(result, result.length));
        return result;
    }


    private static String bytesToName(byte[] bytes, int offset) {
        StringBuilder name = new StringBuilder();
        int i = offset;
        while (i < bytes.length && bytes[i] != 0) {
            int length = bytes[i] & 0xFF;
            i++;
            if ((length & 0xC0) == 0xC0) {
                int pointer = ((length & 0x3F) << 8) | (bytes[i] & 0xFF);
                name.append(bytesToName(bytes, pointer));
                i++;
                break;
            } else {
                if (name.length() > 0) {
                    name.append(".");
                }
                name.append(new String(bytes, i, length, StandardCharsets.UTF_8));
                i += length;
            }
        }
        return name.toString();
    }

    private static int findNullByte(byte[] bytes, int offset) {
        for (int i = offset; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    public int getByteSize() {
        return domainToBytes(name).length + 4; // Domain bytes + 2 (type) + 2 (class)
    }

}