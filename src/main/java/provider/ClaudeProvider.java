package provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import core.Config;
import okhttp3.Request;
import provider.prompt.*;

public class ClaudeProvider extends BaseAIProvider {
    private final String model;

    public ClaudeProvider() {
        model = "claude-opus-3";
    }

    public ClaudeProvider(String model) {
        this.model = model;
    }

    @Override
    public Object getPromptWithContext(String prompt, String context) {
        ClaudeMessage[] messages = new ClaudeMessage[2];
        messages[0] = new ClaudeMessage("system", new ClaudeContent("text", context));
        messages[1] = new ClaudeMessage("user", new ClaudeContent("text", prompt));
        return new ClaudePrompt(model, 1000, 0.8, messages);
    }

    @Override
    public String getUrl() {
        return "https://api.anthropic.com/v1/messages";
    }

    public String getApiKey() {
        return Config.getClaudeKey();
    }

    @Override
    public String extractContentFromResponse(String response) {
        //try {
            //JsonNode jsonNode = mapper.readTree(response);
            return response;//jsonNode.get("choices").get(0).get("message").get("content").asText();
       // } catch (JsonProcessingException e) {
        //    throw new RuntimeException(e);
       // }
    }

    @Override
    public Request.Builder addHeadersToBuilder(Request.Builder builder) {
        return builder.header("x-api-key", getApiKey()).header("anthropic-version", "2023-06-01");
    }

    @Override
    public String getModel() {
        return model;
    }
}
