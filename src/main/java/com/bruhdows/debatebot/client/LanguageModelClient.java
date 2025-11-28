package com.bruhdows.debatebot.client;

import java.util.function.Consumer;

public interface LanguageModelClient {
    void streamResponse(String systemPrompt, String userPrompt, Consumer<String> onToken, Runnable onComplete);
}
