import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class codeWriter {
    private final PrintWriter writer;
    private String filename;
    private int labelNumber;
    private String functionName;


    private void writeDecSP() {
        writer.write("@SP\n");
        writer.write("M=M-1\n");
    }

    private void writeBinaryOp(String op) {
          popToD_RamSP();
        writer.write("@SP\n");
        writer.write("A=M-1\n");
        writer.write(op+"\n");
    }

    private void writeLogicalOp(String jmpSym) {
        final String jumpLabel = String.format("LABEL_%s_%d", jmpSym, labelNumber);

        popToD_RamSP();

        writer.write("@SP\n");
        writer.write("A=M-1\n");

        writer.write("D=M-D\n");
        writer.write("M=-1\n");
        writer.write(String.format("@%s\n", jumpLabel));
        writer.write(String.format("D;%s\n", jmpSym));

        writer.write("@SP\n");
        writer.write("A=M-1\n");
        writer.write("M=0\n");
        writer.write(String.format("(%s)\n", jumpLabel));

        ++labelNumber;
    }

    private void writeUnaryOp(String line) {
        writer.write("@SP\n");
        writer.write("A=M-1\n");
        writer.write(line+"\n");
    }

    private void writeOffsetSegmentValue(String segment, String symbol, int offset) {
        writer.write(String.format("%s\n", symbol));
        writer.write("D=A\n");

        if (!segment.equals("pointer")) {
            writer.write(String.format("@%d\n", offset));
            writer.write("A=D+A\n");
            writer.write("D=A\n");
        }
    }
    private void writeOffsetSegmentMemory(String symbol, int offset) {
        writer.write(String.format("%s\n", symbol));
        writer.write("D=M\n");
        writer.write(String.format("@%d\n", offset));
        writer.write("A=D+A\n");
        writer.write("D=A\n");
    }

    private void writeOffsetSegment(String segment, String symbol, int offset) {
        switch (segment) {
            case "temp", "pointer" -> writeOffsetSegmentValue(segment, symbol, offset);
            case "local", "argument", "this", "that" -> writeOffsetSegmentMemory(symbol, offset);
            default -> throw new RuntimeException("writeOffsetSegment: " + segment);
        }
    }

    private void pushToRamSP_D() {
        writer.write("@SP\n");
        writer.write("AM=M+1\n");
        writer.write("A=A-1\n");
        writer.write("M=D\n");
    }

    private void writePushSegment(String segment, int index) {
        switch (segment) {
            case "constant" -> {
                writer.write(String.format("@%d\n", index));
                writer.write("D=A\n");

                pushToRamSP_D();
            }
            case "static" -> {
                final String sym = String.format("@%s.%d\n", filename.split(".+?/(?=[^/]+$)")[1], index);
                writer.write(sym);
                writer.write("D=M\n");

                pushToRamSP_D();
            }
            case "local", "argument", "this", "that", "pointer", "temp" -> {
                final String sym = switch (segment) {
                    case "local" -> "@LCL";
                    case "argument" -> "@ARG";
                    case "this" -> "@THIS";
                    case "that" -> "@THAT";
                    case "pointer" -> index == 0 ? "@THIS" : "@THAT";
                    case "temp" -> "@5";
                    default -> throw new RuntimeException("writePushSegment: " + segment);
                };

                writeOffsetSegment(segment, sym, index);
                writer.write("D=M\n");

                pushToRamSP_D();
            }
        }
    }

    private void popToD_RamSP() {
        writeDecSP();
        writer.write("A=M\n");
        writer.write("D=M\n");

    }
    private void writePopSegment(String segment, int index) {
        switch (segment) {
            case "static" -> {
                final String sym = String.format("@%s.%d\n", filename.split(".+?/(?=[^/]+$)")[1], index);

                popToD_RamSP();

                writer.write(sym);
                writer.write("M=D\n");
            }

            case "local", "argument", "this", "that", "temp", "pointer" -> {
                final String sym = switch (segment) {
                    case "local" -> "@LCL";
                    case "argument" -> "@ARG";
                    case "this" -> "@THIS";
                    case "that" -> "@THAT";
                    case "temp" -> "@5";
                    case "pointer" -> index == 0 ? "@THIS" : "@THAT";
                    default -> throw new RuntimeException("writePopSegment " + segment);
                };

                writeOffsetSegment(segment, sym, index);

                writer.write("@R13\n");
                writer.write("M=D\n");

                popToD_RamSP();

                writer.write("@R13\n");
                writer.write("A=M\n");
                writer.write("M=D\n");
            }
        }
    }

    public codeWriter(String outputName) {
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(outputName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.labelNumber = 0;
        writer.write("@256\n");
        writer.write("D=A\n");
        writer.write("@SP\n");
        writer.write("M=D\n");

        writeCall("Sys.init", 0);
    }

    // translation of a new file
    public void setFilename(String filename) {
        String f = filename;
        int n = filename.lastIndexOf("/");
        if (n != -1) {
            f = filename.substring(n + 1);
        }
        n = f.lastIndexOf(".vm");
        if (n != -1) {
            f = f.substring(0, n);
        }
        this.filename = f;
    }

    public void writeArithmetic(String command) {
        switch (command) {
            case "add" -> writeBinaryOp("M=D+M");
            case "sub" -> writeBinaryOp("M=M-D");
            case "neg" -> writeUnaryOp("M=-M");
            case "and" -> writeBinaryOp("M=D&M");
            case "or" -> writeBinaryOp("M=D|M");
            case "eq" -> writeLogicalOp("JEQ");
            case "gt" -> writeLogicalOp("JGT");
            case "lt" -> writeLogicalOp("JLT");
            case "not" -> writeUnaryOp("M=!M");
        }
    }

    public void writePushPop(commandType cmd, String segment, int index) {
        if (cmd == commandType.C_PUSH) {
            writePushSegment(segment, index);
        } else if (cmd == commandType.C_POP) {
            writePopSegment(segment, index);
        }
    }

    public void writeLabel(String label) {
        writer.write(String.format("(%s.%s$%s)\n", this.filename, this.functionName, label));
    }

    public void writeGoto(String label) {
        final String labelName = String.format("%s.%s$%s", this.filename, this.functionName, label);

        writer.write(String.format("@%s\n", labelName));
        writer.write("0;JMP\n");
    }

    public void writeIf(String label) {
        final String labelName = String.format("%s.%s$%s", this.filename, this.functionName, label);
        writeDecSP();
        writer.write("M=D\n");
        writer.write("D=M\n");

        writer.write(String.format("@%s\n", labelName));
        writer.write("D;JNZ\n");
    }
    public void writeFunction(String functionName, int nVars) {
        writer.write(String.format("(%s.%s)\n", this.filename, functionName));
        for (int i = 0; i < nVars; i++) {
            writePushSegment("constant", 0);
        }
    }

    private void pushSymbol(String sym) {
        writer.write(String.format("@%s\n", sym));
        writer.write("D=M\n");

        pushToRamSP_D();
    }
    public void writeCall(String functionName, int nVars) {
        this.functionName = functionName;
        final String label = String.format("%s.%s$ret.%d", this.filename, functionName, this.labelNumber);
        writer.write(String.format("@%s\n", label));
        writer.write("@D=A\n");
        pushToRamSP_D();
        pushSymbol("LCL");
        pushSymbol("ARG");
        pushSymbol("THIS");
        pushSymbol("THAT");

        writer.write("@SP\n");
        writer.write("D=M\n");

        writer.write("@5\n");
        writer.write("D=D-A\n");

        writer.write(String.format("@%d\n", nVars));
        writer.write("D=D-A\n");

        writer.write("@ARG\n");
        writer.write("M=D\n");

        writer.write("@SP\n");
        writer.write("D=M\n");

        writer.write("@LCL\n");
        writer.write("M=D\n");

        writeGoto(functionName);

        writer.write(String.format("(%s)\n", functionName));

        this.labelNumber++;
    }

    private void writeReturnOffset(String symbol, int offset) {
        writer.write("@R13\n");
        writer.write("D=M\n");

        writer.write(String.format("@%d\n", offset));
        writer.write("D=D-A\n");
        writer.write("A=D\n");
        writer.write("D=M\n");

        writer.write(String.format("@%s\n", symbol));
        writer.write("M=D\n");
    }
    public void writeReturn() {
        writer.write("@LCL\n");
        writer.write("D=M\n");

        writer.write("@R13\n");
        writer.write("M=D\n");

        writeReturnOffset("R14\n", 5);

        writer.write("@LCL\n");
        writer.write("AM=M-1\n");
        writer.write("D=M\n");

        writer.write("@ARG\n");
        writer.write("A=M\n");
        writer.write("M=D\n");

        writer.write("@ARG\n");
        writer.write("D=M\n");
        writer.write("D=D+1\n");

        writer.write("@SP\n");
        writer.write("M=D\n");

        writeReturnOffset("THAT", 1);
        writeReturnOffset("THIS", 2);
        writeReturnOffset("ARG", 3);
        writeReturnOffset("LCL", 4);

        writer.write("@R14\n");
        writer.write("A=M\n");
        writer.write("0;JMP\n");
    }

    public void close() {
        writer.close();
    }
}