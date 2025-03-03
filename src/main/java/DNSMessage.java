import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DNSMessage {
    private DNSHeader header;
    private List<DNSQuestion> questions;
    private List<DNSAnswer> answers;

    public DNSMessage(DNSHeader header, List<DNSQuestion> questions, List<DNSAnswer> answers) {
        this.header = new DNSHeader(header.getID(), header.getFlags(), header.getQDCOUNT(), header.getANCOUNT(),
                header.getNSCOUNT(), header.getARCOUNT());
        this.questions = new ArrayList<>(questions);
        this.answers = new ArrayList<>(answers);
    }

    public int getQuestionCount() {
        return header.getQDCOUNT();
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

    public void setAnswers(List<DNSAnswer> answers) {
        this.answers = answers;
    }

    public byte[] getMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(512)
                .put(header.getHeader());
        for (DNSQuestion question : questions)
            buffer.put(question.getQuestion());
        for (DNSAnswer answer : answers)
            buffer.put(answer.getAnswer());
        return buffer.array();
    }

    public String toString() {
        return header + " - " + questions + " - " + answers;
    }

}