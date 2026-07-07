package com.prayer_chat.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standard error body for all REST endpoints: {@code {"error": "...", "details": {...}}}.
 * The {@code error} key matches what the frontend already expects from ad-hoc
 * {@code Map.of("error", ...)} responses, so adopting this DTO is backward compatible.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String error, Map<String, ?> details) {

    public static ApiErrorResponse of(String error) {
        return new ApiErrorResponse(error, null);
    }

    public static ApiErrorResponse of(String error, Map<String, ?> details) {
        return new ApiErrorResponse(error, details);
    }
}
