
public class ReservationEntry {

    InstrType type;
    int vj;
    int vk;
    int qj;
    int qk;
    int dest;
    int cyclesLeft;
    int addr; // Might change type according to mem implementation
    boolean step1LoadStore;
    int pc;

    public ReservationEntry(boolean busy, InstrType type, int vj, int vk, int qj, int qk, int dest, int addr, int cyclesLeft) {
        this.type = type;
        this.vj = vj;
        this.vk = vk;
        this.qj = qj;
        this.qk = qk;
        this.dest = dest;
        this.addr = addr;
        this.cyclesLeft = cyclesLeft;
    }
}
