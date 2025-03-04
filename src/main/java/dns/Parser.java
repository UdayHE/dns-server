package dns;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Parser {

    private static final String SEPARATOR = ".";

    private int currPosition = 0;

    public Message parse(DatagramPacket packet) {
        byte[] data = packet.getData();
        Header header = parseHeader(data);
        int qdCount = header.getQdCount();
        currPosition = 12;
        List<Question> questions = new ArrayList<>();
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < qdCount; i++)
            questions.add(parseQuestion(data));
        for (int i = 0; i < qdCount; i++)
            answers.add(parseAnswer(data));
        return new Message(header, questions, answers);
    }

    private Header parseHeader(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        short id = buffer.getShort();
        short flags = buffer.getShort();
        short qdCount = buffer.getShort();
        short anCount = buffer.getShort();
        short nsCount = buffer.getShort();
        short arCount = buffer.getShort();
        return new Header(id, flags, qdCount, anCount, nsCount, arCount);
    }

    private Question parseQuestion(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(currPosition);
        byte labelLength = buffer.get();
        StringBuilder labelBuilder = new StringBuilder();
        while (labelLength > 0) {
            labelBuilder.append(new String(buffer.array(), buffer.position(), labelLength, StandardCharsets.UTF_8));
            buffer.position(buffer.position() + labelLength);
            labelLength = buffer.get();
            if (labelLength > 0) labelBuilder.append(SEPARATOR);
        }
        short qType = buffer.getShort();
        short qClass = buffer.getShort();

        Question question = new Question(labelBuilder.toString(), qType, qClass);

        currPosition = buffer.position();
        return question;
    }

    private Answer parseAnswer(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(currPosition);
        String domainName = parseDomainName(buffer);
        short qType = buffer.getShort();
        short qClass = buffer.getShort();
        buffer.getInt(); //ttl
        short rdLength = buffer.getShort();

        byte[] rdata = new byte[rdLength];
      //  buffer.position();
        buffer.get(rdata);

        String rdataStr;
        if (qType == 1 && rdLength == 4)  // A Record (IPv4)
            rdataStr = String.format("%d.%d.%d.%d", rdata[0] & 0xFF, rdata[1] & 0xFF, rdata[2] & 0xFF, rdata[3] & 0xFF);
        else
            rdataStr = new String(rdata, StandardCharsets.UTF_8); // Keep this for other record types

        Answer answer = new Answer(domainName, qType, qClass, rdLength, rdataStr);

        currPosition = buffer.position(); // Update position
        return answer;
    }

    private String parseDomainName(ByteBuffer buffer) {
        int labelLength = buffer.get();
        StringBuilder labelBuilder = new StringBuilder();
        while (labelLength > 0) {
            labelBuilder.append(new String(buffer.array(), buffer.position(), labelLength, StandardCharsets.UTF_8));
            buffer.position(buffer.position() + labelLength);
            labelLength = buffer.get();
            if (labelLength > 0) labelBuilder.append(SEPARATOR);
        }
        return labelBuilder.toString();
    }
}