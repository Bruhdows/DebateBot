package com.bruhdows.debatebot.debate;

import com.bruhdows.debatebot.DebateBot;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

public class DebateListener extends ListenerAdapter {
    private final DebateManager debateManager;

    public DebateListener(DebateManager debateManager) {
        this.debateManager = debateManager;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("debate") && event.getChannel().getIdLong() == DebateBot.getConfig().getDebateChannelId()) {
            event.deferReply().queue();
            OptionMapping topic = event.getOption("topic");
            if (topic == null) {
                event.reply("Invalid topic!").queue();
                return;
            }
            String topicString = topic.getAsString();
            debateManager.startDebate(event, topicString);
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        debateManager.handleMessage(event);
    }
}
