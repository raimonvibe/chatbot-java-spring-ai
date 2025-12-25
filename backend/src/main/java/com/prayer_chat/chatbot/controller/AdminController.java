package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.BibleDataLoaderService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import com.prayer_chat.chatbot.service.EmbeddingImporterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin endpoints for Bible data management
 * These endpoints allow manual control over Bible data loading and embedding generation
 * 
 * SECURITY: Only available in "local" or "test" profiles.
 * - "local": For local development
 * - "test": For testing (allows @WithMockUser to work properly)
 * 
 * For production imports: Temporarily set SPRING_PROFILES_ACTIVE=local in Render,
 * perform the import, then remove the profile setting for security.
 */
@RestController
@RequestMapping("/api/admin/bible")
@Profile({"local", "test"})  // Available in local and test profiles
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final BibleDataLoaderService bibleDataLoaderService;
    private final BibleVerseRepository bibleVerseRepository;
    private final ChristianContentAnalysisService christianContentAnalysisService;
    private final EmbeddingImporterService embeddingImporterService;
    private final UserRepository userRepository;

    public AdminController(
            BibleDataLoaderService bibleDataLoaderService,
            BibleVerseRepository bibleVerseRepository,
            ChristianContentAnalysisService christianContentAnalysisService,
            EmbeddingImporterService embeddingImporterService,
            UserRepository userRepository) {
        this.bibleDataLoaderService = bibleDataLoaderService;
        this.bibleVerseRepository = bibleVerseRepository;
        this.christianContentAnalysisService = christianContentAnalysisService;
        this.embeddingImporterService = embeddingImporterService;
        this.userRepository = userRepository;
    }

    /**
     * Get Bible data status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            long totalVerses = bibleVerseRepository.count();
            long versesWithEmbeddings = bibleVerseRepository.findVersesWithEmbeddings().size();
            long versesWithoutEmbeddings = bibleVerseRepository.countVersesWithoutEmbeddings();
            boolean dataLoaded = totalVerses > 0;
            boolean embeddingsReady = versesWithoutEmbeddings == 0 && totalVerses > 0;

            Map<String, Object> status = new HashMap<>();
            status.put("dataLoaded", dataLoaded);
            status.put("totalVerses", totalVerses);
            status.put("versesWithEmbeddings", versesWithEmbeddings);
            status.put("versesWithoutEmbeddings", versesWithoutEmbeddings);
            status.put("embeddingsReady", embeddingsReady);
            status.put("embeddingsPercentage", totalVerses > 0 
                ? (double) versesWithEmbeddings / totalVerses * 100 
                : 0.0);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting Bible data status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Load Bible data from JSON files
     */
    @PostMapping("/load-data")
    public ResponseEntity<Map<String, Object>> loadBibleData() {
        try {
            long existingCount = bibleVerseRepository.count();
            if (existingCount > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Bible data already loaded");
                response.put("existingVerses", existingCount);
                response.put("action", "skipped");
                return ResponseEntity.ok(response);
            }

            logger.info("Admin: Starting Bible data load...");
            int loadedVerses = bibleDataLoaderService.loadBibleData();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bible data loaded successfully");
            response.put("loadedVerses", loadedVerses);
            response.put("action", "loaded");
            
            logger.info("Admin: Bible data loaded: {} verses", loadedVerses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error loading Bible data", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load Bible data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Generate embeddings for all Bible verses
     * WARNING: This is expensive and time-consuming!
     */
    @PostMapping("/generate-embeddings")
    public ResponseEntity<Map<String, Object>> generateEmbeddings() {
        try {
            long totalVerses = bibleVerseRepository.count();
            if (totalVerses == 0) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No Bible data loaded. Load data first using /api/admin/bible/load-data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            long versesWithoutEmbeddings = bibleVerseRepository.countVersesWithoutEmbeddings();
            if (versesWithoutEmbeddings == 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "All verses already have embeddings");
                response.put("totalVerses", totalVerses);
                response.put("action", "skipped");
                return ResponseEntity.ok(response);
            }

            logger.info("Admin: Starting embedding generation for {} verses...", versesWithoutEmbeddings);
            logger.warn("⚠️  This will take a long time and cost API credits!");
            
            // Generate embeddings (this will take a while)
            int processed = christianContentAnalysisService.generateEmbeddingsForAllVerses();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Embedding generation completed");
            response.put("processedVerses", processed);
            response.put("totalVerses", totalVerses);
            response.put("action", "generated");
            
            logger.info("Admin: Embedding generation completed: {} verses processed", processed);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error generating embeddings", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate embeddings: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get progress of embedding generation (if running)
     */
    @GetMapping("/embedding-progress")
    public ResponseEntity<Map<String, Object>> getEmbeddingProgress() {
        try {
            long totalVerses = bibleVerseRepository.count();
            long versesWithEmbeddings = bibleVerseRepository.findVersesWithEmbeddings().size();
            long versesWithoutEmbeddings = bibleVerseRepository.countVersesWithoutEmbeddings();
            
            double percentage = totalVerses > 0 
                ? (double) versesWithEmbeddings / totalVerses * 100 
                : 0.0;

            Map<String, Object> progress = new HashMap<>();
            progress.put("totalVerses", totalVerses);
            progress.put("versesWithEmbeddings", versesWithEmbeddings);
            progress.put("versesWithoutEmbeddings", versesWithoutEmbeddings);
            progress.put("percentage", Math.round(percentage * 100.0) / 100.0);
            progress.put("completed", versesWithoutEmbeddings == 0 && totalVerses > 0);

            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            logger.error("Error getting embedding progress", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Import embeddings from JSON file (generated in Google Colab)
     * 
     * @param filePath Path to the bible_embeddings.json file (relative to application working directory or absolute path)
     */
    @PostMapping("/import-embeddings")
    public ResponseEntity<Map<String, Object>> importEmbeddings(@RequestParam String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "filePath parameter is required");
                return ResponseEntity.badRequest().body(error);
            }

            logger.info("Admin: Starting embedding import from: {}", filePath);
            int imported = embeddingImporterService.importEmbeddings(filePath);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Embeddings imported successfully");
            response.put("importedVerses", imported);
            response.put("action", "imported");
            
            logger.info("Admin: Embedding import completed: {} verses imported", imported);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error importing embeddings", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to import embeddings: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * TEMPORARY: Add ADMIN role to current user
     * SECURITY: Only available in "local" profile, only adds role to current authenticated user
     * Remove this endpoint after adding ADMIN role!
     */
    @PostMapping("/add-admin-role")
    @Transactional
    public ResponseEntity<Map<String, Object>> addAdminRoleToCurrentUser(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        try {
            if (oAuth2User == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            User user = oAuth2User.getUser();
            if (user.getRoles().contains("ADMIN")) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "User already has ADMIN role");
                response.put("email", user.getEmail());
                response.put("roles", user.getRoles());
                return ResponseEntity.ok(response);
            }

            // Add ADMIN role
            user.getRoles().add("ADMIN");
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "ADMIN role added successfully");
            response.put("email", user.getEmail());
            response.put("roles", user.getRoles());
            
            logger.info("Admin: Added ADMIN role to user: {}", user.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding ADMIN role", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to add ADMIN role: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

