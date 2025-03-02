import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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
        byte[] response = buildDnsResponse(dnsMessage);

        return response;
    }

    static DNSMessage parseDnsQuery(byte[] request) {
        DNSMessage dnsMessage = new DNSMessage();
        ByteBuffer buffer = ByteBuffer.wrap(request).order(ByteOrder.BIG_ENDIAN);

        // Parse Header (Stage 5 & onwards)
        dnsMessage.id = buffer.getShort();
        short flags = buffer.getShort();
        dnsMessage.qr = (flags >> 15) & 1;
        dnsMessage.opCode = (flags >> 11) & 0x0F;
        dnsMessage.rd = (flags >> 8) & 1;
        dnsMessage.qdCount = buffer.getShort();
        dnsMessage.anCount = buffer.getShort();
        dnsMessage.nsCount = buffer.getShort();
        dnsMessage.arCount = buffer.getShort();

        //Parse Questions (Stage 6 & 7)
        for (int i = 0; i < dnsMessage.qdCount; i++) {
            Question question = new Question();
            question.qName = parseDomainName(buffer);
            question.qType = buffer.getShort();
            question.qClass = buffer.getShort();
            dnsMessage.questions.add(question);
        }

        return dnsMessage;
    }

    private static String parseDomainName(ByteBuffer buffer) {
        StringBuilder domainName = new StringBuilder();
        int length = buffer.get() & 0xFF;  // Ensure unsigned value

        while (length != 0) {
            if ((length & 0xC0) == 0xC0) {
                // Compressed label (Stage 7)
                int offset = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                int currentPosition = buffer.position();
                buffer.position(offset);
                domainName.append(parseDomainName(buffer));
                buffer.position(currentPosition);
                return domainName.toString();
            } else {
                // Uncompressed label
                for (int i = 0; i < length; i++) {
                    domainName.append((char) buffer.get());
                }
                length = buffer.get() & 0xFF;
                if (length != 0) {
                    domainName.append(".");
                }
            }
        }
        return domainName.toString();
    }

    static byte[] buildDnsResponse(DNSMessage dnsMessage) {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);

        // Build Header (Stages 2 & 5)
        buffer.putShort(dnsMessage.id); // ID
        short flags = (short) 0;

        flags |= (1 << 15);  // QR = 1 (response)
        flags |= (dnsMessage.opCode << 11); // Opcode
        flags |= (0 << 10); // AA = 0
        flags |= (0 << 9); // TC = 0
        flags |= (dnsMessage.rd << 8); // RD from request
        flags |= (0 << 7); // RA = 0
        flags |= (dnsMessage.opCode != 0 ? 4 : 0); // RCODE (Stage 5)
        buffer.putShort(flags);

        buffer.putShort((short) dnsMessage.questions.size()); // QDCOUNT
        buffer.putShort((short) dnsMessage.questions.size()); // ANCOUNT (one answer per question)
        buffer.putShort((short) 0); // NSCOUNT
        buffer.putShort((short) 0); // ARCOUNT

        // Build Question Section (Stages 3 & 6)
        for (Question question : dnsMessage.questions) {
            writeDomainName(buffer, question.qName);  //QNAME
            buffer.putShort(question.qType); // QTYPE
            buffer.putShort(question.qClass); // QCLASS
        }

        // Build Answer Section (Stage 4)
        for (Question question : dnsMessage.questions) {
            writeDomainName(buffer, question.qName); // NAME
            buffer.putShort((short) 1);  // TYPE (A record)
            buffer.putShort((short) 1);  // CLASS (IN)
            buffer.putInt(60); // TTL
            buffer.putShort((short) 4); // RDLENGTH
            buffer.put((byte) 8);  //IP Address
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

        public java.util.List<Question> questions = new java.util.ArrayList<>();
    }

    static class Question {
        public String qName;
        public short qType;
        public short qClass;
    }
}
