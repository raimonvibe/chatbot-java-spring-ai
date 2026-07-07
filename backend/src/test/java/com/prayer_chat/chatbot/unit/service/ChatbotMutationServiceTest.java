package com.prayer_chat.chatbot.unit.service;

import com.prayer_chat.chatbot.dto.ChatbotUpdatePayload;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.service.ChatbotMutationService;
import com.prayer_chat.chatbot.service.UrlValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotMutationServiceTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private UrlValidationService urlValidationService;

    private ChatbotMutationService service;

    @BeforeEach
    void setUp() {
        service = new ChatbotMutationService(chatbotRepository, urlValidationService);
    }

    @Test
    void applyPatch_updatesOnlyProvidedFields() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setName("Original");
        chatbot.setJesusTeachingsEnabled(false);

        ChatbotUpdatePayload patch = new ChatbotUpdatePayload();
        patch.setJesusTeachingsEnabled(true);
        patch.setBrandingConfig("{\"primaryColor\":\"#112233\"}");

        when(chatbotRepository.save(any(Chatbot.class))).thenAnswer(inv -> inv.getArgument(0));

        Chatbot result = service.applyPatch(chatbot, patch);

        assertThat(result.getName()).isEqualTo("Original");
        assertThat(result.getJesusTeachingsEnabled()).isTrue();
        assertThat(result.getBrandingConfig()).contains("112233");
        verify(chatbotRepository).save(chatbot);
    }

    @Test
    void applyPatch_rejectsUnsafeWebsiteUrl() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);

        ChatbotUpdatePayload patch = new ChatbotUpdatePayload();
        patch.setWebsiteUrl("javascript:alert(1)");

        when(urlValidationService.isValidAndSafe("javascript:alert(1)")).thenReturn(false);

        assertThatThrownBy(() -> service.applyPatch(chatbot, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("website URL");
    }

    @Test
    void applyPatch_clearsAvatarWithEmptyString() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setAvatarId("3");

        ChatbotUpdatePayload patch = new ChatbotUpdatePayload();
        patch.setAvatarId("");

        when(chatbotRepository.save(any(Chatbot.class))).thenAnswer(inv -> inv.getArgument(0));

        Chatbot result = service.applyPatch(chatbot, patch);

        ArgumentCaptor<Chatbot> captor = ArgumentCaptor.forClass(Chatbot.class);
        verify(chatbotRepository).save(captor.capture());
        assertThat(captor.getValue().getAvatarId()).isNull();
        assertThat(result.getAvatarId()).isNull();
    }
}
