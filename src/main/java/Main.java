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
        if (args.length > 1 && args[0].equals("--resolver")) {
            resolverAddress = args[1];
        }

        try (DatagramSocket serverSocket = new DatagramSocket(DNS_PORT)) {
            System.out.println("DNS server listening on port " + DNS_PORT);
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

        // Parse Header
        int id = requestBuffer.getShort() & 0xFFFF;
        responseBuffer.putShort((short) id);  // Copy ID

        int flags = requestBuffer.getShort() & 0xFFFF;
        int qr = 1; // Response
        int opcode = (flags >> 11) & 0xF;
        int aa = 0;
        int tc = 0;
        int rd = (flags >> 8) & 0x1;
        int ra = 0;
        int z = 0;
        int rcode = 0; // No error
        int responseFlags = (qr << 15) | (opcode << 11) | (aa << 10) | (tc << 9) | (rd << 8) | (ra << 7) | (z << 4) | rcode;
        responseBuffer.putShort((short) responseFlags);

        int qdcount = requestBuffer.getShort() & 0xFFFF;
        responseBuffer.putShort((short) qdcount); // Copy original question count

        int ancount = qdcount; // Each question gets an answer
        responseBuffer.putShort((short) ancount);

        int nscount = 0;
        responseBuffer.putShort((short) nscount);

        int arcount = 0;
        responseBuffer.putShort((short) arcount);

        // Parse and copy the question section
        List<byte[]> questionNames = new ArrayList<>();
        for (int i = 0; i < qdcount; i++) {
            byte[] questionName = parseDomainName(requestBuffer, request);
            questionNames.add(questionName);
            responseBuffer.put(questionName);
            responseBuffer.putShort(requestBuffer.getShort()); // QType
            responseBuffer.putShort(requestBuffer.getShort()); // QClass
        }

        // Add answer section for each question
        for (byte[] questionName : questionNames) {
            responseBuffer.put(questionName);  // Use uncompressed name
            responseBuffer.putShort((short) 1); // Type A
            responseBuffer.putShort((short) 1); // Class IN
            responseBuffer.putInt(60);          // TTL
            responseBuffer.putShort((short) 4); // RDATA length
            responseBuffer.put(new byte[]{8, 8, 8, 8}); // Fake IP (8.8.8.8)
        }

        byte[] response = new byte[responseBuffer.position()];
        responseBuffer.flip();
        responseBuffer.get(response);
        return response;
    }

    private static byte[] parseDomainName(ByteBuffer buffer, byte[] request) {
        List<Byte> domainNameBytes = new ArrayList<>();
        while (true) {
            byte length = buffer.get();
            domainNameBytes.add(length);
            if ((length & 0xC0) == 0xC0) { // Compressed label
                int pointerOffset = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                domainNameBytes.addAll(parseCompressedName(request, pointerOffset));
                break;
            } else if (length == 0) { // End of label sequence
                break;
            } else { // Uncompressed label
                for (int i = 0; i < length; i++) {
                    domainNameBytes.add(buffer.get());
                }
            }
        }

        byte[] name = new byte[domainNameBytes.size()];
        for (int i = 0; i < domainNameBytes.size(); i++) {
            name[i] = domainNameBytes.get(i);
        }
        return name;
    }

    private static List<Byte> parseCompressedName(byte[] request, int offset) {
        List<Byte> domainNameBytes = new ArrayList<>();
        while (true) {
            byte length = request[offset++];
            domainNameBytes.add(length);
            if (length == 0) {
                break;
            }
            for (int i = 0; i < length; i++) {
                domainNameBytes.add(request[offset++]);
            }
        }
        return domainNameBytes;
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
