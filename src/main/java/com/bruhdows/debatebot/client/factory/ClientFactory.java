package com.bruhdows.debatebot.client.factory;

import com.bruhdows.debatebot.client.ApiType;
import com.bruhdows.debatebot.client.LanguageModelClient;
import com.bruhdows.debatebot.client.impl.GroqClient;
import com.bruhdows.debatebot.client.impl.OllamaClient;

public class ClientFactory {
    public static LanguageModelClient createClient(ApiType apiType, String apiKey, String model, String baseUrl) {
        return switch (apiType) {
            case GROQ -> new GroqClient(apiKey, model);
            case OLLAMA -> new OllamaClient(apiKey, model, baseUrl);
        };
    }
}
