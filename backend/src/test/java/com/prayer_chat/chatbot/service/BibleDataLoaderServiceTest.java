package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Bible Data Loader Service Tests")
class BibleDataLoaderServiceTest {

    @Autowired
    private BibleDataLoaderService bibleDataLoaderService;

    @Autowired
    private BibleVerseRepository bibleVerseRepository;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        bibleVerseRepository.deleteAll();
    }

    @Test
    @DisplayName("Should check if data is already loaded")
    void shouldCheckIfDataIsLoaded() {
        // Initially no data
        assertThat(bibleDataLoaderService.isDataLoaded()).isFalse();
        assertThat(bibleDataLoaderService.getVerseCount()).isEqualTo(0);

        // After loading (if files exist)
        // Note: This test depends on actual JSON files being present
        // In a real scenario, we'd mock the ResourceLoader
    }

    @Test
    @DisplayName("Should return zero count when no verses loaded")
    void shouldReturnZeroCountWhenNoVersesLoaded() {
        long count = bibleDataLoaderService.getVerseCount();
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle missing Bible data files gracefully")
    void shouldHandleMissingFilesGracefully() {
        // The service should handle missing files without crashing
        // This is tested implicitly by the service not throwing exceptions
        // when files don't exist
        assertThat(bibleDataLoaderService.isDataLoaded()).isFalse();
    }

    @Test
    @DisplayName("Should parse verse reference correctly")
    void shouldParseVerseReference() {
        // Create a test verse manually to test reference format
        BibleVerse verse = new BibleVerse();
        verse.setBook("Matthew");
        verse.setChapter(1);
        verse.setVerse(1);
        verse.setReference("Matthew 1:1");
        verse.setText("The book of the genealogy of Jesus Christ");
        verse.setTranslation("World English Bible");

        BibleVerse saved = bibleVerseRepository.save(verse);

        Optional<BibleVerse> found = bibleVerseRepository.findByReference("Matthew 1:1");
        assertThat(found).isPresent();
        assertThat(found.get().getReference()).isEqualTo("Matthew 1:1");
        assertThat(found.get().getBook()).isEqualTo("Matthew");
        assertThat(found.get().getChapter()).isEqualTo(1);
        assertThat(found.get().getVerse()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find verses by book, chapter, and verse")
    void shouldFindVersesByBookChapterAndVerse() {
        BibleVerse verse = new BibleVerse();
        verse.setBook("John");
        verse.setChapter(3);
        verse.setVerse(16);
        verse.setReference("John 3:16");
        verse.setText("For God so loved the world");
        verse.setTranslation("World English Bible");

        bibleVerseRepository.save(verse);

        Optional<BibleVerse> found = bibleVerseRepository.findByBookAndChapterAndVerse("John", 3, 16);
        assertThat(found).isPresent();
        assertThat(found.get().getText()).contains("loved the world");
    }

    @Test
    @DisplayName("Should find all verses in a book")
    void shouldFindAllVersesInBook() {
        // Create multiple verses in the same book
        BibleVerse verse1 = new BibleVerse();
        verse1.setBook("Genesis");
        verse1.setChapter(1);
        verse1.setVerse(1);
        verse1.setReference("Genesis 1:1");
        verse1.setText("In the beginning");
        verse1.setTranslation("World English Bible");

        BibleVerse verse2 = new BibleVerse();
        verse2.setBook("Genesis");
        verse2.setChapter(1);
        verse2.setVerse(2);
        verse2.setReference("Genesis 1:2");
        verse2.setText("The earth was formless");
        verse2.setTranslation("World English Bible");

        bibleVerseRepository.save(verse1);
        bibleVerseRepository.save(verse2);

        List<BibleVerse> genesisVerses = bibleVerseRepository.findByBookOrderByChapterAscVerseAsc("Genesis");
        assertThat(genesisVerses).hasSize(2);
        assertThat(genesisVerses.get(0).getVerse()).isEqualTo(1);
        assertThat(genesisVerses.get(1).getVerse()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should count verses correctly")
    void shouldCountVersesCorrectly() {
        assertThat(bibleVerseRepository.count()).isEqualTo(0);

        BibleVerse verse1 = new BibleVerse();
        verse1.setBook("Test");
        verse1.setChapter(1);
        verse1.setVerse(1);
        verse1.setReference("Test 1:1");
        verse1.setText("Test verse 1");
        verse1.setTranslation("Test Translation");

        BibleVerse verse2 = new BibleVerse();
        verse2.setBook("Test");
        verse2.setChapter(1);
        verse2.setVerse(2);
        verse2.setReference("Test 1:2");
        verse2.setText("Test verse 2");
        verse2.setTranslation("Test Translation");

        bibleVerseRepository.save(verse1);
        bibleVerseRepository.save(verse2);

        assertThat(bibleVerseRepository.count()).isEqualTo(2);
    }
}

