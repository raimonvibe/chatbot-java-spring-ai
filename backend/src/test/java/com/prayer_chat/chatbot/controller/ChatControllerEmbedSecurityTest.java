package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Security tests for the public embed config endpoint GET /api/chat/embed/{id}.
 * Ensures no sensitive data is exposed and XSS vectors (name, description, brandingConfig) are sanitized.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController embed config security tests")
class ChatControllerEmbedSecurityTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private AiChatbotService aiChatbotService;

    @Mock
    private RateLimitingService rateLimitingService;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(aiChatbotService, chatbotRepository, rateLimitingService);
    }

    @Test
    @DisplayName("Embed response must not contain tokens, API keys, or passwords")
    void embedResponseMustNotContainSensitiveData() {
        Chatbot bot = new Chatbot();
        bot.setId(1L);
        bot.setName("Safe Bot");
        bot.setDescription("A bot");
        bot.setIsActive(true);
        bot.setPrimaryLanguage("en");
        bot.setSupportedLanguages(List.of("en"));
        bot.setBrandingConfig("{}");
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(bot));

        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode("1");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String bodyStr = res.getBody().toString();
        assertThat(bodyStr).doesNotContain("api_key").doesNotContain("apiKey")
            .doesNotContain("secret").doesNotContain("token").doesNotContain("password")
            .doesNotContain("ANTHROPIC").doesNotContain("COHERE");
    }

    @Test
    @DisplayName("Embed response sanitizes name and description (no angle brackets)")
    void embedResponseSanitizesNameAndDescription() {
        Chatbot bot = new Chatbot();
        bot.setId(2L);
        bot.setName("<script>alert(1)</script>Church");
        bot.setDescription("Desc with <img onerror=alert(1)>");
        bot.setIsActive(true);
        bot.setPrimaryLanguage("en");
        bot.setSupportedLanguages(List.of());
        bot.setBrandingConfig("{}");
        when(chatbotRepository.findById(2L)).thenReturn(Optional.of(bot));

        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode("2");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKey("name").containsKey("description");
        String name = (String) res.getBody().get("name");
        String description = (String) res.getBody().get("description");
        assertThat(name).doesNotContain("<").doesNotContain(">");
        assertThat(description).doesNotContain("<").doesNotContain(">");
        assertThat(name).doesNotContain("<script>").doesNotContain("</script>");
        // Angle brackets removed so HTML/script cannot be parsed; literal "onerror=" may remain
        assertThat(description).doesNotContain("<").doesNotContain(">");
    }

    @Test
    @DisplayName("Embed response brandingConfig must be sanitized (no script)")
    void embedResponseBrandingConfigSanitized() {
        String maliciousBranding = "{\"primaryColor\":\"#fff</script><script>alert(1)</script>\"}";
        Chatbot bot = new Chatbot();
        bot.setId(3L);
        bot.setName("Bot");
        bot.setDescription("Desc");
        bot.setIsActive(true);
        bot.setPrimaryLanguage("en");
        bot.setSupportedLanguages(List.of());
        bot.setBrandingConfig(maliciousBranding);
        when(chatbotRepository.findById(3L)).thenReturn(Optional.of(bot));

        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode("3");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object branding = res.getBody().get("brandingConfig");
        assertThat(branding).isNotNull();
        String brandingStr = branding.toString();
        assertThat(brandingStr).doesNotContain("</script>").doesNotContain("<script>").doesNotContain("alert(");
        // Sanitizer returns {} for invalid/malicious values
        assertThat(brandingStr).isEqualTo("{}");
    }

    @Test
    @DisplayName("Embed returns theme colors when chatbot has valid brandingConfig (widget colors work)")
    void embedReturnsThemeColorsFromBrandingConfig() {
        String themeJson = "{\"primaryColor\":\"#7D9B69\",\"secondaryColor\":\"#B5C9A8\",\"borderRadius\":\"8px\"}";
        Chatbot bot = new Chatbot();
        bot.setId(5L);
        bot.setName("Theme Bot");
        bot.setWebsiteUrl("https://example.com");
        bot.setDescription("Desc");
        bot.setIsActive(true);
        bot.setPrimaryLanguage("en");
        bot.setSupportedLanguages(List.of());
        bot.setBrandingConfig(themeJson);
        when(chatbotRepository.findById(5L)).thenReturn(Optional.of(bot));

        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode("5");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object branding = res.getBody().get("brandingConfig");
        assertThat(branding).isNotNull();
        String brandingStr = branding.toString();
        assertThat(brandingStr).contains("#7D9B69").contains("#B5C9A8").contains("8px");
    }

    @Test
    @DisplayName("Inactive chatbot returns 403")
    void inactiveChatbotReturns403() {
        Chatbot bot = new Chatbot();
        bot.setId(4L);
        bot.setName("Inactive");
        bot.setIsActive(false);
        when(chatbotRepository.findById(4L)).thenReturn(Optional.of(bot));

        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode("4");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).containsKey("error");
    }

    @Test
    @DisplayName("Unknown embed id returns 404")
    void unknownEmbedIdReturns404() {
        when(chatbotRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode("999");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("SECURITY: Embed path longer than 255 returns 400 (DoS prevention)")
    void embedPathTooLongReturns400() {
        String longId = "a".repeat(256);
        ResponseEntity<Map<String, Object>> res = chatController.getChatbotByEmbedCode(longId);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chatbotRepository, never()).findById(any());
        verify(chatbotRepository, never()).findByEmbedCode(any());
    }

    @Test
    @DisplayName("SECURITY: Embed path empty or blank returns 400")
    void embedPathEmptyReturns400() {
        assertThat(chatController.getChatbotByEmbedCode("").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(chatController.getChatbotByEmbedCode("   ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
