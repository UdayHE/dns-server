import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DNSParser {

    private int currPos = 0;
    private final HashMap<Integer, String> domainMap = new HashMap<>();

    public DNSMessage parse(DatagramPacket packet) {
        byte[] data = packet.getData();
        if (data.length < 12) {
            throw new IllegalArgumentException("Invalid DNS packet: data length is less than 12 bytes.");
        }
        DNSHeader header = parseHeader(data);
        int qdcount = header.getQdCount();
        currPos = 12;
        List<DNSQuestion> questions = new ArrayList<>();
        List<DNSAnswer> answers = new ArrayList<>();

        for (int i = 0; i < qdcount; i++) {
            DNSQuestion question = parseQuestion(data);
            if (question == null) {
                throw new RuntimeException("Failed to parse DNS question.");
            }
            questions.add(question);
        }

        int ancount = header.getAnCount(); // Assuming ANCOUNT is needed for answers
        for (int i = 0; i < ancount; i++) {
            DNSAnswer answer = parseAnswer(data);
            if (answer == null) {
                throw new RuntimeException("Failed to parse DNS answer.");
            }
            answers.add(answer);
        }

        return new DNSMessage(header, questions, answers);
    }

    private DNSHeader parseHeader(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        short ID = buffer.getShort();
        short FLAGS = buffer.getShort();
        short QDCOUNT = buffer.getShort();
        short ANCOUNT = buffer.getShort();
        short NSCOUNT = buffer.getShort();
        short ARCOUNT = buffer.getShort();
        return new DNSHeader(ID, FLAGS, QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT);
    }

    private DNSQuestion parseQuestion(byte[] data) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(currPos);
            String domainName = parseDomainName(buffer);
            short QTYPE = buffer.getShort();
            short QCLASS = buffer.getShort();

            DNSQuestion question = new DNSQuestion(domainName, QTYPE, QCLASS);
            domainMap.put(currPos, domainName);
            currPos = buffer.position();
            return question;
        } catch (Exception e) {
            System.err.println("Error parsing DNS question: " + e.getMessage());
            return null;
        }
    }

    private DNSAnswer parseAnswer(byte[] data) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(currPos);
            String domainName = parseDomainName(buffer);
            short qType = buffer.getShort();
            short qClass = buffer.getShort();
            short rdLength = buffer.getShort();

            if (buffer.remaining() < rdLength) {
                throw new IllegalArgumentException("Invalid DNS packet: insufficient data for RDATA.");
            }

            byte[] rdata = new byte[rdLength];
            buffer.get(rdata);

            String rdataStr;
            if (qType == 1 && rdLength == 4) { // A Record (IPv4)
                rdataStr = String.format("%d.%d.%d.%d", rdata[0] & 0xFF, rdata[1] & 0xFF, rdata[2] & 0xFF, rdata[3] & 0xFF);
            } else {
                rdataStr = new String(rdata, StandardCharsets.UTF_8); // Keep this for other record types
            }

            DNSAnswer answer = new DNSAnswer(domainName, qType, qClass, rdLength, rdataStr);
            currPos = buffer.position(); // Update position
            return answer;
        } catch (Exception e) {
            System.err.println("Error parsing DNS answer: " + e.getMessage());
            return null;
        }
    }

    private String parseDomainName(ByteBuffer buffer) {
        StringBuilder labelBuilder = new StringBuilder();
        int initialPos = buffer.position();

        while (buffer.position() < buffer.limit()) {
            int labelLength = buffer.get() & 0xFF;

            if (labelLength == 0) {
                break;
            } else if ((labelLength & 0xC0) == 0xC0) { // Check for compression
                int offset = ((labelLength & 0x3F) << 8) | (buffer.get() & 0xFF);
                if (domainMap.containsKey(offset)) {
                    labelBuilder.append(domainMap.get(offset));
                } else {
                    throw new RuntimeException("Failed to find compressed domain name at offset: " + offset);
                }
                break;
            } else {
                if (labelBuilder.length() > 0) {
                    labelBuilder.append(".");
                }
                byte[] label = new byte[labelLength];
                buffer.get(label);
                labelBuilder.append(new String(label, StandardCharsets.UTF_8));
            }
        }

        domainMap.put(initialPos, labelBuilder.toString());
        return labelBuilder.toString();
    }
}
