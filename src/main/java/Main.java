import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        String resolverIP = args[1].split(":")[0];
        int resolverPort = Integer.parseInt(args[1].split(":")[1]);
        SocketAddress resolver = new InetSocketAddress(resolverIP, resolverPort);

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            while (true) {
                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);
                System.out.println("Received data");

                DNSMessage question = new DNSMessage(buf);

                // Extract OPCODE from flags
                int opcode = (question.flags >> 11) & 0xF;  // Extract bits 11-14

                if (opcode != 0) {  // If OPCODE is not `0` (Standard Query)
                    question.setErrorResponse(4);  // Set `RCODE = 4` (Not Implemented)
                } else {
                    for (String qd : question.qd) {
                        DNSMessage forward = question.clone();
                        forward.qd = new ArrayList<>();
                        forward.qd.add(qd);
                        byte[] buffer = forward.array();

                        DatagramPacket forwardPacket = new DatagramPacket(buffer, buffer.length, resolver);
                        serverSocket.send(forwardPacket);

                        buffer = new byte[512];
                        forwardPacket = new DatagramPacket(buffer, buffer.length);
                        serverSocket.receive(forwardPacket);

                        forward = new DNSMessage(buffer);
                        for (String an : forward.an.keySet()) {
                            question.an.put(an, forward.an.get(an));
                        }
                    }

                    // Set response flags
                    char[] requestFlags = String.format("%16s", Integer.toBinaryString(question.flags)).replace(' ', '0').toCharArray();
                    requestFlags[0] = '1';  // QR (Response)
                    requestFlags[8] = '0';  // RD (Recursion Desired) → Reset to `0`
                    requestFlags[7] = '1';  // RA (Recursion Available)
                    question.flags = (short) Integer.parseInt(new String(requestFlags), 2);
                }

                byte[] buffer = question.array();
                DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, packet.getSocketAddress());
                serverSocket.send(sendPacket);
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}

class DNSMessage {
    public short id;
    public short flags;
    public List<String> qd = new ArrayList<>();
    public Map<String, byte[]> an = new HashMap<>();

    public DNSMessage() {}

    public DNSMessage(byte[] array) {
        ByteBuffer buffer = ByteBuffer.wrap(array);
        id = buffer.getShort();
        flags = buffer.getShort();
        int qdcount = buffer.getShort();
        int ancount = buffer.getShort();
        buffer.getShort(); // nscount
        buffer.getShort(); // arcount

        for (int i = 0; i < qdcount; i++) {
            qd.add(decodeDomainName(buffer, array));
            buffer.getShort(); // Type
            buffer.getShort(); // Class
        }

        for (int i = 0; i < ancount; i++) {
            String domain = decodeDomainName(buffer, array);
            buffer.getShort(); // Type = A
            buffer.getShort(); // Class = IN
            buffer.getInt();   // TTL
            buffer.getShort(); // Length
            byte[] ip = new byte[4];
            buffer.get(ip);
            an.put(domain, ip);
        }
    }

    public void setErrorResponse(int rcode) {
        int qr = 1;  // Set response flag
        int opcode = (flags >> 11) & 0xF; // Extract original opcode
        int aa = 0;
        int tc = 0;
        int rd = (flags >> 8) & 0x1;
        int ra = 0;
        int z = 0;

        flags = (short) ((qr << 15) | (opcode << 11) | (aa << 10) | (tc << 9) | (rd << 8) | (ra << 7) | (z << 4) | rcode);
        qd.clear();  // No questions in response
        an.clear();  // No answers
    }

    public byte[] array() {
        ByteBuffer buffer = ByteBuffer.allocate(512);

        buffer.putShort(id);
        buffer.putShort(flags);
        buffer.putShort((short) qd.size());
        buffer.putShort((short) an.size());
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        for (String domain : qd) {
            buffer.put(encodeDomainName(domain));
            buffer.putShort((short) 1);
            buffer.putShort((short) 1);
        }

        for (String domain : an.keySet()) {
            buffer.put(encodeDomainName(domain));
            buffer.putShort((short) 1);
            buffer.putShort((short) 1);
            buffer.putInt(3600);
            buffer.putShort((short) 4);
            buffer.put(an.get(domain));
        }

        byte[] response = new byte[buffer.position()];
        buffer.flip();
        buffer.get(response);
        return response;
    }

    private byte[] encodeDomainName(String domain) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : domain.split("\\.")) {
            out.write(label.length());
            out.writeBytes(label.getBytes());
        }
        out.write(0);
        return out.toByteArray();
    }

    private String decodeDomainName(ByteBuffer buffer, byte[] request) {
        byte labelLength;
        StringJoiner labels = new StringJoiner(".");
        boolean compressed = false;
        int position = 0;

        while ((labelLength = buffer.get()) != 0) {
            if ((labelLength & 0xC0) == 0xC0) {
                compressed = true;
                int offset = ((labelLength & 0x3F) << 8) | (buffer.get() & 0xFF);
                position = buffer.position();
                buffer.position(offset);
            } else {
                byte[] label = new byte[labelLength];
                buffer.get(label);
                labels.add(new String(label));
            }
        }

        if (compressed) {
            buffer.position(position);
        }
        return labels.toString();
    }

    public DNSMessage clone() {
        DNSMessage clone = new DNSMessage();
        clone.id = id;
        clone.flags = flags;
        clone.qd.addAll(qd);
        for (String domain : an.keySet()) {
            clone.an.put(domain, an.get(domain).clone());
        }
        return clone;
    }
}