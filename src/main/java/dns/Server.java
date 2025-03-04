package dns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

    private static final Logger log = Logger.getLogger(Server.class.getName());

    private static final int PORT = 2053;
    private static final int BUFFER_SIZE = 512;
    private static final String ARGS_SEPARATOR = ":";
    private static final Server INSTANCE = new Server();

    private Server() {}

    public static Server getInstance() {
        return INSTANCE;
    }

    public void start(String[] args) {
        log.log(Level.INFO, "DNS-Server Started....");
        SocketAddress resolver = getResolver(args);
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            while (true) {
                final byte[] buffer = new byte[BUFFER_SIZE];
                final DatagramPacket packet = receiveFromServerSocket(serverSocket, buffer);
                Message request = new Parser().parse(packet);
                List<Answer> answers = getAnswers(request, resolver, serverSocket);
                Message response = getResponse(request, answers);
                final byte[] responseBuffer = response.getMessage();
                sendFromServerSocket(packet.getSocketAddress(), serverSocket, responseBuffer);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Exception in DNS-Server: {0}", e.getMessage());
        }
    }

    private void sendFromServerSocket(SocketAddress resolver, DatagramSocket serverSocket, byte[] requestBuffer) throws IOException {
        final DatagramPacket resolverReqPacket = new DatagramPacket(requestBuffer, requestBuffer.length, resolver);
        serverSocket.send(resolverReqPacket);
    }

    private DatagramPacket receiveFromServerSocket(DatagramSocket serverSocket, byte[] respBuffer) throws IOException {
        final DatagramPacket resolverRespPacket = new DatagramPacket(respBuffer, respBuffer.length);
        serverSocket.receive(resolverRespPacket);
        return resolverRespPacket;
    }

    private Message getResponse(Message request, List<Answer> answers) {
        Message response = new Message(request.getHeader(), request.getQuestions(), answers);
        response.getHeader().setQr((byte) 1);
        response.getHeader().setAnCount((short) response.getQuestions().size());
        return response;
    }

    private List<Answer> getAnswers(Message request, SocketAddress resolver, DatagramSocket serverSocket) throws IOException {
        List<Answer> answers = new ArrayList<>();
        if (resolver == null) {
            log.log(Level.WARNING, "Resolver address is not provided.");
            return answers;
        }
        for (int i = 0; i < request.getQuestionCount(); i++) {
            try {
                Message resolverRequest = new Message(request.getHeader(),
                        List.of(request.getQuestions().get(i)),
                        new ArrayList<>());
                resolverRequest.getHeader().setQr((byte) 0);
                byte[] requestBuffer = resolverRequest.getMessage();

                sendFromServerSocket(resolver, serverSocket, requestBuffer);

                byte[] respBuffer = new byte[BUFFER_SIZE];
                final DatagramPacket resolverRespPacket = receiveFromServerSocket(serverSocket, respBuffer);

                Message resolverResponse = new Parser().parse(resolverRespPacket);
                if (!resolverResponse.getQuestions().isEmpty()) {
                    answers.add(resolverResponse.getAnswers().getFirst());
                }
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error processing DNS request for question {0} : {1}", new Object[]{i, e.getMessage()});
            }
        }
        return answers;
    }

    private SocketAddress getResolver(String[] args) {
        if (args.length > 1) {
            String[] resolverPair = args[1].split(ARGS_SEPARATOR);
            String resolverIp = resolverPair[0];
            int resolverPort = Integer.parseInt(resolverPair[1]);
            return new InetSocketAddress(resolverIp, resolverPort);
        }
        return null;
    }
}
