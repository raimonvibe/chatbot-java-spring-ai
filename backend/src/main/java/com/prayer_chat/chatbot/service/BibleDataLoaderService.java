package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for loading Bible data from JSON files into the database
 */
@Service
public class BibleDataLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(BibleDataLoaderService.class);
    private static final Pattern VERSE_PATTERN = Pattern.compile("\\[\\s*(\\d+)\\s*\\]\\s*([^\\[]+)");

    private final BibleVerseRepository bibleVerseRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${app.bible.data.old-testament:classpath:data/bible/data/old-testament-data.json}")
    private String oldTestamentPath;

    @Value("${app.bible.data.new-testament:classpath:data/bible/data/new-testament-data.json}")
    private String newTestamentPath;

    @Value("${app.bible.auto-load:false}")
    private boolean autoLoad;

    @Value("${app.bible.load-old-testament:false}")
    private boolean loadOldTestament;

    public BibleDataLoaderService(
            BibleVerseRepository bibleVerseRepository,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper) {
        this.bibleVerseRepository = bibleVerseRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Load all Bible data from JSON files
     * This method parses the JSON structure and extracts individual verses
     */
    @Transactional
    public int loadBibleData() {
        logger.info("Starting Bible data load...");
        int totalVerses = 0;

        try {
            // Load Old Testament (only if enabled)
            if (loadOldTestament) {
                Resource oldTestamentResource = resourceLoader.getResource(oldTestamentPath);
                if (oldTestamentResource.exists()) {
                    logger.info("Loading Old Testament from: {}", oldTestamentPath);
                    int oldTestamentVerses = loadTestament(oldTestamentResource.getInputStream());
                    totalVerses += oldTestamentVerses;
                    logger.info("Loaded {} verses from Old Testament", oldTestamentVerses);
                } else {
                    logger.warn("Old Testament file not found at: {}", oldTestamentPath);
                }
            } else {
                logger.info("Old Testament loading is disabled (app.bible.load-old-testament=false)");
            }

            // Load New Testament
            Resource newTestamentResource = resourceLoader.getResource(newTestamentPath);
            if (newTestamentResource.exists()) {
                logger.info("Loading New Testament from: {}", newTestamentPath);
                int newTestamentVerses = loadTestament(newTestamentResource.getInputStream());
                totalVerses += newTestamentVerses;
                logger.info("Loaded {} verses from New Testament", newTestamentVerses);
            } else {
                logger.warn("New Testament file not found at: {}", newTestamentPath);
            }

            logger.info("Bible data load completed. Total verses loaded: {}", totalVerses);
            return totalVerses;

        } catch (Exception e) {
            logger.error("Error loading Bible data", e);
            throw new RuntimeException("Failed to load Bible data", e);
        }
    }

    /**
     * Load a single testament (Old or New) from JSON
     */
    private int loadTestament(InputStream inputStream) throws Exception {
        JsonNode root = objectMapper.readTree(inputStream);
        JsonNode books = root.get("books");
        
        if (books == null || !books.isArray()) {
            logger.warn("No books array found in Bible data");
            return 0;
        }

        int totalVerses = 0;
        List<BibleVerse> versesToSave = new ArrayList<>();

        for (JsonNode bookNode : books) {
            String bookName = bookNode.get("name").asText();
            JsonNode chapters = bookNode.get("chapters");

            if (chapters == null || !chapters.isArray()) {
                continue;
            }

            for (JsonNode chapterNode : chapters) {
                String chapterNumberStr = chapterNode.get("number").asText();
                int chapterNumber = Integer.parseInt(chapterNumberStr);
                String content = chapterNode.get("content").asText();

                // Parse individual verses from chapter content
                List<BibleVerse> chapterVerses = parseVersesFromChapter(
                    bookName, chapterNumber, content
                );

                // Check if verses already exist before adding
                for (BibleVerse verse : chapterVerses) {
                    if (!bibleVerseRepository.findByBookAndChapterAndVerse(
                            verse.getBook(), verse.getChapter(), verse.getVerse()).isPresent()) {
                        versesToSave.add(verse);
                    }
                }

                totalVerses += chapterVerses.size();

                // Batch save every 1000 verses to avoid memory issues
                if (versesToSave.size() >= 1000) {
                    bibleVerseRepository.saveAll(versesToSave);
                    logger.debug("Saved batch of {} verses", versesToSave.size());
                    versesToSave.clear();
                }
            }
        }

        // Save remaining verses
        if (!versesToSave.isEmpty()) {
            bibleVerseRepository.saveAll(versesToSave);
            logger.debug("Saved final batch of {} verses", versesToSave.size());
        }

        return totalVerses;
    }

    /**
     * Parse individual verses from chapter content
     * Content format: "[1] verse text [2] verse text ..."
     */
    private List<BibleVerse> parseVersesFromChapter(String bookName, int chapterNumber, String content) {
        List<BibleVerse> verses = new ArrayList<>();
        Matcher matcher = VERSE_PATTERN.matcher(content);

        while (matcher.find()) {
            int verseNumber = Integer.parseInt(matcher.group(1));
            String verseText = matcher.group(2).trim();

            // Skip empty verses
            if (verseText.isEmpty()) {
                continue;
            }

            String reference = String.format("%s %d:%d", bookName, chapterNumber, verseNumber);
            
            BibleVerse verse = new BibleVerse(
                bookName,
                chapterNumber,
                verseNumber,
                reference,
                verseText,
                "World English Bible"
            );

            verses.add(verse);
        }

        return verses;
    }

    /**
     * Check if Bible data is already loaded
     */
    public boolean isDataLoaded() {
        return bibleVerseRepository.count() > 0;
    }

    /**
     * Get count of loaded verses
     */
    public long getVerseCount() {
        return bibleVerseRepository.count();
    }
}

