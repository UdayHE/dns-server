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
        if (args.length > 1 && args[0].equals("--resolver")) {
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
                    responseData = handleForwarding(receivePacket.getData(), resolverAddress, serverSocket);
                } else {
                    responseData = buildDnsResponse(parseDnsQuery(receivePacket.getData()));
                }

                if (responseData != null) {
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, receivePacket.getSocketAddress());
                    serverSocket.send(responsePacket);
                    System.out.println("Sent response to: " + receivePacket.getSocketAddress());
                }
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    static byte[] handleForwarding(byte[] requestData, String resolverAddress, DatagramSocket socket) {
        try {
            DNSMessage originalRequest = parseDnsQuery(requestData);

            if (originalRequest.questions.size() > 1) {
                System.out.println("Multiple questions detected, splitting requests.");

                List<byte[]> responses = new ArrayList<>();
                for (Question question : originalRequest.questions) {
                    byte[] singleQuestionRequest = buildSingleQuestionQuery(originalRequest.id, question);
                    byte[] response = sendQueryToResolver(singleQuestionRequest, resolverAddress, socket);
                    if (response != null) {
                        responses.add(response);
                    }
                }

                return mergeResponses(originalRequest, responses);
            } else {
                return sendQueryToResolver(requestData, resolverAddress, socket);
            }
        } catch (IOException e) {
            System.err.println("Error forwarding DNS query: " + e.getMessage());
            return null;
        }
    }

    static byte[] buildSingleQuestionQuery(short id, Question question) {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort(id);
        buffer.putShort((short) 0x0100);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        writeDomainName(buffer, question.qName);
        buffer.putShort(question.qType);
        buffer.putShort(question.qClass);
        buffer.flip();
        byte[] queryData = new byte[buffer.remaining()];
        buffer.get(queryData);
        return queryData;
    }

    static String parseDomainName(ByteBuffer buffer, byte[] request) {
        StringBuilder domainName = new StringBuilder();
        int length;
        while ((length = buffer.get() & 0xFF) != 0) {
            if ((length & 0xC0) == 0xC0) { // Compression detected
                int offset = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                return parseCompressedName(request, offset);
            } else {
                for (int i = 0; i < length; i++) {
                    domainName.append((char) buffer.get());
                }
                domainName.append(".");
            }
        }
        return domainName.toString();
    }

    static String parseCompressedName(byte[] request, int offset) {
        StringBuilder domainName = new StringBuilder();
        int length;
        while ((length = request[offset++] & 0xFF) != 0) {
            for (int i = 0; i < length; i++) {
                domainName.append((char) request[offset++]);
            }
            domainName.append(".");
        }
        return domainName.toString();
    }


    static DNSMessage parseDnsQuery(byte[] request) {
        DNSMessage dnsMessage = new DNSMessage();
        ByteBuffer buffer = ByteBuffer.wrap(request).order(ByteOrder.BIG_ENDIAN);
        dnsMessage.id = buffer.getShort();
        buffer.getShort();
        dnsMessage.qdCount = buffer.getShort();
        dnsMessage.anCount = buffer.getShort();
        dnsMessage.nsCount = buffer.getShort();
        dnsMessage.arCount = buffer.getShort();

        for (int i = 0; i < dnsMessage.qdCount; i++) {
            Question question = new Question();
            question.qName = parseDomainName(buffer, request);
            question.qType = buffer.getShort();
            question.qClass = buffer.getShort();
            dnsMessage.questions.add(question);
        }
        return dnsMessage;
    }

    static byte[] buildDnsResponse(DNSMessage dnsMessage) {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);

        // DNS Header
        buffer.putShort(dnsMessage.id);
        buffer.putShort((short) 0x8180); // Response, no error
        buffer.putShort((short) dnsMessage.questions.size()); // Question count
        buffer.putShort((short) dnsMessage.questions.size()); // Answer count
        buffer.putShort((short) 0); // Authority count
        buffer.putShort((short) 0); // Additional count

        // Question Section: Uncompressed
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
            buffer.put((byte) 8);  // Example IP: 8.8.8.8
            buffer.put((byte) 8);
            buffer.put((byte) 8);
            buffer.put((byte) 8);
        }

        buffer.flip();
        byte[] response = new byte[buffer.remaining()];
        buffer.get(response);
        return response;
    }

    static byte[] mergeResponses(DNSMessage originalRequest, List<byte[]> responses) {
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort(originalRequest.id);
        buffer.putShort((short) 0x8180); // Response, no error

        int totalAnswers = 0;
        for (byte[] response : responses) {
            totalAnswers += extractAnswerCount(response);
        }

        buffer.putShort((short) originalRequest.questions.size()); // Correct question count
        buffer.putShort((short) totalAnswers); // Correct total answer count
        buffer.putShort((short) 0); // Authority count
        buffer.putShort((short) 0); // Additional count

        // Write original question section (uncompressed)
        for (Question question : originalRequest.questions) {
            writeDomainName(buffer, question.qName);
            buffer.putShort(question.qType);
            buffer.putShort(question.qClass);
        }

        // Extract and write only the answer sections from the resolver responses
        for (byte[] response : responses) {
            ByteBuffer responseBuffer = ByteBuffer.wrap(response);
            responseBuffer.position(12 + extractQuestionSectionLength(response)); // Skip to the answer section

            while (responseBuffer.hasRemaining() && buffer.position() < 512) {
                buffer.put(responseBuffer.get());
            }
        }

        buffer.flip();
        byte[] mergedResponse = new byte[buffer.remaining()];
        buffer.get(mergedResponse);
        return mergedResponse;
    }

    static int extractAnswerCount(byte[] response) {
        return ByteBuffer.wrap(response, 6, 2).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    static int extractQuestionSectionLength(byte[] response) {
        ByteBuffer buffer = ByteBuffer.wrap(response);
        buffer.position(12); // Start of question section
        int length = 0;
        while (buffer.get() != 0) { // Read until end of domain name
            length++;
        }
        return length + 5; // Include null terminator and type/class fields
    }

    static byte[] sendQueryToResolver(byte[] requestData, String resolverAddress, DatagramSocket socket) throws IOException {
        String[] parts = resolverAddress.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        InetSocketAddress resolverSocketAddress = new InetSocketAddress(host, port);
        DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, resolverSocketAddress);

        socket.send(requestPacket);
        System.out.println("Forwarded query to resolver at " + resolverAddress);

        byte[] responseBuffer = new byte[512];
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(responsePacket);
        System.out.println("Received response from resolver.");

        return Arrays.copyOfRange(responsePacket.getData(), 0, responsePacket.getLength());
    }

    static void writeDomainName(ByteBuffer buffer, String domainName) {
        for (String label : domainName.split("\\.")) {
            buffer.put((byte) label.length());
            for (char c : label.toCharArray()) {
                buffer.put((byte) c);
            }
        }
        buffer.put((byte) 0);
    }

    static class DNSMessage {
        public short id;
        public short qdCount, anCount, nsCount, arCount;
        public List<Question> questions = new ArrayList<>();
    }

    static class Question {
        public String qName;
        public short qType, qClass;
    }
}
