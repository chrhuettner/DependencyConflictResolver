package core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if(args.length < 1) {
            System.err.println("Usage: bump <pathToConfigFile>");
        }else if(args[0].equalsIgnoreCase("bump")) {
            if(args.length > 2) {
                System.out.println("Input after folder path gets ignored!");
            }
            String pathToBump = args[1];

            ObjectMapper mapper = new ObjectMapper();

            BumpConfig config = null;
            try {
                config = mapper.readValue(new File(pathToBump), BumpConfig.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Read config from "+pathToBump);
            System.out.println("------------------------------");
            System.out.println("BUMP folder: " + config.getPathToBUMPFolder());
            System.out.println("Threads: " + config.getThreads());
            System.out.println("Max Retries: " + config.getMaxRetries());
            System.out.println("Max Iterations: " + config.getMaxIterations());
            System.out.println("Output Path: " + config.getPathToOutput());
            System.out.println("LLM provider: " + config.getLlmProvider());
            System.out.println("LLM name: " + config.getLlmName());
            System.out.println("Docker host: " + config.getDockerHostUri());
            System.out.println("Docker registry: " + config.getDockerRegistryUri());
            System.out.println("Word Similarity Model: " + config.getWordSimilarityModel());
            System.out.println("------------------------------");
            System.out.println("Executing BUMP solver:");

            BumpRunner.runBUMP(config);

        }

    }
}