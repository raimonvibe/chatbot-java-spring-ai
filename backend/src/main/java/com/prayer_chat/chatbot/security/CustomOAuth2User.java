package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom OAuth2User that wraps both OAuth2User and our User entity
 * Implements Serializable for Spring Session persistence
 */
public class CustomOAuth2User implements OAuth2User, Serializable {

    private static final long serialVersionUID = 1L;

    private transient OAuth2User oauth2User;
    private final User user;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(OAuth2User oauth2User, User user) {
        this.oauth2User = oauth2User;
        this.user = user;
        // Store attributes in a serializable map
        this.attributes = new HashMap<>(oauth2User.getAttributes());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getAuthorities();
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    public String getEmail() {
        return (String) attributes.get("email");
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }
}
