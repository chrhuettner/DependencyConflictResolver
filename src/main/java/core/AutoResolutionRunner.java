package core;

import provider.AIProvider;
import provider.ChatGPTProvider;
import provider.ClaudeProvider;
import provider.OllamaProvider;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static core.Main.buildPrompt;
import static core.Main.systemContext;

public class AutoResolutionRunner {

    public static void main(String[] args) throws IOException {
        List<LibraryBlock> libs = parseInputFile(Path.of("breaking_examples.java"));

        List<AIProvider> providers = List.of(
                //new ChatGPTProvider(),
                //new ClaudeProvider(),
                new OllamaProvider("codellama:7b"),
                new OllamaProvider("codellama:13b"),
                new OllamaProvider("codegemma:7b"),
                new OllamaProvider("deepseek-coder:6.7b"),
                new OllamaProvider("starcoder2:7b"),
                new OllamaProvider("deepseek-r1:1.5b"),
                new OllamaProvider("qwen3:8b"),
                new OllamaProvider("starcoder2:15b")
        );

        for (LibraryBlock lib : libs) {
            String libName = lib.artifactId;
            String oldVersion = lib.fromVersion;
            String newVersion = lib.toVersion;

            String baseRepo = "target/local-repo";
            String groupPath = lib.groupId.replace('.', '/');
            String artifact = lib.artifactId;

            String leftJar = baseRepo + "/" + groupPath + "/" + artifact + "/" + oldVersion + "/" + artifact + "-" + oldVersion + ".jar";
            String rightJar = baseRepo + "/" + groupPath + "/" + artifact + "/" + newVersion + "/" + artifact + "-" + newVersion + ".jar";

            for (String methodCall : lib.methodCalls) {


                int objIndex = methodCall.indexOf(" obj.");
                if (objIndex == -1) continue; // skip malformed line

                String className = methodCall.substring(0, objIndex).trim();

                String methodPart = methodCall.substring(objIndex + 5);
                int parenIndex = methodPart.indexOf('(');
                if (parenIndex == -1) continue;

                String methodName = methodPart.substring(0, parenIndex);

                String brokenCode = "obj." + methodPart;

                String prompt = buildPrompt(
                        libName,
                        oldVersion,
                        newVersion,
                        leftJar,
                        rightJar,
                        className,
                        methodName,
                        brokenCode,
                        new String[]{},
                        "",
                        ""
                );

                System.out.println("Prompt for " + className + "#" + methodName + ":\n" + prompt + "\n");


                ConflictResolutionResult response = providers.get(6).sendPromptAndReceiveResponse(prompt, systemContext);

                System.out.println(response);
            }
        }

    }

    static class LibraryBlock {
        String groupId;
        String artifactId;
        String fromVersion;
        String toVersion;
        List<String> methodCalls = new ArrayList<>();
    }

    public static List<LibraryBlock> parseInputFile(Path filePath) throws IOException {
        List<LibraryBlock> blocks = new ArrayList<>();
        LibraryBlock currentBlock = null;

        for (String line : Files.readAllLines(filePath)) {
            line = line.trim();
            if (line.startsWith("// Library:")) {
                if (currentBlock != null) {
                    blocks.add(currentBlock);
                }
                currentBlock = new LibraryBlock();
                String[] coords = line.replace("// Library:", "").trim().split(":");
                if (coords.length == 2) {
                    currentBlock.groupId = coords[0];
                    currentBlock.artifactId = coords[1];
                } else {
                    throw new IllegalArgumentException("Invalid library coordinate: " + line);
                }
            } else if (line.startsWith("// From:")) {
                currentBlock.fromVersion = line.replace("// From:", "").replace(".jar", "").trim();
            } else if (line.startsWith("// To:")) {
                currentBlock.toVersion = line.replace("// To:", "").replace(".jar", "").trim();
            } else if (!line.isBlank() && currentBlock != null) {
                currentBlock.methodCalls.add(line);
            }
        }

        if (currentBlock != null) {
            blocks.add(currentBlock);
        }

        return blocks;
    }


    private static String extractMethodName(String codeLine) {
        int paren = codeLine.indexOf('(');
        if (paren == -1) return "unknownMethod";
        String before = codeLine.substring(0, paren);
        int lastDot = before.lastIndexOf('.');
        return lastDot != -1 ? before.substring(lastDot + 1) : before;
    }

    private static String extractClassName(String codeLine) {

        return codeLine.substring(0, codeLine.indexOf(' '));
    }

    private static String extractVersion(String jarName) {
        return jarName.substring(0, jarName.lastIndexOf('.'));
    }

    private static String getLibrarySimpleName(String full) {
        // com.google.code.gson → Gson
        String[] parts = full.split("\\.");
        return parts[parts.length - 1].substring(0, 1).toUpperCase() + parts[parts.length - 1].substring(1);
    }

    private static String findJarPath(String baseRepo, String library, String version) throws IOException {
        Path searchRoot = Path.of(baseRepo, library.replace('.', '/'));
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            Optional<Path> found = stream
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.endsWith(".jar") && fileName.contains(version);
                    })
                    .findFirst();

            if (found.isPresent()) {
                return found.get().toString();
            } else {
                throw new FileNotFoundException("Could not find jar containing version " + version + " under " + searchRoot);
            }
        }
    }


}
