package provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class BaseAIProvider implements AIProvider {
    protected final OkHttpClient client;
    protected final ObjectMapper mapper;

    public BaseAIProvider() {
        client = new OkHttpClient.Builder().connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build();
        mapper = new ObjectMapper();
    }

    @Override
    public String sendPromptAndReceiveResponse(String prompt, String context) {
        String json;
        try {
            json = mapper.writeValueAsString(getPromptWithContext(prompt, context));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        Request request = buildRequestFromJsonString(json);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response + ": " + response.body().string());
            }
            if (response.body() == null) {
                throw new IOException("Empty response");
            }
            return extractContentFromResponse(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Request buildRequestFromJsonString(String json) {
        RequestBody body = RequestBody.create(
                json, MediaType.parse("application/json"));

       Request.Builder baseRequest = new Request.Builder()
                .url(getUrl())
                .post(body);

       return addHeadersToBuilder(baseRequest).build();
    }

    public abstract Object getPromptWithContext(String prompt, String context);

    public abstract String getUrl();

    public abstract String extractContentFromResponse(String response);

    public abstract Request.Builder addHeadersToBuilder(Request.Builder builder);
}
