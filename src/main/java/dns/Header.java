package dns;

import java.nio.ByteBuffer;

/**
 * Represents the DNS message header.
 * Contains fields such as ID, flags, and counts of question, answer, authority, and additional records.
 */
public class Header {

    private static final int BUFFER_SIZE = 12;

    private final short id;
    private byte qr;
    private byte opcode;
    private boolean aa;
    private boolean tc;
    private boolean rd;
    private boolean ra;
    private byte rcode;
    private final short flags;
    private final short qdCount;
    private short anCount;
    private final short nsCount;
    private final short arCount;

    /**
     * Returns the ID of the DNS message.
     */
    public short getId() {
        return id;
    }

    /**
     * Returns the count of answer records in the DNS message.
     */
    public short getAnCount() {
        return anCount;
    }

    /**
     * Returns the count of authority records in the DNS message.
     */
    public short getNsCount() {
        return nsCount;
    }

    /**
     * Returns the count of additional records in the DNS message.
     */
    public short getArCount() {
        return arCount;
    }

    /**
     * Returns the combined flags of the DNS message.
     */
    public short getFlags() {
        return flags;
    }

    /**
     * Constructs a new DNS message header with the specified parameters.
     * @param id The ID of the DNS message.
     * @param flags The combined flags of the DNS message.
     * @param qdCount The count of question records.
     * @param anCount The count of answer records.
     * @param nsCount The count of authority records.
     * @param arCount The count of additional records.
     */
    public Header(short id, short flags, short qdCount, short anCount, short nsCount, short arCount) {
        this.id = id;
        this.qdCount = qdCount;
        this.anCount = anCount;
        this.nsCount = nsCount;
        this.arCount = arCount;
        this.flags = flags;
        extractFlags(flags);
    }

    /**
     * Extracts individual flag values from the combined flags.
     * @param flags The combined flags of the DNS message.
     */
    private void extractFlags(short flags) {
        byte[] flagBytes = ByteBuffer.allocate(2).putShort(flags).array();
        byte firstHalf = flagBytes[0];
        this.qr = (byte) (firstHalf & (1 << 7));
        this.opcode = (byte) ((firstHalf >> 3) & 15);
        this.aa = false;
        this.tc = false;
        this.rd = (firstHalf & 1) != 0;
    }

    /**
     * Constructs the flag bytes from individual flag values.
     * @return The constructed flag bytes as a byte array.
     */
    private byte[] getFlagBytes() {
        byte flagsFirstHalf = (byte) (((qr & 1) << 7)
                | ((opcode & 15) << 3)
                | ((aa ? 1 : 0) << 2)
                | ((tc ? 1 : 0) << 1)
                | (rd ? 1 : 0));

        byte flagsSecondHalf = (byte) (((ra ? 1 : 0) << 7) | ((opcode == 0) ? 0 : 4));
        return new byte[]{flagsFirstHalf, flagsSecondHalf};
    }

    /**
     * Returns the complete DNS message header as a byte array.
     * @return The DNS message header as a byte array.
     */
    public byte[] getHeader() {
        return ByteBuffer.allocate(BUFFER_SIZE)
                .putShort(id)
                .put(getFlagBytes())
                .putShort(qdCount)
                .putShort(anCount)
                .putShort(nsCount)
                .putShort(arCount)
                .array();
    }

    /**
     * Returns the count of question records in the DNS message.
     */
    public short getQdCount() {
        return qdCount;
    }

    /**
     * Sets the QR flag of the DNS message.
     * @param i The new value for the QR flag.
     */
    public void setQr(byte i) {
        this.qr = i;
    }

    /**
     * Sets the count of answer records in the DNS message.
     * @param anCount The new count of answer records.
     */
    public void setAnCount(short anCount) {
        this.anCount = anCount;
    }

    /**
     * Provides a string representation of the DNS message header.
     * @return A string representation of the DNS message header.
     */
    public String toString() {
        return "id: " + id +
                "\nqr: " + qr +
                "\nopcode: " + opcode +
                "\naa: " + aa +
                "\ntc: " + tc +
                "\nrd: " + rd +
                "\nra: " + ra +
                "\nrcode: " + rcode +
                "\nqdCount: " + qdCount +
                "\nanCount: " + anCount +
                "\nnsCount: " + nsCount +
                "\narCount: " + arCount;
    }
}
