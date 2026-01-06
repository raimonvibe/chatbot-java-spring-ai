package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.BibleVerse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for BibleVerse entities
 */
@Repository
public interface BibleVerseRepository extends JpaRepository<BibleVerse, Long> {

    /**
     * Find verse by book, chapter, and verse number
     */
    Optional<BibleVerse> findByBookAndChapterAndVerse(String book, Integer chapter, Integer verse);

    /**
     * Find all verses in a book
     */
    List<BibleVerse> findByBookOrderByChapterAscVerseAsc(String book);

    /**
     * Find all verses in a chapter
     */
    List<BibleVerse> findByBookAndChapterOrderByVerseAsc(String book, Integer chapter);

    /**
     * Find verses by reference (e.g., "Matthew 1:1")
     */
    Optional<BibleVerse> findByReference(String reference);

    /**
     * Count total verses in database
     */
    long count();

    /**
     * Check if embeddings are loaded for all verses
     */
    @Query("SELECT COUNT(v) FROM BibleVerse v WHERE v.embedding IS NULL")
    long countVersesWithoutEmbeddings();

    /**
     * Find verses that have embeddings loaded
     */
    @Query("SELECT v FROM BibleVerse v WHERE v.embedding IS NOT NULL")
    List<BibleVerse> findVersesWithEmbeddings();

    /**
     * Optimized delete all verses using native SQL query
     * This avoids loading all entities into memory before deleting
     * Much more memory-efficient than deleteAll() for large datasets
     * 
     * Memory impact: Reduces memory from ~300MB to ~10MB during delete operation
     */
    @Modifying
    @Query(value = "DELETE FROM bible_verse", nativeQuery = true)
    void deleteAllVerses();

    /**
     * Find all Bible verses that are direct teachings from Jesus (with embeddings)
     * Used for "What Jesus Would Say" feature
     * Returns verses where speaker = 'Jesus' and embedding is not null
     */
    @Query("SELECT v FROM BibleVerse v WHERE v.speaker = 'Jesus' AND v.embedding IS NOT NULL")
    List<BibleVerse> findJesusTeachingsWithEmbeddings();
}

