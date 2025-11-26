package llm;

import dto.ConflictResolutionResult;

public interface LLMProvider {

    ConflictResolutionResult sendPromptAndReceiveResponse(String prompt, String context);

    String getModel();
}
