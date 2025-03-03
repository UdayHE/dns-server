package dns;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {

    private final DNSHeader header;
    private final List<DNSQuestion> questions;
    private final List<DNSAnswer> answers;

    private static final String SEPARATOR = " - ";
    private static final int BUFFER_SIZE = 512;

    public DNSMessage(DNSHeader header, List<DNSQuestion> questions, List<DNSAnswer> answers) {
        this.header = new DNSHeader(header.getId(), header.getFlags(), header.getQdCount(), header.getAnCount(),
                header.getNsCount(), header.getArCount());
        this.questions = new ArrayList<>(questions);
        this.answers = new ArrayList<>(answers);
    }

    public int getQuestionCount() {
        return header.getQdCount();
    }

    public DNSHeader getHeader() {
        return header;
    }

    public List<DNSQuestion> getQuestions() {
        return questions;
    }

    public List<DNSAnswer> getAnswers() {
        return answers;
    }

    public byte[] getMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE)
                .put(header.getHeader());
        for (DNSQuestion question : questions)
            buffer.put(question.getQuestion());
        for (DNSAnswer answer : answers)
            buffer.put(answer.getAnswer());
        return buffer.array();
    }

    public String toString() {
        return header + SEPARATOR + questions + SEPARATOR + answers;
    }

}