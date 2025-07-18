package provider.prompt;

public class ChatGPTMessage {
    public String role;
    public String content;

    public ChatGPTMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
