package dependencysimilarity;

import com.github.dockerjava.api.DockerClient;
import core.JarDiffUtil;
import japicmp.model.JApiClass;
import llm.WordSimilarityModel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyAnalyser {

    public static List<Double> analyseDependencySimilarity(String dependencyUri, String ollamaUri, String modelName) {
        WordSimilarityModel wordSimilarityModel = new WordSimilarityModel(modelName, ollamaUri);
        Path pathToDependency = Path.of(dependencyUri);
        if (!Files.exists(pathToDependency)) {
            if (dependencyUri.endsWith("-sources.jar")) {
                dependencyUri = dependencyUri.replace("-sources.jar", ".jar");
            }
            File dependencyDirectory = new File("/dependencies");
            dependencyDirectory.mkdirs();
            pathToDependency = downloadDependency(dependencyUri, dependencyDirectory);
        }

        JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(pathToDependency.toString(), pathToDependency.toString());
        System.out.println("Similarity of methods inside of the classes: ");

        Long embeddingStartTime = System.currentTimeMillis();
        ConcurrentHashMap<String, List<double[]>> embeddings = jarDiffUtil.getMethodEmbeddingsOfDependency(wordSimilarityModel);
        Long embeddingEndTime = System.currentTimeMillis();
        System.out.println("Computing embeddings took " + (embeddingEndTime - embeddingStartTime) + " ms");
        System.out.println("Computing similarity among class methods: ");
        Map<String, Double> innerSimilarities = jarDiffUtil.getClassMethodsSimilarity(embeddings);
        Map<String, Double> cleanedSimilarities = new HashMap<>();
        for (Map.Entry<String, Double> entry : innerSimilarities.entrySet()) {
            if (Double.isNaN(entry.getValue())) {
               // System.out.println(entry.getKey() + ": Less than 2 methods");
            } else {
                //System.out.println(entry.getKey() + ": " + entry.getValue());
                cleanedSimilarities.put(entry.getKey(), entry.getValue());

            }
        }

        List<Double> statistics = new ArrayList<>();


        DoubleSummaryStatistics stats = cleanedSimilarities.values().stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        System.out.println();
        //TODO: Iterate over every BUMP dependency and check trends. Maybe they are similar???
        System.out.println("Class statistics: ");

        System.out.println("Average: " + stats.getAverage());
        System.out.println("Min: " + stats.getMin());
        System.out.println("Max: " + stats.getMax());
        System.out.println("Count: " + stats.getCount());

        statistics.add(stats.getAverage());
        statistics.add(stats.getMin());
        statistics.add(stats.getMax());
        statistics.add((double) stats.getCount());

        List<Double> sorted = cleanedSimilarities.values().stream()
                .sorted()
                .toList();

        double median;
        int size = sorted.size();
        if (size % 2 == 0) {
            median = (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            median = sorted.get(size / 2);
        }
        System.out.println("Median: " + median);

        statistics.add(median);


        double p25 = sorted.get((int) (0.25 * (size - 1)));
        double p75 = sorted.get((int) (0.75 * (size - 1)));
        System.out.println("25th percentile: " + p25);
        System.out.println("75th percentile: " + p75);
        statistics.add(p25);
        statistics.add(p75);


        double mean = stats.getAverage();
        double variance = cleanedSimilarities.values().stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(Double.NaN);
        double stdDev = Math.sqrt(variance);

        System.out.println("Variance: " + variance);
        System.out.println("Standard Deviation: " + stdDev);
        statistics.add(variance);
        statistics.add(stdDev);

        return statistics;
    }

    public static Path downloadDependency(String dependencyUri, File targetDirectory) {
        String dependencyName = dependencyUri;
        if (dependencyName.contains("/")) {
            dependencyName = dependencyName.substring(dependencyName.lastIndexOf("/") + 1);
        }
        Path targetPath = Path.of(targetDirectory.getPath()).resolve(dependencyName);

        try {
            URL url = new URI(dependencyUri).toURL();
            InputStream inPrev = url.openStream();
            Files.copy(inPrev, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return targetPath;
    }
}
