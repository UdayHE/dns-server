import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

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

                // Parse received query using `DNSMessage`
                DNSMessage requestMessage = new DNSMessage(clientPacket.getData());

                // Forward the query and get a response
                byte[] resolverResponse = forwardQuery(clientPacket.getData(), resolverIP, resolverPort);

                // Create a proper response with extracted IP
                byte[] finalResponse = DNSMessage.createResponse(requestMessage, resolverResponse);

                // Debugging
                System.out.println("Final response being sent: " + Arrays.toString(finalResponse));

                DatagramPacket responsePacket = new DatagramPacket(
                        finalResponse, finalResponse.length, clientPacket.getSocketAddress()
                );

                serverSocket.send(responsePacket);
                System.out.println("Forwarded response to client.");
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    /**
     * Forwards a single DNS query to an upstream resolver and returns the response.
     */
    private static byte[] forwardQuery(byte[] query, String resolverIP, int resolverPort) throws IOException {
        try (DatagramSocket resolverSocket = new DatagramSocket()) {
            InetAddress resolverInetAddress = InetAddress.getByName(resolverIP);

            DatagramPacket resolverRequestPacket = new DatagramPacket(
                    query, query.length, resolverInetAddress, resolverPort
            );

            resolverSocket.send(resolverRequestPacket);

            // Use a larger buffer to handle larger responses
            byte[] responseBuffer = new byte[4096]; // Increased buffer size to 4096 bytes
            DatagramPacket resolverResponsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            resolverSocket.receive(resolverResponsePacket);

            byte[] responseData = Arrays.copyOf(resolverResponsePacket.getData(), resolverResponsePacket.getLength());

            // Debugging: Print hex response
            System.out.println("Final response (hex): " + bytesToHex(responseData));

            // Extract and print the IPv4 address from the response (last 4 bytes)
            int lastIndex = responseData.length - 4;
            if (lastIndex >= 0) {
                System.out.println("IPv4 Address from resolver: " +
                        (responseData[lastIndex] & 0xFF) + "." +
                        (responseData[lastIndex + 1] & 0xFF) + "." +
                        (responseData[lastIndex + 2] & 0xFF) + "." +
                        (responseData[lastIndex + 3] & 0xFF));
            } else {
                System.out.println("Warning: Could not extract IPv4 address from resolver response.");
            }

            return responseData;
        }
    }

    // Helper function to convert byte arrays to hex strings for debugging
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
