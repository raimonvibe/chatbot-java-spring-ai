package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({com.prayer_chat.chatbot.config.TestSecurityConfig.class, 
        com.prayer_chat.chatbot.config.MockAiConfiguration.class,
        com.prayer_chat.chatbot.config.TestJacksonConfiguration.class})
@Transactional
@DisplayName("Admin Controller Tests")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BibleVerseRepository bibleVerseRepository;

    @BeforeEach
    void setUp() {
        bibleVerseRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should get Bible data status")
    void shouldGetBibleDataStatus() throws Exception {
        // Create a test verse
        BibleVerse verse = new BibleVerse();
        verse.setBook("Test");
        verse.setChapter(1);
        verse.setVerse(1);
        verse.setReference("Test 1:1");
        verse.setText("Test verse");
        verse.setTranslation("Test Translation");
        bibleVerseRepository.save(verse);

        mockMvc.perform(get("/api/admin/bible/status"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.dataLoaded").value(true))
            .andExpect(jsonPath("$.totalVerses").value(1))
            .andExpect(jsonPath("$.versesWithEmbeddings").exists())
            .andExpect(jsonPath("$.versesWithoutEmbeddings").exists())
            .andExpect(jsonPath("$.embeddingsReady").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return correct status when no data loaded")
    void shouldReturnCorrectStatusWhenNoDataLoaded() throws Exception {
        mockMvc.perform(get("/api/admin/bible/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataLoaded").value(false))
            .andExpect(jsonPath("$.totalVerses").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should get embedding progress")
    void shouldGetEmbeddingProgress() throws Exception {
        // Create verses with and without embeddings
        BibleVerse verseWithEmbedding = new BibleVerse();
        verseWithEmbedding.setBook("Test");
        verseWithEmbedding.setChapter(1);
        verseWithEmbedding.setVerse(1);
        verseWithEmbedding.setReference("Test 1:1");
        verseWithEmbedding.setText("Test verse 1");
        verseWithEmbedding.setTranslation("Test");
        float[] embedding = new float[1024];
        verseWithEmbedding.setEmbedding(embedding);
        bibleVerseRepository.save(verseWithEmbedding);

        BibleVerse verseWithoutEmbedding = new BibleVerse();
        verseWithoutEmbedding.setBook("Test");
        verseWithoutEmbedding.setChapter(1);
        verseWithoutEmbedding.setVerse(2);
        verseWithoutEmbedding.setReference("Test 1:2");
        verseWithoutEmbedding.setText("Test verse 2");
        verseWithoutEmbedding.setTranslation("Test");
        bibleVerseRepository.save(verseWithoutEmbedding);

        mockMvc.perform(get("/api/admin/bible/embedding-progress"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalVerses").value(2))
            .andExpect(jsonPath("$.versesWithEmbeddings").value(1))
            .andExpect(jsonPath("$.versesWithoutEmbeddings").value(1))
            .andExpect(jsonPath("$.percentage").exists())
            .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return completed true when all verses have embeddings")
    void shouldReturnCompletedTrueWhenAllHaveEmbeddings() throws Exception {
        BibleVerse verse = new BibleVerse();
        verse.setBook("Test");
        verse.setChapter(1);
        verse.setVerse(1);
        verse.setReference("Test 1:1");
        verse.setText("Test verse");
        verse.setTranslation("Test");
        float[] embedding = new float[1024];
        verse.setEmbedding(embedding);
        bibleVerseRepository.save(verse);

        mockMvc.perform(get("/api/admin/bible/embedding-progress"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should deny access to non-admin users")
    void shouldDenyAccessToNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/bible/status"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should require authentication")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/bible/status"))
            .andExpect(status().isUnauthorized());
    }
}

