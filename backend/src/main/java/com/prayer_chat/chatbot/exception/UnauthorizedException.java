package com.prayer_chat.chatbot.exception;

/**
 * Thrown when an endpoint requires authentication but no principal is present.
 * Mapped to 401 by {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Authentication required");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
