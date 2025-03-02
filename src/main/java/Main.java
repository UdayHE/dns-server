import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    private static String resolverAddress = null;

    public static void main(String[] args) {
        // Parse command-line arguments
        if (args.length > 0 && args[0].equals("--resolver")) {
            resolverAddress = args[1];
        }

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            System.out.println("DNS server listening on port 2053");

            while (true) {
                byte[] receiveBuf = new byte[512];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                serverSocket.receive(receivePacket);

                System.out.println("Received packet from: " + receivePacket.getSocketAddress());

                byte[] responseData;

                if (resolverAddress != null) {
                    // Stage 8: Forwarding DNS Server
                    responseData = handleForwarding(receivePacket.getData(), resolverAddress, serverSocket);
                    if (responseData == null) {
                        System.out.println("Failed to get response from resolver.");
                        continue;
                    }
                } else {
                    // Stages 1-7: Build the DNS response
                    responseData = handleDnsQuery(receivePacket.getData());
                }

                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, receivePacket.getSocketAddress());
                serverSocket.send(responsePacket);

                System.out.println("Sent response to: " + receivePacket.getSocketAddress());
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    static byte[] handleForwarding(byte[] requestData, String resolverAddress, DatagramSocket socket) {
        try {
            String[] parts = resolverAddress.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            InetSocketAddress resolverSocketAddress = new InetSocketAddress(host, port);
            DatagramPacket request = new DatagramPacket(requestData, requestData.length, resolverSocketAddress);

            socket.send(request);

            byte[] responseBuffer = new byte[512];
            DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(response);

            return Arrays.copyOfRange(response.getData(), 0, response.getLength());

        } catch (IOException e) {
            System.err.println("Error forwarding DNS query: " + e.getMessage());
            return null;
        }
    }

    static byte[] handleDnsQuery(byte[] request) {
        // Parse the DNS query
        DNSMessage dnsMessage = parseDnsQuery(request);

        // Build the DNS response
        byte[] response = buildDnsResponse(dnsMessage, request);
        return response;
    }

    static DNSMessage parseDnsQuery(byte[] request) {
        DNSMessage dnsMessage = new DNSMessage();
        ByteBuffer buffer = ByteBuffer.wrap(request).order(ByteOrder.BIG_ENDIAN);

        // Parse Header
        dnsMessage.id = buffer.getShort();
        short flags = buffer.getShort();
        dnsMessage.qr = (flags >> 15) & 1;
        dnsMessage.opCode = (flags >> 11) & 0x0F;
        dnsMessage.rd = (flags >> 8) & 1;
        dnsMessage.qdCount = buffer.getShort();
        dnsMessage.anCount = buffer.getShort();
        dnsMessage.nsCount = buffer.getShort();
        dnsMessage.arCount = buffer.getShort();

        // Parse Questions
        for (int i = 0; i < dnsMessage.qdCount; i++) {
            Question question = new Question();
            question.qName = parseDomainName(buffer, request);
            question.qType = buffer.getShort();
            question.qClass = buffer.getShort();
            dnsMessage.questions.add(question);
        }

        return dnsMessage;
    }

    private static String parseDomainName(ByteBuffer buffer, byte[] request) {
        StringBuilder domainName = new StringBuilder();
        int length;
        int initialPosition = buffer.position();

        while ((length = buffer.get() & 0xFF) != 0) {
            if ((length & 0xC0) == 0xC0) {
                // Compressed label
                int offset = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                domainName.append(parseCompressedName(request, offset));
                return domainName.toString();
            } else {
                // Uncompressed label
                for (int i = 0; i < length; i++) {
                    domainName.append((char) buffer.get());
                }
                if ((buffer.position() < request.length) && ((buffer.get(buffer.position()) & 0xFF) != 0)) {
                    domainName.append(".");
                }

            }
        }

        return domainName.toString();
    }

    private static String parseCompressedName(byte[] request, int offset) {
        StringBuilder domainName = new StringBuilder();
        int length = request[offset] & 0xFF;
        int current = offset;

        while (length != 0) {
            if ((length & 0xC0) == 0xC0) {
                // Recursive compression
                int newOffset = ((length & 0x3F) << 8) | (request[current + 1] & 0xFF);
                return parseCompressedName(request, newOffset);
            } else {
                // Uncompressed label within the compressed name
                for (int i = 0; i < length; i++) {
                    domainName.append((char) request[current + 1 + i]);
                }
                current += length + 1;
                if (current < request.length && (request[current] & 0xFF) != 0) {
                    domainName.append(".");
                }
                length = request[current] & 0xFF;
            }
        }
        return domainName.toString();
    }

    static byte[] buildDnsResponse(DNSMessage dnsMessage, byte[] request) {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);

        // Header
        buffer.putShort(dnsMessage.id);
        short flags = (short) 0;

        flags |= (1 << 15);  // QR = 1 (response)
        flags |= (dnsMessage.opCode << 11); // Opcode
        flags |= (0 << 10); // AA = 0
        flags |= (0 << 9); // TC = 0
        flags |= (dnsMessage.rd << 8); // RD from request
        flags |= (0 << 7); // RA = 0
        flags |= (dnsMessage.opCode != 0 ? 4 : 0); // RCODE

        buffer.putShort(flags);
        buffer.putShort((short) dnsMessage.questions.size()); // QDCOUNT
        buffer.putShort((short) dnsMessage.questions.size()); // ANCOUNT
        buffer.putShort((short) 0); // NSCOUNT
        buffer.putShort((short) 0); // ARCOUNT

        // Question Section: Copy from the request
        ByteBuffer requestBuffer = ByteBuffer.wrap(request).order(ByteOrder.BIG_ENDIAN);
        int questionStart = 12; // Header size
        requestBuffer.position(questionStart);

        // Store the positions of the questions to use later for constructing the answer section.
        List<Integer> questionPositions = new ArrayList<>();

        for (Question question : dnsMessage.questions) {
            questionPositions.add(buffer.position());  //Store start position for each question.
            writeDomainName(buffer, question.qName);
            buffer.putShort((short) question.qType);  // Type
            buffer.putShort((short) question.qClass);  // Class
        }


        // Answer Section
        for (int i = 0; i < dnsMessage.questions.size(); i++) {
            Question question = dnsMessage.questions.get(i);

            writeDomainName(buffer, question.qName); // Name
            buffer.putShort((short) 1); // Type A
            buffer.putShort((short) 1); // Class IN
            buffer.putInt(60); // TTL
            buffer.putShort((short) 4); // Data length
            buffer.put((byte) 8);  // IP Address
            buffer.put((byte) 8);
            buffer.put((byte) 8);
            buffer.put((byte) 8);
        }

        buffer.flip();
        byte[] response = new byte[buffer.remaining()];
        buffer.get(response);
        return response;
    }

    private static void writeDomainName(ByteBuffer buffer, String domainName) {
        String[] labels = domainName.split("\\.");
        for (String label : labels) {
            buffer.put((byte) label.length());
            for (int i = 0; i < label.length(); i++) {
                buffer.put((byte) label.charAt(i));
            }
        }
        buffer.put((byte) 0); // Null terminator
    }

    static class DNSMessage {
        public short id;
        public int qr;
        public int opCode;
        public int aa;
        public int tc;
        public int rd;
        public int ra;
        public int rcode;
        public short qdCount;
        public short anCount;
        public short nsCount;
        public short arCount;
        public List<Question> questions = new ArrayList<>();
    }

    static class Question {
        public String qName;
        public short qType;
        public short qClass;
    }
}
