package core;


import japicmp.model.JApiMethod;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.impl.DefaultServiceLocator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Crawler {

    private static RepositorySystem repoSystem;
    private static RepositorySystemSession session;
    private static List<RemoteRepository> repos;

    public static void main(String[] args) throws Exception {

        repoSystem = newRepositorySystem();
        session = newRepositorySystemSession(repoSystem);

        repos = new ArrayList<>();
        repos.add(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());


        List<String> lines = Files.readAllLines(Paths.get("libraries.txt"));

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("jar_diff_output.txt"))) {
            for (String line : lines) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue; // skip empty lines or comments
                }

                String[] parts = line.split("\\s+");
                if (parts.length != 4) {
                    System.out.println("Skipping invalid line: " + line);
                    continue;
                }

                String groupId = parts[0];
                String artifactId = parts[1];
                String oldVersion = parts[2];
                String newVersion = parts[3];

                System.out.println("Downloading jars for " + groupId + ":" + artifactId + " " + oldVersion + " vs " + newVersion);
                String oldJar = null;
                try {
                    oldJar = downloadArtifact(groupId, artifactId, oldVersion);
                } catch (ArtifactResolutionException e) {
                    System.out.println("Failed to find " + groupId + ":" + artifactId + " " + oldVersion);
                }


                String newJar = null;
                try {
                    newJar = downloadArtifact(groupId, artifactId, newVersion);
                } catch (ArtifactResolutionException e) {
                    System.out.println("Failed to find " + groupId + ":" + artifactId + " " + newVersion);
                }

                if (oldJar == null || newJar == null) {
                    continue;
                }

                JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(oldJar, newJar);
                List<JApiMethod> changedMethods = jarDiffUtil.getChangedMethods();

                if (!changedMethods.isEmpty()) {
                    writer.write("Library: " + groupId + ":" + artifactId + "\n");
                    writer.write("Versions: " + oldVersion + " " + newVersion + "\n");
                    writer.write("Changed methods:\n");
                    for (JApiMethod method : changedMethods) {
                        String returnType = method.getReturnType().getOldReturnType();
                        if (returnType.equals("n.a.")) {
                            returnType = method.getReturnType().getNewReturnType();
                        }
                        //TODO: Use JSON instead
                        writer.write("  - " + JarDiffUtil.getFullMethodSignature(method.getOldMethod().orElse(method.getNewMethod().orElse(null)).toString(), returnType, true, method.getParameters()) + " of class " + method.getjApiClass().getFullyQualifiedName() + " Changes: " + method.getChangeStatus() + "\n");
                    }
                    writer.write("\n");
                    writer.flush();
                    System.out.println("Found changes for " + artifactId);
                } else {
                    System.out.println("No changes found for " + artifactId);
                }
            }
        }

        System.out.println("Analysis complete. See jar_diff_output.txt");
    }

    private static String downloadArtifact(String groupId, String artifactId, String version) throws Exception {
        String coords = groupId + ":" + artifactId + ":" + version;
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new DefaultArtifact(coords));
        artifactRequest.setRepositories(repos);

        ArtifactResult artifactResult = repoSystem.resolveArtifact(session, artifactRequest);

        File file = artifactResult.getArtifact().getFile();
        System.out.println("Downloaded " + coords + " to " + file.getAbsolutePath());
        return file.getAbsolutePath();
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository("target/local-repo");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }
}


