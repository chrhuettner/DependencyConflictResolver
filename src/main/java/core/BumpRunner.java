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
import core.context.CannotFindSymbolProvider;
import core.context.ContextProvider;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import provider.AIProvider;
import provider.ChatGPTProvider;
import provider.ClaudeProvider;
import provider.OllamaProvider;
import solver.CodeConflictSolver;

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
import static core.LogParser.projectIsFixableThroughCodeModification;

public class BumpRunner {

    public static boolean useContainerExtraction = true;
    public static boolean usePromptCaching = false;

    // for example: GitHub.connect().getRepository(ghc.owner + '/' + ghc.repo).getCompare(branch, ghc.hash).status;
   // public static final Pattern METHOD_CHAIN_PATTERN = Pattern.compile(
   //         "\\.?([A-Za-z_]\\w*)\\s*(?:\\([^()]*\\))?");

   // private static final Pattern METHOD_CHAIN_DETECTION_PATTERN = Pattern.compile(
   //         "((new|=|\\.)\\s*\\w+)\\s*(?=\\(.*\\))");

    // For example: [ERROR] /lithium/src/main/java/com/wire/lithium/Server.java:[160,16] cannot access io.dropwizard.core.setup.Environment
    //private static final Pattern CLASS_FILE_NOT_FOUND_PATTERN = Pattern.compile(
    //        "cannot access (\\S*)");

    // For example:   class file for io.dropwizard.core.setup.Environment not found
   // private static final Pattern CLASS_FILE_NOT_FOUND_DETAIL_PATTERN = Pattern.compile(
   //         "class file for (\\S*) not found");

    public static final int REFINEMENT_LIMIT = 3;

    // public static List<ContextProvider> contextProviders = new ArrayList<>();
    // public static List<CodeConflictSolver> codeConflictSolvers = new ArrayList<>();


    public static void main(String[] args) {
        String targetDirectory = "testFiles/downloaded";
        File outputDir = new File(targetDirectory);
        File outputDirSrcFiles = new File("testFiles/projectSources");

        String targetDirectoryClasses = "testFiles/brokenClasses";
        String targetDirectoryFixedClasses = "testFiles/correctedClasses";
        String targetDirectoryFixedLogs = "testFiles/correctedLogs";
        String targetDirectoryPrompts = "testFiles/prompts";
        String targetDirectoryLLMResponses = "testFiles/LLMResponses";
        String directoryOldContainerLogs = "testFiles/oldContainerLogs";
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
        AIProvider qwen3_coder480b_cloud = new OllamaProvider("qwen3-coder:480b-cloud");

        AIProvider activeProvider = qwen3_coder480b_cloud;

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
        AtomicInteger fixableProjects = new AtomicInteger();
        AtomicInteger imposterProjects = new AtomicInteger();


        int limit = 1;
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
                    // cb541fd65c7b9bbc3424ea927f1dab223261d156.json has its upgraded dependency deprecated (no classes or code at all in the dependency, just a deprecation notice)
                    // 4a3efad6e00824e5814b9c8f571c9c98aad40281.json has deleted its enum (CertificationPermission) with nothing there to replace it
                    // ghcr.io/chains-project/breaking-updates:17f2bcaaba4805b218743f575919360c5aec5da4-pre straight up fails because it cannot find a dependency
                    // 10d7545c5771b03dd9f6122bd5973a759eb2cd03 cannot be fixed because the dropwizard-core library needs to be upgraded
                    //TODO: Filter BUMP projects so only fixable projects remain (the above two examples are considered not fixable)

                    //TODO: Check this json, class name extraction fails here ab85440ce7321d895c7a9621224ce8059162a26a
                    /*if (!file.getName().equals("10d7545c5771b03dd9f6122bd5973a759eb2cd03.json")) {
                        activeThreadCount.decrementAndGet();
                        return;
                    }*/

                    String strippedFileName = file.getName().substring(0, file.getName().lastIndexOf("."));


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

                    boolean projectIsFixableWithSourceCodeModification = projectIsFixableThroughCodeModification(Path.of("testFiles/brokenLogs" + "/" + strippedFileName + "_" + project));

                    if (!projectIsFixableWithSourceCodeModification) {
                        activeThreadCount.decrementAndGet();
                        return;
                    }
                    fixableProjects.incrementAndGet();

                    if (!Files.exists(Path.of(outputDirSrcFiles + "/" + dependencyArtifactID + "_" + strippedFileName))) {
                        CreateContainerResponse oldContainer = pullImageAndCreateContainer(dockerClient, oldUpdateImage);
                        if (logFromContainerContainsError(dockerClient, oldContainer, strippedFileName, project, directoryOldContainerLogs)) {
                            System.out.println(strippedFileName + "_" + project + " is not working despite being in the pre set!!!!");
                            imposterProjects.incrementAndGet();
                            activeThreadCount.decrementAndGet();
                            return;
                        }
                        extractCompiledCodeFromContainer(outputDirSrcFiles, dockerClient, oldUpdateImage, dependencyArtifactID + "_" + strippedFileName);


                    }

                    String oldName = targetPathOld.toUri().toString();
                    oldName = oldName.substring(oldName.lastIndexOf("/") + 1);

                    String newName = targetPathNew.toUri().toString();
                    newName = newName.substring(newName.lastIndexOf("/") + 1);

                    Path oldDependencyPath = Path.of(outputDirSrcFiles + "/" + dependencyArtifactID + "_" + strippedFileName + "/tmp/dependencies/" + oldName);

                    if (Files.exists(oldDependencyPath)) {
                        File updatedDependencyFile = new File(targetPathNew.toUri());
                        File oldDependencyFile = new File(oldDependencyPath.toUri());

                        Files.move(oldDependencyFile.toPath(), Path.of(outputDirSrcFiles + "/" + dependencyArtifactID + "_" + strippedFileName + "/tmp/" + oldName), StandardCopyOption.REPLACE_EXISTING);
                        Files.copy(updatedDependencyFile.toPath(), Path.of(outputDirSrcFiles + "/" + dependencyArtifactID + "_" + strippedFileName + "/tmp/dependencies/" + newName), StandardCopyOption.REPLACE_EXISTING);
                    }


                    if (!Files.exists(Path.of("testFiles/brokenLogs/" + strippedFileName + "_" + project))) {
                        getBrokenLogFromContainer(dockerClient, brokenUpdateImage, project, strippedFileName);
                    }


                    List<Object> errors = parseLog(Path.of("testFiles/brokenLogs" + "/" + strippedFileName + "_" + project));

                    removeDuplicatedErrors(errors);

                    List<ProposedChange> proposedChanges = new ArrayList<>();
                    HashMap<String, ProposedChange> errorSet = new HashMap<>();
                    System.out.println(project + " contains " + errors.size() + " errors");

                    Context context = new Context(project, previousVersion, newVersion, dependencyArtifactID, strippedFileName, outputDirClasses, brokenUpdateImage,
                            targetPathOld, targetPathNew, targetDirectoryClasses, outputDirSrcFiles, activeProvider, dockerClient, errorSet, proposedChanges, null,
                            targetDirectoryLLMResponses, targetDirectoryPrompts, targetDirectoryFixedClasses, targetDirectoryFixedLogs, null);

                    List<ContextProvider> contextProviders = ContextProvider.getContextProviders(context);
                    List<CodeConflictSolver> codeConflictSolvers = CodeConflictSolver.getCodeConflictSolvers(context);

                    boolean errorsWereFixed = false;
                    int amountOfTries = 0;
                    for (; amountOfTries < REFINEMENT_LIMIT; amountOfTries++) {
                        try {
                            for (Object error : errors) {
                                if (!(error instanceof LogParser.CompileError)) {
                                    continue;
                                }
                                context.setCompileError((LogParser.CompileError) error);
                                context.setStrippedClassName(extractClassIfNotCached(context));

                                fixError(context, contextProviders, codeConflictSolvers);
                            }

                            if (validateFix(context)) {
                                errorsWereFixed = true;
                                amountOfTries++;
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (errorsWereFixed) {
                        System.out.println("Took " + amountOfTries + " tries to fix " + strippedFileName);
                        successfulFixes.getAndIncrement();
                    } else {
                        System.out.println("Took " + amountOfTries + " tries, but could not fix " + strippedFileName);
                        failedFixes.getAndIncrement();
                    }

                    activeThreadCount.decrementAndGet();

                } catch (Exception e) {
                    activeThreadCount.decrementAndGet();
                    e.printStackTrace();
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

        System.out.println(imposterProjects.get() + " projects are not buildable despite being in the pre set!!!");
        System.out.println(satisfiedConflictPairs.get() + " out of " + totalPairs.get() + " project pairs have accessible dependencies");
        System.out.println(fixableProjects.get() + " projects are fixable");
        System.out.println("Fixed " + successfulFixes.get() + " out of " + satisfiedConflictPairs.get() + " projects (" + failedFixes.get() + " were not fixed)");
        try {
            objectMapper.writeValue(new File("testFiles/downloaded/validEntries.json"), validEntryNames);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean validateFix(Context context) {
        HashMap<String, List<ProposedChange>> groupedChangesByClassName = new HashMap<>();

        for (ProposedChange proposedChange : context.getProposedChanges()) {
            if (!groupedChangesByClassName.containsKey(proposedChange.className())) {
                groupedChangesByClassName.put(proposedChange.className(), new ArrayList<>());
            }
            groupedChangesByClassName.get(proposedChange.className()).add(proposedChange);
        }

        CreateContainerResponse container = pullImageAndCreateContainer(context.getDockerClient(), context.getBrokenUpdateImage());

        for (String className : groupedChangesByClassName.keySet()) {

            replaceBrokenCodeInClass(className, context.getTargetDirectoryClasses(), context.getTargetDirectoryFixedClasses(), context.getStrippedFileName(), groupedChangesByClassName.get(className));

            replaceFileInContainer(context.getDockerClient(), container, Paths.get(context.getTargetDirectoryFixedClasses() + "/" + context.getStrippedFileName() + "_" + className), groupedChangesByClassName.get(className).get(0).file());

            /*if (groupedChangesByClassName.get(className).get(0).file().equals("/docker-adapter/src/test/java/com/artipie/docker/http/LargeImageITCase.java")) {
                getFileFromContainer(context.getDockerClient(), container, groupedChangesByClassName.get(className).get(0).file(), new File("testFiles/TEST"), "");
            }*/
        }

        return !logFromContainerContainsError(context.getDockerClient(), container, context.getStrippedFileName(), context.getProject(), context.getTargetDirectoryFixedLogs());
    }

    public static void removeDuplicatedErrors(List<Object> errors) {
        for (int i = errors.size() - 1; i >= 0; i--) {
            LogParser.CompileError compileError = null;
            if (errors.get(i) instanceof LogParser.CompileError) {
                compileError = (LogParser.CompileError) errors.get(i);
            }

            for (int j = i - 1; j >= 0; j--) {
                if (errors.get(j) instanceof LogParser.CompileError && compileError != null) {
                    LogParser.CompileError innerCompileError = (LogParser.CompileError) errors.get(j);
                    if (innerCompileError.file.equals(compileError.file) && innerCompileError.line == compileError.line) {
                        // Keep details
                        ((LogParser.CompileError) errors.get(i)).details.putAll(((LogParser.CompileError) errors.get(j)).details);
                        errors.remove(j);
                        i--;
                        break;
                    }
                }
            }
        }
    }

    public static String extractClassIfNotCached(Context context) {
        String strippedClassName = context.getCompileError().file.substring(context.getCompileError().file.lastIndexOf("/") + 1);
        if (!Files.exists(Path.of("testFiles/brokenClasses/" + context.getStrippedFileName() + "_" + strippedClassName))) {
            extractClassFromContainer(context.getOutputDirClasses(), context.getDockerClient(), context.getBrokenUpdateImage(), context.getCompileError().file, context.getStrippedFileName());
        } else {
            System.out.println("Class already exists at " + Path.of("testFiles/brokenClasses/" + context.getStrippedFileName() + "_" + strippedClassName));
        }
        return strippedClassName;
    }

    public static void fixError(Context context, List<ContextProvider> contextProviders, List<CodeConflictSolver> codeConflictSolvers) throws IOException, ClassNotFoundException {
        BrokenCode brokenCode = readBrokenLine(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName(), new int[]{context.getCompileError().line, context.getCompileError().column});

        if (context.getErrorSet().containsKey(brokenCode.code().trim())) {
            int offset = context.getCompileError().line - context.getErrorSet().get(brokenCode.code().trim()).start();
            context.getProposedChanges().add(new ProposedChange(context.getStrippedClassName(), context.getErrorSet().get(brokenCode.code().trim()).code(), context.getCompileError().file,
                    offset + context.getErrorSet().get(brokenCode.code().trim()).start(), offset + context.getErrorSet().get(brokenCode.code().trim()).end()));
            return;
        }

        System.out.println(context.getCompileError().file + " " + context.getCompileError().line + " " + context.getCompileError().column);
        String targetClass = "";
        String targetMethod = "";
        String[] targetMethodParameterClassNames = new String[0];

        boolean errorGetsTargetByAtLeastOneProvider = false;
        for (ContextProvider contextProvider : contextProviders) {
            if (contextProvider.errorIsTargetedByProvider(context.getCompileError(), brokenCode)) {
                errorGetsTargetByAtLeastOneProvider = true;
                ErrorLocation errorLocation = contextProvider.getErrorLocation(context.getCompileError(), brokenCode);
                //if (errorLocation.className() != null) {
                targetClass = errorLocation.className();
                //}
                //if (errorLocation.methodName() != null) {
                targetMethod = errorLocation.methodName();
                //}
                //if (errorLocation.targetMethodParameterClassNames() != null) {
                targetMethodParameterClassNames = errorLocation.targetMethodParameterClassNames();
                //}
                break;
            }
        }

        if (!errorGetsTargetByAtLeastOneProvider) {
            System.out.println("UNCATEGORIZED " + brokenCode.code());
        }

        ErrorLocation errorLocation = new ErrorLocation(targetClass, targetMethod, targetMethodParameterClassNames);
        for (CodeConflictSolver codeConflictSolver : codeConflictSolvers) {
            if (codeConflictSolver.errorIsTargetedBySolver(context.getCompileError(), brokenCode, errorLocation)) {
                if (codeConflictSolver.errorIsFixableBySolver(context.getCompileError(), brokenCode, errorLocation)) {
                    ProposedChange proposedChange = codeConflictSolver.solveConflict(context.getCompileError(), brokenCode, errorLocation);
                    context.getProposedChanges().add(proposedChange);
                    context.getProposedChanges().add(proposedChange);
                    context.getErrorSet().put(brokenCode.code().trim(), proposedChange);
                    return;
                }
            }
        }



            /*if (brokenCode.code().startsWith("import")) {
                targetClass = brokenCode.code().substring(brokenCode.code().indexOf(" "), brokenCode.code().lastIndexOf(";")).trim();
                if (targetClass.contains(".")) {
                    targetClass = targetClass.substring(targetClass.lastIndexOf(".") + 1);
                }
                // Whole package import
                if (targetClass.endsWith("*")) {
                    targetClass = "";
                }

                JarDiffUtil jarDiffUtil = new JarDiffUtil(context.getTargetPathOld().toString(), context.getTargetPathNew().toString());

                String alternative = jarDiffUtil.getAlternativeClassImport(targetClass);

                if (alternative != null) {
                    ProposedChange proposedChange = new ProposedChange(strippedClassName, "import " + alternative + ";", context.getCompileError().file, brokenCode.start(), brokenCode.end());
                    context.getProposedChanges().add(proposedChange);
                    context.getErrorSet().put(brokenCode.code().trim(), proposedChange);
                    return;
                }
            }

            if (brokenCode.code().trim().startsWith("@Override") && context.getCompileError().message.startsWith("method does not override or implement a method from a supertype")) {
                ProposedChange proposedChange = new ProposedChange(strippedClassName, brokenCode.code().trim().substring("@Override".length()), context.getCompileError().file, brokenCode.start(), brokenCode.end());
                context.getProposedChanges().add(proposedChange);
                context.getErrorSet().put(brokenCode.code().trim(), proposedChange);
                return;
            }

            Matcher typecastMatcher = CAST_PATTERN.matcher(context.getCompileError().message);
            Matcher deprecationMatcher = DEPRECATION_PATTERN.matcher(context.getCompileError().message);
            Matcher constructorTypesMatcher = CONSTRUCTOR_TYPES_PATTERN.matcher(context.getCompileError().message);
            Matcher methodChainDetectionMatcher = METHOD_CHAIN_DETECTION_PATTERN.matcher(brokenCode.code());
            Matcher classFileNotFoundMatcher = CLASS_FILE_NOT_FOUND_PATTERN.matcher(context.getCompileError().message);

            if (typecastMatcher.find()) {
                targetMethodParameterClassNames = new String[]{typecastMatcher.group(2)};
            }

            //if (classFileNotFoundMatcher.find()) {
            //    for (String detail : context.getCompileError().details.values()) {
            //        Matcher classFileNotFoundDetailMatcher = CLASS_FILE_NOT_FOUND_DETAIL_PATTERN.matcher(detail);
            //        if (classFileNotFoundDetailMatcher.find()) {
            //            String className = classFileNotFoundDetailMatcher.group(1);
            //            String strippedClassesName = className.substring(className.lastIndexOf(".") + 1);
            //             int line = getImportLineIndex(strippedClassesName, Paths.get(context.getTargetDirectoryClasses() + "/" + context.getStrippedFileName() + "_" + strippedClassName));

            //             ProposedChange proposedChange = new ProposedChange(strippedClassName, "import "+className+";", context.getCompileError().file, line, line);
            //             context.getProposedChanges().add(proposedChange);
            //            //context.getErrorSet().put(brokenCode.code().trim(), proposedChange);
            //            return;
            //         }
            //     }

            //  } else
            if (constructorTypesMatcher.find()) {
                targetMethod = constructorTypesMatcher.group(1);
                targetClass = constructorTypesMatcher.group(2);
            } else if (context.getCompileError().message.equals("cannot find symbol")) {
                if (context.getCompileError().details.containsKey("symbol")) {
                    String sym = context.getCompileError().details.get("symbol");
                    if (sym.startsWith("symbol")) {
                        sym = sym.substring("symbol".length() + 1).trim();
                    }
                    if (sym.startsWith("class")) {
                        targetClass = sym.substring(sym.indexOf("class") + "class".length() + 1);
                    } else if (sym.startsWith("method")) {
                        targetMethod = context.getCompileError().details.get("symbol");
                        targetMethod = targetMethod.substring(targetMethod.indexOf(" ") + 1);
                        if (targetMethod.indexOf("(") != targetMethod.indexOf(")") - 1) {
                            String parameterString = targetMethod.substring(targetMethod.indexOf("(") + 1, targetMethod.indexOf(")"));
                            String[] parameters = parameterString.split(",");
                            targetMethodParameterClassNames = new String[parameters.length];
                            for (int i = 0; i < parameters.length; i++) {
                                targetMethodParameterClassNames[i] = primitiveClassNameToWrapperName(parameters[i]);
                            }
                        }

                        targetMethod = targetMethod.substring(0, targetMethod.indexOf("("));

                        if (context.getCompileError().details.containsKey("location")) {
                            targetClass = context.getCompileError().details.get("location");
                            if (targetClass.contains("of type")) {
                                targetClass = targetClass.substring(targetClass.indexOf("of type") + "of type".length() + 1);
                            } else {
                                targetClass = targetClass.substring(targetClass.indexOf("class") + "class".length() + 1);
                            }
                        }
                    }
                }
            } else if (deprecationMatcher.find()) {
                targetMethod = deprecationMatcher.group(1);
                targetClass = deprecationMatcher.group(3);
            } else if (brokenCode.code().trim().startsWith("super")) {
                String parent = readParent(strippedClassName, context.getTargetDirectoryClasses(), context.getStrippedFileName());
                System.out.println("super " + parent);
                targetClass = parent;
                targetMethod = parent;
            //}else if(context.getCompileError().message.startsWith("incompatible types:")){
                // Let the LLM decide on its own
            } else if (methodChainDetectionMatcher.find()) {
                MethodChainAnalysis methodChainAnalysis = analyseMethodChain(context.getCompileError().column, brokenCode.start(), brokenCode.code(), context.getTargetDirectoryClasses(), context.getStrippedFileName(),
                        strippedClassName, context.getTargetPathOld(), context.getTargetPathNew(), context.getOutputDirSrcFiles().toPath().resolve(Path.of(context.getDependencyArtifactId() + "_" + context.getStrippedFileName())).toString());

                if (methodChainAnalysis != null) {
                    targetClass = methodChainAnalysis.targetClass();
                    targetMethod = methodChainAnalysis.targetMethod();

                    if (targetMethodParameterClassNames.length == 0) {
                        targetMethodParameterClassNames = methodChainAnalysis.parameterTypes();
                    }
                }

            } else {
                System.out.println("UNCAT " + brokenCode.code());
            }*/

        System.out.println("Target class: " + targetClass);
        System.out.println("Target method: " + targetMethod);



            /*ConflictResolutionResult result = null;

            String llmResponseFileName = context.getTargetDirectoryLLMResponses() + "/" + context.getStrippedFileName() + "_" + context.getCompileError().line + "_" + targetClass + "_" + context.getActiveProvider() + ".txt";

            String erroneousClass = readBrokenClass(strippedClassName, context.getTargetDirectoryClasses(), context.getStrippedFileName());
            String erroneousScope = readBrokenEnclosingScope(strippedClassName, context.getTargetDirectoryClasses(), context.getStrippedFileName(), context.getCompileError().line);

            if (!usePromptCaching || !Files.exists(Path.of(llmResponseFileName))) {
                String errorPrompt = "line: " + context.getCompileError().line + ", column: " + context.getCompileError().column + System.lineSeparator() + context.getCompileError().message;

                for (String detail : context.getCompileError().details.keySet()) {
                    errorPrompt = errorPrompt + System.lineSeparator() + detail + " " + context.getCompileError().details.get(detail);
                }
                String prompt = Main.buildPrompt(context.getDependencyArtifactId(), context.getPreviousVersion(), context.getNewVersion(), context.getTargetPathOld().toString(), context.getTargetPathNew().toString(),
                        targetClass, targetMethod, brokenCode.code(), targetMethodParameterClassNames, errorPrompt, erroneousClass, erroneousScope);

                Files.write(Path.of(context.getTargetDirectoryPrompts() + "/" + context.getStrippedFileName() + "_" + context.getCompileError().line + "_" + targetClass + ".txt"), prompt.getBytes());

                System.out.println(prompt);
                int counter = 0;
                while (result == null) {
                    try {
                        result = Main.sendAndPrintCode(context.getActiveProvider(), prompt);
                    } catch (AIProviderException e) {
                        System.err.println("AI Provider Exception " + counter + ": " + e.getMessage());
                        counter++;
                    }
                }

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

            ProposedChange proposedChange = new ProposedChange(strippedClassName, result.code(), context.getCompileError().file, brokenCode.start(), brokenCode.end());
            context.getProposedChanges().add(proposedChange);
            context.getErrorSet().put(brokenCode.code().trim(), proposedChange);*/

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

    public static void extractWholeEntry(TarArchiveInputStream tais, TarArchiveEntry entry, File outputDir) throws IOException {
        File outputFile = new File(outputDir, entry.getName());

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

    public static void extractCompiledCodeFromContainer(File targetDirectory, DockerClient dockerClient, String imagePath, String projectName) {
        System.out.println("Fetching class from container (this takes some time)");
        CreateContainerResponse container = pullImageAndCreateContainerWithPackageCommand(dockerClient, imagePath);

        dockerClient.startContainerCmd(container.getId()).exec();
        try {
            dockerClient.waitContainerCmd(container.getId()).start().awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        File projectDir = new File(targetDirectory, projectName);

        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }


        getSourceFiles(dockerClient, container, projectDir);
        getDependencyFiles(dockerClient, container, projectDir);
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

    public static boolean logFromContainerContainsError(DockerClient dockerClient, CreateContainerResponse container, String fileName, String projectName, String dir) {
        dockerClient.startContainerCmd(container.getId()).exec();
        dockerClient.waitContainerCmd(container.getId()).start().awaitStatusCode();
        final boolean[] containsError = {false};
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(dir + "/" + fileName + "_" + projectName));

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
                    int[] bracketOccurrencesInLine = getBracketOccurrencesInString(brokenCode);
                    if (!(brokenCode.endsWith(";") || brokenCode.endsWith("{") || brokenCode.endsWith("}") || (bracketOccurrencesInLine[0] == bracketOccurrencesInLine[1] && bracketOccurrencesInLine[0] != 0))) {
                        for (int j = i + 1; j < allLines.size(); j++) {
                            String line = allLines.get(j);

                            brokenCode = brokenCode + line;
                            if (allLines.get(j).endsWith(";")) {
                                end = j + 1;
                                break;
                            }

                            bracketOccurrencesInLine = getBracketOccurrencesInString(brokenCode);

                            if (bracketOccurrencesInLine[0] == bracketOccurrencesInLine[1] && bracketOccurrencesInLine[0] != 0) {
                                end = j + 1;
                                break;
                            }


                        }
                    } else {
                        end = start;
                    }
                    return new BrokenCode(brokenCode, start, end, "");
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static int[] getBracketOccurrencesInString(String line) {
        int[] occurrences = new int[2];
        for (int k = 0; k < line.length(); k++) {
            char c = line.charAt(k);
            if (c == '(') {
                occurrences[0]++;
            } else if (c == ')') {
                occurrences[1]++;
            }
        }

        return occurrences;
    }

    public static CleanedLines cleanLines(List<String> lines, int indexBeforeCleaning) {
        List<String> newLines = new ArrayList<>();
        int indexAfterCleaning = indexBeforeCleaning;

        boolean lastCharWasSlash = false;
        boolean lastCharWasStar = false;
        boolean isInComment = false;
        for (int j = 0; j < lines.size(); j++) {
            String line = lines.get(j);
            if (line.startsWith("//")) {
                if (j < indexBeforeCleaning) {
                    indexAfterCleaning--;
                }
                continue;
            }
            int commentStart = -1, commentEnd = -1;
            if (line.contains("/*") || line.trim().endsWith("/")) {
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '/') {
                        lastCharWasSlash = true;
                        if (isInComment && lastCharWasStar) {
                            isInComment = false;
                            commentEnd = i;
                        }
                    } else if (c == '*') {
                        lastCharWasStar = true;
                        if (!isInComment && lastCharWasSlash) {
                            isInComment = true;
                            commentStart = i - 1;
                        }
                    } else {
                        lastCharWasSlash = false;
                        lastCharWasStar = false;
                    }
                }
            }

            if (isInComment || commentEnd != -1 || commentStart != -1) {
                String lineWithoutComments = "";
                if (commentStart != -1) {
                    lineWithoutComments += line.substring(0, commentStart);
                }
                if (commentEnd != -1) {
                    lineWithoutComments += line.substring(commentEnd + 1);
                }
                if (!lineWithoutComments.isBlank()) {
                    newLines.add(lineWithoutComments);
                } else {
                    if (j < indexBeforeCleaning) {
                        indexAfterCleaning--;
                    }
                }
                continue;
            }

            newLines.add(line);
        }

        return new CleanedLines(newLines, indexBeforeCleaning, indexAfterCleaning);
    }

    public static int getImportLineIndex(String className, Path path) {
        try {
            List<String> allLines = Files.readAllLines(path);
            for (int i = 0; i < allLines.size(); i++) {
                String line = allLines.get(i).trim();
                if (line.startsWith("import") && line.endsWith(className + ";")) {
                    return i;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    public static String getClassNameOfVariable(String variableName, Path path, int lineNumber) {
        try {
            List<String> allLines = Files.readAllLines(path);

            CleanedLines cleanedLines = cleanLines(allLines, lineNumber);

            allLines = cleanedLines.lines();

            Pattern declarationPattern = Pattern.compile(
                    "\\b([A-Za-z_][A-Za-z0-9_]*)\\s+(" + variableName + ")\\s*[,|;|=|\\)]");

            for (int i = Math.min(cleanedLines.indexAfterCleaning(), allLines.size()); i >= 0; i--) {
                String line = allLines.get(i);
                int variableIndex = line.indexOf(variableName);
                if (variableIndex > 0) {
                    Matcher declarationMatcher = declarationPattern.matcher(line);
                    if (declarationMatcher.find()) {
                        String match = declarationMatcher.group(1);
                        if (match.trim().equals("new")) {
                            return null;
                        }
                        return match;
                    }
                }

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static String getReturnTypeOfMethod(String methodName, String directory, String fileName, String className) {
        try {
            List<String> allLines = Files.readAllLines(Paths.get(directory + "/" + fileName + "_" + className));


            for (int i = 0; i < allLines.size(); i++) {
                String line = allLines.get(i);
                int methodIndex = line.indexOf(methodName);
                String returnType = "";
                for (int j = methodIndex - 2; j >= 0; j--) {
                    char c = methodName.charAt(j);
                    if (c == ' ') {
                        break;
                    }
                    returnType = c + returnType;
                }

                return returnType;

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

    public static String readBrokenEnclosingScope(String className, String directory, String fileName, int lineNumber) {
        Pattern methodDeclarationPattern = Pattern.compile("(public|protected|private|static|\\s) +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *\\([^\\)]*\\) *(\\{?|[^;])");
        try {
            List<String> lines = Files.readAllLines(Path.of(directory + "/" + fileName + "_" + className));

            int start = 0;
            for (int i = lineNumber - 1; i >= 0; i--) {
                Matcher declarationMatcher = methodDeclarationPattern.matcher(lines.get(i));

                if (declarationMatcher.find()) {
                    start = i;
                    break;
                }
            }

            int openCurlyBraces = 0;
            int closedCurlyBraces = 0;

            int end = 0;

            outerloop:
            for (int i = start; i < lines.size(); i++) {
                for (int j = 0; j < lines.get(i).length(); j++) {
                    char c = lines.get(i).charAt(j);
                    if (c == '{') {
                        openCurlyBraces++;
                    } else if (c == '}') {
                        closedCurlyBraces++;
                    }

                    if (openCurlyBraces == closedCurlyBraces && openCurlyBraces != 0) {
                        end = i;
                        break outerloop;
                    }
                }
            }

            String scope = "";

            for (int i = start; i <= end; i++) {
                scope = scope + lines.get(i) + System.lineSeparator();
            }

            return scope;


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

    public static CreateContainerResponse pullImageAndCreateContainerWithPackageCommand(DockerClient dockerClient, String imagePath) {
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
            //TODO: Check if this works for all BUMP projects (it probably wont lol)
            return dockerClient.createContainerCmd(imagePath)
                    .withCmd("sh", "-c",
                            //"mvn clean test -B | tee %s.log")
                            // Use multiple lines instead of comma seperated scopes because of older maven versions
                            "mvn -B dependency:copy-dependencies -DoutputDirectory=/tmp/dependencies")
                    //"mvn clean package org.apache.maven.plugins:maven-shade-plugin:3.5.0:shade -Dmaven.test.skip=true")
                    .exec();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    public static Path searchForClassInSourceFiles(File sourceDir, String className) {
        try {
            return Files.walk(sourceDir.toPath())
                    .filter(path -> {
                        if (path.toString().endsWith(".java")) {
                            String pathSuffix = path.toString().substring(0, path.toString().lastIndexOf("."));
                            if (pathSuffix.endsWith(className)) {
                                return true;
                            }
                        }
                        return false;
                    }).findFirst().orElse(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getSourceFiles(DockerClient dockerClient, CreateContainerResponse container, File outputDir) {
        try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(container.getId(), "/").exec();
             TarArchiveInputStream tarInput = new TarArchiveInputStream(tarStream)) {

            TarArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                String path = entry.getName();
                //System.out.println(path);
                if (!path.contains("src/")) {
                    continue;
                }

                File outputFile = new File(outputDir.getAbsolutePath() + "\\" + entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    if (path.endsWith("java") || path.endsWith(".jar")) {
                        extractWholeEntry(tarInput, entry, outputDir);
                    } else {
                        //System.out.println("Rejected "+entry.getName());
                    }
                }

                //extractEntry(tarInput, entry, outputDir, "");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getDependencyFiles(DockerClient dockerClient, CreateContainerResponse container, File outputDir) {
        try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(container.getId(), "/").exec();
             TarArchiveInputStream tarInput = new TarArchiveInputStream(tarStream)) {

            TarArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                String path = entry.getName();
                //System.out.println(path);
                if (!path.contains("tmp/dependencies")) {
                    continue;
                }

                File outputFile = new File(outputDir.getAbsolutePath() + "\\" + entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    if (path.endsWith("java") || path.endsWith(".jar")) {
                        extractWholeEntry(tarInput, entry, outputDir);
                    } else {
                        //System.out.println("Rejected "+entry.getName());
                    }
                }

                //extractEntry(tarInput, entry, outputDir, "");
            }
        } catch (IOException e) {
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

            System.out.println("File " + fileNameInContainer + " replaced successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getClosingBraceIndex(String s, int start) {
        int openBrackets = 0;
        int closedBrackets = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                openBrackets++;
            } else if (c == ')') {
                closedBrackets++;
            }

            if (openBrackets == closedBrackets && openBrackets != 0) {
                return i;
            }
        }
        return -1;
    }

    public static List<String> getParameterTypesOfMethodCall(SourceCodeAnalyzer sourceCodeAnalyzer, String methodCall, String targetDirectoryClasses, String strippedFileName, String strippedClassName, int line) {
        List<String> parameterTypes = new ArrayList<>();
        if (methodCall.indexOf("(") != methodCall.indexOf(")") - 1) {
            int closingBraceIndex = getClosingBraceIndex(methodCall, methodCall.indexOf("("));
            String potentialInnerChain = methodCall.substring(methodCall.indexOf("(") + 1, closingBraceIndex);
            String[] paramSplit = potentialInnerChain.split(",");
            for (String param : paramSplit) {
                param = param.trim();
                if (!param.contains("(")) {
                    parameterTypes.add(getClassNameOfVariable(param, Paths.get(targetDirectoryClasses + "/" + strippedFileName + "_" + strippedClassName), line));
                } else {
                    String methodName = param.substring(0, param.indexOf("("));
                    List<String> innerTypes = getParameterTypesOfMethodCall(sourceCodeAnalyzer, potentialInnerChain, targetDirectoryClasses, strippedFileName, strippedClassName, line);
                    parameterTypes.add(sourceCodeAnalyzer.getReturnTypeOfMethod(strippedClassName, methodName, innerTypes.toArray(new String[innerTypes.size()])));
                }

            }

        }

        return parameterTypes;
    }

    /*public static MethodChainAnalysis analyseMethodChain(int compileErrorColumn, int line, String brokenCode, String targetDirectoryClasses,
                                                         String strippedFileName, String strippedClassName, Path targetPathOld, Path targetPathNew, String srcDirectory) {
        int errorIndex = Math.min(compileErrorColumn, brokenCode.length() - 1);
        String brokenSymbol = "";

        SourceCodeAnalyzer sourceCodeAnalyzer = new SourceCodeAnalyzer(srcDirectory);
        if (brokenCode.contains("=")) {
            brokenCode = brokenCode.substring(brokenCode.indexOf("=") + 1).trim();
        }

        boolean isConstructor = false;

        if (brokenCode.contains("new")) {
            brokenCode = brokenCode.substring(brokenCode.indexOf("new") + "new".length()).trim();
            isConstructor = true;
        }

        if (brokenCode.contains("return")) {
            brokenCode = brokenCode.substring(brokenCode.indexOf("return") + "return".length()).trim();
        }

        String targetClass = "";
        String targetMethod = "";

        String[] parameterNames = new String[0];


        List<String> precedingMethodChain = new ArrayList<>();

        while (brokenCode.indexOf("(") != brokenCode.indexOf(")") - 1) {
            int closingBraceIndex = getClosingBraceIndex(brokenCode, brokenCode.indexOf("("));
            String potentialInnerChain = brokenCode.substring(brokenCode.indexOf("(") + 1, closingBraceIndex);
            if (!potentialInnerChain.contains("(")) {
                break;
            }
            if (errorIndex >= brokenCode.indexOf("(") && errorIndex < closingBraceIndex) {
                brokenCode = potentialInnerChain;
            } else {
                brokenCode = brokenCode.substring(0, brokenCode.indexOf("(") + 1) + brokenCode.substring(closingBraceIndex);
            }

        }

        Matcher methodChainMatcher = METHOD_CHAIN_PATTERN.matcher(brokenCode);

        List<String[]> methodChainParams = new ArrayList<>();

        while (methodChainMatcher.find()) {

            String method = methodChainMatcher.group().replaceAll("\\.", "");
            if (method.contains("(")) {
                method = method.substring(0, method.indexOf("("));

                int methodCallStart = brokenCode.indexOf(method);
                int methodCallEnd = getClosingBraceIndex(brokenCode, methodCallStart);

                String methodCall = brokenCode.substring(methodCallStart, methodCallEnd + 1);
                methodChainParams.add(getParameterTypesOfMethodCall(sourceCodeAnalyzer, methodCall, targetDirectoryClasses, strippedFileName, strippedClassName, line).toArray(new String[]{}));
            } else {
                methodChainParams.add(new String[]{});
            }

            precedingMethodChain.add(method);
        }


        if (precedingMethodChain.size() > 0) {
            String classNameOfVariable;
            if (precedingMethodChain.get(0).equals("super")) {
                classNameOfVariable = readParent(strippedClassName, targetDirectoryClasses, strippedFileName);
            } else if (precedingMethodChain.get(0).equals("this")) {
                classNameOfVariable = strippedClassName;
            } else {

                classNameOfVariable = getClassNameOfVariable(precedingMethodChain.get(0), Paths.get(targetDirectoryClasses + "/" + strippedFileName + "_" + strippedClassName), line);

                if (precedingMethodChain.size() == 1) {
                    targetClass = classNameOfVariable;
                    if (targetMethod.isEmpty()) {
                        targetMethod = brokenSymbol;
                    }
                } else if (classNameOfVariable == null) {
                    String parent = readParent(strippedClassName, targetDirectoryClasses, strippedFileName);
                    if (parent != null) {
                        Path parentPath = searchForClassInSourceFiles(new File(srcDirectory), parent);
                        if (parentPath != null) {
                            classNameOfVariable = getClassNameOfVariable(precedingMethodChain.get(0), parentPath, Integer.MAX_VALUE);
                        } else {
                            classNameOfVariable = sourceCodeAnalyzer.getTypeOfFieldInClass(new File(srcDirectory + "/tmp/dependencies"), parent, precedingMethodChain.get(0));

                        }
                    }
                }
            }

            if (classNameOfVariable == null) {
                // Assume its a static call
                targetClass = precedingMethodChain.get(0);
                if (isConstructor) {
                    targetMethod = precedingMethodChain.get(0);
                }
            }
            if (precedingMethodChain.size() > 1) {
                if (classNameOfVariable == null) {
                    return null;
                }
                //JarDiffUtil jarDiffUtil = new JarDiffUtil(targetPathOld.toString(), targetPathNew.toString());

                String previousClassName = null;
                String intermediateClassName = classNameOfVariable;


                for (int i = 1; i < precedingMethodChain.size() - 1; i++) {
                    if (intermediateClassName == null) {
                        return new MethodChainAnalysis(previousClassName, precedingMethodChain.get(i - 1), methodChainParams.get(i - 1));
                    }
                    previousClassName = intermediateClassName;
                    intermediateClassName = sourceCodeAnalyzer.getReturnTypeOfMethod(intermediateClassName, precedingMethodChain.get(i), methodChainParams.get(i));
                }

                if (intermediateClassName == null) {
                    return new MethodChainAnalysis(previousClassName, precedingMethodChain.get(precedingMethodChain.size() - 2), methodChainParams.get(methodChainParams.size() - 2));
                }

                targetClass = intermediateClassName;
                targetMethod = precedingMethodChain.get(precedingMethodChain.size() - 1);

                System.out.println("targetClass: " + targetClass);
                System.out.println("targetMethod: " + targetMethod);

                String targetMethodParameters = brokenCode.substring(brokenCode.indexOf(targetMethod) + targetMethod.length());

                if (!(targetMethodParameters.isEmpty() || targetMethodParameters.equals("()"))) {


                    int openBrackets = 0;
                    int closingBrackets = 0;
                    int end = targetMethodParameters.length();
                    for (int i = 0; i < targetMethodParameters.length(); i++) {
                        char c = targetMethodParameters.charAt(i);
                        if (c == '(') {
                            openBrackets++;
                        } else if (c == ')') {
                            closingBrackets++;
                        }

                        if (openBrackets == closingBrackets) {
                            end = i - 1;
                        }
                    }

                    targetMethodParameters = targetMethodParameters.substring(1, end);


                    // TODO: This might fail if the parameter has a method invocation with multiple parameters seperated by ,

                    if (!targetMethodParameters.isBlank()) {
                        parameterNames = targetMethodParameters.split(",");

                        for (int i = 0; i < parameterNames.length; i++) {
                            String parameterName = parameterNames[i].trim();
                            if (parameterName.endsWith(".class")) {
                                parameterNames[i] = "java.lang.Class";
                            } else {
                                String callerClass = getClassNameOfVariable(parameterName, Paths.get(targetDirectoryClasses + "/" + strippedFileName + "_" + strippedClassName), line);
                                parameterNames[i] = callerClass;
                            }
                        /*if (parameterName.contains("(")) {
                            String[] splitParameter = parameterName.split("\\.");
                            String callerClass = getClassNameOfVariable(splitParameter[0], targetDirectoryClasses, strippedFileName, strippedClassName, line);
                            String methodName = splitParameter[1].substring(0, splitParameter[1].indexOf("("));
                            //parameterNames[i] = jarDiffUtil.getMethodReturnType(callerClass, methodName);
                        }
                        }
                    }
                }

            }
            System.out.println(brokenSymbol);
        } else {
            targetClass = brokenSymbol;
        }

        return new MethodChainAnalysis(targetClass, targetMethod, parameterNames);
    }*/

    public static String primitiveClassNameToWrapperName(String parameter) {
        switch (parameter) {
            case "boolean":
                return Boolean.class.getName();
            case "int":
                return Integer.class.getName();
            case "double":
                return Double.class.getName();
            case "float":
                return Float.class.getName();
            case "byte":
                return Byte.class.getName();
            case "short":
                return Short.class.getName();
            case "Long":
                return Long.class.getName();
            case "char":
                return Character.class.getName();
            default:
                return parameter;
        }
    }
}
