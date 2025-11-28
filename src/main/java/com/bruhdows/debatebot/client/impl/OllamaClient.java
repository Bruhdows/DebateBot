package com.bruhdows.debatebot.client.impl;

import com.bruhdows.debatebot.client.LanguageModelClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OllamaClient implements LanguageModelClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String model;
    private final String baseUrl;

    public OllamaClient(String model, String baseUrl) {
        this.model = model;
        String url = baseUrl.replace("localhost", "127.0.0.1");
        this.baseUrl = url.endsWith("/") ? url : url + "/";
        logger.info("OllamaClient initialized: {} -> {}", this.baseUrl, model);
    }

    @Override
    public void streamResponse(String systemPrompt, String userPrompt,
                               Consumer<String> onToken, Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject requestBody = getJsonObject(systemPrompt, userPrompt);

                RequestBody body = RequestBody.create(
                        requestBody.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(baseUrl + "api/generate")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body().string();
                        logger.error("Ollama error: {} - {}", response.code(), errorBody);
                        onComplete.run();
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            try {
                                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                                if (json.has("response")) {
                                    String token = json.get("response").getAsString();
                                    if (token != null && !token.isEmpty()) {
                                        onToken.accept(token);
                                    }
                                }
                                if (json.has("done") && json.get("done").getAsBoolean()) {
                                    break;
                                }
                            } catch (Exception ex) {
                                logger.warn("Failed to parse line: {}", line);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Ollama stream error: {}", e.getMessage(), e);
            } finally {
                onComplete.run();
            }
        });
    }

    @NotNull
    private JsonObject getJsonObject(String systemPrompt, String userPrompt) {
        String fullPrompt = systemPrompt + "\n\n" + userPrompt;

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", fullPrompt);
        requestBody.addProperty("stream", true);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.7);
        options.addProperty("top_p", 0.9);
        options.addProperty("num_predict", 300);
        requestBody.add("options", options);
        return requestBody;
    }
}
