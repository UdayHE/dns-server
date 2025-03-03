package dns;

import java.nio.ByteBuffer;

public class DNSHeader {
    private short id;
    private byte qr;
    private byte opcode;
    private boolean aa;
    private boolean tc;
    private boolean rd;
    private boolean ra;
    private byte rcode;
    private short flags;
    private short qdCount;
    private short anCount;
    private short nsCount;
    private short arCount;

    public short getId() {
        return id;
    }

    public short getAnCount() {
        return anCount;
    }

    public short getNsCount() {
        return nsCount;
    }

    public short getArCount() {
        return arCount;
    }

    public short getFlags() {
        return flags;
    }

    public DNSHeader(short id, short flags, short qdCount, short anCount, short nsCount, short arCount) {
        this.id = id;
        this.qdCount = qdCount;
        this.anCount = anCount;
        this.nsCount = nsCount;
        this.arCount = arCount;
        this.flags = flags;
        extractFlags(flags);
    }

    private void extractFlags(short flags) {
        byte[] flagBytes = ByteBuffer.allocate(2).putShort(flags).array();
        byte firstHalf = flagBytes[0];
        byte secondHalf = flagBytes[1];
        this.qr = (byte) (firstHalf&(1<<7));
//        this.qr = 1;
        this.opcode = (byte) ((firstHalf>>3)&15);
//        this.aa = (secondHalf&(1<<2)) != 0;
//        this.tc = (secondHalf&(1<<1)) != 0;
        this.aa = false;
        this.tc = false;
        this.rd = (firstHalf&1) != 0;
//        System.out.println("Extracted flags: " + qr + " " + opcode + " " + aa + " " + tc + " " + rd);
    }

    private byte[] getFlagBytes() {
        byte flagsFirstHalf = (byte) (((qr&1) << 7)
                | ((opcode&15) << 3)
                | ((aa ? 1 : 0) << 2)
                | ((tc ? 1 : 0) << 1)
                | (rd ? 1 : 0));
//        byte flagsSecondHalf = (byte) (((ra ? 1 : 0) << 7) | (rcode&15));
        byte flagsSecondHalf = (byte) (((ra ? 1 : 0) << 7) | ((opcode == 0) ? 0 : 4));
        return new byte[]{flagsFirstHalf, flagsSecondHalf};
    }

    public byte[] getHeader() {
        return ByteBuffer.allocate(12)
                .putShort(id)
                .put(getFlagBytes())
                .putShort(qdCount)
                .putShort(anCount)
                .putShort(nsCount)
                .putShort(arCount)
                .array();
    }

    public short getQdCount() {
        return qdCount;
    }

    public void setQr(byte i) {
        this.qr = i;
    }

    public void setAnCount(short anCount) {
        this.anCount = anCount;
    }

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
