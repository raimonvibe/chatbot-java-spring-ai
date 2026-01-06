# Generate Bible Embeddings in Google Colab (Free!)

This guide shows you how to generate Bible verse embeddings in Google Colab for free, then import them into your database.

## Why Google Colab?

- ✅ **Free** - No API costs for running the script
- ✅ **Fast** - Can use GPU/TPU if available
- ✅ **Easy** - Simple Python script, no Java setup needed
- ✅ **Flexible** - Can pause and resume if needed

## Step-by-Step Guide

### Step 1: Prepare Files

1. Download the Bible JSON files from your repository:
   - `backend/src/main/resources/data/bible/data/old-testament-data.json`
   - `backend/src/main/resources/data/bible/data/new-testament-data.json`

2. Download the Python script:
   - `scripts/generate-embeddings-colab.py`

### Step 2: Open Google Colab

1. Go to [Google Colab](https://colab.research.google.com/)
2. Create a new notebook
3. Upload the files:
   - Click the folder icon (📁) in the left sidebar
   - Upload the 2 JSON files and the Python script

### Step 3: Install Dependencies

In a Colab cell, run:

```python
!pip install cohere pandas tqdm
```

### Step 4: Set Your API Key

In a Colab cell, run:

```python
import os
os.environ['COHERE_API_KEY'] = 'your-cohere-api-key-here'
```

**Note:** You still need a Cohere API key, but Colab execution is free. Check Cohere's free tier limits.

### Step 5: Run the Script

In a Colab cell, run:

```python
exec(open('generate-embeddings-colab.py').read())
```

Or copy-paste the script content into a cell and run it.

### Step 6: Monitor Progress

The script will:
- Load all Bible verses from JSON files
- Generate embeddings in batches of 100
- Show progress with a progress bar
- Save results to `bible_embeddings.json`

**Estimated time:** 30-60 minutes for ~31,000 verses (depends on API rate limits)

### Step 7: Download Results

1. Once complete, download `bible_embeddings.json` from Colab
2. File will be ~50-100 MB (depending on embedding dimensions)

### Step 8: Import into Database

**Option A: Via Admin Endpoint (if implemented)**

```bash
curl -X POST "http://localhost:8081/api/admin/bible/import-embeddings?filePath=/path/to/bible_embeddings.json" \
  -H "Authorization: Bearer your-admin-token"
```

**Option B: Via Java Import Script**

1. Place `bible_embeddings.json` in the backend directory
2. Run the import service (needs to be implemented as a CommandLineRunner)

**Option C: Direct Database Import (PostgreSQL)**

If using PostgreSQL, you can import directly:

```sql
-- This would need a custom import script
-- The JSON structure matches the BibleVerse entity
```

## Script Configuration

You can customize the script:

```python
BATCH_SIZE = 100  # Increase for faster processing (max 96 for Cohere)
MODEL = "embed-multilingual-v3.0"  # Same as Java app
OUTPUT_FILE = "bible_embeddings.json"
```

## Cost Comparison

### Java App (Current Method)
- **Time:** 30-60 minutes
- **Cost:** Full Cohere API costs
- **Complexity:** Requires running Java app

### Google Colab (This Method)
- **Time:** 30-60 minutes (same)
- **Cost:** Only Cohere API costs (Colab is free)
- **Complexity:** Simple Python script
- **Bonus:** Can use GPU/TPU if available

## Troubleshooting

### API Rate Limits

If you hit rate limits, the script will retry. You can also:

```python
# Add longer delay between batches
time.sleep(2)  # Increase from 0.5 to 2 seconds
```

### Memory Issues

If Colab runs out of memory:

```python
# Process in smaller chunks
BATCH_SIZE = 50  # Reduce batch size
```

### Resume from Checkpoint

The script doesn't have checkpoint support yet, but you can modify it to:
1. Save progress periodically
2. Skip verses that already have embeddings
3. Resume from last checkpoint

## Output Format

The generated JSON file has this structure:

```json
{
  "version": "1.0",
  "model": "embed-multilingual-v3.0",
  "total_verses": 31102,
  "verses": [
    {
      "book": "Matthew",
      "chapter": 1,
      "verse": 1,
      "reference": "Matthew 1:1",
      "text": "The book of the genealogy...",
      "translation": "World English Bible",
      "embedding": [0.123, 0.456, ...]  // 1024 dimensions
    }
  ]
}
```

## Next Steps

After importing embeddings:

1. Verify import:
   ```bash
   curl http://localhost:8081/api/admin/bible/status
   ```

2. Should show:
   ```json
   {
     "embeddingsReady": true,
     "versesWithEmbeddings": 31102,
     "versesWithoutEmbeddings": 0
   }
   ```

3. Test Christian Content Analysis:
   - Create a chatbot
   - Analyze website
   - Run Christian Content Analysis
   - See ranked Bible verses!

## Benefits

✅ **Free execution** - No server costs  
✅ **Easy to use** - Simple Python script  
✅ **Flexible** - Can pause/resume  
✅ **Fast** - Can use GPU if available  
✅ **Same quality** - Uses same Cohere model as Java app  

Enjoy your free embedding generation! 🎉

