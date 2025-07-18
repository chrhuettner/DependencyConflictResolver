package core;

import java.io.*;
import java.util.regex.*;

public class BreakingChangeExampleGenerator {

    public static void main(String[] args) {
        String inputFilePath = "jar_diff_output.txt";
        String outputFilePath = "breaking_examples.java";

        try (
                BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))
        ) {
            String line;
            String fromJar = "";
            String toJar = "";

            Pattern methodPattern = Pattern.compile(
                    "-\\s+"
                            + "(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|default|volatile|transient)\\s+"
                            + "(?:"
                            +   "(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|default|volatile|transient)\\s+"
                            + ")*"
                            + "([\\w.$<>\\[\\]]+)\\s+"      // return type
                            + "(\\w+)\\s*"             // method name
                            + "\\(([^)]*)\\)\\s+"      // params
                            + "of class\\s+"
                            + "([\\w.$]+)"

            );
            //TODO: Use JSON instead
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                System.out.println(line);

                // Detect jar paths if included
                if (line.startsWith("Library:")) {
                    String lib = line.substring(line.indexOf(":")+1).trim();
                    writer.write("// Library: " + lib + "\n");
                } else if (line.startsWith("Versions:")) {
                    String[] versions = line.split(":")[1].trim().split(" ");
                    fromJar =  versions[0];
                    toJar =  versions[1];

                    writer.write("// From: " + fromJar + ".jar\n");
                    writer.write("// To:   " + toJar + ".jar\n");
                }

                Matcher matcher = methodPattern.matcher(line);
                if (matcher.find()) {
                    String returnType = matcher.group(1);
                    String methodName = matcher.group(2);
                    String paramList = matcher.group(3).trim();
                    String className = matcher.group(4);

                    boolean isStatic = line.contains(" static ");

                    // Generate call
                    StringBuilder call = new StringBuilder();
                    call.append(className).append(" ");
                    if (isStatic) {
                        call.append(className.replace('$', '.')).append(".").append(methodName).append("(");
                    } else {
                        call.append("obj.").append(methodName).append("(");
                    }

                    if (!paramList.isEmpty()) {
                        int paramCount = paramList.split(",").length;
                        for (int i = 0; i < paramCount; i++) {
                            call.append("null");
                            if (i < paramCount - 1) call.append(", ");
                        }
                    }

                    call.append(");");

                    writer.write(call.toString() + "\n");
                }
            }

            System.out.println("Generated: " + outputFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
