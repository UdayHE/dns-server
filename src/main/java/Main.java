import dns.DNSAnswer;
import dns.DNSMessage;
import dns.DNSParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        SocketAddress resolver = getResolver(args);
        if (resolver == null) {
            System.out.println("No resolver address provided in arguments.");
            return;
        }

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            DNSParser dnsParser = new DNSParser();  // Create DNSParser instance once

            while (true) {
                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);

                DNSMessage request = dnsParser.parse(packet);

                List<DNSAnswer> answers = new ArrayList<>();
                for (int i = 0; i < request.getQuestionCount(); i++) {
                    DNSMessage resolverRequest = new DNSMessage(request.getHeader(),
                            List.of(request.getQuestions().get(i)),
                            new ArrayList<>());
                    resolverRequest.getHeader().setQr((byte) 0);
                    byte[] reqBuffer = resolverRequest.getMessage();

                    DatagramPacket resolverReqPacket = new DatagramPacket(reqBuffer, reqBuffer.length, resolver);
                    serverSocket.send(resolverReqPacket);

                    byte[] respBuffer = new byte[512];
                    DatagramPacket resolverRespPacket = new DatagramPacket(respBuffer, respBuffer.length);
                    serverSocket.receive(resolverRespPacket);

                    DNSMessage resolverResponse = dnsParser.parse(resolverRespPacket);
                    if (!resolverResponse.getQuestions().isEmpty()) {
                        answers.add(resolverResponse.getAnswers().getFirst());
                    }
                }

                DNSMessage response = new DNSMessage(request.getHeader(), request.getQuestions(), answers);
                response.getHeader().setQr((byte) 1);
                response.getHeader().setAnCount((short) answers.size());  // Use answers.size() instead of request.getQuestions().size()

                byte[] bufResponse = response.getMessage();
                DatagramPacket packetResponse = new DatagramPacket(bufResponse, bufResponse.length, packet.getSocketAddress());
                serverSocket.send(packetResponse);
            }
        } catch (IOException e) {
            System.out.println("IO Exception: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Illegal Argument Exception: " + e.getMessage());
        } catch (NullPointerException e) {
            System.out.println("Null Pointer Exception: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("General Exception: " + e.getMessage());
        }
    }

    private static SocketAddress getResolver(String[] args) {
        try {
            if (args.length > 1) {
                String[] resolverPair = args[1].split(":");
                String resolverIp = resolverPair[0];
                int resolverPort = Integer.parseInt(resolverPair[1]);
                return new InetSocketAddress(resolverIp, resolverPort);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number format provided: " + e.getMessage());
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Resolver address is not properly formatted: " + e.getMessage());
        }
        return null;
    }
}
