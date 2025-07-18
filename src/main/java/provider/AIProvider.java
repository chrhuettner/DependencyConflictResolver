package provider;

public interface AIProvider {

    String sendPromptAndReceiveResponse(String prompt, String context);

    String getModel();
}
