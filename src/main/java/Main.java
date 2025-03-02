import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Main {
    private static final int LISTEN_PORT = 2053;
    private static final int BUFFER_SIZE = 512;
    private static InetAddress resolverAddress;
    private static int resolverPort;

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equals("--resolver")) {
            System.out.println("Usage: java DNSForwarder --resolver <ip>:<port>");
            return;
        }

        try {
            String[] resolverParts = args[1].split(":");
            resolverAddress = InetAddress.getByName(resolverParts[0]);
            resolverPort = Integer.parseInt(resolverParts[1]);
        } catch (Exception e) {
            System.err.println("Invalid resolver address.");
            return;
        }

        System.out.println("DNS Forwarder started on port " + LISTEN_PORT);
        System.out.println("Forwarding DNS queries to: " + resolverAddress.getHostAddress() + ":" + resolverPort);

        try (DatagramSocket serverSocket = new DatagramSocket(LISTEN_PORT)) {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                DatagramPacket clientPacket = new DatagramPacket(buffer, BUFFER_SIZE);
                serverSocket.receive(clientPacket);
                System.out.println("Received DNS request from " + clientPacket.getSocketAddress());

                // Forward the query and get the response
                byte[] response = forwardQuery(clientPacket.getData());

                // Send response back to the client
                DatagramPacket responsePacket = new DatagramPacket(
                        response, response.length, clientPacket.getSocketAddress());
                serverSocket.send(responsePacket);
                System.out.println("Forwarded response to client.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] forwardQuery(byte[] query) throws IOException {
        try (DatagramSocket resolverSocket = new DatagramSocket()) {
            DatagramPacket requestPacket = new DatagramPacket(
                    query, query.length, resolverAddress, resolverPort);
            resolverSocket.send(requestPacket);

            byte[] responseBuffer = new byte[BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, BUFFER_SIZE);
            resolverSocket.receive(responsePacket);

            return responsePacket.getData();
        }
    }
}
