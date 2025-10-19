package core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import provider.AIProvider;
import provider.ChatGPTProvider;
import provider.ClaudeProvider;
import provider.OllamaProvider;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static core.LogParser.parseLog;
import static core.Main.buildPrompt;

public class BumpRunner {

    private static boolean useContainerExtraction = true;
    private static boolean usePromptCaching = false;

    public static void main(String[] args) {
        String targetDirectory = "testFiles/downloaded";
        File outputDir = new File(targetDirectory);

        String targetDirectoryClasses = "testFiles/brokenClasses";
        String targetDirectoryFixedClasses = "testFiles/correctedClasses";
        String targetDirectoryFixedLogs = "testFiles/correctedLogs";
        String targetDirectoryPrompts = "testFiles/prompts";
        String targetDirectoryLLMResponses = "testFiles/LLMResponses";
        File outputDirClasses = new File(targetDirectoryClasses);

        AIProvider chatgptProvider = new ChatGPTProvider();
        AIProvider claudeProvider = new ClaudeProvider();
        AIProvider codeLama7bProvider = new OllamaProvider("codellama:7b");
        AIProvider codeLama13bProvider = new OllamaProvider("codellama:13b");
        AIProvider codeGemma7bProvider = new OllamaProvider("codegemma:7b");
        AIProvider deepseekCoder6b7Provider = new OllamaProvider("deepseek-coder:6.7b");
        AIProvider starCoder2_7bProvider = new OllamaProvider("starcoder2:7b");
        AIProvider deepSeekR1b5 = new OllamaProvider("deepseek-r1:1.5b");
        AIProvider qwen3_8b = new OllamaProvider("qwen3:8b");
        AIProvider starCoder2_15bProvider = new OllamaProvider("starcoder2:15b");
        AIProvider nomicEmbedTextProvider = new OllamaProvider("nomic-embed-text");
        AIProvider cogito8bProvider = new OllamaProvider("cogito:8b");
        AIProvider deepseekR1_7b = new OllamaProvider("deepseek-r1:7b");
        AIProvider gptOss20b = new OllamaProvider("gpt-oss:20b");
        AIProvider gptOss120bCloud = new OllamaProvider("gpt-oss:120b-cloud");

        AIProvider activeProvider = gptOss120bCloud;

        List<AIProvider> providers = new ArrayList<>();

        //providers.add(chatgptProvider);
        //providers.add(claudeProvider);
        //providers.add(codeLama7bProvider);
        //providers.add(codeLama13bProvider);
        //providers.add(codeGemma7bProvider);   //Unpromising
        //providers.add(deepseekCoder6b7Provider);    //Unpromising
        //providers.add(starCoder2_7bProvider);       //Unpromising
        //providers.add(deepSeekR1b5);                //Unpromising
        // providers.add(qwen3_8b);
        //providers.add(gptOss20b);

        // Edit docker desktop to expose this port
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(1000)
                .connectionTimeout(Duration.ofSeconds(3000000))
                .responseTimeout(Duration.ofSeconds(4500000))
                .build();

        DockerClient dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);

        File bumpFolder = new File("testFiles/BUMPSubset");
        ObjectMapper objectMapper = new ObjectMapper();

        List<String> validEntryNames = new ArrayList<>();
        AtomicInteger satisfiedConflictPairs = new AtomicInteger();
        AtomicInteger totalPairs = new AtomicInteger();
        List<Thread> activeThreads = new ArrayList<>();
        AtomicInteger activeThreadCount = new AtomicInteger();
        AtomicInteger failedFixes = new AtomicInteger();
        AtomicInteger successfulFixes = new AtomicInteger();

        int limit = 4;
        for (File file : bumpFolder.listFiles()) {
            while (activeThreadCount.getAndUpdate(operand -> {
                if (operand >= limit) {
                    return operand;
                }
                return operand + 1;
            }) >= limit) {
                try {
                    //System.out.println(activeThreadCount.get());
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Thread virtualThread = Thread.ofVirtual().start(() -> {
                try {
                    JsonNode jsonNode = objectMapper.readTree(file);
                    JsonNode updatedDependency = jsonNode.get("updatedDependency");

                    String mavenSourceLinkPre = cleanString(updatedDependency.get("mavenSourceLinkPre").toString());
                    String mavenSourceLinkBreaking = cleanString(updatedDependency.get("mavenSourceLinkBreaking").toString());

                    if (mavenSourceLinkPre != null && mavenSourceLinkPre.endsWith("-sources.jar")) {
                        mavenSourceLinkPre = mavenSourceLinkPre.replace("-sources.jar", ".jar");
                    }

                    if (mavenSourceLinkBreaking != null && mavenSourceLinkBreaking.endsWith("-sources.jar")) {
                        mavenSourceLinkBreaking = mavenSourceLinkBreaking.replace("-sources.jar", ".jar");
                    }

                    String project = cleanString(jsonNode.get("project").toString());

                    String previousVersion = cleanString(updatedDependency.get("previousVersion").toString());
                    String newVersion = cleanString(updatedDependency.get("newVersion").toString());
                    String dependencyGroupID = cleanString(updatedDependency.get("dependencyGroupID").toString());
                    String dependencyArtifactID = cleanString(updatedDependency.get("dependencyArtifactID").toString());
                    String preCommitReproductionCommand = cleanString(jsonNode.get("preCommitReproductionCommand").toString());
                    String breakingUpdateReproductionCommand = cleanString(jsonNode.get("breakingUpdateReproductionCommand").toString());
                    String updatedFileType = cleanString(updatedDependency.get("updatedFileType").toString());
                    if (!updatedFileType.equals("JAR")) {
                        activeThreadCount.decrementAndGet();
                        return;
                    }

                   /* if(totalPairs.get() > 3){
                        return;
                    }*/

                    // cd5bb39f43e4570b875027073da3d4e43349ead1.json requires plexus-xml in new version => pom edit needed

                    /*if (!file.getName().equals("d9d866185ffa05b8d42b00801f17e4db92ae78bd.json")) {
                        activeThreadCount.decrementAndGet();
                        return;
                    }*/


                    String brokenUpdateImage = breakingUpdateReproductionCommand.substring(breakingUpdateReproductionCommand.lastIndexOf(" ")).trim();
                    String oldUpdateImage = preCommitReproductionCommand.substring(preCommitReproductionCommand.lastIndexOf(" ")).trim();

                    String combinedArtifactNameNew = dependencyArtifactID + "-" + newVersion + ".jar";
                    String combinedArtifactNameOld = dependencyArtifactID + "-" + previousVersion + ".jar";

                    System.out.println(file.getName());
                    Path targetPathOld = downloadLibrary(mavenSourceLinkPre, outputDir, dockerClient, oldUpdateImage, combinedArtifactNameOld);
                    Path targetPathNew = downloadLibrary(mavenSourceLinkBreaking, outputDir, dockerClient, brokenUpdateImage, combinedArtifactNameNew);

                    totalPairs.getAndIncrement();
                    if (Files.exists(targetPathOld) && Files.exists(targetPathNew)) {
                        satisfiedConflictPairs.getAndIncrement();
                    } else {
                        activeThreadCount.decrementAndGet();
                        return;
                    }

                    String strippedFileName = file.getName().substring(0, file.getName().lastIndexOf("."));

                    if (!Files.exists(Path.of("testFiles/brokenLogs/" + strippedFileName + "_" + project))) {
                        getBrokenLogFromContainer(dockerClient, brokenUpdateImage, project, strippedFileName);
                    }

                    //HashMap<String, int[]> failedClasses = readLogs(project, "testFiles/brokenLogs", strippedFileName);
                    //directory + "/" + fileName + "_" + projectName
                    List<Object> errors = parseLog(Path.of("testFiles/brokenLogs" + "/" + strippedFileName + "_" + project));

                    for (int i = errors.size() - 1; i >= 0; i--) {
                        LogParser.CompileError compileError = null;
                        if (errors.get(i) instanceof LogParser.CompileError) {
                            compileError = (LogParser.CompileError) errors.get(i);
                        }

                        for (int j = i - 1; j >= 0; j--) {
                            if (errors.get(j) instanceof LogParser.CompileError && compileError != null) {
                                LogParser.CompileError innerCompileError = (LogParser.CompileError) errors.get(j);
                                if (innerCompileError.line == compileError.line) {
                                    // Keep details
                                    ((LogParser.CompileError)errors.get(i)).details.putAll(((LogParser.CompileError)errors.get(j)).details);
                                    errors.remove(j);
                                    i--;
                                    break;
                                }
                            }
                        }
                    }

                    List<ProposedChange> proposedChanges = new ArrayList<>();
                    HashMap<String, ProposedChange> errorSet = new HashMap<>();
                    System.out.println(project + " contains " + errors.size() + " errors");
                    errorloop:
                    for (Object error : errors) {
                        if (!(error instanceof LogParser.CompileError)) {
                            continue;
                        }
                        LogParser.CompileError compileError = (LogParser.CompileError) error;

                        System.out.println(compileError.file + " " + compileError.line + " " + compileError.column);
                        String targetClass = "";
                        String targetMethod = "";
                        String[] targetMethodParameterClassNames = new String[0];
                        int errorIndex = -1;
                        if (compileError.message.equals("cannot find symbol")) {
                            if (compileError.details.containsKey("symbol")) {
                                String sym = compileError.details.get("symbol");
                                if(sym.startsWith("class")){
                                    targetClass = sym.substring(sym.indexOf("class") + "class".length() + 1);
                                }else{
                                    errorIndex = compileError.column;

                                }
                                //targetClass = compileError.details.get("location");
                                //targetClass = targetClass.substring(targetClass.lastIndexOf(".") + 1);

                            } else {
                                errorIndex = compileError.column;
                            }
                        }

                        if (compileError.details.containsKey("symbol")) {
                            targetMethod = compileError.details.get("symbol");
                            if (targetMethod.startsWith("method")) {
                                targetMethod = targetMethod.substring(targetMethod.indexOf(" ") + 1);
                                if (targetMethod.indexOf("(") != targetMethod.indexOf(")") - 1) {
                                    String parameterString = targetMethod.substring(targetMethod.indexOf("(") + 1, targetMethod.indexOf(")"));
                                    String[] parameters = parameterString.split(",");
                                    targetMethodParameterClassNames = new String[parameters.length];
                                    for (int i = 0; i < parameters.length; i++) {
                                        switch (parameters[i]) {
                                            case "boolean":
                                                targetMethodParameterClassNames[i] = Boolean.class.getName();
                                                break;
                                            case "int":
                                                targetMethodParameterClassNames[i] = Integer.class.getName();
                                                break;
                                            case "double":
                                                targetMethodParameterClassNames[i] = Double.class.getName();
                                                break;
                                            case "float":
                                                targetMethodParameterClassNames[i] = Float.class.getName();
                                                break;
                                            case "byte":
                                                targetMethodParameterClassNames[i] = Byte.class.getName();
                                                break;
                                            case "short":
                                                targetMethodParameterClassNames[i] = Short.class.getName();
                                                break;
                                            case "Long":
                                                targetMethodParameterClassNames[i] = Long.class.getName();
                                                break;
                                            case "char":
                                                targetMethodParameterClassNames[i] = Character.class.getName();
                                                break;
                                            default:
                                                targetMethodParameterClassNames[i] = parameters[i];
                                        }
                                    }
                                }

                                targetMethod = targetMethod.substring(0, targetMethod.indexOf("("));
                            }
                        }

                        String strippedClassName = compileError.file.substring(compileError.file.lastIndexOf("/") + 1);
                        if (!Files.exists(Path.of("testFiles/brokenClasses/" + strippedFileName + "_" + strippedClassName))) {
                            extractClassFromContainer(outputDirClasses, dockerClient, brokenUpdateImage, compileError.file, strippedFileName);
                        } else {
                            System.out.println("Class already exists at " + Path.of("testFiles/brokenClasses/" + strippedFileName + "_" + strippedClassName));
                        }


                        BrokenCode brokenCode = readBrokenLine(strippedClassName, targetDirectoryClasses, strippedFileName, new int[]{compileError.line, compileError.column});
                        //String targetClass = targetDependencyClass.substring(0, strippedClassName.lastIndexOf("."));
                        //TODO detect super call here then use the parent class
                        if (brokenCode.code().startsWith("import")) {
                            targetClass = brokenCode.code().substring(brokenCode.code().indexOf(" "), brokenCode.code().lastIndexOf(";")).trim();
                            if (targetClass.contains(".")) {
                                targetClass = targetClass.substring(targetClass.lastIndexOf(".") + 1);
                            }
                        } else if (brokenCode.code().trim().startsWith("super")) {
                            String parent = readParent(strippedClassName, targetDirectoryClasses, strippedFileName);
                            System.out.println("super " + parent);
                            targetClass = parent;
                            targetMethod = parent;
                        } else if (errorIndex != -1) {
                            String brokenSymbol = "";

                            List<String> precedingMethodChain = new ArrayList<>();
                            String method = "";

                            int chainStart = errorIndex;

                            for (; chainStart >= 0; chainStart--) {
                                char c = brokenCode.code().charAt(chainStart);
                                if(c == ' ' || c == ',' || c == ';' || c == '(') {
                                    break;
                                }
                            }


                            for (int i = chainStart+1; i < errorIndex; i++) {
                                char c = brokenCode.code().charAt(i);
                                if (c == ' ') {
                                    continue;
                                }
                                if (c == '.' || c == '(') {
                                    precedingMethodChain.add(method);
                                    method = "";
                                }
                                method += c;
                            }

                            int symbolStart = errorIndex;

                            for (; symbolStart >= 0; symbolStart--) {
                                char c = brokenCode.code().charAt(symbolStart);
                                if(c == ' ' || c == ',' || c == ';' || c == '.') {
                                    break;
                                }
                            }

                            for (int i = symbolStart+1; i < brokenCode.code().length(); i++) {
                                char c = brokenCode.code().charAt(i);
                                if (c == '.' || c == '(' || c == ' ' ) {
                                    break;
                                }
                                brokenSymbol += c;
                            }

                            if(precedingMethodChain.size() > 0) {
                                String classNameOfVariable = getClassNameOfVariable(precedingMethodChain.get(0), targetDirectoryClasses, strippedFileName, strippedClassName, brokenCode.start());

                                if (precedingMethodChain.size() == 1) {
                                    targetClass = classNameOfVariable;
                                    targetMethod = brokenSymbol;
                                } else {
                                    System.out.println("test");
                                    //TODO: Recursive scan
                                }
                                System.out.println(brokenSymbol);
                            }else{
                                targetClass = brokenSymbol;
                            }
                        }
                        //else{
                        //    continue;
                        //}


                        /*else{
                            System.out.println("Skipped non import related error");
                            //TODO Fetch appropriate broken code and method name so the prompt can be built better
                            activeThreadCount.decrementAndGet();
                            return;
                        }*/


                        if (errorSet.containsKey(brokenCode.code().trim())) {
                            int offset = compileError.line - errorSet.get(brokenCode.code().trim()).start();
                            proposedChanges.add(new ProposedChange(strippedClassName, errorSet.get(brokenCode.code().trim()).code(), compileError.file, offset + errorSet.get(brokenCode.code().trim()).start(), offset + errorSet.get(brokenCode.code().trim()).end()));
                            continue;
                        }

                        System.out.println("Target class: " + targetClass);

                        ConflictResolutionResult result;

                        String llmResponseFileName = targetDirectoryLLMResponses + "/" + strippedFileName + "_" + compileError.line + "_" + targetClass + "_" + activeProvider + ".txt";

                        String erroneousClass = readBrokenClass(strippedClassName, targetDirectoryClasses, strippedFileName);

                        if (!usePromptCaching || !Files.exists(Path.of(llmResponseFileName))) {
                            //TODO maybe also include the error message in the prompt?
                            String prompt = buildPrompt(dependencyArtifactID, previousVersion, newVersion, targetPathOld.toString(), targetPathNew.toString(),
                                    targetClass, targetMethod, brokenCode.code(), targetMethodParameterClassNames, "line: " + compileError.line + ", column: " + compileError.column + System.lineSeparator() + compileError.message, erroneousClass);

                            Files.write(Path.of(targetDirectoryPrompts + "/" + strippedFileName + "_" + compileError.line + "_" + targetClass + ".txt"), prompt.getBytes());

                            System.out.println(prompt);
                            result = Main.sendAndPrintCode(activeProvider, prompt);

                            FileOutputStream fileOutputStream
                                    = new FileOutputStream(llmResponseFileName);
                            ObjectOutputStream objectOutputStream
                                    = new ObjectOutputStream(fileOutputStream);
                            objectOutputStream.writeObject(result);
                            objectOutputStream.flush();
                            objectOutputStream.close();
                            //Files.write(Path.of(targetDirectoryLLMResponses + "/" + strippedFileName + "_" + compileError.line + "_" + targetClass + ".txt"), result.toString().getBytes());
                        } else {
                            System.out.println("Loading LLM response from stored responses");
                            FileInputStream fileInputStream = new FileInputStream(llmResponseFileName);
                            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                            result = (ConflictResolutionResult) objectInputStream.readObject();
                            objectInputStream.close();
                        }

                        System.out.println(result);

                        ProposedChange proposedChange = new ProposedChange(strippedClassName, result.code(), compileError.file, brokenCode.start(), brokenCode.end());
                        proposedChanges.add(proposedChange);
                        errorSet.put(brokenCode.code().trim(), proposedChange);
                    }

                    HashMap<String, List<ProposedChange>> groupedChangesByClassName = new HashMap<>();

                    for (ProposedChange proposedChange : proposedChanges) {
                        if (!groupedChangesByClassName.containsKey(proposedChange.className())) {
                            groupedChangesByClassName.put(proposedChange.className(), new ArrayList<>());
                        }
                        groupedChangesByClassName.get(proposedChange.className()).add(proposedChange);
                    }

                    CreateContainerResponse container = pullImageAndCreateContainer(dockerClient, brokenUpdateImage);

                    for (String className : groupedChangesByClassName.keySet()) {

                        replaceBrokenCodeInClass(className, targetDirectoryClasses, targetDirectoryFixedClasses, strippedFileName, groupedChangesByClassName.get(className));

                        replaceFileInContainer(dockerClient, container, Paths.get(targetDirectoryFixedClasses + "/" + strippedFileName + "_" + className), groupedChangesByClassName.get(className).get(0).file());
                    }

                    if (getCorrectedLogFromContainer(dockerClient, container, strippedFileName, project)) {
                        failedFixes.incrementAndGet();
                    } else {
                        successfulFixes.incrementAndGet();
                    }


                    //String prompt = buildPrompt(dependencyArtifactID, previousVersion, newVersion, targetPathOld.toString(), targetPathNew.toString(),
                    //        "", "", "", new String[]{});

                    //System.out.println(prompt);
                    //break;
                    activeThreadCount.decrementAndGet();

                } catch (IOException e) {
                    System.err.println(e);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

            });
            activeThreads.add(virtualThread);
        }

        for (Thread activeThread : activeThreads) {
            try {
                activeThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


        System.out.println(satisfiedConflictPairs.get() + " out of " + totalPairs.get() + " project pairs have accessible dependencies");
        System.out.println("Fixed " + successfulFixes.get() + " out of " + satisfiedConflictPairs.get() + " projects (" + failedFixes.get() + " were not fixed)");
        try {
            objectMapper.writeValue(new File("testFiles/downloaded/validEntries.json"), validEntryNames);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String cleanString(String str) {
        if (str.equals("null")) {
            return null;
        }
        return str.substring(1, str.length() - 1);
    }

    public static void extractEntry(TarArchiveInputStream tais, TarArchiveEntry entry, File outputDir, String outputNameModifier) throws IOException {
        File outputFile = new File(outputDir, outputNameModifier + entry.getName().substring(entry.getName().lastIndexOf('/') + 1));

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            int len;
            long remaining = entry.getSize();
            while (remaining > 0 && (len = tais.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                fos.write(buffer, 0, len);
                remaining -= len;
            }
        }
    }

    public static void extractLibraryFromContainer(File targetDirectory, DockerClient dockerClient, String imagePath, String artifactNameWithVersion) {
        System.out.println("Fetching library from container (this takes some time)");
        CreateContainerResponse container = pullImageAndCreateContainer(dockerClient, imagePath);
        getFileFromContainer(dockerClient, container, artifactNameWithVersion, targetDirectory, "");
        dockerClient.removeContainerCmd(container.getId()).exec();
    }

    public static void extractClassFromContainer(File targetDirectory, DockerClient dockerClient, String imagePath, String className, String fileName) {
        System.out.println("Fetching class from container (this takes some time)");
        CreateContainerResponse container = pullImageAndCreateContainer(dockerClient, imagePath);
        getFileFromContainer(dockerClient, container, className, targetDirectory, fileName + "_");
        dockerClient.removeContainerCmd(container.getId()).exec();
    }

    public static void getBrokenLogFromContainer(DockerClient dockerClient, String imagePath, String projectName, String fileName) {
        CreateContainerResponse container = pullImageAndCreateContainer(dockerClient, imagePath);

        dockerClient.startContainerCmd(container.getId()).exec();
        dockerClient.waitContainerCmd(container.getId()).start().awaitStatusCode();
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("testFiles/brokenLogs/" + fileName + "_" + projectName));

            dockerClient.logContainerCmd(container.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String s = new String(frame.getPayload());

                            try {
                                bw.write(s);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }).awaitCompletion();
            bw.flush();
            dockerClient.removeContainerCmd(container.getId()).exec();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean getCorrectedLogFromContainer(DockerClient dockerClient, CreateContainerResponse container, String fileName, String projectName) {
        dockerClient.startContainerCmd(container.getId()).exec();
        dockerClient.waitContainerCmd(container.getId()).start().awaitStatusCode();
        final boolean[] containsError = {false};
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("testFiles/correctedLogs/" + fileName + "_" + projectName));

            dockerClient.logContainerCmd(container.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String s = new String(frame.getPayload());
                            if (s.contains("[ERROR]")) {
                                containsError[0] = true;
                            }
                            try {
                                bw.write(s);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }).awaitCompletion();
            bw.flush();
            dockerClient.removeContainerCmd(container.getId()).exec();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        return containsError[0];
    }

    public static HashMap<String, int[]> readLogs(String projectName, String directory, String fileName) {
        String regex = "\\[[0-9]*,[0-9]*\\]";
        Pattern pattern = Pattern.compile(regex);
        HashMap<String, int[]> classLookup = new HashMap<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(directory + "/" + fileName + "_" + projectName));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("[ERROR]")) {
                    line = line.substring(7);
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String[] splitRange = line.substring(matcher.start() + 1, matcher.end() - 1).split(",");
                        line = line.substring(0, matcher.start() - 1).trim();

                        classLookup.put(line, new int[]{Integer.parseInt(splitRange[0]), Integer.parseInt(splitRange[1])});
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return classLookup;
    }

    public static BrokenCode readBrokenLine(String className, String directory, String fileName, int[] indices) {
        try {
            List<String> allLines = Files.readAllLines(Paths.get(directory + "/" + fileName + "_" + className));
            //BufferedReader br = new BufferedReader(new FileReader(directory + "/" + fileName + "_" + className));
            String brokenCode = null;
            int start = 0, end = allLines.size();
            for (int i = 0; i < allLines.size(); i++) {
                if (i + 1 == indices[0]) {
                    start = i + 1;
                    brokenCode = allLines.get(i);
                    if (!(brokenCode.endsWith(";") || brokenCode.endsWith("{") || brokenCode.endsWith("}"))) {
                        for (int j = i + 1; j < allLines.size(); j++) {
                            brokenCode = brokenCode + allLines.get(j);
                            if (allLines.get(j).endsWith(";")) {
                                end = j + 1;
                                break;
                            }
                        }
                    } else {
                        end = start;
                    }
                    return new BrokenCode(brokenCode, start, end);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static String getClassNameOfVariable(String variableName, String directory, String fileName, String className, int lineNumber) {
        try {
            List<String> allLines = Files.readAllLines(Paths.get(directory + "/" + fileName + "_" + className));


            for (int i = lineNumber; i >= 0; i--) {
                String line = allLines.get(i);
                int variableIndex = line.indexOf(variableName);
                if (variableIndex > 0) {
                    String cutLine = line.substring(0, variableIndex);
                    if(allLines.get(i).charAt(variableIndex - 1) != ' ' || cutLine.isBlank()) {
                        continue;
                    }
                    int startIndex = Math.max(cutLine.lastIndexOf('('), cutLine.lastIndexOf(','));

                    return cutLine.substring(startIndex + 1).trim();
                }

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static String readBrokenClass(String className, String directory, String fileName) {
        try {
            return Files.readAllLines(Path.of(directory + "/" + fileName + "_" + className)).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static String readParent(String className, String directory, String fileName) {
        String regex = ".*class .* extends .*";
        try {
            BufferedReader br = new BufferedReader(new FileReader(directory + "/" + fileName + "_" + className));
            String line = null;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                if (line.matches(regex)) {
                    return line.substring(line.indexOf("extends ") + "extends ".length(), line.indexOf("{")).trim();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void replaceBrokenCodeInClass(String className, String directory, String outDirectory, String fileName, List<ProposedChange> changes) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(directory + "/" + fileName + "_" + className));

            for (ProposedChange change : changes) {
                lines.set(change.start() - 1, change.code());
                for (int i = change.start(); i < change.end(); i++) {
                    lines.set(i, "");
                }
                /*String[] changeLines = change.code().split(System.lineSeparator());
                int offset = 0;
                for(String changeLine : changeLines) {
                    if(changeLine.startsWith("/")){
                        lines.set(change.line()+offset-1, changeLine);
                    }else {
                        if(changeLine.matches("[0-9].*")) {
                            int lineNumber = Integer.parseInt(changeLine.substring(0, changeLine.indexOf(":")));
                            String trimmedChange = changeLine.substring(changeLine.indexOf(":") + 1).trim();
                            lines.set(lineNumber - 1, trimmedChange);
                        }else{
                            lines.set(change.line()+offset-1, changeLine);
                        }
                    }
                    offset++;
                }*/

            }

            // Use \n so the docker containers (linux) don't complain
            String content = String.join("\n", lines) + "\n";

            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outDirectory + "/" + fileName + "_" + className))) {
                writer.write(content);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path downloadLibrary(String libraryUrl, File targetDirectory, DockerClient dockerClient, String imagePath, String artifactNameWithVersion) {
        Path targetPath = Path.of(targetDirectory.getPath()).resolve(artifactNameWithVersion);
        if (!Files.exists(targetPath)) {
            if (libraryUrl == null) {
                if (useContainerExtraction) {
                    extractLibraryFromContainer(targetDirectory, dockerClient, imagePath, artifactNameWithVersion);
                }
            } else {
                try {
                    URL url = new URI(libraryUrl).toURL();
                    InputStream inPrev = url.openStream();
                    Files.copy(inPrev, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException | URISyntaxException e) {
                    System.err.println("Error downloading " + artifactNameWithVersion + " from " + libraryUrl + ". Resorting to container extraction: " + useContainerExtraction);
                    if (useContainerExtraction) {
                        extractLibraryFromContainer(targetDirectory, dockerClient, imagePath, artifactNameWithVersion);
                    }
                }
            }
        } else {
            System.out.println("Library already exists locally at " + targetPath);
        }
        return targetPath;
    }

    public static CreateContainerResponse pullImageAndCreateContainer(DockerClient dockerClient, String imagePath) {
        try {
            dockerClient.pullImageCmd(imagePath)
                    .exec(new PullImageResultCallback() {
                        @Override
                        public void onNext(PullResponseItem item) {
                            String status = item.getStatus();
                            String progress = item.getProgress();
                            String id = item.getId();

                            if (status != null) {
                                if (progress != null && !progress.isEmpty()) {
                                    System.out.printf("%s: %s %s%n", id != null ? id : "", status, progress);
                                } else {
                                    System.out.printf("%s: %s%n", id != null ? id : "", status);
                                }
                            }

                            super.onNext(item);
                        }
                    }).awaitCompletion();
            return dockerClient.createContainerCmd(imagePath).exec();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void getFileFromContainer(DockerClient dockerClient, CreateContainerResponse container, String fileName, File outputDir, String outputNameModifier) {
        try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(container.getId(), "/").exec();
             TarArchiveInputStream tarInput = new TarArchiveInputStream(tarStream)) {

            TarArchiveEntry entry;
            boolean found = false;
            while ((entry = tarInput.getNextEntry()) != null) {
                String path = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                //System.out.println(path);
                if (!path.endsWith(fileName)) {
                    continue;
                }

                System.out.println("Found " + fileName + " in container, proceeding to download it into " + outputDir.getAbsolutePath());
                extractEntry(tarInput, entry, outputDir, outputNameModifier);
                found = true;
                break;
            }
            if (!found) {
                System.err.println("No file with name " + fileName + " found in container");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFilePathFromContainer(DockerClient dockerClient, CreateContainerResponse container, String fileName) {
        try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(container.getId(), "/").exec();
             TarArchiveInputStream tarInput = new TarArchiveInputStream(tarStream)) {

            TarArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                String path = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                System.out.println(path);
                if (!path.endsWith(fileName)) {
                    continue;
                }

                return path;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void replaceFileInContainer(DockerClient dockerClient, CreateContainerResponse container, Path filePath, String fileNameInContainer) {
        String fileName = filePath.getFileName().toString();

        ByteArrayOutputStream tarOut = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(tarOut);

        try {
            byte[] fileContent = Files.readAllBytes(filePath);
            TarArchiveEntry entry = new TarArchiveEntry(fileNameInContainer.substring(fileNameInContainer.lastIndexOf("/") + 1));
            entry.setSize(fileContent.length);
            tos.putArchiveEntry(entry);
            tos.write(fileContent);
            tos.closeArchiveEntry();
            tos.close();

            ByteArrayInputStream tarStream = new ByteArrayInputStream(tarOut.toByteArray());

            String containerId = container.getId();
            String destPath = fileNameInContainer;
            //getFilePathFromContainer(dockerClient, container, fileNameInContainer);
            destPath = destPath.substring(0, destPath.lastIndexOf("/"));


            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(tarStream)
                    .withRemotePath(destPath)
                    .exec();

            System.out.println("File replaced successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
