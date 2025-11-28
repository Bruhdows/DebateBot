package com.bruhdows.debatebot;

import com.bruhdows.debatebot.client.ApiType;
import com.bruhdows.debatebot.client.LanguageModelClient;
import com.bruhdows.debatebot.client.factory.ClientFactory;
import com.bruhdows.debatebot.config.Config;
import com.bruhdows.debatebot.config.ConfigManager;
import com.bruhdows.debatebot.debate.DebateListener;
import com.bruhdows.debatebot.debate.DebateManager;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebateBot {

    @Getter
    private static Config config;
    private static final Logger LOGGER = LoggerFactory.getLogger(DebateBot.class);

    public static void main(String[] args) {
        try {
            config = ConfigManager.register(Config.class, "config.json");

            if (!isValidToken(config.getToken()) || (config.getApiType() == ApiType.GROQ && config.getApiKey().isEmpty())) {
                LOGGER.error("Missing Discord token or Groq API key (required for Groq). For Ollama, API key can be empty.");
                System.exit(1);
                return;
            }

            LOGGER.info("Starting DebateBot with {} API...", config.getApiType());

            LanguageModelClient client = ClientFactory.createClient(
                    config.getApiType(),
                    config.getApiKey(),
                    config.getApiModel(),
                    config.getApiBaseUrl()
            );

            DebateManager debateManager = new DebateManager(client, config);

            JDA jda = JDABuilder.createDefault(config.getToken())
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .setActivity(Activity.of(Activity.ActivityType.WATCHING, "debates"))
                    .addEventListeners(new DebateListener(debateManager))
                    .build()
                    .awaitReady();

            jda.updateCommands().addCommands(
                    Commands.slash("debate", "Start a debate")
                            .addOption(OptionType.STRING, "topic", "Debate topic", true)
                            .addOption(OptionType.STRING, "opening_prompt", "Custom opening system prompt (optional)", false)
                            .addOption(OptionType.STRING, "reply_prompt", "Custom reply system prompt (optional)", false)
            ).queue();

            LOGGER.info("DebateBot ready in {} guilds!", jda.getGuildCache().size());

        } catch (Exception e) {
            LOGGER.error("Failed to start", e);
            System.exit(1);
        }
    }

    private static boolean isValidToken(String token) {
        return token != null && !token.trim().isEmpty() &&
                token.startsWith("MT") && token.length() > 50;
    }
}
