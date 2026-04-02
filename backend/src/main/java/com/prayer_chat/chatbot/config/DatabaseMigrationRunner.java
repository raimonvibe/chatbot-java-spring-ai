package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.repository.ChatbotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Database migration runner to fix NULL values in existing columns
 * 
 * This runs before other CommandLineRunners to ensure database schema is correct
 * Disabled in test profile to avoid running during tests
 */
@Component
@Order(0) // Run first, before all other CommandLineRunners
@Profile("!test") // Don't run in test profile
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final ChatbotRepository chatbotRepository;

    public DatabaseMigrationRunner(ChatbotRepository chatbotRepository) {
        this.chatbotRepository = chatbotRepository;
    }

    @Override
    public void run(String... args) {
        logger.info("Running database migrations...");
        
        try {
            // Fix NULL values in jesus_teachings_enabled column
            int updated = chatbotRepository.updateNullJesusTeachingsEnabled();
            if (updated > 0) {
                logger.info("Database migration: updated {} chatbot(s) with NULL jesus_teachings_enabled to false", updated);
            } else {
                logger.debug("No chatbots with NULL jesus_teachings_enabled found");
            }
        } catch (Exception e) {
            // Log error but don't fail startup - column might not exist yet or already be fixed
            logger.warn("Could not update NULL jesus_teachings_enabled values: {}", e.getMessage());
            logger.debug("This is normal if the column doesn't exist yet or has already been migrated", e);
        }
        
        logger.info("Database migrations completed");
    }
}

