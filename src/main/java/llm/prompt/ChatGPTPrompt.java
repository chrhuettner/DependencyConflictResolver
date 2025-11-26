package llm.prompt;

public class ChatGPTPrompt {
    public String model;
    public ChatGPTMessage[] messages;

    public ChatGPTPrompt(String model, ChatGPTMessage[] messages) {
        this.model = model;
        this.messages = messages;
    }
}
