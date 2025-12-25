# Bible Data Setup Guide

This guide explains how to load Bible data and generate embeddings for the Christian Content Analysis feature.

## Overview

The Bible data consists of:
- **~31,000 Bible verses** from Old and New Testament (World English Bible)
- **Embeddings** for semantic search (1024-dimensional vectors using Cohere)

## Automatic Loading

By default, Bible data is **automatically loaded** when the application starts if it's not already in the database.

Configuration in `application.yml`:
```yaml
app:
  bible:
    auto-load: true  # Automatically load data if missing
    auto-generate-embeddings: false  # Don't auto-generate (too expensive!)
```

## Manual Setup Steps

### Step 1: Start the Application

The Bible data will be automatically loaded on first startup. Check the logs:

```
Checking if Bible data needs to be loaded...
Bible data not found. Starting to load Bible data from JSON files...
✅ Successfully loaded 31102 Bible verses into database
```

### Step 2: Check Status

```bash
curl http://localhost:8081/api/admin/bible/status
```

Response:
```json
{
  "dataLoaded": true,
  "totalVerses": 31102,
  "versesWithEmbeddings": 0,
  "versesWithoutEmbeddings": 31102,
  "embeddingsReady": false,
  "embeddingsPercentage": 0.0
}
```

### Step 3: Generate Embeddings

⚠️ **WARNING**: This is expensive and time-consuming!
- **Time**: 30-60 minutes for ~31,000 verses
- **Cost**: Significant Cohere API credits
- **Rate Limits**: May hit rate limits, will retry automatically

**Option A: Via Admin Endpoint (Recommended)**

```bash
# Start the generation (will run in background)
curl -X POST http://localhost:8081/api/admin/bible/generate-embeddings
```

**Option B: Via Application Config**

Set in `application.yml`:
```yaml
app:
  bible:
    auto-generate-embeddings: true  # ⚠️ Not recommended for production!
```

Then restart the application.

### Step 4: Monitor Progress

```bash
# Check progress
curl http://localhost:8081/api/admin/bible/embedding-progress
```

Response:
```json
{
  "totalVerses": 31102,
  "versesWithEmbeddings": 5000,
  "versesWithoutEmbeddings": 26102,
  "percentage": 16.09,
  "completed": false
}
```

### Step 5: Verify Completion

```bash
curl http://localhost:8081/api/admin/bible/status
```

When `embeddingsReady: true`, you're done!

## Testing Christian Content Analysis

### Prerequisites

1. Bible data loaded ✅
2. Embeddings generated ✅
3. A chatbot with analyzed website content

### Test the Analysis

1. **Create a chatbot** (if you don't have one):
```bash
curl -X POST http://localhost:8081/api/chatbots/onboarding \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=your-session-id" \
  -d '{"websiteUrl": "https://example.com"}'
```

2. **Wait for website analysis** to complete (check chatbot status)

3. **Run Christian Content Analysis**:
```bash
curl -X POST "http://localhost:8081/api/chatbots/1/analyze-christian-content?maxVerses=20&similarityThreshold=0.5" \
  -H "Cookie: JSESSIONID=your-session-id"
```

Response:
```json
{
  "chatbotId": 1,
  "websiteUrl": "https://example.com",
  "relevantVerses": [
    {
      "id": 123,
      "reference": "Matthew 5:16",
      "book": "Matthew",
      "chapter": 5,
      "verse": 16,
      "text": "Let your light shine before others...",
      "translation": "World English Bible",
      "similarity": 0.85,
      "similarityPercentage": 85
    }
  ],
  "averageSimilarity": 0.72,
  "totalVersesAnalyzed": 31102,
  "versesAboveThreshold": 15
}
```

## Importing Pre-Generated Embeddings (Recommended)

If you have a pre-generated `bible_embeddings.json` file (e.g., from Google Colab), you can import it instead of generating embeddings via API. This is **much faster and cheaper**!

See **[EMBEDDINGS_IMPORT_RENDER.md](./EMBEDDINGS_IMPORT_RENDER.md)** for detailed instructions on uploading and importing the 662MB embeddings file on Render.

**Quick Summary:**
1. Upload `bible_embeddings.json` to Render (via cloud storage or Render Shell)
2. Temporarily enable import: Set `APP_BIBLE_ALLOW_IMPORT=true` in Render environment variables
3. Call import endpoint: `POST /api/admin/bible/import-embeddings?filePath=data/bible_embeddings.json`
4. Verify import: `GET /api/admin/bible/status`
5. **Important:** Disable `APP_BIBLE_ALLOW_IMPORT` after import for security!

## Admin Endpoints

All admin endpoints require ADMIN role and are available when:
- Running with `local` or `test` profile, OR
- `APP_BIBLE_ALLOW_IMPORT=true` is set (for production imports)

Endpoints:
- `GET /api/admin/bible/status` - Check Bible data and embedding status
- `POST /api/admin/bible/load-data` - Manually load Bible data
- `POST /api/admin/bible/generate-embeddings` - Generate embeddings for all verses (expensive!)
- `GET /api/admin/bible/embedding-progress` - Check embedding generation progress
- `POST /api/admin/bible/import-embeddings?filePath=...` - Import embeddings from JSON file

## Troubleshooting

### Data Not Loading

- Check that JSON files exist: `backend/src/main/resources/data/bible/data/*.json`
- Check application logs for errors
- Verify database connection

### Embeddings Generation Failing

- Check Cohere API key is set: `COHERE_API_KEY`
- Check API rate limits (may need to wait)
- Check application logs for specific errors
- Verify network connectivity to Cohere API

### Analysis Not Working

- Verify embeddings are generated: `embeddingsReady: true`
- Verify chatbot has analyzed website content
- Check similarity threshold (try lowering it)
- Check application logs for errors

## Cost Estimation

**Embedding Generation:**
- ~31,000 verses × Cohere API cost per embedding
- Check current Cohere pricing: https://cohere.com/pricing
- Estimated: $X-XX depending on model and volume discounts

**Analysis Queries:**
- Each analysis compares website content against all verses
- Uses cosine similarity (no API calls needed after embeddings are generated)
- Very fast once embeddings are in database

## Production Recommendations

1. **Load data once** during initial deployment
2. **Generate embeddings manually** via admin endpoint (not auto)
3. **Monitor API costs** during embedding generation
4. **Backup database** after embeddings are generated
5. **Set `auto-load: false`** after initial setup to speed up startup

