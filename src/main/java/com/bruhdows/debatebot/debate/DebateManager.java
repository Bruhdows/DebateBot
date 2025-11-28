package com.bruhdows.debatebot.debate;

import com.bruhdows.debatebot.client.GroqClient;
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
    private final GroqClient groqClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public DebateManager(GroqClient groqClient) {
        this.groqClient = groqClient;
        cleanupOldSessions();
    }

    public void startDebate(SlashCommandInteractionEvent event, String topic) {
        MessageChannel channel = event.getChannel();
        if (!(channel instanceof TextChannel textChannel)) {
            channel.sendMessage("This command can only be used in a text channel.").queue();
            return;
        }

        textChannel.sendTyping().queue();

        textChannel.createThreadChannel("Debate: " + topic)
                .queue(thread -> {
                    DebateSession session = new DebateSession();
                    session.setThreadId(thread.getIdLong());
                    session.setTopic(topic);
                    session.setLeaderUserId(event.getUser().getId());
                    sessions.put(thread.getIdLong(), session);

                    thread.sendMessage("**Topic: `" + topic + "`**\n\n**AI starts:**").queue(msg -> {
                        session.setCurrentReplyMessage(msg);
                        generateFirstResponse(session, thread);
                    });
                });
    }

    private void generateFirstResponse(DebateSession session, ThreadChannel thread) {
        if (!session.tryLock()) return;

        String systemPrompt = """
            You are a sharp, aggressive debater starting the debate on: %s
            Make a BOLD opening statement (2-3 sentences).
            End with a direct challenge to the human.
            Keep it SHORT and punchy. No fluff.
            """.formatted(session.getTopic());

        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        StringBuilder fullResponse = new StringBuilder();

        groqClient.streamResponse(systemPrompt, "Start the debate.",
                token -> {
                    fullResponse.append(token);
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    if (elapsed > 800) {
                        session.getCurrentReplyMessage()
                                .editMessage("**AI: " + fullResponse.toString().trim() + "**").queue();
                        startTime.set(System.currentTimeMillis());
                    }
                },
                () -> {
                    String finalText = fullResponse.toString().trim();
                    if (!finalText.isEmpty()) {
                        session.getCurrentReplyMessage()
                                .editMessage("**AI: " + finalText + "**\n\n**Your turn!**").queue();
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

        try {
            String content = event.getMessage().getContentRaw();
            session.addUserMessage(content);

            if (containsConcede(content)) {
                thread.sendMessage("Human concedes! AI wins this round.").queue();
                session.setBotWins(session.getBotWins() + 1);
                session.unlock();
                return;
            }

            replyInThread(session, thread);
        } catch (Exception e) {
            session.unlock();
        }
    }

    private void replyInThread(DebateSession session, ThreadChannel thread) {
        thread.sendTyping().queue();
        Message msg = thread.sendMessage("AI thinking...").complete();
        session.setCurrentReplyMessage(msg);

        String systemPrompt = """
            Topic: %s
            Recent debate:
            %s
            
            Your sharp reply (2-4 sentences max). Be aggressive, make bold claims.
            End with direct question/challenge. Keep SHORT.
            """.formatted(session.getTopic(), session.getContext(8));

        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        StringBuilder fullResponse = new StringBuilder();

        groqClient.streamResponse(systemPrompt, session.getHistory().get(session.getHistory().size() - 1),
                token -> {
                    fullResponse.append(token);
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    if (elapsed > 800) {
                        msg.editMessage("AI: " + fullResponse.toString().trim()).queue();
                        startTime.set(System.currentTimeMillis());
                    }
                },
                () -> {
                    String finalText = fullResponse.toString().trim();
                    if (!finalText.isEmpty()) {
                        msg.editMessage("AI: " + finalText).queue();
                        session.addBotMessage(finalText);
                    }
                    session.unlock();
                });
    }

    private boolean containsConcede(String message) {
        String lower = message.toLowerCase();
        return lower.contains("concede") ||
                lower.contains("you win") ||
                lower.contains("gg") ||
                lower.contains("good game");
    }

    private void cleanupOldSessions() {
        scheduler.scheduleAtFixedRate(() -> {
            sessions.entrySet().removeIf(entry -> {
                DebateSession session = entry.getValue();
                return ChronoUnit.HOURS.between(session.getLastActivity(), Instant.now()) > 1;
            });
        }, 0, 5, TimeUnit.MINUTES);
    }
}
