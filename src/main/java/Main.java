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
            System.out.println("DNS server listening on port " + DNS_PORT); // Add a log message
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
        int rcode = 0; // No error or Not implemented
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
        List<byte[]> questionNames = new ArrayList<>(); // Store only the name part
        for (int i = 0; i < qdcount; i++) {
            byte[] questionName = parseQuestionName(requestBuffer);
            questionNames.add(questionName);
            // Copy the complete question (name, type, class) to the response
            responseBuffer.put(questionName);
            responseBuffer.putShort(requestBuffer.getShort()); // QType
            responseBuffer.putShort(requestBuffer.getShort()); // QClass
        }

        // Add answer section for each question
        for (byte[] questionName : questionNames) {
            // Name (copy from question)
            for (byte b : questionName) {
                responseBuffer.put(b);
            }
            responseBuffer.putShort((short) 1); // Type A
            responseBuffer.putShort((short) 1); // Class IN
            responseBuffer.putInt(60);          // TTL
            responseBuffer.putShort((short) 4); // RDATA length
            responseBuffer.put(new byte[]{8, 8, 8, 8}); // RDATA (8.8.8.8)
        }

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.flip();
        responseBuffer.get(response);
        return response;
    }

    private static byte[] parseQuestionName(ByteBuffer buffer) {
        List<Byte> questionBytes = new ArrayList<>();
        int startPosition = buffer.position();

        while (true) {
            byte length = buffer.get();
            questionBytes.add(length);
            if ((length & 0xC0) == 0xC0) { // Compressed label
                byte nextByte = buffer.get();
                questionBytes.add(nextByte);
                break; // Compression pointer, end of name
            } else if (length == 0) { // End of label sequence
                break;
            } else { // Uncompressed label
                for (int i = 0; i < length; i++) {
                    questionBytes.add(buffer.get());
                }
            }
        }

        byte[] name = new byte[questionBytes.size()];
        for (int i = 0; i < questionBytes.size(); i++) {
            name[i] = questionBytes.get(i);
        }

        return name;
    }

    private static byte[] forwardDnsQuery(byte[] request, String resolverAddress) throws IOException {
        String[] parts = resolverAddress.split(":");
        InetAddress resolverIp = InetAddress.getByName(parts[0]);
        int resolverPort = Integer.parseInt(parts[1]);
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(request, request.length, resolverIp, resolverPort);
            socket.send(packet);
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            return responsePacket.getData();
        }
    }
}
