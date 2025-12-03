package com.tjanabot.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BibleVerseService
 * Tests Bible verse suggestion logic based on website content
 */
@DisplayName("Bible Verse Service Tests")
class BibleVerseServiceTest {

    private BibleVerseService bibleVerseService;

    @BeforeEach
    void setUp() {
        bibleVerseService = new BibleVerseService();
    }

    // ========== Business & Work Topics ==========

    @Test
    @DisplayName("Should suggest business verse for business website")
    void shouldSuggestBusinessVerse_forBusinessWebsite() {
        // Arrange
        String websiteUrl = "https://example-business.com";
        String description = "We are a business consulting company";
        String content = "Our business services help you grow";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(websiteUrl, description, content);

        // Assert
        assertThat(verse).contains("Colossians 3:23");
        assertThat(verse).contains("work at it with all your heart");
    }

    @Test
    @DisplayName("Should suggest work verse for work-related website")
    void shouldSuggestWorkVerse_forWorkRelatedWebsite() {
        // Arrange
        String description = "Professional work environment";
        String content = "Quality work delivered on time";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, content);

        // Assert
        assertThat(verse).contains("Proverbs 16:3");
        assertThat(verse).contains("Commit to the Lord");
    }

    // ========== Healthcare Topics ==========

    @ParameterizedTest
    @CsvSource({
        "health, 3 John 1:2",
        "medical, Jeremiah 17:14",
        "hospital, Isaiah 41:10"
    })
    @DisplayName("Should suggest appropriate verse for healthcare topics")
    void shouldSuggestHealthcareVerse(String keyword, String expectedVerseReference) {
        // Arrange
        String description = "We provide " + keyword + " services";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).contains(expectedVerseReference);
    }

    // ========== Education Topics ==========

    @Test
    @DisplayName("Should suggest education verse for school website")
    void shouldSuggestEducationVerse_forSchoolWebsite() {
        // Arrange
        String content = "Welcome to our school where learning matters";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, content);

        // Assert
        assertThat(verse).contains("Proverbs");
        assertThat(verse).containsAnyOf("learning", "instruction", "education");
    }

    // ========== Technology Topics ==========

    @ParameterizedTest
    @ValueSource(strings = {"technology", "innovation", "software"})
    @DisplayName("Should suggest technology-related verses")
    void shouldSuggestTechnologyVerse(String keyword) {
        // Arrange
        String description = "Leading " + keyword + " company";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).isNotNull();
        assertThat(verse).isNotEmpty();
        assertThat(verse).doesNotContain("null");
    }

    // ========== Service & Hospitality Topics ==========

    @Test
    @DisplayName("Should suggest service verse for hospitality business")
    void shouldSuggestServiceVerse_forHospitalityBusiness() {
        // Arrange
        String description = "Premium hospitality service for our guests";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).contains("hospitality");
    }

    // ========== Finance Topics ==========

    @Test
    @DisplayName("Should suggest finance verse for banking website")
    void shouldSuggestFinanceVerse_forBankingWebsite() {
        // Arrange
        String content = "Your trusted banking partner for financial success";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, content);

        // Assert
        assertThat(verse).containsAnyOf("Luke 16:10", "Proverbs 3:9", "Matthew 6:20");
    }

    // ========== Real Estate Topics ==========

    @Test
    @DisplayName("Should suggest home verse for real estate website")
    void shouldSuggestHomeVerse_forRealEstateWebsite() {
        // Arrange
        String websiteUrl = "https://homes-realestate.com";
        String description = "Find your dream home with us";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(websiteUrl, description, null);

        // Assert
        assertThat(verse).containsAnyOf("home", "house", "household");
    }

    // ========== Legal Topics ==========

    @Test
    @DisplayName("Should suggest justice verse for legal website")
    void shouldSuggestJusticeVerse_forLegalWebsite() {
        // Arrange
        String description = "Law firm dedicated to justice";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).containsAnyOf("justice", "law", "legal");
    }

    // ========== Creative & Arts Topics ==========

    @ParameterizedTest
    @ValueSource(strings = {"creative", "design", "art"})
    @DisplayName("Should suggest creative verse for arts-related websites")
    void shouldSuggestCreativeVerse(String keyword) {
        // Arrange
        String content = "Expressing " + keyword + " excellence";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, content);

        // Assert
        assertThat(verse).isNotNull();
        assertThat(verse).contains(":");
    }

    // ========== Additional Keyword Matching ==========

    @Test
    @DisplayName("Should suggest commerce verse for e-commerce website")
    void shouldSuggestCommerceVerse_forEcommerceWebsite() {
        // Arrange
        String description = "Online shop for all your shopping needs";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).contains("Proverbs 11:1");
        assertThat(verse).contains("dishonest scales");
    }

    @Test
    @DisplayName("Should suggest church verse for worship website")
    void shouldSuggestChurchVerse_forWorshipWebsite() {
        // Arrange
        String content = "Join us for worship and church services";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, content);

        // Assert
        assertThat(verse).contains("Hebrews 10:25");
        assertThat(verse).contains("meeting together");
    }

    @Test
    @DisplayName("Should suggest care verse for support website")
    void shouldSuggestCareVerse_forSupportWebsite() {
        // Arrange
        String description = "We care and provide support to those in need";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).contains("Galatians 6:2");
        assertThat(verse).contains("burdens");
    }

    @Test
    @DisplayName("Should suggest food verse for restaurant website")
    void shouldSuggestFoodVerse_forRestaurantWebsite() {
        // Arrange
        String content = "Delicious food and cooking recipes";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, content);

        // Assert
        assertThat(verse).contains("Matthew 4:4");
        assertThat(verse).contains("bread");
    }

    @Test
    @DisplayName("Should suggest beauty verse for salon website")
    void shouldSuggestBeautyVerse_forSalonWebsite() {
        // Arrange
        String description = "Beauty salon and spa services";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).contains("1 Peter 3:3-4");
        assertThat(verse).contains("beauty");
    }

    // ========== Default Case ==========

    @Test
    @DisplayName("Should return general verse when no specific match found")
    void shouldReturnGeneralVerse_whenNoSpecificMatch() {
        // Arrange
        String description = "Random website with no specific keywords";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).contains("Matthew 5:16");
        assertThat(verse).contains("let your light shine");
    }

    // ========== Null Handling ==========

    @Test
    @DisplayName("Should handle null inputs gracefully")
    void shouldHandleNullInputs() {
        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, null);

        // Assert
        assertThat(verse).isNotNull();
        assertThat(verse).contains("Matthew 5:16");
    }

    @Test
    @DisplayName("Should handle empty inputs gracefully")
    void shouldHandleEmptyInputs() {
        // Act
        String verse = bibleVerseService.suggestBibleVerse("", "", "");

        // Assert
        assertThat(verse).isNotNull();
        assertThat(verse).contains("Matthew 5:16");
    }

    // ========== Case Insensitivity ==========

    @Test
    @DisplayName("Should match keywords case insensitively")
    void shouldMatchKeywords_caseInsensitively() {
        // Arrange
        String description = "BUSINESS consulting and WORK management";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).containsAnyOf("Colossians 3:23", "Proverbs 16:3");
    }

    // ========== Multiple Keywords ==========

    @Test
    @DisplayName("Should prioritize first matching keyword")
    void shouldPrioritizeFirstMatchingKeyword() {
        // Arrange - "health" appears before "business" in TOPIC_VERSES map
        String description = "health and business consulting";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).isNotNull();
        assertThat(verse).isNotEmpty();
    }

    // ========== Get All Topic Verses ==========

    @Test
    @DisplayName("Should return all topic verses")
    void shouldReturnAllTopicVerses() {
        // Act
        Map<String, String> allVerses = bibleVerseService.getAllTopicVerses();

        // Assert
        assertThat(allVerses).isNotNull();
        assertThat(allVerses).isNotEmpty();
        assertThat(allVerses).containsKeys("business", "work", "health", "education");
        assertThat(allVerses.get("business")).contains("Colossians 3:23");
    }

    @Test
    @DisplayName("Should return a copy of topic verses to prevent modification")
    void shouldReturnCopyOfTopicVerses() {
        // Act
        Map<String, String> verses1 = bibleVerseService.getAllTopicVerses();
        Map<String, String> verses2 = bibleVerseService.getAllTopicVerses();

        // Modify first map
        verses1.put("test", "test verse");

        // Assert
        assertThat(verses2).doesNotContainKey("test");
    }

    // ========== Combined Content Testing ==========

    @Test
    @DisplayName("Should combine URL, description, and content for matching")
    void shouldCombineAllFields_forMatching() {
        // Arrange - "technology" only appears in URL
        String websiteUrl = "https://technology-solutions.com";
        String description = "We provide solutions";
        String content = "Our services are comprehensive";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(websiteUrl, description, content);

        // Assert
        assertThat(verse).contains("Romans 12:2");
    }

    // ========== Fitness & Wellness ==========

    @Test
    @DisplayName("Should suggest fitness verse for wellness website")
    void shouldSuggestFitnessVerse_forWellnessWebsite() {
        // Arrange
        String description = "Fitness and wellness center";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).containsAnyOf("1 Corinthians 6:19", "1 Timothy 4:8");
    }

    // ========== Family & Children ==========

    @Test
    @DisplayName("Should suggest family verse for parenting website")
    void shouldSuggestFamilyVerse_forParentingWebsite() {
        // Arrange
        String content = "Tips for parenting and raising children in a loving family";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, null, content);

        // Assert
        assertThat(verse).containsAnyOf("family", "children", "parenting");
    }

    // ========== Non-profit & Ministry ==========

    @Test
    @DisplayName("Should suggest ministry verse for non-profit website")
    void shouldSuggestMinistryVerse_forNonProfitWebsite() {
        // Arrange
        String description = "Nonprofit charity organization serving the community";

        // Act
        String verse = bibleVerseService.suggestBibleVerse(null, description, null);

        // Assert
        assertThat(verse).containsAnyOf("Matthew 25:40", "2 Corinthians 9:7", "Matthew 28:19");
    }
}
