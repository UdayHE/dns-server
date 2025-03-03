import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static SocketAddress getResolver(String[] args) {
        if (args.length > 1) {
            String[] resolverPair = args[1].split(":");
            String resolverIp = resolverPair[0];
            int resolverPort = Integer.parseInt(resolverPair[1]);
            return new InetSocketAddress(resolverIp, resolverPort);
        }
        return null;
    }

    public static void main(String[] args){
        System.out.println("Logs from your program will appear here!");

        SocketAddress resolver = getResolver(args);

        try(DatagramSocket serverSocket = new DatagramSocket(2053)) {
            while(true) {
                final byte[] buf = new byte[512];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);

                Parser parser = new Parser();
                DNSMessage request = parser.parse(packet);

                List<DNSAnswer> answers = new ArrayList<>();
                for (int i = 0; i < request.getQuestionCount(); i++) {
                    DNSMessage resolverRequest = new DNSMessage(request.getHeader(),
                            List.of(request.getQuestions().get(i)),
                            new ArrayList<>());
                    resolverRequest.getHeader().setQr((byte) 0);
                    byte[] reqBuffer = resolverRequest.getMessage();
                    assert resolver != null;
                    final DatagramPacket resolverReqPacket = new DatagramPacket(reqBuffer, reqBuffer.length, resolver);
                    serverSocket.send(resolverReqPacket);

                    byte[] respBuffer = new byte[512];
                    final DatagramPacket resolverRespPacket = new DatagramPacket(respBuffer, respBuffer.length);
                    serverSocket.receive(resolverRespPacket);
                    Parser parser1 = new Parser();
                    DNSMessage resolverResponse = parser1.parse(resolverRespPacket);
                    if (!resolverResponse.getQuestions().isEmpty())
                        answers.add(resolverResponse.getAnswers().getFirst());
                }

                DNSMessage response = new DNSMessage(request.getHeader(), request.getQuestions(), answers);
                response.getHeader().setQr((byte) 1);
                response.getHeader().setAnCount((short) response.getQuestions().size());

                final byte[] bufResponse = response.getMessage();
                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }
}