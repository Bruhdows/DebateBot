package com.bruhdows.debatebot.debate;

import com.bruhdows.debatebot.client.LanguageModelClient;
import com.bruhdows.debatebot.config.Config;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DebateManager {
    private final Map<Long, DebateSession> sessions = new ConcurrentHashMap<>();
    private final LanguageModelClient client;
    private final Config config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public DebateManager(LanguageModelClient client, Config config) {
        this.client = client;
        this.config = config;
        cleanupOldSessions();
    }

    public void startDebate(SlashCommandInteractionEvent event, String topic, String openingPrompt, String replyPrompt) {
        MessageChannel channel = event.getChannel();
        if (!(channel instanceof TextChannel textChannel)) {
            channel.sendMessage("This command can only be used in a text channel.").queue();
            return;
        }

        String finalOpeningPrompt = openingPrompt != null && !openingPrompt.isEmpty()
                ? openingPrompt : config.getOpeningSystemPrompt();
        String finalReplyPrompt = replyPrompt != null && !replyPrompt.isEmpty()
                ? replyPrompt : config.getReplySystemPrompt();

        textChannel.createThreadChannel("Debate: " + topic)
                .queue(thread -> {
                    DebateSession session = new DebateSession();
                    session.setThreadId(thread.getIdLong());
                    session.setTopic(topic);
                    session.setLeaderUserId(event.getUser().getId());
                    session.setOpeningPrompt(finalOpeningPrompt);
                    session.setReplyPrompt(finalReplyPrompt);
                    sessions.put(thread.getIdLong(), session);

                    thread.sendMessage("**Topic: " + topic + "**\n\nAI starts:").queue(msg -> {
                        session.setCurrentReplyMessage(msg);
                        generateFirstResponse(session);
                        thread.sendTyping().queue();
                    });
                });
    }

    private void generateFirstResponse(DebateSession session) {
        if (!session.tryLock()) return;
        if (session.isClosed()) {
            session.unlock();
            return;
        }

        String systemPrompt = session.getOpeningPrompt().formatted(session.getTopic());

        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        StringBuilder fullResponse = new StringBuilder();

        client.streamResponse(systemPrompt, "Start debate.",
                token -> {
                    fullResponse.append(token);
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    if (elapsed > 800) {
                        session.getCurrentReplyMessage()
                                .editMessage(fullResponse.toString().trim()).queue();
                        startTime.set(System.currentTimeMillis());
                    }
                },
                () -> {
                    String finalText = fullResponse.toString().trim();
                    if (!finalText.isEmpty()) {
                        session.getCurrentReplyMessage()
                                .editMessage(finalText + "\n\n**Your turn!**").queue();
                        session.addBotMessage(finalText);
                    }
                    session.unlock();
                });
    }

    public void handleMessage(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        MessageChannel channel = event.getChannel();
        if (!(channel instanceof ThreadChannel thread)) return;

        DebateSession session = sessions.get(thread.getIdLong());
        if (session == null || !session.tryLock()) {
            channel.sendMessage("Still thinking...").queue();
            return;
        }

        if (session.isClosed()) {
            session.unlock();
            return;
        }

        try {
            String content = event.getMessage().getContentRaw();
            session.addUserMessage(content);

            if (containsConcede(content)) {
                thread.sendMessage("**Human concedes! AI wins. 🏆**").queue();
                closeSession(session, thread);
                return;
            }

            replyInThread(session, thread);
        } catch (Exception e) {
            session.unlock();
        }
    }

    private void replyInThread(DebateSession session, ThreadChannel thread) {
        if (session.isClosed()) return;

        thread.sendTyping().queue();
        Message msg = thread.sendMessage("🤔 AI thinking...").complete();
        session.setCurrentReplyMessage(msg);

        String context = session.getContext(16);
        String systemPrompt = session.getReplyPrompt()
                .formatted(session.getTopic(), context);

        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        StringBuilder fullResponse = new StringBuilder();

        client.streamResponse(systemPrompt, session.getHistory().getLast(),
                token -> {
                    fullResponse.append(token);
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    if (elapsed > 800) {
                        msg.editMessage(fullResponse.toString().trim()).queue();
                        startTime.set(System.currentTimeMillis());
                    }
                },
                () -> {
                    String finalText = fullResponse.toString().trim();
                    if (!finalText.isEmpty()) {
                        msg.editMessage(finalText).queue();
                        session.addBotMessage(finalText);

                        if (containsBotConcede(finalText)) {
                            thread.sendMessage("**AI concedes! Human wins. 🎉**").queue();
                            closeSession(session, thread);
                        }
                    }
                    session.unlock();
                });
    }

    private void closeSession(DebateSession session, ThreadChannel thread) {
        session.setClosed(true);
        thread.getManager().setLocked(true).queue();
        thread.getManager().setArchived(true).queue();
    }

    private boolean containsConcede(String message) {
        String lower = message.toLowerCase();
        return lower.contains("concede") || lower.contains("you win") ||
                lower.contains("gg") || lower.contains("good game");
    }

    private boolean containsBotConcede(String message) {
        String lower = message.toLowerCase();
        return lower.contains("i concede") || lower.contains("you win") ||
                lower.contains("i lose");
    }

    private void cleanupOldSessions() {
        scheduler.scheduleAtFixedRate(() -> sessions.entrySet().removeIf(entry -> {
            DebateSession session = entry.getValue();
            return ChronoUnit.HOURS.between(session.getLastActivity(), Instant.now()) > 1;
        }), 0, 5, TimeUnit.MINUTES);
    }
}
