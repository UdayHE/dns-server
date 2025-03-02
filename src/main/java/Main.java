import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int DNS_PORT = 2053;

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(DNS_PORT)) {
            System.out.println("DNS server listening on port " + DNS_PORT);

            while (true) {
                byte[] receiveBuf = new byte[512];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                serverSocket.receive(receivePacket);

                System.out.println("Received packet from: " + receivePacket.getSocketAddress());

                byte[] responseData = handleDnsQuery(receivePacket.getData());

                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, receivePacket.getSocketAddress());
                serverSocket.send(responsePacket);

                System.out.println("Sent response to: " + receivePacket.getSocketAddress());
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    static byte[] handleDnsQuery(byte[] request) {
        DNSMessage dnsMessage = parseDnsQuery(request);
        return buildDnsResponse(dnsMessage);
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

        // Parse Questions (handling compression)
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
        int originalPos = buffer.position();

        while ((length = buffer.get() & 0xFF) != 0) {
            if ((length & 0xC0) == 0xC0) { // Check for compression
                int offset = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                return parseCompressedName(request, offset);
            } else {
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
        int current = offset;
        int length = request[current++] & 0xFF;

        while (length != 0) {
            if ((length & 0xC0) == 0xC0) {
                int newOffset = ((length & 0x3F) << 8) | (request[current] & 0xFF);
                return parseCompressedName(request, newOffset);
            } else {
                for (int i = 0; i < length; i++) {
                    domainName.append((char) request[current++]);
                }
                if (request[current] != 0) {
                    domainName.append(".");
                }
                length = request[current++] & 0xFF;
            }
        }
        return domainName.toString();
    }

    static byte[] buildDnsResponse(DNSMessage dnsMessage) {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);

        // Header
        buffer.putShort(dnsMessage.id);
        buffer.putShort((short) 0x8180); // Standard response, no error
        buffer.putShort((short) dnsMessage.questions.size());
        buffer.putShort((short) dnsMessage.questions.size()); // Answers match the number of questions
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        // Question Section: Uncompressed domain names
        for (Question question : dnsMessage.questions) {
            writeDomainName(buffer, question.qName);
            buffer.putShort(question.qType);
            buffer.putShort(question.qClass);
        }

        // Answer Section
        for (Question question : dnsMessage.questions) {
            writeDomainName(buffer, question.qName);
            buffer.putShort((short) 1);  // Type A
            buffer.putShort((short) 1);  // Class IN
            buffer.putInt(3600); // TTL
            buffer.putShort((short) 4);  // RDLENGTH (IPv4)
            buffer.put((byte) 127);  // Example IP: 127.0.0.2
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            buffer.put((byte) 2);
        }

        buffer.flip();
        byte[] response = new byte[buffer.remaining()];
        buffer.get(response);
        return response;
    }

    private static void writeDomainName(ByteBuffer buffer, String domainName) {
        for (String label : domainName.split("\\.")) {
            buffer.put((byte) label.length());
            for (char c : label.toCharArray()) {
                buffer.put((byte) c);
            }
        }
        buffer.put((byte) 0); // Null terminator
    }

    static class DNSMessage {
        public short id;
        public int qr, opCode, rd;
        public short qdCount, anCount, nsCount, arCount;
        public List<Question> questions = new ArrayList<>();
    }

    static class Question {
        public String qName;
        public short qType, qClass;
    }
}
