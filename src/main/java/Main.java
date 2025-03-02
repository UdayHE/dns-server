import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static final int DNS_PORT = 2053;
    public static final int BUFFER_SIZE = 512;

    public static void main(String[] args) {
        if (args.length < 2 || !args[0].equals("--resolver")) {
            System.out.println("Usage: ./your_server --resolver <ip>:<port>");
            return;
        }

        String[] resolverAddress = args[1].split(":");
        String resolverIp = resolverAddress[0];
        int resolverPort = Integer.parseInt(resolverAddress[1]);

        try (DatagramSocket serverSocket = new DatagramSocket(DNS_PORT)) {
            while (true) {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(requestPacket);

                // Parse the incoming DNS request
                DNSMessage requestMessage = DNSMessage.parse(requestPacket.getData());

                // Forward the request to the resolver
                DNSMessage responseMessage = forwardRequest(requestMessage, resolverIp, resolverPort);

                // Send the response back to the client
                byte[] responseData = responseMessage.toByteArray();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length,
                        requestPacket.getSocketAddress());
                serverSocket.send(responsePacket);
            }
        } catch (SocketException e) {
            System.out.println("SocketException: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static DNSMessage forwardRequest(DNSMessage requestMessage, String resolverIp, int resolverPort) throws IOException {
        try (DatagramSocket resolverSocket = new DatagramSocket()) {
            InetAddress resolverAddress = InetAddress.getByName(resolverIp);

            // Forward each question separately
            List<DNSMessage> responseMessages = new ArrayList<>();
            for (DNSQuestion question : requestMessage.questions) {
                DNSMessage singleQuestionMessage = new DNSMessage(requestMessage.header, List.of(question));
                byte[] requestData = singleQuestionMessage.toByteArray();
                DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, resolverAddress, resolverPort);
                resolverSocket.send(requestPacket);

                byte[] responseBuffer = new byte[BUFFER_SIZE];
                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                resolverSocket.receive(responsePacket);

                DNSMessage responseMessage = DNSMessage.parse(responsePacket.getData());
                responseMessages.add(responseMessage);
            }

            // Merge responses
            return mergeResponses(requestMessage.header, responseMessages);
        }
    }

    private static DNSMessage mergeResponses(DNSHeader header, List<DNSMessage> responseMessages) {
        List<DNSQuestion> questions = new ArrayList<>();
        List<DNSRecord> answers = new ArrayList<>();

        for (DNSMessage responseMessage : responseMessages) {
            questions.addAll(responseMessage.questions);
            answers.addAll(responseMessage.answers);
        }

        DNSHeader mergedHeader = new DNSHeader(header.id, 1, header.opcode, 0, 0, header.rd, 0, 0, 0,
                questions.size(), answers.size(), 0, 0);

        return new DNSMessage(mergedHeader, questions, answers, List.of(), List.of());
    }
}

class DNSMessage {
    DNSHeader header;
    List<DNSQuestion> questions;
    List<DNSRecord> answers;
    List<DNSRecord> authorities;
    List<DNSRecord> additionals;

    DNSMessage(DNSHeader header, List<DNSQuestion> questions) {
        this(header, questions, List.of(), List.of(), List.of());
    }

    DNSMessage(DNSHeader header, List<DNSQuestion> questions, List<DNSRecord> answers,
               List<DNSRecord> authorities, List<DNSRecord> additionals) {
        this.header = header;
        this.questions = questions;
        this.answers = answers;
        this.authorities = authorities;
        this.additionals = additionals;
    }

    static DNSMessage parse(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        DNSHeader header = DNSHeader.parse(buffer);

        List<DNSQuestion> questions = new ArrayList<>();
        for (int i = 0; i < header.qdcount; i++) {
            questions.add(DNSQuestion.parse(buffer));
        }

        List<DNSRecord> answers = new ArrayList<>();
        for (int i = 0; i < header.ancount; i++) {
            answers.add(DNSRecord.parse(buffer));
        }

        return new DNSMessage(header, questions, answers, List.of(), List.of());
    }

    byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(Main.BUFFER_SIZE);
        header.write(buffer);

        for (DNSQuestion question : questions) {
            question.write(buffer);
        }

        for (DNSRecord answer : answers) {
            answer.write(buffer);
        }

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }
}

class DNSHeader {
    int id;
    int qr;
    int opcode;
    int aa;
    int tc;
    int rd;
    int ra;
    int z;
    int rcode;
    int qdcount;
    int ancount;
    int nscount;
    int arcount;

    DNSHeader(int id, int qr, int opcode, int aa, int tc, int rd, int ra, int z, int rcode,
              int qdcount, int ancount, int nscount, int arcount) {
        this.id = id;
        this.qr = qr;
        this.opcode = opcode;
        this.aa = aa;
        this.tc = tc;
        this.rd = rd;
        this.ra = ra;
        this.z = z;
        this.rcode = rcode;
        this.qdcount = qdcount;
        this.ancount = ancount;
        this.nscount = nscount;
        this.arcount = arcount;
    }

    static DNSHeader parse(ByteBuffer buffer) {
        int id = buffer.getShort() & 0xFFFF;
        int flags = buffer.getShort() & 0xFFFF;
        int qr = (flags >> 15) & 0x1;
        int opcode = (flags >> 11) & 0xF;
        int aa = (flags >> 10) & 0x1;
        int tc = (flags >> 9) & 0x1;
        int rd = (flags >> 8) & 0x1;
        int ra = (flags >> 7) & 0x1;
        int z = (flags >> 4) & 0x7;
        int rcode = flags & 0xF;

        int qdcount = buffer.getShort() & 0xFFFF;
        int ancount = buffer.getShort() & 0xFFFF;
        int nscount = buffer.getShort() & 0xFFFF;
        int arcount = buffer.getShort() & 0xFFFF;

        return new DNSHeader(id, qr, opcode, aa, tc, rd, ra, z, rcode, qdcount, ancount, nscount, arcount);
    }

    void write(ByteBuffer buffer) {
        buffer.putShort((short) id);
        int flags = (qr << 15) | (opcode << 11) | (aa << 10) | (tc << 9) | (rd << 8) | (ra << 7) | (z << 4) | rcode;
        buffer.putShort((short) flags);
        buffer.putShort((short) qdcount);
        buffer.putShort((short) ancount);
        buffer.putShort((short) nscount);
        buffer.putShort((short) arcount);
    }
}

class DNSQuestion {
    String name;
    int type;
    int clazz;

    DNSQuestion(String name, int type, int clazz) {
        this.name = name;
        this.type = type;
        this.clazz = clazz;
    }

    static DNSQuestion parse(ByteBuffer buffer) {
        String name = readName(buffer);
        int type = buffer.getShort() & 0xFFFF;
        int clazz = buffer.getShort() & 0xFFFF;
        return new DNSQuestion(name, type, clazz);
    }

    void write(ByteBuffer buffer) {
        writeName(buffer, name);
        buffer.putShort((short) type);
        buffer.putShort((short) clazz);
    }

    public static String readName(ByteBuffer buffer) {
        StringBuilder name = new StringBuilder();
        int length;
        while ((length = buffer.get() & 0xFF) != 0) {
            if ((length & 0xC0) == 0xC0) {
                int offset = ((length & 0x3F) << 8) | (buffer.get() & 0xFF);
                ByteBuffer savedBuffer = buffer.duplicate();
                savedBuffer.position(offset);
                name.append(readName(savedBuffer));
                return name.toString();
            } else {
                byte[] label = new byte[length];
                buffer.get(label);
                name.append(new String(label)).append('.');
            }
        }
        return name.length() > 0 ? name.substring(0, name.length() - 1) : "";
    }

    public static void writeName(ByteBuffer buffer, String name) {
        for (String label : name.split("\\.")) {
            buffer.put((byte) label.length());
            buffer.put(label.getBytes());
        }
        buffer.put((byte) 0);
    }
}

class DNSRecord {
    String name;
    int type;
    int clazz;
    int ttl;
    int rdlength;
    byte[] rdata;

    DNSRecord(String name, int type, int clazz, int ttl, int rdlength, byte[] rdata) {
        this.name = name;
        this.type = type;
        this.clazz = clazz;
        this.ttl = ttl;
        this.rdlength = rdlength;
        this.rdata = rdata;
    }

    static DNSRecord parse(ByteBuffer buffer) {
        String name = DNSQuestion.readName(buffer);
        int type = buffer.getShort() & 0xFFFF;
        int clazz = buffer.getShort() & 0xFFFF;
        int ttl = buffer.getInt();
        int rdlength = buffer.getShort() & 0xFFFF;
        byte[] rdata = new byte[rdlength];
        buffer.get(rdata);
        return new DNSRecord(name, type, clazz, ttl, rdlength, rdata);
    }

    void write(ByteBuffer buffer) {
        DNSQuestion.writeName(buffer, name);
        buffer.putShort((short) type);
        buffer.putShort((short) clazz);
        buffer.putInt(ttl);
        buffer.putShort((short) rdlength);
        buffer.put(rdata);
    }
}