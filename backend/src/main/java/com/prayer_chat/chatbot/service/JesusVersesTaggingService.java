package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for tagging Bible verses as Jesus's direct teachings
 * Uses a JSON mapping file to identify which verses are Jesus's words
 */
@Service
public class JesusVersesTaggingService {

    private static final Logger logger = LoggerFactory.getLogger(JesusVersesTaggingService.class);

    private final BibleVerseRepository bibleVerseRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private static final String JESUS_VERSES_FILE = "classpath:data/jesus_verses.json";

    public JesusVersesTaggingService(
            BibleVerseRepository bibleVerseRepository,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper) {
        this.bibleVerseRepository = bibleVerseRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Tag verses as Jesus's teachings based on the JSON mapping file
     * @return Number of verses tagged
     */
    @Transactional
    public int tagJesusVerses() {
        logger.info("Starting Jesus verses tagging...");

        try {
            // Load the JSON mapping file
            Resource resource = resourceLoader.getResource(JESUS_VERSES_FILE);
            if (!resource.exists()) {
                logger.warn("Jesus verses mapping file not found at: {}", JESUS_VERSES_FILE);
                logger.warn("Skipping Jesus verses tagging. Create {} to enable this feature.", JESUS_VERSES_FILE);
                return 0;
            }

            logger.info("Loading Jesus verses mapping from: {}", JESUS_VERSES_FILE);
            JsonNode root = objectMapper.readTree(resource.getInputStream());

            // Build a set of verse references that are Jesus's words
            Set<String> jesusVerseReferences = new HashSet<>();
            JsonNode books = root.get("books");

            if (books == null || !books.isObject()) {
                logger.warn("Invalid JSON structure: 'books' node not found or not an object");
                return 0;
            }

            // Iterate through each book
            books.fields().forEachRemaining(bookEntry -> {
                String bookName = bookEntry.getKey();
                JsonNode bookData = bookEntry.getValue();
                JsonNode chapters = bookData.get("chapters");

                if (chapters != null && chapters.isObject()) {
                    // Iterate through each chapter
                    chapters.fields().forEachRemaining(chapterEntry -> {
                        String chapterNumber = chapterEntry.getKey();
                        JsonNode chapterData = chapterEntry.getValue();
                        JsonNode verses = chapterData.get("verses");

                        if (verses != null && verses.isArray()) {
                            // Add each verse reference to the set
                            verses.forEach(verseNode -> {
                                int verseNumber = verseNode.asInt();
                                String reference = String.format("%s %s:%d", bookName, chapterNumber, verseNumber);
                                jesusVerseReferences.add(reference);
                            });
                        }
                    });
                }
            });

            logger.info("Loaded {} Jesus verse references from mapping file", jesusVerseReferences.size());

            // Find and tag verses in the database
            AtomicInteger taggedCount = new AtomicInteger(0);
            int batchSize = 100;
            List<BibleVerse> versesToUpdate = new java.util.ArrayList<>();

            for (String reference : jesusVerseReferences) {
                bibleVerseRepository.findByReference(reference).ifPresent(verse -> {
                    if (!verse.isJesusTeaching()) {
                        verse.setSpeaker("Jesus");
                        versesToUpdate.add(verse);
                        taggedCount.incrementAndGet();

                        // Batch update every 100 verses
                        if (versesToUpdate.size() >= batchSize) {
                            bibleVerseRepository.saveAll(versesToUpdate);
                            logger.debug("Tagged batch of {} verses as Jesus teachings", versesToUpdate.size());
                            versesToUpdate.clear();
                        }
                    }
                });
            }

            // Save remaining verses
            if (!versesToUpdate.isEmpty()) {
                bibleVerseRepository.saveAll(versesToUpdate);
                logger.debug("Tagged final batch of {} verses as Jesus teachings", versesToUpdate.size());
            }

            // Count total tagged verses
            long totalTagged = bibleVerseRepository.countBySpeaker("Jesus");

            logger.info("✅ Jesus verses tagging completed!");
            logger.info("📊 Tagged {} new verses as Jesus teachings", taggedCount.get());
            logger.info("📊 Total Jesus verses in database: {}", totalTagged);

            return taggedCount.get();

        } catch (Exception e) {
            logger.error("❌ Error tagging Jesus verses", e);
            throw new RuntimeException("Failed to tag Jesus verses", e);
        }
    }

    /**
     * Check if Jesus verses are already tagged
     */
    public boolean areJesusVersesTagged() {
        return bibleVerseRepository.countBySpeaker("Jesus") > 0;
    }

    /**
     * Get count of tagged Jesus verses
     */
    public long getJesusVersesCount() {
        return bibleVerseRepository.countBySpeaker("Jesus");
    }
}

