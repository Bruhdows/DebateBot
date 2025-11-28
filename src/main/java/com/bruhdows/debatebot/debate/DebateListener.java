package com.bruhdows.debatebot.debate;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DebateListener extends ListenerAdapter {
    private final DebateManager debateManager;

    public DebateListener(DebateManager debateManager) {
        this.debateManager = debateManager;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("debate")) {
            String topic = Objects.requireNonNull(event.getOption("topic")).getAsString();
            String openingPrompt = event.getOption("opening_prompt") != null ?
                    Objects.requireNonNull(event.getOption("opening_prompt")).getAsString() : null;
            String replyPrompt = event.getOption("reply_prompt") != null ?
                    Objects.requireNonNull(event.getOption("reply_prompt")).getAsString() : null;

            debateManager.startDebate(event, topic, openingPrompt, replyPrompt);
            event.reply("Starting debate thread...").setEphemeral(true).queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        debateManager.handleMessage(event);
    }
}