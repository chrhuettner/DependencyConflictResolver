package provider;

import core.ConflictResolutionResult;

public interface AIProvider {

    ConflictResolutionResult sendPromptAndReceiveResponse(String prompt, String context);

    String getModel();
}
