package provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import core.Config;
import okhttp3.Request;
import provider.prompt.ChatGPTMessage;
import provider.prompt.ChatGPTPrompt;

public class ChatGPTProvider extends BaseAIProvider {
    private final String model;

    public ChatGPTProvider() {
        model = "gpt-4o-mini";
    }

    public ChatGPTProvider(String model) {
        this.model = model;
    }

    @Override
    public Object getPromptWithContext(String prompt, String context) {
        ChatGPTMessage[] messages = new ChatGPTMessage[2];
        messages[0] = new ChatGPTMessage("system", context);
        messages[1] = new ChatGPTMessage("user", prompt);
        return new ChatGPTPrompt(model, messages);
    }

    @Override
    public String getUrl() {
        return "https://api.openai.com/v1/chat/completions";
    }

    public String getApiKey() {
        return Config.getOpenAiApiKey();
    }

    @Override
    public String extractContentFromResponse(String response) {
        try {
            JsonNode jsonNode = mapper.readTree(response);
            return jsonNode.get("choices").get(0).get("message").get("content").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Request.Builder addHeadersToBuilder(Request.Builder builder) {
        return builder.header("Authorization", "Bearer " + getApiKey());
    }

    @Override
    public String getModel() {
        return model;
    }
}
