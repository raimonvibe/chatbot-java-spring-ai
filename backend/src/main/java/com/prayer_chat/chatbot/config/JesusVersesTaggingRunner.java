package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.service.JesusVersesTaggingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes Jesus verses tagging on application startup
 * Tags Bible verses as Jesus's direct teachings based on JSON mapping file
 * 
 * Runs after BibleDataInitializer to ensure verses are loaded first
 * Disabled in test profile to avoid tagging during tests
 */
@Component
@Order(3) // Run after BibleDataInitializer (@Order(1)) and EmbeddingImportRunner (@Order(2))
@Profile("!test") // Don't run in test profile
public class JesusVersesTaggingRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(JesusVersesTaggingRunner.class);

    private final JesusVersesTaggingService jesusVersesTaggingService;

    @Value("${app.bible.auto-tag-jesus-verses:true}")
    private boolean autoTagJesusVerses;

    public JesusVersesTaggingRunner(JesusVersesTaggingService jesusVersesTaggingService) {
        this.jesusVersesTaggingService = jesusVersesTaggingService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!autoTagJesusVerses) {
            logger.info("Jesus verses auto-tagging is disabled (app.bible.auto-tag-jesus-verses=false)");
            return;
        }

        logger.info("Checking if Jesus verses need to be tagged...");

        // Check if verses are already tagged
        if (jesusVersesTaggingService.areJesusVersesTagged()) {
            long taggedCount = jesusVersesTaggingService.getJesusVersesCount();
            logger.info("✅ Jesus verses already tagged: {} verses found in database", taggedCount);
            return;
        }

        // Tag verses
        logger.info("Jesus verses not found. Starting to tag verses from JSON mapping file...");
        logger.info("This may take a few minutes...");

        try {
            int taggedCount = jesusVersesTaggingService.tagJesusVerses();
            logger.info("✅ Successfully tagged {} Jesus verses", taggedCount);
            logger.info("✅ Jesus verses tagging completed!");
        } catch (Exception e) {
            logger.error("❌ Failed to tag Jesus verses", e);
            logger.error("You can try tagging them manually via admin endpoint");
            // Don't fail startup - this is not critical
            logger.warn("⚠️  Service will continue running without Jesus verses tagging");
        }
    }
}

