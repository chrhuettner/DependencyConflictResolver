package core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
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
import java.io.IOException;
import java.io.InputStream;
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


    public static void main(String[] args) {
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
        int i = 0;
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

                String brokenUpdateImage = breakingUpdateReproductionCommand.substring(breakingUpdateReproductionCommand.lastIndexOf(" ")).trim();

                if (mavenSourceLinkPre.equals("ul") || mavenSourceLinkBreaking.equals("ul")) {
                    System.out.println("Skipped " + dependencyArtifactID + " due to missing sources");
                    continue;
                }


                System.out.println(mavenSourceLinkPre);
                URL urlPre = new URL(mavenSourceLinkPre);
                String fileNamePre = Paths.get(urlPre.getPath()).getFileName().toString();

                Path targetPathPre = Paths.get("testFiles/downloaded").resolve(fileNamePre);

                if (!Files.exists(targetPathPre)) {
                    InputStream inPrev = urlPre.openStream();
                    Files.copy(inPrev, targetPathPre, StandardCopyOption.REPLACE_EXISTING);
                }


                URL urlNew = new URL(mavenSourceLinkBreaking);

                String fileNameNew = Paths.get(urlNew.getPath()).getFileName().toString();
                Path targetPathNew = Paths.get("testFiles/downloaded").resolve(fileNameNew);

                if (!Files.exists(targetPathNew)) {
                    InputStream inNew = urlNew.openStream();
                    Files.copy(inNew, targetPathNew, StandardCopyOption.REPLACE_EXISTING);

                }


                // Pull an image
                dockerClient.pullImageCmd(brokenUpdateImage).start().awaitCompletion();

                // Create container
                CreateContainerResponse container = dockerClient.createContainerCmd(brokenUpdateImage)
                        .exec();

                //dockerClient.startContainerCmd(container.getId()).exec();
                HashMap<String, String> classFileLocations = new HashMap<>();
                try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(container.getId(), "/").exec();
                     TarArchiveInputStream tarInput = new TarArchiveInputStream(tarStream)) {

                    TarArchiveEntry entry;
                    while ((entry = tarInput.getNextEntry()) != null) {
                        // Print only top-level entries
                        String path = entry.getName();
                        //System.out.println(path);

                        //TODO: extract missing dependencies (those where the entry is null in the json) from the docker container
                        //TODO: get .java file from the github repo based on url and breakingCommit
                        classFileLocations.put(path.substring(path.lastIndexOf("/")), path);
                    }
                }


                String prompt = buildPrompt(dependencyArtifactID, previousVersion, newVersion, targetPathNew.toString(), targetPathNew.toString(),
                        "", "", "", new String[]{});

                System.out.println(prompt);
                break;

            } catch (IOException e) {
                System.err.println(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
        try {
            objectMapper.writeValue(new File("testFiles/downloaded/validEntries.json"), validEntryNames);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String cleanString(String str) {
        return str.substring(1, str.length() - 1);
    }
}
