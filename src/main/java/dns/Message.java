package dns;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// Represents a DNS message, which includes a header, questions, and answers.
public class Message {

    private static final String SEPARATOR = " - ";
    private static final int BUFFER_SIZE = 512;

    private final Header header;
    private final List<Question> questions;
    private final List<Answer> answers;

    // Constructs a new DNS message with the specified header, questions, and answers.
    public Message(Header header, List<Question> questions, List<Answer> answers) {
        this.header = new Header(header.getId(), header.getFlags(), header.getQdCount(), header.getAnCount(),
                header.getNsCount(), header.getArCount());
        this.questions = new ArrayList<>(questions);
        this.answers = new ArrayList<>(answers);
    }

    // Returns the number of questions in the DNS message.
    public int getQuestionCount() {
        return header.getQdCount();
    }

    // Retrieves the header of the DNS message.
    public Header getHeader() {
        return header;
    }

    // Retrieves the list of questions in the DNS message.
    public List<Question> getQuestions() {
        return questions;
    }

    // Retrieves the list of answers in the DNS message.
    public List<Answer> getAnswers() {
        return answers;
    }

    // Constructs a byte array representation of the DNS message.
    public byte[] getMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE)
                .put(header.getHeader());
        for (Question question : questions)
            buffer.put(question.getName());
        for (Answer answer : answers)
            buffer.put(answer.getAnswer());
        return buffer.array();
    }

    // Provides a string representation of the DNS message, including the header, questions, and answers.
    public String toString() {
        return header + SEPARATOR + questions + SEPARATOR + answers;
    }

}
