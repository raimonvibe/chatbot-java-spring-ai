package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Embedding Importer Service Tests")
class EmbeddingImporterServiceTest {

    @Autowired
    private EmbeddingImporterService embeddingImporterService;

    @Autowired
    private BibleVerseRepository bibleVerseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private File tempJsonFile;

    @BeforeEach
    void setUp() throws IOException {
        bibleVerseRepository.deleteAll();

        // Create a test verse without embedding
        BibleVerse verse = new BibleVerse();
        verse.setBook("Matthew");
        verse.setChapter(1);
        verse.setVerse(1);
        verse.setReference("Matthew 1:1");
        verse.setText("The book of the genealogy of Jesus Christ");
        verse.setTranslation("World English Bible");
        bibleVerseRepository.save(verse);

        // Create temporary JSON file with embedding data in working directory
        // This ensures the file is within the allowed directory for path validation
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        tempJsonFile = new File(dataDir, "test-embeddings-" + System.currentTimeMillis() + ".json");
        String jsonContent = """
            {
              "version": "1.0",
              "model": "embed-multilingual-v3.0",
              "total_verses": 1,
              "verses": [
                {
                  "book": "Matthew",
                  "chapter": 1,
                  "verse": 1,
                  "reference": "Matthew 1:1",
                  "text": "The book of the genealogy of Jesus Christ",
                  "translation": "World English Bible",
                  "embedding": [0.1, 0.2, 0.3, 0.4, 0.5]
                }
              ]
            }
            """;
        Files.write(tempJsonFile.toPath(), jsonContent.getBytes());
    }

    @Test
    @DisplayName("Should import embeddings from JSON file")
    void shouldImportEmbeddingsFromJsonFile() {
        // Use relative path from working directory
        String relativePath = "data/" + tempJsonFile.getName();
        int imported = embeddingImporterService.importEmbeddings(relativePath);

        assertThat(imported).isEqualTo(1);

        Optional<BibleVerse> verse = bibleVerseRepository.findByBookAndChapterAndVerse("Matthew", 1, 1);
        assertThat(verse).isPresent();
        assertThat(verse.get().getEmbedding()).isNotNull();
        assertThat(verse.get().getEmbedding().length).isEqualTo(5);
        assertThat(verse.get().getEmbeddingDimensions()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should skip verses not found in database")
    void shouldSkipVersesNotFoundInDatabase() throws IOException {
        // Create JSON with verse that doesn't exist in database
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        File tempFile = new File(dataDir, "test-embeddings-missing-" + System.currentTimeMillis() + ".json");
        String jsonContent = """
            {
              "version": "1.0",
              "model": "embed-multilingual-v3.0",
              "total_verses": 1,
              "verses": [
                {
                  "book": "John",
                  "chapter": 1,
                  "verse": 1,
                  "reference": "John 1:1",
                  "text": "In the beginning was the Word",
                  "translation": "World English Bible",
                  "embedding": [0.1, 0.2, 0.3]
                }
              ]
            }
            """;
        Files.write(tempFile.toPath(), jsonContent.getBytes());

        String relativePath = "data/" + tempFile.getName();
        int imported = embeddingImporterService.importEmbeddings(relativePath);

        assertThat(imported).isEqualTo(0); // No verses imported because verse doesn't exist
    }

    @Test
    @DisplayName("Should throw exception when file does not exist")
    void shouldThrowExceptionWhenFileDoesNotExist() {
        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings("nonexistent/file.json")
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Failed to import embeddings");
    }

    @Test
    @DisplayName("Should throw exception when JSON format is invalid")
    void shouldThrowExceptionWhenJsonFormatInvalid() throws IOException {
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        File invalidFile = new File(dataDir, "test-invalid-" + System.currentTimeMillis() + ".json");
        Files.write(invalidFile.toPath(), "invalid json".getBytes());

        String relativePath = "data/" + invalidFile.getName();
        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings(relativePath)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle verses without embedding in JSON")
    void shouldHandleVersesWithoutEmbedding() throws IOException {
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        File tempFile = new File(dataDir, "test-no-embedding-" + System.currentTimeMillis() + ".json");
        String jsonContent = """
            {
              "version": "1.0",
              "model": "embed-multilingual-v3.0",
              "total_verses": 1,
              "verses": [
                {
                  "book": "Matthew",
                  "chapter": 1,
                  "verse": 1,
                  "reference": "Matthew 1:1",
                  "text": "The book of the genealogy of Jesus Christ",
                  "translation": "World English Bible"
                }
              ]
            }
            """;
        Files.write(tempFile.toPath(), jsonContent.getBytes());

        String relativePath = "data/" + tempFile.getName();
        int imported = embeddingImporterService.importEmbeddings(relativePath);

        assertThat(imported).isEqualTo(0); // No embedding to import
    }

    @Test
    @DisplayName("Should update existing embeddings")
    void shouldUpdateExistingEmbeddings() {
        // First import
        String relativePath1 = "data/" + tempJsonFile.getName();
        int imported1 = embeddingImporterService.importEmbeddings(relativePath1);
        assertThat(imported1).isEqualTo(1);

        Optional<BibleVerse> verse1 = bibleVerseRepository.findByBookAndChapterAndVerse("Matthew", 1, 1);
        assertThat(verse1).isPresent();
        float[] firstEmbedding = verse1.get().getEmbedding();

        // Import again with different embedding
        try {
            String jsonContent2 = """
                {
                  "version": "1.0",
                  "model": "embed-multilingual-v3.0",
                  "total_verses": 1,
                  "verses": [
                    {
                      "book": "Matthew",
                      "chapter": 1,
                      "verse": 1,
                      "reference": "Matthew 1:1",
                      "text": "The book of the genealogy of Jesus Christ",
                      "translation": "World English Bible",
                      "embedding": [0.9, 0.8, 0.7, 0.6, 0.5]
                    }
                  ]
                }
                """;
            File workingDir = new File(System.getProperty("user.dir"));
            File dataDir = new File(workingDir, "data");
            dataDir.mkdirs();
            File tempFile2 = new File(dataDir, "test-embeddings2-" + System.currentTimeMillis() + ".json");
            Files.write(tempFile2.toPath(), jsonContent2.getBytes());

            String relativePath2 = "data/" + tempFile2.getName();
            int imported2 = embeddingImporterService.importEmbeddings(relativePath2);
            assertThat(imported2).isEqualTo(1);

            Optional<BibleVerse> verse2 = bibleVerseRepository.findByBookAndChapterAndVerse("Matthew", 1, 1);
            assertThat(verse2).isPresent();
            float[] secondEmbedding = verse2.get().getEmbedding();

            // Embedding should be updated
            assertThat(secondEmbedding[0]).isNotEqualTo(firstEmbedding[0]);
        } catch (IOException e) {
            // Test cleanup
        }
    }

    @Test
    @DisplayName("Should handle empty verses array")
    void shouldHandleEmptyVersesArray() throws IOException {
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        File tempFile = new File(dataDir, "test-empty-" + System.currentTimeMillis() + ".json");
        String jsonContent = """
            {
              "version": "1.0",
              "model": "embed-multilingual-v3.0",
              "total_verses": 0,
              "verses": []
            }
            """;
        Files.write(tempFile.toPath(), jsonContent.getBytes());

        String relativePath = "data/" + tempFile.getName();
        int imported = embeddingImporterService.importEmbeddings(relativePath);

        assertThat(imported).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject path traversal attacks")
    void shouldRejectPathTraversalAttacks() {
        // Test various path traversal attempts
        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings("../../../etc/passwd")
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Path traversal detected");

        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings("..\\..\\..\\windows\\system32\\config\\sam")
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Path traversal detected");

        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings("data/../../../etc/passwd")
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Path traversal detected");
    }

    @Test
    @DisplayName("Should reject files outside working directory")
    void shouldRejectFilesOutsideWorkingDirectory() {
        // Test absolute path outside working directory
        final String absolutePath;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            absolutePath = "C:\\Windows\\System32\\config\\sam";
        } else {
            absolutePath = "/etc/passwd";
        }
        
        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings(absolutePath)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("File path must be within application directory");
    }

    @Test
    @DisplayName("Should reject files without .json extension")
    void shouldRejectFilesWithoutJsonExtension() throws IOException {
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        File nonJsonFile = new File(dataDir, "test-file.txt");
        Files.write(nonJsonFile.toPath(), "test content".getBytes());

        final String relativePath = "data/" + nonJsonFile.getName();
        assertThatThrownBy(() -> 
            embeddingImporterService.importEmbeddings(relativePath)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("File must have .json extension");
    }

    @Test
    @DisplayName("Should accept valid relative paths within working directory")
    void shouldAcceptValidRelativePaths() throws IOException {
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        dataDir.mkdirs();
        File validFile = new File(dataDir, "valid-embeddings.json");
        
        String jsonContent = """
            {
              "version": "1.0",
              "model": "embed-multilingual-v3.0",
              "total_verses": 1,
              "verses": [
                {
                  "book": "Matthew",
                  "chapter": 1,
                  "verse": 1,
                  "reference": "Matthew 1:1",
                  "text": "The book of the genealogy of Jesus Christ",
                  "translation": "World English Bible",
                  "embedding": [0.1, 0.2, 0.3, 0.4, 0.5]
                }
              ]
            }
            """;
        Files.write(validFile.toPath(), jsonContent.getBytes());

        // Should work with relative path
        int imported = embeddingImporterService.importEmbeddings("data/valid-embeddings.json");
        assertThat(imported).isEqualTo(1);
    }
}

