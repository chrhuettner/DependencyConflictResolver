package provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;
import provider.prompt.OllamaMessage;
import provider.prompt.OllamaOptions;
import provider.prompt.OllamaPrompt;

public class OllamaProvider extends BaseAIProvider {
    private final String model;

    public OllamaProvider() {
        model = "deepseek-r1:7b";
    }

    public OllamaProvider(String model) {
        this.model = model;
    }

    @Override
    public Object getPromptWithContext(String prompt, String context) {
        OllamaMessage[] messages = new OllamaMessage[2];
        messages[0] = new OllamaMessage("system", context);
        messages[1] = new OllamaMessage("user", prompt);
        return new OllamaPrompt(model, messages, false, new OllamaOptions(1));
    }

    @Override
    public String getUrl() {
        return "http://localhost:11434/api/chat";
    }

    @Override
    public String extractContentFromResponse(String response) {
        try {
            JsonNode jsonNode = mapper.readTree(response);
            return jsonNode.get("message").get("content").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Request.Builder addHeadersToBuilder(Request.Builder builder) {
        // No further headers needed for local Ollama
        return builder;
    }

    @Override
    public String getModel() {
        return model;
    }
}
