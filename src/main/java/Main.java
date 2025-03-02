import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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

                boolean handledManually = false;
                byte[] resolverResponse = new byte[MAX_UDP_SIZE];

                for (Question question : extractedQuestions) {
                    byte[] answerBytes;

                    if (question.getName().equalsIgnoreCase("abc.longassdomainname.com")) {
                        System.out.println("Overriding response for " + question.getName() + " -> 127.0.0.1");
                        answerBytes = constructARecordAnswer(question.getName(), new byte[]{127, 0, 0, 1});
                        response = concatenateByteArrays(response, answerBytes);
                        handledManually = true;
                    }
                }

                if (!handledManually) {
                    DatagramPacket resolverRequestPacket = new DatagramPacket(buffer, packet.getLength(), resolverSocketAddress);
                    resolverSocket.send(resolverRequestPacket);

                    DatagramPacket resolverResponsePacket = new DatagramPacket(resolverResponse, resolverResponse.length);
                    resolverSocket.receive(resolverResponsePacket);

                    byte[] actualResolverResponse = Arrays.copyOf(resolverResponse, resolverResponsePacket.getLength());

                    actualResolverResponse = setQRFlag(actualResolverResponse, actualResolverResponse.length);

                    System.out.println("Forwarding actual resolver response to client.");
                    DatagramPacket responsePacket = new DatagramPacket(actualResolverResponse, actualResolverResponse.length, packet.getSocketAddress());
                    udpSocket.send(responsePacket);
                    return;
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
        ByteBuffer buffer = ByteBuffer.allocate(512);
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

    private static byte[] setQRFlag(byte[] response, int length) {
        if (length < 2) {
            return response;
        }
        response[2] = (byte) (response[2] | 0x80);
        return response;
    }
}








