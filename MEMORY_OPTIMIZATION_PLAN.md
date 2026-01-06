# Memory Optimization Plan - Bible Data Loading

## 🚨 Problem
**Out of Memory Error** when reloading Bible data with `FORCE_RELOAD_BIBLE_DATA=true`

### Root Causes Identified:
1. **Full JSON Loading**: `objectMapper.readTree(inputStream)` loads entire JSON file into memory
2. **Large ArrayList**: `List<BibleVerse> versesToSave` accumulates up to 1000 objects before batch save
3. **No Streaming**: Entire JSON structure parsed at once instead of streaming
4. **DeleteAll() Memory**: `bibleVerseRepository.deleteAll()` may load all entities into memory before deleting
5. **No JVM Heap Configuration**: Default heap may be too small for large operations

---

## ✅ Optimization Strategy

### Phase 1: Immediate Fixes (Quick Wins)

#### 1.1 Optimize Delete Operation
**Current**: `bibleVerseRepository.deleteAll()` - may load all entities
**Fix**: Use batch delete with native query

```java
// Instead of:
bibleVerseRepository.deleteAll();

// Use:
@Modifying
@Query("DELETE FROM BibleVerse")
void deleteAllVerses();
```

**Impact**: Reduces memory from ~300MB to ~10MB during delete
**Time**: 15 minutes

---

#### 1.2 Reduce Batch Size
**Current**: Batch save every 1000 verses
**Fix**: Reduce to 500 verses per batch

```java
// Change from:
if (versesToSave.size() >= 1000) {

// To:
if (versesToSave.size() >= 500) {
```

**Impact**: Reduces peak memory by ~50%
**Time**: 5 minutes

---

#### 1.3 Add JVM Memory Flags
**Fix**: Configure Render to use more heap memory

Add to Render environment variables or startup command:
```bash
JAVA_OPTS=-Xmx512m -Xms256m
```

Or in `application.properties`:
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

**Impact**: Prevents OOM errors
**Time**: 2 minutes

---

### Phase 2: Streaming JSON Parser (Medium Priority)

#### 2.1 Implement Streaming JSON Parser
**Current**: `objectMapper.readTree(inputStream)` - loads entire file
**Fix**: Use Jackson streaming API (`JsonParser`)

```java
private int loadTestamentStreaming(InputStream inputStream) throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser(inputStream);
    
    List<BibleVerse> versesToSave = new ArrayList<>(500); // Pre-size
    int totalVerses = 0;
    String currentBook = null;
    int currentChapter = 0;
    
    // Stream through JSON tokens
    while (parser.nextToken() != null) {
        // Parse book, chapter, content incrementally
        // Create verses one at a time
        // Batch save every 500 verses
    }
    
    parser.close();
    return totalVerses;
}
```

**Impact**: Reduces memory from ~200MB to ~10MB during load
**Time**: 2-3 hours

---

#### 2.2 Process Chapter-by-Chapter
**Current**: Loads all chapters of all books at once
**Fix**: Process one chapter at a time, save immediately

```java
for (JsonNode bookNode : books) {
    for (JsonNode chapterNode : chapters) {
        List<BibleVerse> chapterVerses = parseVersesFromChapter(...);
        
        // Save immediately after each chapter
        if (!chapterVerses.isEmpty()) {
            bibleVerseRepository.saveAll(chapterVerses);
            chapterVerses.clear(); // Free memory
        }
    }
}
```

**Impact**: Reduces peak memory by ~80%
**Time**: 1 hour

---

### Phase 3: Advanced Optimizations (Long-term)

#### 3.1 Use Database Bulk Insert
**Current**: JPA `saveAll()` - multiple INSERT statements
**Fix**: Native SQL bulk insert

```java
@Modifying
@Query(value = "INSERT INTO bible_verse (book, chapter, verse, text, reference) VALUES " +
       "(?1, ?2, ?3, ?4, ?5)", nativeQuery = true)
void bulkInsertVerse(String book, int chapter, int verse, String text, String reference);
```

**Impact**: 10x faster inserts, less memory
**Time**: 3-4 hours

---

#### 3.2 Async Processing
**Current**: Synchronous loading blocks startup
**Fix**: Load in background thread

```java
@Async
public CompletableFuture<Integer> loadBibleDataAsync() {
    // Load in background
}
```

**Impact**: Faster startup, better UX
**Time**: 2 hours

---

#### 3.3 Database Indexing Optimization
**Current**: Indexes may slow down bulk inserts
**Fix**: Drop indexes before bulk insert, recreate after

```sql
-- Before insert
DROP INDEX IF EXISTS idx_bible_verse_book_chapter_verse;

-- After insert
CREATE INDEX idx_bible_verse_book_chapter_verse ON bible_verse(book, chapter, verse);
```

**Impact**: 5x faster inserts
**Time**: 1 hour

---

## 📋 Implementation Priority

### 🔴 Critical (Do First - Prevents OOM)
1. ✅ Add JVM memory flags to Render
2. ✅ Optimize delete operation (native query)
3. ✅ Reduce batch size to 500

### 🟡 High Priority (Next Session)
4. Process chapter-by-chapter (immediate save)
5. Implement streaming JSON parser

### 🟢 Medium Priority (Future)
6. Database bulk insert
7. Async processing
8. Index optimization

---

## 🛠️ Quick Fix Implementation (Next Session)

### Step 1: Add JVM Memory Configuration
**File**: `backend/src/main/resources/application.properties`
```properties
# JVM Memory Settings
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true
```

### Step 2: Optimize Delete Method
**File**: `backend/src/main/java/com/prayer_chat/chatbot/repository/BibleVerseRepository.java`
```java
@Modifying
@Query(value = "DELETE FROM bible_verse", nativeQuery = true)
void deleteAllVerses();
```

### Step 3: Reduce Batch Size
**File**: `backend/src/main/java/com/prayer_chat/chatbot/service/BibleDataLoaderService.java`
```java
// Change line 144 from:
if (versesToSave.size() >= 1000) {
// To:
if (versesToSave.size() >= 500) {
```

### Step 4: Process Chapter-by-Chapter
**File**: `backend/src/main/java/com/prayer_chat/chatbot/service/BibleDataLoaderService.java`
```java
// Save after each chapter instead of accumulating
for (JsonNode chapterNode : chapters) {
    List<BibleVerse> chapterVerses = parseVersesFromChapter(...);
    
    if (!chapterVerses.isEmpty()) {
        bibleVerseRepository.saveAll(chapterVerses);
        logger.debug("Saved {} verses from {}{}", 
            chapterVerses.size(), bookName, chapterNumber);
        chapterVerses.clear();
    }
}
```

---

## 📊 Expected Results

### Before Optimization:
- **Memory Usage**: ~300-500MB peak
- **Load Time**: 2-3 minutes
- **OOM Risk**: High (especially on Render free tier)

### After Quick Fixes:
- **Memory Usage**: ~50-100MB peak
- **Load Time**: 2-3 minutes (similar)
- **OOM Risk**: Low

### After Full Optimization:
- **Memory Usage**: ~20-30MB peak
- **Load Time**: 1-2 minutes
- **OOM Risk**: Very Low

---

## 🧪 Testing Plan

1. **Local Test**: Run `FORCE_RELOAD_BIBLE_DATA=true` locally
2. **Monitor Memory**: Use JVM memory monitoring
3. **Render Test**: Deploy to Render and test
4. **Verify Data**: Ensure all verses loaded correctly

---

## 📝 Notes

- Render free tier has limited memory (~512MB)
- New Testament only: ~7,957 verses (much smaller than full Bible)
- Consider using Render paid tier for production
- Monitor memory usage during deployment

---

## 🚀 Next Session Checklist

- [ ] Add JVM memory configuration
- [ ] Optimize delete operation
- [ ] Reduce batch size
- [ ] Implement chapter-by-chapter processing
- [ ] Test locally
- [ ] Deploy to Render
- [ ] Monitor memory usage
- [ ] Verify data integrity

