package com.prayer_chat.chatbot.dto;

import com.prayer_chat.chatbot.model.User;

import java.util.List;
import java.util.Set;

/**
 * API response for authenticated user profile ({@code GET /api/auth/me}).
 */
public record UserResponse(
    Long id,
    String username,
    String email,
    Set<String> roles,
    String authProvider,
    String picture
) {
    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        String picture = user.getProfileImageUrl();
        if (picture != null && picture.isBlank()) {
            picture = null;
        }
        Set<String> roles = user.getRoles() != null ? Set.copyOf(user.getRoles()) : Set.of();
        String provider = user.getAuthProvider() != null ? user.getAuthProvider().name() : null;
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            roles,
            provider,
            picture
        );
    }
}
