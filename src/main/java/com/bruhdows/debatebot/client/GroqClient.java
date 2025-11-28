package com.bruhdows.debatebot.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GroqClient {
    private static final Logger logger = LoggerFactory.getLogger(GroqClient.class);
    private static final Gson gson = new Gson();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String apiKey;
    private final String model;

    public GroqClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public void streamResponse(String systemPrompt, String userPrompt,
                               Consumer<String> onToken, Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            try {
                String fullPrompt = systemPrompt + "\n\nUSER: " + userPrompt;

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);

                JsonObject message = new JsonObject();
                message.addProperty("role", "user");
                message.addProperty("content", fullPrompt);

                requestBody.add("messages", gson.toJsonTree(List.of(message)));
                requestBody.addProperty("max_tokens", 300);
                requestBody.addProperty("stream", true);
                requestBody.addProperty("temperature", 0.7);

                RequestBody body = RequestBody.create(
                        gson.toJson(requestBody),
                        MediaType.get("application/json")
                );

                Request request = new Request.Builder()
                        .url("https://api.groq.com/openai/v1/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        logger.error("Groq API error: {}", response.body().string());
                        onComplete.run();
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) continue;

                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) break;

                            try {
                                JsonElement chunk = JsonParser.parseString(data);
                                if (!chunk.isJsonObject()) continue;

                                JsonObject chunkObj = chunk.getAsJsonObject();
                                if (!chunkObj.has("choices")) continue;

                                JsonArray choicesArray = chunkObj.getAsJsonArray("choices");
                                if (choicesArray.isEmpty()) continue;

                                JsonObject choice = choicesArray.get(0).getAsJsonObject();
                                if (!choice.has("delta")) continue;

                                JsonObject delta = choice.getAsJsonObject("delta");
                                if (!delta.has("content")) continue;

                                String token = delta.get("content").getAsString();
                                if (token != null && !token.isEmpty()) {
                                    onToken.accept(token);
                                }
                            } catch (Exception ex) {
                                logger.warn("Failed parsing chunk: {}", ex.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Stream error", e);
            } finally {
                onComplete.run();
            }
        });
    }
}
