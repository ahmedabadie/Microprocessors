
import java.util.ArrayList;
import java.util.Scanner;

public class Processor {

    int PC;

    int[] regs;
    int[] regStatus;

    int instructionBuffer;
    int instructionsInBuffer; // Fetched Instructions

    int cyclesSimulated;

    MemoryHierarchy M; // To Be done

    ArrayList<ROBEntry> ROB;

    int head;
    int tail;
    int ROBsize;
    int instructionsInROB;
    
    int maxIssuedPerCycle;

    ArrayList<String[]> fetched;
    int cyclesLeftToFetch;
    String[] instructionToBeFetched;

    ReservationEntry e; // Issued Instruction

    // Reservation Stations
    ArrayList<ReservationEntry> rs;

    int[] numCycles = new int[11];
    int[] numInstrs = new int[11];
    int[] maxInstrs = new int[11];

    public Processor(MemoryHierarchy M, int maxIssuedPerCycle, int instructionBuffer, int ROBsize, int[] maxInstrs, int[] numCycles) {
        PC = 32768;

        this.M = M;
        this.maxIssuedPerCycle = maxIssuedPerCycle;
        
        this.maxInstrs = maxInstrs;
        this.numCycles = numCycles;
        
        regs = new int[8];
        regStatus = new int[8];

        for (int i = 0; i < 8; i++) {
            regStatus[i] = -1;
        }
        regs[0] = 0;

        fetched = new ArrayList<String[]>();

        rs = new ArrayList<ReservationEntry>();

        this.ROBsize = ROBsize;
        ROB = new ArrayList<ROBEntry>();
        for (int i = 0; i < ROBsize; i++) {
            ROB.add(null);
        }
        head = 1;
        tail = 1;

        this.instructionBuffer = instructionBuffer;
        instructionsInBuffer = 0;
    }

    public void simulate() {
        // Commit Stage
        if (ROB.get(head - 1) != null && ROB.get(head - 1).ready) {
            regs[ROB.get(head - 1).dest] = ROB.get(head - 1).value;

            if (regStatus[ROB.get(head - 1).dest] == head) {
                regStatus[ROB.get(head - 1).dest] = -1;
            }
            instructionsInROB--;
            ROB.set(head - 1, null);
            head++;
            if (head == ROBsize + 1) {
                head = 1;
            }

        }

        // Write Stage
        for (int i = 0; i < rs.size(); i++) {
            if (rs.get(i).cyclesLeft == 0) {
                ROB.get(rs.get(i).dest - 1).ready = true;

                decreaseType(rs.get(i).type); // Todo unsure
                for (int j = 0; j < rs.size(); j++) {
                    if (rs.get(j).qj == rs.get(i).dest) {
                        rs.get(j).qj = -1;
                    }
                    if (rs.get(j).qk == rs.get(i).dest) {
                        rs.get(j).qk = -1;
                    }
                }
                rs.remove(i);
                break;
            }
        }

        // Execute Stage
        for (int i = 0; i < rs.size(); i++) {
            if (rs.get(i).qj == -1 && rs.get(i).qk == -1) {
                execute(rs.get(i));
            }
        }

        //Issue Stage
        e = null; // Will Contain Issued Instruction
        int issuedInstructions = 0;
        while (issuedInstructions < maxIssuedPerCycle && fetched.size() > 0 && Issue(fetched.get(0))) {
            fetched.remove(0);
            instructionsInBuffer--;
            instructionsInROB++;
            issuedInstructions++;
            if (e != null) { // If there is an instruction to be issued
                tail++;
                if (tail == ROBsize + 1) {
                    tail = 1;
                }
                rs.add(e);
            }
        }

        // Fetch Stage
        if(cyclesLeftToFetch == 0) fetch();
        
        if(cyclesLeftToFetch > 0) {
        	cyclesLeftToFetch--;
        	if(cyclesLeftToFetch == 0) {
        		fetched.add(instructionToBeFetched);
        	}
        }

        cyclesSimulated++;
        System.out.println("Cycle: " + cyclesSimulated);
        System.out.println();
        printAll();
        System.out.println();
    }

    public int computeResult(InstrType type, int vj, int vk, int a) {

        if (type == InstrType.ADDI) {
            return (vj + a);
        }
        else if (type == InstrType.ADD) {
            return (vj + vk);
        }
        else if (type == InstrType.SUB) {
            return (vj - vk);
        }
        else if (type == InstrType.MUL) {
            return (vj * vk);
        }
        else if (type == InstrType.NAND) {
            return ~(vj & vk);
        }
        
        return 0;
    }

    public void fetch() {
    	FetchedObject fetchedObject = M.read(PC);
    	String instr = fetchedObject.getData();
    	System.out.println(fetchedObject.getCycles());
    	if(instr == null) instr = "ADD R7 R7 R1";
    	instructionToBeFetched = instr.split(" ");
    	cyclesLeftToFetch = fetchedObject.getCycles();
    	PC+= 2;
    }

    public boolean Issue(String[] strInstr) {
        if (instructionsInROB == ROBsize) {
            return false;
        }

        Instruction instr = new Instruction(strInstr);
        int idx = instr.getInstrType().ordinal();
        if (numInstrs[idx] <= maxInstrs[idx]) {

            int rd = instr.getRegA();
            int rs = instr.getRegB();
            int rt = instr.getRegB();
            int addr = instr.getImm(); // ADDi is an exception ? and check -1 bug

            int vj = 0;
            int vk = 0;
            int qj = -1;
            int qk = -1;

            if (rs != 0) {
                if (regStatus[rs] != -1) { // If not busy
                    int h = regStatus[rs];
                    if (ROB.get(h).ready) {
                        vj = ROB.get(h).value;
                    } else {
                        qj = h;
                    }
                } else {
                    vj = regs[rs];
                }
            }

            if (rt != 0) {
                if (regStatus[rt] != -1) { // If not busy
                    int h = regStatus[rt];
                    if (ROB.get(h).ready) {
                        vk = ROB.get(h).value;
                    } else {
                        qj = h;
                    }
                } else {
                    vk = regs[rt];
                }
            }

            boolean busy = true;
            int dest = head;

            int cycles = numCycles[instr.getInstrType().ordinal()];
            e = new ReservationEntry(busy, instr.getInstrType(), vj, vk, qj, qk, dest, addr, cycles);

            if (instr.getInstrType() != InstrType.SW) {
                regStatus[rd] = head;
            }
            ROBEntry sd = new ROBEntry(instr.getInstrType(), rd, 0, false); // FIx -1 thingy
            ROB.set(tail - 1, sd);
            return true;
        }

        return false;
    }

    public void decreaseType(InstrType type) {
        numInstrs[type.ordinal()]--;
    }

    public boolean canExecute(ReservationEntry e) {
        if (e.qj == -1 && e.qk == -1) {
            return true;
        }
        return false;
    }

    public void execute(ReservationEntry e) {
        e.cyclesLeft--;
    }

    public void printAll() {
        System.out.println("        ROB       ");
        System.out.println("  Type Dest Value Ready");
        for (int i = 0; i < ROBsize; i++) {
            System.out.print((i + 1) + " ");
            if (ROB.get(i) != null) {
                System.out.format("%-5s", ROB.get(i).instruction);
                System.out.format("%-5d", ROB.get(i).dest);
                System.out.format("%-6d", ROB.get(i).value);
                System.out.format("%-5b", ROB.get(i).ready);
            }
            System.out.println();
        }
        System.out.println("        Reservation Stations       ");
        System.out.println("Op   Vj Vk Qj Qk Dest A   CyclesLeft");
        for (int i = 0; i < rs.size(); i++) {
            System.out.format("%-5s", rs.get(i).type);
            if (rs.get(i).vj != -1) {
                System.out.format("%-3d", rs.get(i).vj);
            } else {
                System.out.print("   ");
            }
            if (rs.get(i).vk != -1) {
                System.out.format("%-3d", rs.get(i).vk);
            } else {
                System.out.print("   ");
            }
            if (rs.get(i).qj != -1) {
                System.out.format("%-3d", rs.get(i).qj);
            } else {
                System.out.print("   ");
            }
            if (rs.get(i).qk != -1) {
                System.out.format("%-3d", rs.get(i).qk);
            } else {
                System.out.print("   ");
            }
            System.out.format("%-5d", rs.get(i).dest);
            System.out.format("%-4d", rs.get(i).addr);
            System.out.print(rs.get(i).cyclesLeft);
            System.out.println();
        }
        System.out.println("          Registers status            ");
        System.out.println("R0 R1 R2 R3 R4 R5 R6 R7");
        for (int i = 0; i < 8; i++) {
            if (regStatus[i] != -1) {
                System.out.format("%-3d", regStatus[i]);
            } else {
                System.out.print("   ");
            }
        }
    }

    public static void main(String[] args) throws InvalidNumberOfBanksException {
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the number of cache Levels");
        int numCacheLevels = sc.nextInt();
        
        int[] S = new int[Math.min(3, numCacheLevels)];
        int[] m = new int[Math.min(3, numCacheLevels)];
        CacheWriteHitPolicy[] cacheWriteHitPolicy = new CacheWriteHitPolicy[Math.min(3, numCacheLevels)];    
        System.out.println("Enter the Line Size L of the cache(s)");
        int L = sc.nextInt();
        for(int i = 1; i <= Math.min(3, numCacheLevels); i++) {
        	System.out.println("Enter S, M and the writing policy (0 for WriteBack, 1 for WriteThrough) of cache #" + i + " (seperated by spaces)");
        	S[i-1] = sc.nextInt();
        	m[i-1] = sc.nextInt();
        	int t = sc.nextInt();
        	if(t == 0) cacheWriteHitPolicy[i-1] = CacheWriteHitPolicy.WriteBack;
        	else cacheWriteHitPolicy[i-1] = CacheWriteHitPolicy.WriteThrough;
        }
        
        int[] cycles = new int[Math.min(3, numCacheLevels)];
        for(int i = 1; i <= Math.min(3, numCacheLevels); i++) {
        	System.out.println("Enter the access time (in cycles) of Cache #" + i);
        	cycles[i-1] = sc.nextInt();   	
        }
        System.out.println("Enter the main memory access time");
        int memoryCycles = sc.nextInt();
        
        MemoryHierarchy M = new MemoryHierarchy(numCacheLevels, L, S, m, cycles, memoryCycles, cacheWriteHitPolicy);
        
        System.out.println("Enter the pipeline width");
        int pipelineWidth = sc.nextInt();
        
        System.out.println("Enter the size of the instruction buffer (queue)");
        int insturctionBuffer = sc.nextInt();
        
        int[] maxInstrs = new int[11];
        System.out.println("Enter the number of reservation stations for each of the following instructions (seperated by spaces)");
        System.out.println("LW, SW, JMP, BEQ, JALR, RET, ADD, SUB, ADDI, NAND, MUL");
        maxInstrs[InstrType.LW.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.SW.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.JMP.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.BEQ.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.JALR.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.RET.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.ADD.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.SUB.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.ADDI.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.NAND.ordinal()] = sc.nextInt();
        maxInstrs[InstrType.MUL.ordinal()] = sc.nextInt();
        
        System.out.println("Enter the number of ROB Entries Available");
        int ROBsize = sc.nextInt();
        
        int[] numCycles = new int[11];
        System.out.println("Enter the number of cycles needed by each of the following (seperated by spaces)");
        System.out.println("JMP, BEQ, JALR, RET, ADD, SUB, ADDI, NAND, MUL");
        numCycles[InstrType.JMP.ordinal()] = sc.nextInt();
        numCycles[InstrType.BEQ.ordinal()] = sc.nextInt();
        numCycles[InstrType.JALR.ordinal()] = sc.nextInt();
        numCycles[InstrType.RET.ordinal()] = sc.nextInt();
        numCycles[InstrType.ADD.ordinal()] = sc.nextInt();
        numCycles[InstrType.SUB.ordinal()] = sc.nextInt();
        numCycles[InstrType.ADDI.ordinal()] = sc.nextInt();
        numCycles[InstrType.NAND.ordinal()] = sc.nextInt();
        numCycles[InstrType.MUL.ordinal()] = sc.nextInt();
        
        System.out.println("Your Program should be in address location 32768");
        Processor p = new Processor(M, pipelineWidth, insturctionBuffer, ROBsize, maxInstrs, numCycles);
        
//        M.write(32768, "ADD R7 R7 R0");
        for(int i = 0; i < 25; i++)
        p.simulate();
    }
}
