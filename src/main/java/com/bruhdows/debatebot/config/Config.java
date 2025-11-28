package com.bruhdows.debatebot.config;

import lombok.Data;

@Data
public class Config {

    private String token = "";
    private String groqApiKey = "";
    private String groqModel = "llama-3.1-8b-instant";
    private long debateChannelId = 0L;
    private int maxTokens = 300;

}
