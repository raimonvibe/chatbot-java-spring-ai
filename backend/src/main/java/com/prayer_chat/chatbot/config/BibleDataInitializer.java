package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.service.BibleDataLoaderService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes Bible data on application startup if not already loaded
 * This runs automatically when the application starts
 * 
 * Disabled in test profile to avoid loading Bible data during tests
 */
@Component
@Order(1) // Run early, but after database initialization
@Profile("!test") // Don't run in test profile
public class BibleDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BibleDataInitializer.class);

    private final BibleDataLoaderService bibleDataLoaderService;
    private final BibleVerseRepository bibleVerseRepository;
    private final ChristianContentAnalysisService christianContentAnalysisService;

    @Value("${app.bible.auto-load:true}")
    private boolean autoLoad;

    @Value("${app.bible.auto-generate-embeddings:false}")
    private boolean autoGenerateEmbeddings;

    @Value("${FORCE_RELOAD_BIBLE_DATA:false}")
    private boolean forceReload;

    public BibleDataInitializer(
            BibleDataLoaderService bibleDataLoaderService,
            BibleVerseRepository bibleVerseRepository,
            ChristianContentAnalysisService christianContentAnalysisService) {
        this.bibleDataLoaderService = bibleDataLoaderService;
        this.bibleVerseRepository = bibleVerseRepository;
        this.christianContentAnalysisService = christianContentAnalysisService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Check if force reload is requested
        if (forceReload) {
            logger.warn("⚠️  FORCE_RELOAD_BIBLE_DATA is set to true!");
            logger.warn("⚠️  Deleting all existing Bible verses and reloading...");
            try {
                long deletedCount = bibleVerseRepository.count();
                // Use optimized native query delete to avoid loading all entities into memory
                bibleVerseRepository.deleteAllVerses();
                logger.info("✅ Deleted {} existing Bible verses (using optimized native query)", deletedCount);
            } catch (InvalidDataAccessResourceUsageException e) {
                // Table may not exist yet (e.g. first deploy); skip delete and proceed to load
                if (e.getCause() != null && e.getCause().getMessage() != null
                        && e.getCause().getMessage().contains("does not exist")) {
                    logger.info("Bible table not present yet; skipping delete, will load data.");
                } else {
                    throw e;
                }
            }
            logger.info("🔄 Reloading Bible data (only New Testament will be loaded if load-old-testament=false)...");
        }

        if (!autoLoad && !forceReload) {
            logger.info("Bible data auto-loading is disabled (app.bible.auto-load=false)");
            return;
        }

        logger.info("Checking if Bible data needs to be loaded...");

        // Check if data is already loaded
        long verseCount = bibleVerseRepository.count();
        
        if (verseCount > 0) {
            logger.info("Bible data already loaded: {} verses found in database", verseCount);
            
            // Check if embeddings need to be generated
            long versesWithoutEmbeddings = bibleVerseRepository.countVersesWithoutEmbeddings();
            if (versesWithoutEmbeddings > 0 && autoGenerateEmbeddings) {
                logger.info("Found {} verses without embeddings. Starting embedding generation...", versesWithoutEmbeddings);
                logger.warn("⚠️  WARNING: Generating embeddings for {} verses will take a long time and cost API credits!", versesWithoutEmbeddings);
                logger.warn("⚠️  This is running automatically because app.bible.auto-generate-embeddings=true");
                logger.warn("⚠️  Consider setting it to false and running manually via admin endpoint");
                
                // Generate embeddings in background (async would be better, but this is simpler)
                try {
                    int processed = christianContentAnalysisService.generateEmbeddingsForAllVerses();
                    logger.info("✅ Successfully generated embeddings for {} verses", processed);
                } catch (Exception e) {
                    logger.error("❌ Error generating embeddings. You can generate them later via admin endpoint.", e);
                }
            } else if (versesWithoutEmbeddings > 0) {
                logger.info("Found {} verses without embeddings. Set app.bible.auto-generate-embeddings=true to generate automatically", versesWithoutEmbeddings);
                logger.info("Or use admin endpoint to generate embeddings manually");
            } else {
                logger.info("✅ All verses have embeddings. Bible data is ready to use!");
            }
            
            return;
        }

        // Data not loaded, load it now
        logger.info("Bible data not found. Starting to load Bible data from JSON files...");
        logger.info("This may take a few minutes...");
        
        try {
            int loadedVerses = bibleDataLoaderService.loadBibleData();
            logger.info("✅ Successfully loaded {} Bible verses into database", loadedVerses);
            
            // Optionally generate embeddings automatically
            if (autoGenerateEmbeddings) {
                logger.info("Starting automatic embedding generation for all verses...");
                logger.warn("⚠️  WARNING: This will take a VERY long time (30+ minutes) and cost significant API credits!");
                logger.warn("⚠️  Consider setting app.bible.auto-generate-embeddings=false and running manually");
                
                try {
                    int processed = christianContentAnalysisService.generateEmbeddingsForAllVerses();
                    logger.info("✅ Successfully generated embeddings for {} verses", processed);
                    logger.info("✅ Bible data is now fully ready to use!");
                } catch (Exception e) {
                    logger.error("❌ Error generating embeddings. Data is loaded but embeddings need to be generated manually.", e);
                    logger.info("You can generate embeddings later via admin endpoint");
                }
            } else {
                logger.info("Bible data loaded successfully!");
                logger.info("⚠️  Note: Embeddings are not generated automatically (app.bible.auto-generate-embeddings=false)");
                logger.info("⚠️  You need to generate embeddings manually before using Christian Content Analysis");
                logger.info("⚠️  Use admin endpoint or set app.bible.auto-generate-embeddings=true (not recommended for production)");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to load Bible data", e);
            logger.error("You can try loading it manually via admin endpoint");
            throw e; // Fail startup if data loading fails
        }
    }
}

