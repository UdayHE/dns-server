import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equals("--resolver")) {
            System.out.println("Usage: ./your_server --resolver <ip>:<port>");
            return;
        }

        String resolverAddress = args[1];
        String[] resolverParts = resolverAddress.split(":");
        String resolverIP = resolverParts[0];
        int resolverPort = Integer.parseInt(resolverParts[1]);

        System.out.println("DNS Forwarder started on port 2053...");
        System.out.println("Forwarding DNS queries to: " + resolverIP + ":" + resolverPort);

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            while (true) {
                final byte[] buf = new byte[512];
                final DatagramPacket clientPacket = new DatagramPacket(buf, buf.length);
                serverSocket.receive(clientPacket);

                System.out.println("Received a DNS request.");

                byte[] clientQuery = clientPacket.getData();
                byte[] resolverResponse = forwardQuery(clientQuery, resolverIP, resolverPort);

                // ✅ Debug: Print final response before sending
                System.out.println("Final response being sent: " + Arrays.toString(resolverResponse));

                DatagramPacket responsePacket = new DatagramPacket(
                        resolverResponse, resolverResponse.length,
                        clientPacket.getSocketAddress()
                );

                serverSocket.send(responsePacket);
                System.out.println("Forwarded response to client.");
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static List<byte[]> forwardQueries(byte[] query, String resolverIP, int resolverPort) throws IOException {
        List<byte[]> responses = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(query);

        short transactionId = buffer.getShort();
        buffer.getShort(); // Flags
        short qdCount = buffer.getShort();
        buffer.getShort(); // ANCOUNT
        buffer.getShort(); // NSCOUNT
        buffer.getShort(); // ARCOUNT

        int offset = 12; // Start of question section

        for (int i = 0; i < qdCount; i++) {
            byte[] singleQuestion = extractSingleQuestion(query, offset);
            offset += singleQuestion.length + 4;

            responses.add(forwardQuery(singleQuestion, resolverIP, resolverPort));
        }

        return responses;
    }

    private static byte[] forwardQuery(byte[] query, String resolverIP, int resolverPort) throws IOException {
        try (DatagramSocket resolverSocket = new DatagramSocket()) {
            InetAddress resolverInetAddress = InetAddress.getByName(resolverIP);

            DatagramPacket resolverRequestPacket = new DatagramPacket(
                    query, query.length, resolverInetAddress, resolverPort
            );

            resolverSocket.send(resolverRequestPacket);

            byte[] responseBuffer = new byte[512];
            DatagramPacket resolverResponsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            resolverSocket.receive(resolverResponsePacket);

            byte[] responseData = resolverResponsePacket.getData();

            ByteBuffer responseWrapper = ByteBuffer.wrap(responseData);
            short transactionId = responseWrapper.getShort();
            short flags = responseWrapper.getShort();

            System.out.println("Received response ID: " + transactionId);
            System.out.println("Received response flags (before modification): " + Integer.toBinaryString(flags));

            flags |= (1 << 15); // ✅ Set QR bit to 1 (response)

            System.out.println("Modified response flags (after setting QR): " + Integer.toBinaryString(flags));

            ByteBuffer modifiedResponse = ByteBuffer.allocate(responseData.length);
            modifiedResponse.putShort(transactionId);
            modifiedResponse.putShort(flags);
            modifiedResponse.put(responseData, 4, responseData.length - 4); // Copy the rest of the response

            return modifiedResponse.array();
        }
    }


    private static byte[] extractSingleQuestion(byte[] data, int offset) {
        int end = offset;
        while (data[end] != 0) {
            end++;
        } // Find end of domain name
        end += 5; // Move past null byte + QTYPE (2 bytes) + QCLASS (2 bytes)

        return ByteBuffer.allocate(end - offset).put(data, offset, end - offset).array();
    }

    private static byte[] mergeResponses(List<byte[]> responses, byte[] originalQuery) {
        ByteBuffer mergedBuffer = ByteBuffer.allocate(512);
        mergedBuffer.put(originalQuery, 0, 12); // Copy header

        for (byte[] response : responses) {
            mergedBuffer.put(response, 12, response.length - 12); // Copy answers
        }

        return mergedBuffer.array();
    }
}
