package llm.prompt;

public class ClaudePrompt {
    public String model;
    public int max_tokens;
    public double temperature;
    public ClaudeMessage[] messages;

    public ClaudePrompt(String model, int max_tokens, double temperature, ClaudeMessage[] messages) {
        this.model = model;
        this.max_tokens = max_tokens;
        this.temperature = temperature;
        this.messages = messages;
    }
}
