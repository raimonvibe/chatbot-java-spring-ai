package com.tjanabot.chatbot.helpers;

import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.security.CustomOAuth2User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for creating test authentication objects
 * Used in integration tests to simulate authenticated users
 */
public class TestAuthenticationHelper {

    /**
     * Create an Authentication object with CustomOAuth2User for MockMvc tests
     * This is needed because controllers use @AuthenticationPrincipal CustomOAuth2User
     */
    public static Authentication createCustomOAuth2UserAuthentication(User user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "test_" + user.getId());
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getUsername());

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "email"
        );

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, user);
        return new UsernamePasswordAuthenticationToken(
            customOAuth2User,
            null,
            customOAuth2User.getAuthorities()
        );
    }
}

