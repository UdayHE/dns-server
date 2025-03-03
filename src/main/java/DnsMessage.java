import java.io.ByteArrayOutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DnsMessage {
    private DnsHeader header;
    private List<DnsQuestion> questions;
    private List<DnsAnswer> answers;

    public DnsMessage(DnsHeader header, List<DnsQuestion> questions, List<DnsAnswer> answers) {
        this.header = new DnsHeader(header.getID(), header.getFlags(), header.getQDCOUNT(), header.getANCOUNT(),
                header.getNSCOUNT(), header.getARCOUNT());
        this.questions = new ArrayList<>(questions);
        this.answers = new ArrayList<>(answers);
    }

    public int getQuestionCount() {
        return header.getQDCOUNT();
    }

    public DnsHeader getHeader() {
        return header;
    }

    public List<DnsQuestion> getQuestions() {
        return questions;
    }

    public List<DnsAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<DnsAnswer> answers) {
        this.answers = answers;
    }

    public byte[] getMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(512)
                .put(header.getHeader());
        for (DnsQuestion question : questions)
            buffer.put(question.getQuestion());
        for (DnsAnswer answer : answers)
            buffer.put(answer.getAnswer());
        return buffer.array();
    }

    public String toString() {
        return header + " - " + questions + " - " + answers;
    }

}