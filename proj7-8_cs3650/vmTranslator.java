//Pablo Duenas
//Project 6
//CS 3650

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class vmTranslator {
    public static void translate(String[] args) {
        for (String arg : args) {
            File f = new File(arg);
            if (f.isFile()) {
                final int extIndex = arg.lastIndexOf(".");
                final String outputName = arg.substring(0, extIndex) + ".asm";
                codeWriter writer = new codeWriter(outputName);
                translate(writer, arg);
                writer.close();
            } else if (f.isDirectory()) {
                String outputName = arg;
                Path p = Paths.get(arg);
                outputName = outputName + "/" + p.getFileName().toString() + ".asm";
                codeWriter writer = new codeWriter(outputName);
                try(Stream<Path> stream = Files.list(p)) {
                    stream.filter(file -> !Files.isDirectory(file)).map(Path::toString).filter(file -> file.endsWith(".vm"))
                            .forEach(file -> translate(writer, file));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                writer.close();
            }
        }
    }

    private static void translate(codeWriter writer, String arg) {
        parser parser = new parser(arg);
        writer.setFilename(arg);
        while (parser.hasMoreLines()) {
            String arg1 = "";
            String arg2 = "";
            commandType cmdType = parser.commandType();
            arg1 = cmdType != commandType.C_RETURN ? parser.arg1() : "";
            if (cmdType == commandType.C_PUSH || cmdType == commandType.C_POP ||
                    cmdType == commandType.C_FUNCTION || cmdType == commandType.C_CALL) {
                arg2 = parser.arg2();
            }

            switch (cmdType) {
                case C_ARITHMETIC -> {
                    writer.writeArithmetic(arg1);
                }
                case C_POP, C_PUSH -> {
                    writer.writePushPop(cmdType, arg1, Integer.parseInt(arg2));
                }
                case C_CALL -> {
                    writer.writeCall(arg1, Integer.parseInt(arg2));
                }
                case C_FUNCTION -> {
                    writer.writeFunction(arg1, Integer.parseInt(arg2));
                }
                case C_GOTO -> {
                    writer.writeGoto(arg1);
                }
                case C_IF -> {
                    writer.writeIf(arg1);
                }
                case C_LABEL -> {
                    writer.writeLabel(arg1);
                }
                case C_RETURN -> {
                    writer.writeReturn();
                }
            }
            parser.advance();
        }
    }
}