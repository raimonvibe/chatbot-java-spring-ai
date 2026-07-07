package com.prayer_chat.chatbot.exception;

/**
 * Thrown when the caller is authenticated but lacks permission for the requested action.
 * Mapped to 403 by {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
