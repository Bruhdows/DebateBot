package com.bruhdows.debatebot.debate;

import lombok.Data;
import net.dv8tion.jda.api.entities.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class DebateSession {

    private long threadId;
    private String topic;
    private List<String> history = new ArrayList<>();
    private boolean isLocked = false;
    private Message currentReplyMessage;
    private boolean isBotTurn = true;
    private Instant lastActivity = Instant.now();
    private String leaderUserId;
    private int botWins = 0;
    private int userWins = 0;

    public synchronized boolean tryLock() {
        if (isLocked) return false;
        isLocked = true;
        return true;
    }

    public synchronized void unlock() {
        isLocked = false;
    }

    public void addUserMessage(String content) {
        history.add("USER: " + content);
        lastActivity = Instant.now();
    }

    public void addBotMessage(String content) {
        history.add("BOT: " + content);
        lastActivity = Instant.now();
        isBotTurn = false;
    }

    public String getContext(int maxMessages) {
        return String.join("\n", history.subList(
            Math.max(0, history.size() - maxMessages), history.size()));
    }
}
