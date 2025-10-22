package core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HexDump {
    public static void main(String[] args) throws IOException {
        String[] lines = Files.readAllLines(
                Paths.get("testFiles/brokenClasses/" +
                        "0abf7148300f40a1da0538ab060552bca4a2f1d8_ReportBuilder.java"),
                StandardCharsets.ISO_8859_1).toArray(new String[0]);

        System.out.println(lines[368]);
        System.out.println(lines[368].length());
    }
}