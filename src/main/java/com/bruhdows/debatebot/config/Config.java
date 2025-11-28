package com.bruhdows.debatebot.config;

import com.bruhdows.debatebot.client.ApiType;
import lombok.Data;

@Data
public class Config {
    private String token = "";

    private ApiType apiType = ApiType.GROQ;
    private String apiKey = "";
    private String apiModel = "llama-3.1-8b-instant";
    private String apiBaseUrl = "";

    private long debateChannelId = 0L;
    private int maxTokens = 300;

    private String openingSystemPrompt = """
            Topic: %s. Make **BOLD** 1 sentence opening. End with challenge. SHORT.
            To concede use "you win" or "i lose" or "i concede"
            """;

    private String replySystemPrompt = """
            Topic: %s
            Recent: %s
            Reply with 1 sentence. Aggressive. End with question. SHORT.
            To concede use "you win" or "i lose" or "i concede"
            """;
}
