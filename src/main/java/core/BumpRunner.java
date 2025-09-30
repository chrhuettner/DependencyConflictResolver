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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static core.Main.buildPrompt;

public class BumpRunner {

    private static boolean useContainerExtraction = true;

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

        List<AIProvider> providers = new ArrayList<>();

        //providers.add(chatgptProvider);
        //providers.add(claudeProvider);
        //providers.add(codeLama7bProvider);
        //providers.add(codeLama13bProvider);
        //providers.add(codeGemma7bProvider);   //Unpromising
        //providers.add(deepseekCoder6b7Provider);    //Unpromising
        //providers.add(starCoder2_7bProvider);       //Unpromising
        //providers.add(deepSeekR1b5);                //Unpromising
        providers.add(qwen3_8b);

        // Edit docker desktop to expose this port
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(1000)
                .connectionTimeout(Duration.ofSeconds(30000))
                .responseTimeout(Duration.ofSeconds(45000))
                .build();

        DockerClient dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);

        File bumpFolder = new File("testFiles/BUMP");
        ObjectMapper objectMapper = new ObjectMapper();

        List<String> validEntryNames = new ArrayList<>();
        AtomicInteger satisfiedConflictPairs = new AtomicInteger();
        AtomicInteger totalPairs = new AtomicInteger();
        List<Thread> activeThreads = new ArrayList<>();
        AtomicInteger activeThreadCount = new AtomicInteger();
        AtomicInteger failedFixes = new AtomicInteger();
        AtomicInteger successfulFixes = new AtomicInteger();
        int limit = 8;
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

                    /*if (!project.startsWith("allure-maven") || !file.getName().equals("16ae40b1e17e14ee3ae20ac211647e47399a01a9.json")) {
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

                    HashMap<String, int[]> failedClasses = readLogs(project, "testFiles/brokenLogs", strippedFileName);

                    for (String className : failedClasses.keySet()) {
                        System.out.println(className + " " + failedClasses.get(className)[0] + " " + failedClasses.get(className)[1]);
                        String strippedClassName = className.substring(className.lastIndexOf("/") + 1);
                        if (!Files.exists(Path.of("testFiles/brokenClasses/" + strippedFileName + "_" + strippedClassName))) {
                            extractClassFromContainer(outputDirClasses, dockerClient, brokenUpdateImage, className, strippedFileName);
                        } else {
                            System.out.println("Class already exists at " + Path.of("testFiles/brokenClasses/" + strippedFileName + "_" + strippedClassName));
                        }


                        /*String brokenCode = readBrokenClass(strippedClassName, targetDirectoryClasses, strippedFileName, failedClasses.get(className));
                        String targetClass = strippedClassName.substring(0, strippedClassName.lastIndexOf("."));
                        if (brokenCode.startsWith("import")) {
                            targetClass = brokenCode.substring(brokenCode.indexOf(" "), brokenCode.lastIndexOf(";")).trim();
                            if (targetClass.contains(".")) {
                                targetClass = targetClass.substring(targetClass.lastIndexOf(".") + 1);
                            }
                        }

                        System.out.println("Target class: " + targetClass);
                        String prompt = buildPrompt(dependencyArtifactID, previousVersion, newVersion, targetPathOld.toString(), targetPathNew.toString(),
                                targetClass, "", brokenCode, new String[]{});

                        Files.write(Path.of(targetDirectoryPrompts+"/" + strippedFileName + "_"+failedClasses.get(className)[0]+"_" + targetClass+".txt"), prompt.getBytes());

                        System.out.println(prompt);
                        ConflictResolutionResult result = Main.sendAndPrintCode(qwen3_8b, prompt);
                        Files.write(Path.of(targetDirectoryLLMResponses+"/" + strippedFileName + "_"+failedClasses.get(className)[0]+"_" + targetClass+".txt"), result.toString().getBytes());

                        replaceBrokenCodeInClass(strippedClassName, targetDirectoryClasses, targetDirectoryFixedClasses, strippedFileName, failedClasses.get(className)[0], result.code());

                        CreateContainerResponse container = pullImageAndCreateContainer(dockerClient, brokenUpdateImage);
                        replaceFileInContainer(dockerClient, container, Paths.get(targetDirectoryFixedClasses + "/" + strippedFileName + "_"+failedClasses.get(className)[0]+"_" + strippedClassName), className);

                        if(getCorrectedLogFromContainer(dockerClient, container, strippedFileName, project)){
                            failedFixes.incrementAndGet();
                        }else{
                            successfulFixes.incrementAndGet();
                        }*/
                    }

                    //String prompt = buildPrompt(dependencyArtifactID, previousVersion, newVersion, targetPathOld.toString(), targetPathNew.toString(),
                    //        "", "", "", new String[]{});

                    //System.out.println(prompt);
                    //break;
                    activeThreadCount.decrementAndGet();

                } catch (IOException e) {
                    System.err.println(e);
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
        System.out.println("Fixed "+successfulFixes.get() + " out of " + satisfiedConflictPairs.get() + " projects ("+failedFixes.get()+" were not fixed)");
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
                            if(s.contains("[ERROR]")){
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

    public static String readBrokenClass(String className, String directory, String fileName, int[] indices) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(directory + "/" + fileName + "_" + className));
            String line = null;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                if (lineNumber == indices[0]) {
                    return line;
                }
                lineNumber++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void replaceBrokenCodeInClass(String className, String directory, String outDirectory, String fileName, int lineNumber, String newCode) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(directory + "/" + fileName + "_" + className));

            lines.set(lineNumber - 1, newCode);

            // Use \n so the docker containers (linux) don't complain
            String content = String.join("\n", lines) + "\n";

            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outDirectory + "/" + fileName + "_" + lineNumber + "_" + className))) {
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
