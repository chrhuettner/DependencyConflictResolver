package llm.prompt;

public class ClaudeMessage {
    public String role;
    public ClaudeContent content;

    public ClaudeMessage(String role, ClaudeContent content) {
        this.role = role;
        this.content = content;
    }
}
