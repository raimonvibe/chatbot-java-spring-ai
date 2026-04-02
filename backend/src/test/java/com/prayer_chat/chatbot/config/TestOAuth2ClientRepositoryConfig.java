package com.prayer_chat.chatbot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Provides a servlet {@link ClientRegistrationRepository} bean in test profile when the
 * OAuth2 client auto-configuration does not create one, or under a different name so
 * it does not clash with reactive auto-config. Required by {@link TestSecurityConfig} for
 * .oauth2Login() to work.
 */
@Configuration
@Profile("test")
public class TestOAuth2ClientRepositoryConfig {

    /** Bean name to avoid clash with reactive auto-config's clientRegistrationRepository. */
    public static final String BEAN_NAME = "testServletClientRegistrationRepository";

    @Bean(BEAN_NAME)
    @Primary
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository testServletClientRegistrationRepository() {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
            .clientId("test-client-id")
            .clientSecret("test-client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://www.googleapis.com/oauth2/v4/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName("sub")
            .clientName("Google")
            .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}

