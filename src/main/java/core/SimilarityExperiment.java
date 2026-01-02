package core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dependencysimilarity.DependencyAnalyser;

import java.io.File;
import java.util.*;

public class SimilarityExperiment {
    public static void main(String[] args) {
        File bumpFolder = new File("testFiles/downloaded");
        HashMap<String, List<Double>> dependencyStatistics = new HashMap<>();
        Set<String> alreadyComputed = new HashSet<>();
        //int limit = 1;
        //int iteration = 0;
        for (File file : bumpFolder.listFiles()) {
            /*if(iteration == limit){
                break;
            }*/
            String strippedFileName = file.getName();
            if(strippedFileName.contains("-")){
                strippedFileName = strippedFileName.substring(0, strippedFileName.lastIndexOf("-"));
            }
            if(alreadyComputed.contains(strippedFileName)){
                System.out.println("Skipping file " + strippedFileName + " ("+file.getName()+")");
                continue;
            }

            try {
                dependencyStatistics.put(file.getName(), DependencyAnalyser.analyseDependencySimilarity(file.getPath(), "http://localhost:11434", "nomic-embed-text"));
            }catch (Exception e){
                e.printStackTrace();
            }

            alreadyComputed.add(strippedFileName);
            //iteration ++;
        }
        System.out.println();
        System.out.println("Summary: ");

        for (Map.Entry<String, List<Double>> entry : dependencyStatistics.entrySet()) {
            String fileName = entry.getKey();
            List<Double> stats = entry.getValue();

            if (stats.isEmpty()) {
                System.out.println(fileName + ": No data");
                continue;
            }

            System.out.printf(
                    "%s | Avg: %.4f | Min: %.4f | Max: %.4f | Count: %.0f | Median: %.4f | 25%%: %.4f | 75%%: %.4f | Variance: %.4f | StdDev: %.4f%n",
                    fileName,
                    stats.get(0), // average
                    stats.get(1), // min
                    stats.get(2), // max
                    stats.get(3), // count
                    stats.get(4), // median
                    stats.get(5), // 25th percentile
                    stats.get(6), // 75th percentile
                    stats.get(7), // variance
                    stats.get(8)  // standard deviation
            );
        }

        System.out.println("Statistics of the statistics: ");


        // Transpose
        int numStats = dependencyStatistics.values().iterator().next().size();
        List<List<Double>> transposed = new ArrayList<>();

        for (int i = 0; i < numStats; i++) {
            transposed.add(new ArrayList<>());
        }

        for (List<Double> stats : dependencyStatistics.values()) {
            for (int i = 0; i < stats.size(); i++) {
                transposed.get(i).add(stats.get(i));
            }
        }

        String[] statNames = {"Avg", "Min", "Max", "Count", "Median", "25%", "75%", "Variance", "StdDev"};

        for (int i = 0; i < transposed.size(); i++) {
            List<Double> values = transposed.get(i);
            DoubleSummaryStatistics s = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
            double mean = s.getAverage();
            double min = s.getMin();
            double max = s.getMax();
            long count = s.getCount();
            double variance = values.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(Double.NaN);
            double stdDev = Math.sqrt(variance);

            System.out.printf(
                    "%s of %s: Avg=%.4f | Min=%.4f | Max=%.4f | Count=%d | StdDev=%.4f%n",
                    "Statistic", statNames[i], mean, min, max, count, stdDev
            );
        }

    }
}
