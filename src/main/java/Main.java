import java.io.IOException;
import java.net.*;
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
                List<byte[]> resolverResponses = forwardQueries(clientQuery, resolverIP, resolverPort);

                // Merge responses into a single packet
                byte[] mergedResponse = mergeResponses(resolverResponses, clientQuery);

                //Debug: Print final response before sending
                System.out.println("Final response being sent: " + Arrays.toString(mergedResponse));

                DatagramPacket responsePacket = new DatagramPacket(
                        mergedResponse, mergedResponse.length,
                        clientPacket.getSocketAddress()
                );

                serverSocket.send(responsePacket);
                System.out.println("Forwarded response to client.");
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    /**
     * Splits multiple questions in a query into separate queries, sends them individually,
     * and collects the responses.
     */
    private static List<byte[]> forwardQueries(byte[] query, String resolverIP, int resolverPort) throws IOException {
        List<byte[]> responses = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(query);

        short transactionId = buffer.getShort();  // Extract Transaction ID
        buffer.getShort(); // Flags
        short qdCount = buffer.getShort();  // Number of questions
        buffer.getShort(); // ANCOUNT
        buffer.getShort(); // NSCOUNT
        buffer.getShort(); // ARCOUNT

        int offset = 12; // Start of question section

        for (int i = 0; i < qdCount; i++) {
            byte[] singleQuestion = extractSingleQuestion(query, offset);
            offset += singleQuestion.length + 4; // Move past the current question

            // Create a full query packet for this single question
            byte[] singleQuery = buildSingleQuery(transactionId, singleQuestion);

            // Send individual query to resolver
            responses.add(forwardQuery(singleQuery, resolverIP, resolverPort));
        }

        return responses;
    }

    /**
     * Sends a single query to the upstream resolver and returns the response.
     */
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

            flags |= (1 << 15); // Ensure QR bit is set to 1 (response)

            System.out.println("Modified response flags (after setting QR): " + Integer.toBinaryString(flags));

            ByteBuffer modifiedResponse = ByteBuffer.allocate(responseData.length);
            modifiedResponse.putShort(transactionId);
            modifiedResponse.putShort(flags);
            modifiedResponse.put(responseData, 4, responseData.length - 4); // Copy the rest of the response

            return modifiedResponse.array();
        }
    }

    /**
     * Extracts a single question from the original query.
     */
    private static byte[] extractSingleQuestion(byte[] data, int offset) {
        int end = offset;
        while (data[end] != 0) {
            end++;
        }
        end += 5; // Move past null byte + QTYPE (2 bytes) + QCLASS (2 bytes)

        return Arrays.copyOfRange(data, offset, end);
    }

    /**
     * Builds a DNS query with a single question.
     */
    private static byte[] buildSingleQuery(short transactionId, byte[] question) {
        ByteBuffer buffer = ByteBuffer.allocate(12 + question.length + 4);
        buffer.putShort(transactionId);
        buffer.putShort((short) 0x0100); // Standard query
        buffer.putShort((short) 1); // QDCOUNT = 1
        buffer.putShort((short) 0); // ANCOUNT
        buffer.putShort((short) 0); // NSCOUNT
        buffer.putShort((short) 0); // ARCOUNT
        buffer.put(question);
        buffer.putShort((short) 1); // Type A
        buffer.putShort((short) 1); // Class IN

        return buffer.array();
    }

    /**
     * Merges multiple resolver responses into a single response.
     */
    private static byte[] mergeResponses(List<byte[]> responses, byte[] originalQuery) {
        ByteBuffer mergedBuffer = ByteBuffer.allocate(512);
        mergedBuffer.put(originalQuery, 0, 12); // Copy header

        short totalAnswerCount = 0;
        for (byte[] response : responses) {
            ByteBuffer responseBuffer = ByteBuffer.wrap(response);
            responseBuffer.position(6); // Move to ANCOUNT field
            totalAnswerCount += responseBuffer.getShort(); // Sum up answer counts

            mergedBuffer.put(response, 12, response.length - 12); // Copy answers
        }

        // Set the correct ANCOUNT field
        mergedBuffer.putShort(6, totalAnswerCount);

        return mergedBuffer.array();
    }
}
