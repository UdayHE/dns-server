package dns;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Message {

    private final Header header;
    private final List<Question> questions;
    private final List<Answer> answers;

    private static final String SEPARATOR = " - ";
    private static final int BUFFER_SIZE = 512;

    public Message(Header header, List<Question> questions, List<Answer> answers) {
        this.header = new Header(header.getId(), header.getFlags(), header.getQdCount(), header.getAnCount(),
                header.getNsCount(), header.getArCount());
        this.questions = new ArrayList<>(questions);
        this.answers = new ArrayList<>(answers);
    }

    public int getQuestionCount() {
        return header.getQdCount();
    }

    public Header getHeader() {
        return header;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public List<Answer> getAnswers() {
        return answers;
    }

    public byte[] getMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE)
                .put(header.getHeader());
        for (Question question : questions)
            buffer.put(question.getQuestion());
        for (Answer answer : answers)
            buffer.put(answer.getAnswer());
        return buffer.array();
    }

    public String toString() {
        return header + SEPARATOR + questions + SEPARATOR + answers;
    }

}