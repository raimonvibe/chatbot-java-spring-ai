package com.prayer_chat.chatbot.exception;

/**
 * Thrown when a requested resource (chatbot, conversation, subscription, ...) does not exist
 * or is not visible to the current user. Mapped to 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forResource(String resource, Object id) {
        return new ResourceNotFoundException(resource + " not found: " + id);
    }
}
