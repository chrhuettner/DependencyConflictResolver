package core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import provider.AIProvider;
import provider.ChatGPTProvider;
import provider.ClaudeProvider;
import provider.OllamaProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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

import static core.Main.buildPrompt;
import static core.Main.sendAndPrintCode;

public class BumpRunner {

    private static boolean useContainerExtraction = false;

    public static void main(String[] args) {
        String targetDirectory = "testFiles/downloaded";
        File outputDir = new File(targetDirectory);


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
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);

        File bumpFolder = new File("testFiles/BUMP");
        ObjectMapper objectMapper = new ObjectMapper();

        List<String> validEntryNames = new ArrayList<>();
        int satisfiedConflictPairs = 0;
        int totalPairs = 0;
        for (File file : bumpFolder.listFiles()) {
            try {
                JsonNode jsonNode = objectMapper.readTree(file);
                JsonNode updatedDependency = jsonNode.get("updatedDependency");

                String mavenSourceLinkPre = cleanString(updatedDependency.get("mavenSourceLinkPre").toString());
                String mavenSourceLinkBreaking = cleanString(updatedDependency.get("mavenSourceLinkBreaking").toString());

                String previousVersion = cleanString(updatedDependency.get("previousVersion").toString());
                String newVersion = cleanString(updatedDependency.get("newVersion").toString());
                String dependencyGroupID = cleanString(updatedDependency.get("dependencyGroupID").toString());
                String dependencyArtifactID = cleanString(updatedDependency.get("dependencyArtifactID").toString());
                String preCommitReproductionCommand = cleanString(jsonNode.get("preCommitReproductionCommand").toString());
                String breakingUpdateReproductionCommand = cleanString(jsonNode.get("breakingUpdateReproductionCommand").toString());
                String updatedFileType = cleanString(updatedDependency.get("updatedFileType").toString());
                if (!updatedFileType.equals("JAR")) {
                    continue;
                }


                String brokenUpdateImage = breakingUpdateReproductionCommand.substring(breakingUpdateReproductionCommand.lastIndexOf(" ")).trim();
                String oldUpdateImage = preCommitReproductionCommand.substring(preCommitReproductionCommand.lastIndexOf(" ")).trim();

                String combinedArtifactNameNew = dependencyArtifactID + "-" + newVersion + ".jar";
                String combinedArtifactNameOld = dependencyArtifactID + "-" + previousVersion + ".jar";

                System.out.println(file.getName());
                Path targetPathOld = downloadLibrary(mavenSourceLinkPre, outputDir, dockerClient, oldUpdateImage, combinedArtifactNameOld);
                Path targetPathNew = downloadLibrary(mavenSourceLinkBreaking, outputDir, dockerClient, brokenUpdateImage, combinedArtifactNameNew);

                if (Files.exists(targetPathOld) && Files.exists(targetPathNew)) {
                    satisfiedConflictPairs++;
                }
                totalPairs++;
                //String prompt = buildPrompt(dependencyArtifactID, previousVersion, newVersion, targetPathOld.toString(), targetPathNew.toString(),
                //        "", "", "", new String[]{});

                //System.out.println(prompt);
                //break;

            } catch (IOException e) {
                System.err.println(e);
            }

        }

        System.out.println(satisfiedConflictPairs + " out of " + totalPairs + " entries have valid dependencies");
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

    public static void extractEntry(TarArchiveInputStream tais, TarArchiveEntry entry, File outputDir) throws IOException {
        File outputFile = new File(outputDir, entry.getName().substring(entry.getName().lastIndexOf('/') + 1));

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
        CreateContainerResponse container = pullAndStartContainer(dockerClient, imagePath);
        getLibraryFromContainer(dockerClient, container, artifactNameWithVersion, targetDirectory);
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

    public static CreateContainerResponse pullAndStartContainer(DockerClient dockerClient, String imagePath) {
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

    public static void getLibraryFromContainer(DockerClient dockerClient, CreateContainerResponse container, String artifactWithVersionName, File outputDir) {
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
                if (!path.endsWith(artifactWithVersionName)) {
                    continue;
                }

                System.out.println("Found " + artifactWithVersionName + " in container, proceeding to download it into " + outputDir.getAbsolutePath());
                extractEntry(tarInput, entry, outputDir);
                found = true;
                break;
            }
            if (!found) {
                System.err.println("No library with name " + artifactWithVersionName + " found in container");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
