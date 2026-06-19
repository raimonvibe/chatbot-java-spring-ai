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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")  // Test profile enables AdminController via @Profile({"local", "test"})
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

    private Authentication createAdminAuthentication() {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
            "admin",
            null,
            authorities
        );
    }

    @Test
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

        mockMvc.perform(get("/api/admin/bible/status")
                .with(authentication(createAdminAuthentication())))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.dataLoaded").value(true))
            .andExpect(jsonPath("$.totalVerses").value(1))
            .andExpect(jsonPath("$.versesWithEmbeddings").exists())
            .andExpect(jsonPath("$.versesWithoutEmbeddings").exists())
            .andExpect(jsonPath("$.embeddingsReady").exists());
    }

    @Test
    @DisplayName("Should return correct status when no data loaded")
    void shouldReturnCorrectStatusWhenNoDataLoaded() throws Exception {
        mockMvc.perform(get("/api/admin/bible/status")
                .with(authentication(createAdminAuthentication())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataLoaded").value(false))
            .andExpect(jsonPath("$.totalVerses").value(0));
    }

    @Test
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

        mockMvc.perform(get("/api/admin/bible/embedding-progress")
                .with(authentication(createAdminAuthentication())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalVerses").value(2))
            .andExpect(jsonPath("$.versesWithEmbeddings").value(1))
            .andExpect(jsonPath("$.versesWithoutEmbeddings").value(1))
            .andExpect(jsonPath("$.percentage").exists())
            .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
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

        mockMvc.perform(get("/api/admin/bible/embedding-progress")
                .with(authentication(createAdminAuthentication())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @DisplayName("Should deny access to non-admin users")
    void shouldDenyAccessToNonAdminUsers() throws Exception {
        // Create authentication with USER role (not ADMIN)
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        Authentication userAuth = new UsernamePasswordAuthenticationToken(
            "user",
            null,
            authorities
        );

        mockMvc.perform(get("/api/admin/bible/status")
                .with(authentication(userAuth)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject access without admin credentials")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        // CI can see 403 (anonymous + hasRole ADMIN); locally often 401 (entry point) — both deny access.
        mockMvc.perform(get("/api/admin/bible/status"))
            .andExpect(status().is(anyOf(is(401), is(403))));
    }
}

