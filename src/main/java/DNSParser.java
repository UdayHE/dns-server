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
        try {
            byte[] data = packet.getData();
            DNSHeader header = parseHeader(data);
            int qdcount = header.getQdCount();
            currPos = 12;
            List<DNSQuestion> questions = new ArrayList<>();
            List<DNSAnswer> answers = new ArrayList<>();

            for (int i = 0; i < qdcount; i++)
                questions.add(parseQuestion(data));

            // ANCOUNT should be used to determine the number of answers
            int ancount = header.getAnCount();
            for (int i = 0; i < ancount; i++)
                answers.add(parseAnswer(data));

            return new DNSMessage(header, questions, answers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DNS packet", e);
        }
    }

    private DNSHeader parseHeader(byte[] data) {
        if (data.length < 12) {
            throw new IllegalArgumentException("DNS packet data too short to parse header");
        }
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
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(currPos);
        String domainName = parseDomainName(buffer);
        short QTYPE = buffer.getShort();
        short QCLASS = buffer.getShort();

        DNSQuestion question = new DNSQuestion(domainName, QTYPE, QCLASS);

        domainMap.put(currPos, domainName);
        currPos = buffer.position();
        return question;
    }

    private DNSAnswer parseAnswer(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(currPos);
        String domainName = parseDomainName(buffer);
        short QTYPE = buffer.getShort();
        short QCLASS = buffer.getShort();
        int TTL = buffer.getInt();
        short RDLENGTH = buffer.getShort();

        if (RDLENGTH < 0 || buffer.remaining() < RDLENGTH) {
            throw new IllegalArgumentException("Invalid RDLENGTH or insufficient data for RDATA");
        }

        byte[] rdata = new byte[RDLENGTH];
        buffer.get(rdata);

        String rdataStr;
        if (QTYPE == 1 && RDLENGTH == 4) { // A Record (IPv4)
            rdataStr = String.format("%d.%d.%d.%d", rdata[0] & 0xFF, rdata[1] & 0xFF, rdata[2] & 0xFF, rdata[3] & 0xFF);
        } else {
            rdataStr = new String(rdata, StandardCharsets.UTF_8); // Keep this for other record types
        }

        DNSAnswer answer = new DNSAnswer(domainName, QTYPE, QCLASS, RDLENGTH, rdataStr);

        currPos = buffer.position(); // Update position
        return answer;
    }

    private String parseDomainName(ByteBuffer buffer) {
        StringBuilder labelBuilder = new StringBuilder();
        while (true) {
            byte labelLength = buffer.get();
            if (labelLength == 0) {
                break;
            } else if ((labelLength & 0xC0) == 0xC0) { // Check for pointer using bitwise AND
                int offset = ((labelLength & 0x3F) << 8) | (buffer.get() & 0xFF);
                String cachedDomain = domainMap.get(offset);
                if (cachedDomain == null) {
                    throw new IllegalArgumentException("Pointer offset does not exist in domainMap");
                }
                labelBuilder.append(cachedDomain);
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
        return labelBuilder.toString();
    }
}
