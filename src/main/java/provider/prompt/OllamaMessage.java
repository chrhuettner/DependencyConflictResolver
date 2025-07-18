package provider.prompt;

public class OllamaMessage {
    public String role;
    public String content;

    public OllamaMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
