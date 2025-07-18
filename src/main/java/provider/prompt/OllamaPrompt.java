package provider.prompt;

public class OllamaPrompt {
    public String model;
    public OllamaMessage[] messages;
    public boolean stream;
    public OllamaOptions options;
    public int keep_alive = 0;

    public OllamaPrompt(String model, OllamaMessage[] messages, boolean stream, OllamaOptions options) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
        this.options = options;
    }
}
