package core;

import java.io.IOException;
import java.net.http.*;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.*;

public class WordSimilarityModel {

    private static final String MODEL = "nomic-embed-text";
    private static final String OLLAMA_URL = "http://localhost:11434/api/embeddings";

    static String wordSimilarityPrompt = """
            Compare the semantic similarity of the words: "%s" and "%s"
            """;

    public static double[] getEmbedding(String word)  {
        HttpClient client = HttpClient.newHttpClient();
        String json = String.format("{\"model\": \"%s\", \"prompt\": \"%s\"}", MODEL, word);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = null;
        JsonNode root = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper mapper = new ObjectMapper();
            root = mapper.readTree(response.body());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        JsonNode emb = root.get("embedding");
        double[] vec = new double[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            vec[i] = emb.get(i).asDouble();
        }
        return vec;
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
