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
                    responseData = forwardToResolver(receivePacket.getData(), resolverAddress, serverSocket);
                } else {
                    responseData = handleDnsQuery(receivePacket.getData()); // Only for earlier stages
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

    static byte[] forwardToResolver(byte[] requestData, String resolverAddress, DatagramSocket socket) {
        try {
            String[] parts = resolverAddress.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            InetSocketAddress resolverSocketAddress = new InetSocketAddress(host, port);
            DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, resolverSocketAddress);

            // Send the request to the resolver
            socket.send(requestPacket);
            System.out.println("Forwarded query to resolver at " + resolverAddress);

            // Receive the response from the resolver
            byte[] responseBuffer = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            System.out.println("Received response from resolver.");

            // Return the response exactly as received
            return Arrays.copyOfRange(responsePacket.getData(), 0, responsePacket.getLength());

        } catch (IOException e) {
            System.err.println("Error forwarding DNS query: " + e.getMessage());
            return null;
        }
    }

    static byte[] handleDnsQuery(byte[] request) {
        // This method is only for earlier stages (non-forwarding mode)
        System.out.println("Handling DNS query locally (not forwarding).");
        return buildTestDnsResponse(request);
    }

    static byte[] buildTestDnsResponse(byte[] request) {
        ByteBuffer buffer = ByteBuffer.wrap(request);
        short id = buffer.getShort();  // Transaction ID
        buffer.rewind();

        ByteBuffer responseBuffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
        responseBuffer.putShort(id);
        responseBuffer.putShort((short) 0x8180); // Standard response, no error
        responseBuffer.putShort((short) 1); // 1 Question
        responseBuffer.putShort((short) 1); // 1 Answer
        responseBuffer.putShort((short) 0); // NSCOUNT
        responseBuffer.putShort((short) 0); // ARCOUNT

        // Copy the Question Section from request
        byte[] questionSection = Arrays.copyOfRange(request, 12, request.length);
        responseBuffer.put(questionSection);

        // Answer Section (this should only be used in non-forwarding cases)
        responseBuffer.put(questionSection, 0, questionSection.length - 4); // Name
        responseBuffer.putShort((short) 1);  // Type A
        responseBuffer.putShort((short) 1);  // Class IN
        responseBuffer.putInt(3600); // TTL
        responseBuffer.putShort((short) 4);  // RDLENGTH
        responseBuffer.put((byte) 127);
        responseBuffer.put((byte) 0);
        responseBuffer.put((byte) 0);
        responseBuffer.put((byte) 2); // 127.0.0.2 (shouldn't be used for forwarding)

        responseBuffer.flip();
        byte[] response = new byte[responseBuffer.remaining()];
        responseBuffer.get(response);
        return response;
    }
}
