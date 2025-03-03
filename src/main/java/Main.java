import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int DNS_PORT = 2053;
    private static final int BUFFER_SIZE = 512;

    public static void main(String[] args) {
        String resolverAddress = null;
        if (args.length > 0 && args[0].equals("--resolver")) {
            resolverAddress = args[1];
        }

        try (DatagramSocket serverSocket = new DatagramSocket(DNS_PORT)) {
            while (true) {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);

                byte[] response;
                if (resolverAddress != null) {
                    response = forwardDnsQuery(buffer, resolverAddress);
                } else {
                    response = createDnsResponse(buffer);
                }

                DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                serverSocket.send(responsePacket);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static byte[] createDnsResponse(byte[] request) {
        ByteBuffer requestBuffer = ByteBuffer.wrap(request);
        ByteBuffer responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        // Header section
        int id = requestBuffer.getShort() & 0xFFFF;
        responseBuffer.putShort((short) id);

        int flags = requestBuffer.getShort() & 0xFFFF;
        int qr = 1; // Response
        int opcode = (flags >> 11) & 0xF;
        int aa = 0;
        int tc = 0;
        int rd = (flags >> 8) & 0x1;
        int ra = 0;
        int z = 0;
        int rcode = opcode == 0 ? 0 : 4; // No error or Not implemented
        int responseFlags = (qr << 15) | (opcode << 11) | (aa << 10) | (tc << 9) | (rd << 8) | (ra << 7) | (z << 4) | rcode;
        responseBuffer.putShort((short) responseFlags);

        int qdcount = requestBuffer.getShort() & 0xFFFF;
        responseBuffer.putShort((short) qdcount);

        int ancount = qdcount; // One answer per question
        responseBuffer.putShort((short) ancount);

        int nscount = 0;
        responseBuffer.putShort((short) nscount);

        int arcount = 0;
        responseBuffer.putShort((short) arcount);

        // Parse and copy the question section
        List<byte[]> questions = new ArrayList<>();
        for (int i = 0; i < qdcount; i++) {
            byte[] question = parseQuestion(requestBuffer);
            questions.add(question);
            responseBuffer.put(question);
        }

        // Add answer section for each question
        for (byte[] question : questions) {
            responseBuffer.put(question); // Name (uncompressed)
            responseBuffer.putShort((short) 1); // Type A
            responseBuffer.putShort((short) 1); // Class IN
            responseBuffer.putInt(60); // TTL
            responseBuffer.putShort((short) 4); // RDATA length
            responseBuffer.put(new byte[]{8, 8, 8, 8}); // RDATA (8.8.8.8)
        }

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.flip();
        responseBuffer.get(response);
        return response;
    }

    private static byte[] parseQuestion(ByteBuffer buffer) {
        List<Byte> questionBytes = new ArrayList<>();
        while (true) {
            byte length = buffer.get();
            if ((length & 0xC0) == 0xC0) { // Compressed label
                int pointer = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                byte[] uncompressed = uncompressLabel(buffer, pointer);
                questionBytes.addAll(toByteList(uncompressed));
                break;
            } else if (length == 0) { // End of label sequence
                questionBytes.add(length);
                break;
            } else { // Uncompressed label
                questionBytes.add(length);
                for (int i = 0; i < length; i++) {
                    questionBytes.add(buffer.get());
                }
            }
        }
        questionBytes.add(buffer.get()); // Type
        questionBytes.add(buffer.get());
        questionBytes.add(buffer.get()); // Class
        questionBytes.add(buffer.get());

        byte[] question = new byte[questionBytes.size()];
        for (int i = 0; i < questionBytes.size(); i++) {
            question[i] = questionBytes.get(i);
        }
        return question;
    }

    private static byte[] uncompressLabel(ByteBuffer buffer, int pointer) {
        int oldPosition = buffer.position();
        buffer.position(pointer);
        byte[] uncompressed = parseQuestion(buffer).clone();
        buffer.position(oldPosition);
        return uncompressed;
    }

    private static List<Byte> toByteList(byte[] array) {
        List<Byte> list = new ArrayList<>();
        for (byte b : array) {
            list.add(b);
        }
        return list;
    }

    private static byte[] forwardDnsQuery(byte[] request, String resolverAddress) throws IOException {
        String[] parts = resolverAddress.split(":");
        InetAddress resolverIp = InetAddress.getByName(parts[0]);
        int resolverPort = Integer.parseInt(parts[1]);

        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(request, request.length, resolverIp, resolverPort);
        socket.send(packet);

        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(responsePacket);

        socket.close();
        return responsePacket.getData();
    }
}