import java.nio.ByteBuffer;

public class DNSHeader {
    private short ID;
    private byte QR;
    private byte OPCODE;
    private boolean AA;
    private boolean TC;
    private boolean RD;
    private boolean RA;
    private byte RCODE;
    private short flags;
    private short QDCOUNT;
    private short ANCOUNT;
    private short NSCOUNT;
    private short ARCOUNT;

    public short getID() {
        return ID;
    }



    public short getANCOUNT() {
        return ANCOUNT;
    }

    public short getNSCOUNT() {
        return NSCOUNT;
    }

    public short getARCOUNT() {
        return ARCOUNT;
    }

    public short getFlags() {
        return flags;
    }

    public DNSHeader(short id, short flags, short qdcount, short ancount, short nscount, short arcount) {
        this.ID = id;
        this.QDCOUNT = qdcount;
        this.ANCOUNT = ancount;
        this.NSCOUNT = nscount;
        this.ARCOUNT = arcount;
        this.flags = flags;
        extractFlags(flags);
    }

    private void extractFlags(short flags) {
        byte[] flagBytes = ByteBuffer.allocate(2).putShort(flags).array();
        byte firstHalf = flagBytes[0];
        byte secondHalf = flagBytes[1];
        this.QR = (byte) (firstHalf&(1<<7));
//        this.QR = 1;
        this.OPCODE = (byte) ((firstHalf>>3)&15);
//        this.AA = (secondHalf&(1<<2)) != 0;
//        this.TC = (secondHalf&(1<<1)) != 0;
        this.AA = false;
        this.TC = false;
        this.RD = (firstHalf&1) != 0;
//        System.out.println("Extracted flags: " + QR + " " + OPCODE + " " + AA + " " + TC + " " + RD);
    }

    private byte[] getFlagBytes() {
        byte flagsFirstHalf = (byte) (((QR&1) << 7)
                                    | ((OPCODE&15) << 3)
                                    | ((AA ? 1 : 0) << 2)
                                    | ((TC ? 1 : 0) << 1)
                                    | (RD ? 1 : 0));
//        byte flagsSecondHalf = (byte) (((RA ? 1 : 0) << 7) | (RCODE&15));
        byte flagsSecondHalf = (byte) (((RA ? 1 : 0) << 7) | ((OPCODE == 0) ? 0 : 4));
        return new byte[]{flagsFirstHalf, flagsSecondHalf};
    }

    public byte[] getHeader() {
        return ByteBuffer.allocate(12)
                .putShort(ID)
                .put(getFlagBytes())
                .putShort(QDCOUNT)
                .putShort(ANCOUNT)
                .putShort(NSCOUNT)
                .putShort(ARCOUNT)
                .array();
    }

    public short getQDCOUNT() {
        return QDCOUNT;
    }

    public void setQR(byte i) {
        this.QR = i;
    }

    public void setANCOUNT(short ANCOUNT) {
        this.ANCOUNT = ANCOUNT;
    }

    public String toString() {
        return "ID: " + ID +
                "\nQR: " + QR +
                "\nOPCODE: " + OPCODE +
                "\nAA: " + AA +
                "\nTC: " + TC +
                "\nRD: " + RD +
                "\nRA: " + RA +
                "\nRCODE: " + RCODE +
                "\nQDCOUNT: " + QDCOUNT +
                "\nANCOUNT: " + ANCOUNT +
                "\nNSCOUNT: " + NSCOUNT +
                "\nARCOUNT: " + ARCOUNT;
    }
}