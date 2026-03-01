package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Custom OAuth2 user service to handle Google login
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception ex) {
            logger.error("Error processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException("Error processing OAuth2 user");
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // Extract user info from OAuth2User
        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String pictureUrl = oAuth2User.getAttribute("picture");

        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByGoogleId(googleId);
        User user;

        if (userOptional.isPresent()) {
            // Existing user - update last login and profile image
            user = userOptional.get();
            user.setLastLogin(LocalDateTime.now());
            if (pictureUrl != null && !pictureUrl.isBlank()) {
                user.setProfileImageUrl(pictureUrl.length() > 512 ? pictureUrl.substring(0, 512) : pictureUrl);
            }
            userRepository.save(user);
            logger.info("Existing user logged in via Google: {}", LogSanitizer.sanitize(email));
        } else {
            // New user - check if email already exists
            Optional<User> existingEmailUser = userRepository.findByEmail(email);
            if (existingEmailUser.isPresent()) {
                // Link Google account to existing user
                user = existingEmailUser.get();
                user.setGoogleId(googleId);
                user.setAuthProvider(User.AuthProvider.GOOGLE);
                user.setLastLogin(LocalDateTime.now());
                if (pictureUrl != null && !pictureUrl.isBlank()) {
                    user.setProfileImageUrl(pictureUrl.length() > 512 ? pictureUrl.substring(0, 512) : pictureUrl);
                }
                userRepository.save(user);
                logger.info("Linked Google account to existing user: {}", LogSanitizer.sanitize(email));
            } else {
                // Create new user
                user = new User();
                user.setUsername(email);
                user.setEmail(email);
                user.setGoogleId(googleId);
                user.setAuthProvider(User.AuthProvider.GOOGLE);
                user.setEnabled(true);
                user.setLastLogin(LocalDateTime.now());
                user.getRoles().add("USER");
                if (pictureUrl != null && !pictureUrl.isBlank()) {
                    user.setProfileImageUrl(pictureUrl.length() > 512 ? pictureUrl.substring(0, 512) : pictureUrl);
                }
                userRepository.save(user);
                logger.info("Created new user via Google OAuth: {}", LogSanitizer.sanitize(email));
            }
        }

        return new CustomOAuth2User(oAuth2User, user);
    }

    /**
     * Process OAuth2 user directly from OAuth2User attributes
     * Used by hybrid OAuth flow where we already have the user info
     */
    public User processOAuth2UserDirectly(OAuth2User oAuth2User, String googleId, String email, String name) {
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Email not found from OAuth2 provider");
        }

        String pictureUrl = oAuth2User != null ? oAuth2User.getAttribute("picture") : null;
        Optional<User> userOptional = userRepository.findByGoogleId(googleId);
        User user;

        if (userOptional.isPresent()) {
            // Existing user - update last login and profile image
            user = userOptional.get();
            user.setLastLogin(LocalDateTime.now());
            if (pictureUrl != null && !pictureUrl.isBlank()) {
                user.setProfileImageUrl(pictureUrl.length() > 512 ? pictureUrl.substring(0, 512) : pictureUrl);
            }
            userRepository.save(user);
            logger.info("Existing user logged in via Google: {}", LogSanitizer.sanitize(email));
        } else {
            // New user - check if email already exists
            Optional<User> existingEmailUser = userRepository.findByEmail(email);
            if (existingEmailUser.isPresent()) {
                // Link Google account to existing user
                user = existingEmailUser.get();
                user.setGoogleId(googleId);
                user.setAuthProvider(User.AuthProvider.GOOGLE);
                user.setLastLogin(LocalDateTime.now());
                if (pictureUrl != null && !pictureUrl.isBlank()) {
                    user.setProfileImageUrl(pictureUrl.length() > 512 ? pictureUrl.substring(0, 512) : pictureUrl);
                }
                userRepository.save(user);
                logger.info("Linked Google account to existing user: {}", LogSanitizer.sanitize(email));
            } else {
                // Create new user
                user = new User();
                user.setUsername(email);
                user.setEmail(email);
                user.setGoogleId(googleId);
                user.setAuthProvider(User.AuthProvider.GOOGLE);
                user.setEnabled(true);
                user.setLastLogin(LocalDateTime.now());
                user.getRoles().add("USER");
                if (pictureUrl != null && !pictureUrl.isBlank()) {
                    user.setProfileImageUrl(pictureUrl.length() > 512 ? pictureUrl.substring(0, 512) : pictureUrl);
                }
                userRepository.save(user);
                logger.info("Created new user via Google OAuth: {}", LogSanitizer.sanitize(email));
            }
        }

        return user;
    }
}
