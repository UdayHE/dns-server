package dns;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Parser {

    private int currPos = 0;
    private final HashMap<Integer, String> domainMap = new HashMap<>();

    public Message parse(DatagramPacket packet) {
        byte[] data = packet.getData();
        Header header = parseHeader(data);
        int qdcount = header.getQdCount();
        currPos = 12;
        List<Question> questions = new ArrayList<>();
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < qdcount; i++)
            questions.add(parseQuestion(data));
        for (int i = 0; i < qdcount; i++)
            answers.add(parseAnswer(data));
        return new Message(header, questions, answers);
    }

    private Header parseHeader(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        short ID = buffer.getShort();
        short FLAGS = buffer.getShort();
        short QDCOUNT = buffer.getShort();
        short ANCOUNT = buffer.getShort();
        short NSCOUNT = buffer.getShort();
        short ARCOUNT = buffer.getShort();
        return new Header(ID, FLAGS, QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT);
    }

    private Question parseQuestion(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(currPos);
        byte labelLength = buffer.get();
        StringBuilder labelBuilder = new StringBuilder();
        while (labelLength > 0) {
            if ((labelLength & 192) == 192) {
                labelBuilder.append(".");
                int offset = ((labelLength & 63) << 8) | (buffer.get() & 255);
                labelBuilder.append(domainMap.get(offset));
                break;
            }
            labelBuilder.append(new String(buffer.array(), buffer.position(), labelLength, StandardCharsets.UTF_8));
            buffer.position(buffer.position() + labelLength);
            labelLength = buffer.get();
            if (labelLength > 0) labelBuilder.append(".");
        }
        short QTYPE = buffer.getShort();
        short QCLASS = buffer.getShort();

        Question question = new Question(labelBuilder.toString(), QTYPE, QCLASS);

        domainMap.put(currPos, labelBuilder.toString());
        currPos = buffer.position();
        return question;
    }

    private Answer parseAnswer(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(currPos);
        String domainName = parseDomainName(buffer);
        short QTYPE = buffer.getShort();
        short QCLASS = buffer.getShort();
        int TTL = buffer.getInt();
        short RDLENGTH = buffer.getShort();

        byte[] rdata = new byte[RDLENGTH];
        int ipPos = buffer.position();
        buffer.get(rdata);

        String rdataStr;
        if (QTYPE == 1 && RDLENGTH == 4)  // A Record (IPv4)
            rdataStr = String.format("%d.%d.%d.%d", rdata[0] & 0xFF, rdata[1] & 0xFF, rdata[2] & 0xFF, rdata[3] & 0xFF);
        else
            rdataStr = new String(rdata, StandardCharsets.UTF_8); // Keep this for other record types

        Answer answer = new Answer(domainName, QTYPE, QCLASS, RDLENGTH, rdataStr);
        currPos = buffer.position(); // Update position
        return answer;
    }

    private String parseDomainName(ByteBuffer buffer) {
        int labelLength = buffer.get();
        StringBuilder labelBuilder = new StringBuilder();
        while (labelLength > 0) {
            labelBuilder.append(new String(buffer.array(), buffer.position(), labelLength, StandardCharsets.UTF_8));
            buffer.position(buffer.position() + labelLength);
            labelLength = buffer.get();
            if (labelLength > 0) labelBuilder.append(".");
        }
        return labelBuilder.toString();
    }
}