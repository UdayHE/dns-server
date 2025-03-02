import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
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

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                System.out.println("Received " + packet.getLength() + " bytes from " + packet.getSocketAddress());
                System.out.println(bytesToHex(buffer, packet.getLength()));

                if (packet.getSocketAddress().equals(resolverSocketAddress)) {
                    System.out.println("Got data from resolver!");
                    continue;
                }

                if (packet.getLength() < 12) {
                    continue;
                }

                DnsPacketHeader recHeader = DnsPacketHeader.fromBytes(buffer);
                System.out.println(recHeader);

                DnsPacketHeader responseHeader = new DnsPacketHeader(
                        recHeader.getId(),
                        1, // QR indicator
                        recHeader.getOpcode(),
                        recHeader.getRecursionDesired(),
                        recHeader.getOpcode() == 0 ? 0 : 4, // Response code
                        recHeader.getQuestionCount(),
                        recHeader.getQuestionCount()
                );

                byte[] response = responseHeader.toBytes();

                DnsPacketHeader reqHeaderResolver = new DnsPacketHeader(
                        recHeader.getId(),
                        recHeader.getOpcode(),
                        recHeader.getRecursionDesired(),
                        recHeader.getOpcode() == 0 ? 0 : 4,
                        recHeader.getQuestionCount() > 0 ? 1 : 0
                );

                if (packet.getLength() > 12 && recHeader.getOpcode() == 0) {
                    List<Question> questions = Question.fromBytes(recHeader.getQuestionCount(), buffer, 12);
                    List<byte[]> answers = new ArrayList<>();

                    for (Question question : questions) {
                        byte[] questionBytes = question.toBytes();
                        response = concatenateByteArrays(response, questionBytes);

                        byte[] reqForResolver = concatenateByteArrays(reqHeaderResolver.toBytes(), questionBytes);
                        System.out.println("Sending to resolver: " + bytesToHex(reqForResolver, reqForResolver.length));

                        DatagramPacket resolverPacket = new DatagramPacket(reqForResolver, reqForResolver.length, resolverSocketAddress);
                        resolverSocket.send(resolverPacket);

                        byte[] resolverBuffer = new byte[512];
                        DatagramPacket resolverResponsePacket = new DatagramPacket(resolverBuffer, resolverBuffer.length);
                        resolverSocket.receive(resolverResponsePacket);

                        System.out.println("Got " + resolverResponsePacket.getLength() + " bytes from resolver " + resolverResponsePacket.getSocketAddress());
                        byte[] answerRaw = new byte[resolverResponsePacket.getLength() - reqForResolver.length];
                        System.arraycopy(resolverBuffer, reqForResolver.length, answerRaw, 0, answerRaw.length);
                        answers.add(answerRaw);
                    }

                    for (byte[] answer : answers) {
                        response = concatenateByteArrays(response, answer);
                    }
                }

                System.out.println("Sending " + bytesToHex(response, response.length));
                DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getSocketAddress());
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

    private static String bytesToHex(byte[] bytes, int length) {
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
    private int id;
    private int qrIndicator;
    private int opcode;
    private int recursionDesired;
    private int responseCode;
    private int questionCount;
    private int answerCount;

    public DnsPacketHeader(int id, int qrIndicator, int opcode, int recursionDesired, int responseCode, int questionCount, int answerCount) {
        this.id = id;
        this.qrIndicator = qrIndicator;
        this.opcode = opcode;
        this.recursionDesired = recursionDesired;
        this.responseCode = responseCode;
        this.questionCount = questionCount;
        this.answerCount = answerCount;
    }

    public DnsPacketHeader(int id, int opcode, int recursionDesired, int answerCount, int questionCount) {
        this.id = id;
        this.opcode = opcode;
        this.recursionDesired = recursionDesired;
        this.answerCount = answerCount;
        this.questionCount = questionCount;

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

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putShort((short) id);
        buffer.put((byte) ((qrIndicator << 7) | (opcode << 3) | (recursionDesired & 0x01)));
        buffer.put((byte) responseCode);
        buffer.putShort((short) questionCount);
        buffer.putShort((short) answerCount);
        return buffer.array();
    }

    public static DnsPacketHeader fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int id = buffer.getShort() & 0xFFFF;
        byte flags1 = buffer.get();
        byte flags2 = buffer.get();
        int qrIndicator = (flags1 >> 7) & 0x01;
        int opcode = (flags1 >> 3) & 0x0F;
        int recursionDesired = flags1 & 0x01;
        int responseCode = flags2 & 0x0F;
        int questionCount = buffer.getShort() & 0xFFFF;
        int answerCount = buffer.getShort() & 0xFFFF;
        return new DnsPacketHeader(id, qrIndicator, opcode, recursionDesired, responseCode, questionCount, answerCount);
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
}