import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <resolver_ip:resolver_port>");
            return;
        }

        String resolverAddress = args[0];
        String resolverIP = resolverAddress.split(":")[0];
        int resolverPort = Integer.parseInt(resolverAddress.split(":")[1]);
        SocketAddress resolver = new InetSocketAddress(resolverIP, resolverPort);

        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            System.out.println("DNS server listening on port 2053");

            while (true) {
                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);

                System.out.println("Received data from: " + packet.getSocketAddress());

                DNSMessage question = new DNSMessage(buf);

                // Extract OPCODE from flags (Bits 11-14)
                int opcode = (question.flags >> 11) & 0xF;

                // Extract RD flag
                boolean recursionDesired = (question.flags & 0x0100) != 0;

                if (opcode != 0) { // If OPCODE is not `0` (Standard Query)
                    System.out.println("Non-standard query received.  RCode 4 (Not Implemented) will be sent.");
                    question.setErrorResponse(4, recursionDesired); // RCODE = 4 (Not Implemented)
                    byte[] errorResponse = question.array();
                    DatagramPacket errorPacket = new DatagramPacket(errorResponse, errorResponse.length, packet.getSocketAddress());
                    serverSocket.send(errorPacket);

                } else {
                    System.out.println("Standard query received.");
                    // Forward query to resolver for each question
                    for (String qd : question.qd) {
                        System.out.println("Forwarding query for: " + qd + " to resolver: " + resolver);
                        DNSMessage forward = question.clone();
                        forward.qd = new ArrayList<>(); //clear all questions except current
                        forward.qd.add(qd);
                        forward.an.clear(); //clear answers
                        forward.flags = (short) (forward.flags & (~0x8000)); //clear QR flag

                        byte[] buffer = forward.array();
                        DatagramPacket forwardPacket = new DatagramPacket(buffer, buffer.length, resolver);
                        serverSocket.send(forwardPacket);

                        buffer = new byte[512];
                        forwardPacket = new DatagramPacket(buffer, buffer.length);
                        serverSocket.receive(forwardPacket);

                        DNSMessage response = new DNSMessage(buffer);

                        System.out.println("Received response from resolver for: " + qd);

                        // Add answers from resolver response to original question
                        for (String an : response.an.keySet()) {
                            question.an.put(an, response.an.get(an));
                        }

                        // Set response flags (QR = 1)
                        question.flags = (short) ((question.flags | 0x8000));
                        question.flags = (short) (question.flags | (response.flags & 0x0400));  // copy RA flag
                        question.flags = (short) (question.flags | (response.flags & 0x000F));  // copy RCODE
                        question.anCount = (short) question.an.size();

                        byte[] responseBuffer = question.array();
                        DatagramPacket sendPacket = new DatagramPacket(responseBuffer, responseBuffer.length, packet.getSocketAddress());
                        serverSocket.send(sendPacket);
                        System.out.println("Sent response to client.");
                    }
                }

            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace(); // print the stack trace for debugging

        }
    }


    static class DNSMessage {
        public short id;
        public short flags;
        public List<String> qd = new ArrayList<>();
        public Map<String, byte[]> an = new HashMap<>();
        public short anCount;

        public DNSMessage() {
        }

        public DNSMessage(byte[] array) {
            ByteBuffer buffer = ByteBuffer.wrap(array);
            id = buffer.getShort();
            flags = buffer.getShort();
            int qdcount = buffer.getShort();
            anCount = buffer.getShort();  // Initial answer count
            buffer.getShort(); // NSCOUNT
            buffer.getShort(); // ARCOUNT

            for (int i = 0; i < qdcount; i++) {
                qd.add(decodeDomainName(buffer, array));
                buffer.getShort(); // Type
                buffer.getShort(); // Class
            }

            for (int i = 0; i < anCount; i++) {
                String domain = decodeDomainName(buffer, array);
                buffer.getShort(); // Type = A
                buffer.getShort(); // Class = IN
                int ttl = buffer.getInt(); // TTL
                short rdLength = buffer.getShort(); // Length
                byte[] ip = new byte[rdLength];
                buffer.get(ip);
                an.put(domain, ip);
            }
        }

        public void setErrorResponse(int rcode, boolean recursionDesired) {
            // Set QR = 1, keep RD as received, set RCODE
            flags = (short) (0x8000 | (recursionDesired ? 0x0100 : 0) | (rcode & 0xF));
            qd.clear(); // No questions in response
            an.clear(); // No answers
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
                buffer.putShort((short) 1); // Type A
                buffer.putShort((short) 1); // Class IN
            }

            for (String domain : an.keySet()) {
                buffer.put(encodeDomainName(domain));
                buffer.putShort((short) 1); // Type A
                buffer.putShort((short) 1); // Class IN
                buffer.putInt(3600); // TTL
                byte[] ipAddress = an.get(domain);
                buffer.putShort((short) ipAddress.length); //Data length
                buffer.put(ipAddress); // IP Address
            }

            byte[] response = new byte[buffer.position()];
            buffer.flip();
            buffer.get(response);
            return response;
        }

        @Override
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
                if (compressed) {
                    buffer.position(position);
                    break;
                }
            }
            return labels.toString();
        }
    }
}
