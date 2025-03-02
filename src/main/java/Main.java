import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final int DNS_PORT = 2053;
    private static final int BUFFER_SIZE = 512;
    private static final Map<String, String> DNS_RECORDS = new HashMap<>();

    public static void main(String[] args) {
        // Initialize some static DNS records
        DNS_RECORDS.put("codecrafters.io", "8.8.8.8");
        DNS_RECORDS.put("example.com", "93.184.216.34");

        try (DatagramSocket serverSocket = new DatagramSocket(DNS_PORT)) {
            System.out.println("DNS Server is running on port " + DNS_PORT);

            while (true) {
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);

                byte[] response = handleDnsRequest(packet.getData(), packet.getLength());
                DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getSocketAddress());

                serverSocket.send(responsePacket);
            }
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
    }

    private static byte[] handleDnsRequest(byte[] requestData, int length) {
        ByteBuffer requestBuffer = ByteBuffer.wrap(requestData, 0, length);

        // Parse the DNS header
        int transactionId = requestBuffer.getShort() & 0xFFFF;
        int flags = requestBuffer.getShort();
        int qdcount = requestBuffer.getShort() & 0xFFFF; // Number of questions

        // Build response buffer
        ByteBuffer responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        responseBuffer.putShort((short) transactionId); // Transaction ID
        responseBuffer.putShort((short) 0x8180); // Standard response, No error
        responseBuffer.putShort((short) qdcount); // Number of questions
        responseBuffer.putShort((short) qdcount); // Number of answer records
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        // Parse question section and build response
        for (int i = 0; i < qdcount; i++) {
            String domainName = parseDomainName(requestBuffer);
            responseBuffer.put(requestData, responseBuffer.position(), requestBuffer.position() - responseBuffer.position());

            int qType = requestBuffer.getShort();
            int qClass = requestBuffer.getShort();
            responseBuffer.putShort((short) qType);
            responseBuffer.putShort((short) qClass);

            // Construct answer section
            if (DNS_RECORDS.containsKey(domainName)) {
                responseBuffer.put(requestData, responseBuffer.position() - domainName.length() - 2, domainName.length() + 2);
                responseBuffer.putShort((short) 1); // Type A
                responseBuffer.putShort((short) 1); // Class IN
                responseBuffer.putInt(60); // TTL
                responseBuffer.putShort((short) 4); // RDLENGTH

                // Convert IP address string to bytes
                String ipAddress = DNS_RECORDS.get(domainName);
                for (String part : ipAddress.split("\\.")) {
                    responseBuffer.put((byte) Integer.parseInt(part));
                }
            }
        }
        return trimBuffer(responseBuffer);
    }

    private static String parseDomainName(ByteBuffer buffer) {
        StringBuilder domainName = new StringBuilder();
        while (true) {
            int length = buffer.get() & 0xFF;
            if (length == 0) break;
            byte[] label = new byte[length];
            buffer.get(label);
            if (domainName.length() > 0) domainName.append(".");
            domainName.append(new String(label));
        }
        return domainName.toString();
    }

    private static byte[] trimBuffer(ByteBuffer buffer) {
        byte[] data = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(data);
        return data;
    }
}
