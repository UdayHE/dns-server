import dns.Answer;
import dns.Message;
import dns.Parser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());

    private static final int PORT = 2053;
    private static final int BUFFER_SIZE = 512;
    private static final String ARGS_SEPARATOR = ":";


    public static void main(String[] args) {
        log.log(Level.INFO, "Logs from your program will appear here!");

        SocketAddress resolver = getResolver(args);
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            while (true) {
                final byte[] buffer = new byte[BUFFER_SIZE];
                final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);

                Message request = new Parser().parse(packet);

                List<Answer> answers = new ArrayList<>();
                for (int i = 0; i < request.getQuestionCount(); i++) {
                    Message resolverRequest = new Message(request.getHeader(),
                            List.of(request.getQuestions().get(i)),
                            new ArrayList<>());
                    resolverRequest.getHeader().setQr((byte) 0);
                    byte[] reqBuffer = resolverRequest.getMessage();
                    assert resolver != null;
                    final DatagramPacket resolverReqPacket = new DatagramPacket(reqBuffer, reqBuffer.length, resolver);
                    serverSocket.send(resolverReqPacket);

                    byte[] respBuffer = new byte[BUFFER_SIZE];
                    final DatagramPacket resolverRespPacket = new DatagramPacket(respBuffer, respBuffer.length);
                    serverSocket.receive(resolverRespPacket);
                    Message resolverResponse = new Parser().parse(resolverRespPacket);
                    if (!resolverResponse.getQuestions().isEmpty())
                        answers.add(resolverResponse.getAnswers().getFirst());
                }

                Message response = new Message(request.getHeader(), request.getQuestions(), answers);
                response.getHeader().setQr((byte) 1);
                response.getHeader().setAnCount((short) response.getQuestions().size());

                final byte[] bufResponse = response.getMessage();
                final DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Exception in Main: {0}", e.getMessage());
        }
    }

    private static SocketAddress getResolver(String[] args) {
        if (args.length > 1) {
            String[] resolverPair = args[1].split(ARGS_SEPARATOR);
            String resolverIp = resolverPair[0];
            int resolverPort = Integer.parseInt(resolverPair[1]);
            return new InetSocketAddress(resolverIp, resolverPort);
        }
        return null;
    }
}