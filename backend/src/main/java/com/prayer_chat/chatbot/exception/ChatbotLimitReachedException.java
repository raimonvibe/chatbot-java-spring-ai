package com.prayer_chat.chatbot.exception;

/**
 * Thrown when a user attempts to create a chatbot but has reached their quota.
 * Used for consistent 403 handling in the controller.
 */
public class ChatbotLimitReachedException extends RuntimeException {

    private final int maxAllowed;
    private final long currentCount;

    public ChatbotLimitReachedException(int maxAllowed, long currentCount) {
        super("Chatbot limit reached: " + currentCount + " / " + maxAllowed);
        this.maxAllowed = maxAllowed;
        this.currentCount = currentCount;
    }

    public int getMaxAllowed() {
        return maxAllowed;
    }

    public long getCurrentCount() {
        return currentCount;
    }
}
