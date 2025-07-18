package core;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv dotenv = Dotenv.load();

    public static String getOpenAiApiKey() {
        return dotenv.get("OPENAI_API_KEY");
    }

    public static String getClaudeKey() {
        return dotenv.get("CLAUDE_API_KEY");
    }
}