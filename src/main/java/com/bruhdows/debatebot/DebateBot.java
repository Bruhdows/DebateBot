package com.bruhdows.debatebot;

import com.bruhdows.debatebot.client.GroqClient;
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

    private static final Logger logger = LoggerFactory.getLogger(DebateBot.class);
    @Getter
    private static Config config;
    private static JDA jda;

    // Add to existing DebateBot.java
    static DebateManager debateManager;

    static void main() {
        try {
            config = ConfigManager.register(Config.class, "config.json");

            if (!isValidToken(config.getToken()) || config.getGroqApiKey().isEmpty()) {
                logger.error("Missing token or Groq API key in config.json");
                System.exit(1);
                return;
            }

            logger.info("Starting DebateBot...");

            GroqClient groqClient = new GroqClient(config.getGroqApiKey(), config.getGroqModel());
            debateManager = new DebateManager(groqClient);

            jda = JDABuilder.createDefault(config.getToken())
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .setActivity(Activity.of(Activity.ActivityType.WATCHING, "debates"))
                    .addEventListeners(new DebateListener(debateManager))
                    .build()
                    .awaitReady();

            jda.updateCommands().addCommands(
                    Commands.slash("debate", "Start a debate")
                            .addOption(OptionType.STRING, "topic", "Debate topic", true)
            ).queue();

            logger.info("DebateBot ready in {} guilds!", jda.getGuildCache().size());

        } catch (Exception e) {
            logger.error("Failed to start", e);
            System.exit(1);
        }
    }

    private static boolean isValidToken(String token) {
        return token != null && !token.trim().isEmpty() &&
                token.startsWith("MT") && token.length() > 50;
    }

    public static JDA getJDA() {
        return jda;
    }
}
