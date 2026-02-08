# Post-Christmas Plan: Fix Embedding Import Memory Issue

## Problem
- **Render Free Tier**: 512MB RAM limit
- **Embeddings File**: 662MB JSON file
- **Current Issue**: `objectMapper.readTree()` loads entire file into memory → OutOfMemoryError
- **Result**: Service crashes when trying to import embeddings

## Root Cause
The `EmbeddingImporterService.importEmbeddings()` method uses:
```java
JsonNode root = objectMapper.readTree(new FileInputStream(file));
```
This loads the entire 662MB JSON file + all 31k verses into memory, exceeding the 512MB limit.

## Solutions (Priority Order)

### Option 1: Implement True Streaming JSON Parser (RECOMMENDED)
**Effort**: Medium | **Cost**: Free | **Time**: 2-3 hours

Use Jackson's streaming API to process one verse at a time:

```java
// Instead of loading entire file:
JsonNode root = objectMapper.readTree(file); // ❌ Loads 662MB into memory

// Use streaming parser:
JsonParser parser = jsonFactory.createParser(file);
// Process one verse at a time, never loading entire file
```

**Benefits**:
- Memory usage: ~10-50MB instead of 662MB+
- Works on Render free tier
- No code changes needed

**Implementation Steps**:
1. Replace `readTree()` with `JsonParser` streaming
2. Process verses one-by-one in a loop
3. Save in batches of 500-1000 verses
4. Test locally first, then deploy

**Files to Modify**:
- `backend/src/main/java/com/prayer_chat/chatbot/service/EmbeddingImporterService.java`

---

### Option 2: Upgrade Render Instance (QUICK FIX)
**Effort**: Low | **Cost**: $7-25/month | **Time**: 5 minutes

Upgrade from Free Tier (512MB) to:
- **Starter**: 1GB RAM ($7/month) - Should be enough
- **Standard**: 2GB RAM ($25/month) - More headroom

**Steps**:
1. Render Dashboard → Backend Service → Settings
2. Change Instance Type → Starter or Standard
3. Redeploy

**Benefits**:
- Immediate fix
- No code changes
- More headroom for future growth

**Drawbacks**:
- Monthly cost
- Still not ideal (should fix the root cause)

---

### Option 3: Split Import into Smaller Chunks
**Effort**: High | **Cost**: Free | **Time**: 4-6 hours

Split the 662MB file into smaller chunks (e.g., 50MB each) and import separately:

1. Create a script to split the JSON file
2. Import each chunk separately via API
3. Track progress in database

**Benefits**:
- Works on free tier
- Can resume if interrupted

**Drawbacks**:
- More complex
- Requires multiple API calls
- More error handling needed

---

### Option 4: Direct Database Import (ADVANCED)
**Effort**: Very High | **Cost**: Free | **Time**: 8+ hours

Bypass the Java application and import directly into PostgreSQL:

1. Use `psql` or Python script
2. Parse JSON and insert embeddings directly
3. No Spring Boot memory overhead

**Benefits**:
- Minimal memory usage
- Fastest option

**Drawbacks**:
- Requires database access
- More complex setup
- Bypasses application layer

---

## Recommended Approach

### Phase 1: Quick Fix (If Needed Immediately)
1. Upgrade to Render Starter (1GB RAM) - $7/month
2. This allows import to complete
3. Monitor memory usage

### Phase 2: Proper Fix (After Christmas)
1. Implement streaming JSON parser (Option 1)
2. Test locally with the 662MB file
3. Deploy to Render
4. Downgrade back to free tier if desired

---

## Implementation Details for Option 1 (Streaming Parser)

### Current Code (Memory Intensive):
```java
JsonNode root = objectMapper.readTree(new FileInputStream(file));
JsonNode verses = root.get("verses");
for (JsonNode verseNode : verses) {
    // Process verse
}
```

### New Code (Streaming):
```java
JsonFactory jsonFactory = new JsonFactory();
try (JsonParser parser = jsonFactory.createParser(new FileInputStream(file))) {
    // Skip to "verses" array
    while (parser.nextToken() != JsonToken.START_ARRAY) {
        // Find "verses" key
    }
    
    // Process each verse object
    while (parser.nextToken() == JsonToken.START_OBJECT) {
        JsonNode verseNode = objectMapper.readTree(parser);
        // Process one verse at a time
        // Save in batches
    }
}
```

### Key Changes:
1. Use `JsonParser` instead of `readTree()`
2. Process one verse at a time
3. Never load entire file into memory
4. Keep batch size at 500-1000 verses

---

## Testing Plan

1. **Local Test**:
   ```bash
   # Test with full 662MB file locally
   mvn spring-boot:run -Dspring.profiles.active=local
   # Call import endpoint
   # Monitor memory usage (should stay under 100MB)
   ```

2. **Render Test**:
   - Deploy to Render
   - Monitor memory usage in Render dashboard
   - Should stay well under 512MB

3. **Verification**:
   ```sql
   -- Check how many verses have embeddings
   SELECT COUNT(*) FROM bible_verse WHERE embedding IS NOT NULL;
   -- Should be ~31,000
   ```

---

## Timeline

- **Day 1**: Implement streaming parser (Option 1)
- **Day 2**: Test locally and on Render
- **Day 3**: Monitor and verify import completed

---

## Notes

- The current code has partial streaming implementation but still uses `readTree()` in some places
- Need to complete the streaming implementation
- Consider adding progress tracking for long-running imports
- Add timeout handling for very large imports

---

## Current Status

- ✅ Code cleanup done (removed `/add-admin-role` endpoint)
- ✅ Security hardened
- ⚠️ Import still fails due to memory limit
- ⬜ Streaming parser needs to be completed
- ⬜ Testing needed

---

## Next Steps After Christmas

1. Review this plan
2. Choose approach (recommend Option 1)
3. Implement streaming parser
4. Test thoroughly
5. Deploy and verify

