# Importing Bible Embeddings on Render

This guide explains how to upload and import the 662MB `bible_embeddings.json` file into your Render database.

## Overview

The embeddings file (`data/bible_embeddings.json`) contains pre-generated embeddings for ~31,000 Bible verses. Instead of generating them via API (expensive and time-consuming), you can import them directly.

**File Size:** ~662MB  
**Verses:** ~31,000  
**Format:** JSON with embeddings array

## Prerequisites

1. ✅ Bible verses must already be loaded in the database
2. ✅ You have access to Render dashboard
3. ✅ You have the `bible_embeddings.json` file locally

## Step 1: Verify Bible Data is Loaded

First, ensure Bible verses are in the database:

```bash
# Check status (if admin endpoint is available)
curl https://your-backend.onrender.com/api/admin/bible/status
```

Or check your database directly:
```sql
SELECT COUNT(*) FROM bible_verses;
-- Should be ~31,000
```

## Step 2: Upload File to Render

### Option A: Using Render Shell (Recommended)

1. **Open Render Shell:**
   - Go to your Render dashboard
   - Select your backend service
   - Click "Shell" tab
   - This opens a terminal session

2. **Create data directory:**
   ```bash
   mkdir -p data
   cd data
   ```

3. **Upload file using SCP or SFTP:**
   ```bash
   # From your local machine, upload the file
   scp data/bible_embeddings.json render@your-service.onrender.com:/opt/render/project/src/data/
   ```
   
   **Note:** Render doesn't support direct SCP. Use Option B instead.

### Option B: Using Render File System (Temporary)

1. **Enable local profile temporarily:**
   - In Render dashboard, go to Environment variables
   - Add: `SPRING_PROFILES_ACTIVE=local`
   - This enables admin endpoints

2. **Upload via Render Shell:**
   ```bash
   # In Render Shell, create data directory
   mkdir -p /opt/render/project/src/data
   
   # Use a file transfer method (see below)
   ```

3. **Transfer file using base64 (for small chunks) or use a cloud storage:**
   
   **Better approach:** Upload to cloud storage first, then download:
   ```bash
   # Upload to Google Drive, Dropbox, or S3
   # Then download in Render Shell:
   curl -L "https://your-cloud-storage-url/bible_embeddings.json" -o data/bible_embeddings.json
   ```

### Option C: Use Cloud Storage (Best for Large Files)

1. **Upload to cloud storage:**
   - Upload `bible_embeddings.json` to Google Drive, Dropbox, S3, or similar
   - Make it publicly accessible (temporarily) or use signed URL

2. **Download in Render Shell:**
   ```bash
   # In Render Shell
   mkdir -p data
   cd data
   
   # Download from cloud storage
   curl -L "https://your-cloud-storage-url/bible_embeddings.json" -o bible_embeddings.json
   
   # Verify file size
   ls -lh bible_embeddings.json
   # Should show ~662M
   ```

## Step 3: Import Embeddings

### Method 1: Using Admin Endpoint (Temporary Local Profile)

1. **Temporarily enable local profile:**
   - In Render dashboard, go to Environment variables
   - Add: `SPRING_PROFILES_ACTIVE=local`
   - **Important:** This enables admin endpoints - remove after import!

2. **Redeploy/Restart** your service (Render will restart automatically)

3. **Call import endpoint:**
   ```bash
   curl -X POST "https://your-backend.onrender.com/api/admin/bible/import-embeddings?filePath=data/bible_embeddings.json" \
     -H "Cookie: JSESSIONID=your-session-id" \
     -u admin:password
   ```

   **Note:** You'll need to authenticate with a user that has ADMIN role.

4. **After import completes:**
   - Remove `SPRING_PROFILES_ACTIVE=local` from environment variables
   - Service will restart and admin endpoints will be disabled

### Method 2: Using Database Direct Import (Advanced)

If you can't use the admin endpoint, you can create a one-time migration script:

1. **Create a Spring Boot CommandLineRunner:**
   ```java
   @Component
   @Profile("import-embeddings")
   public class EmbeddingImportRunner implements CommandLineRunner {
       // Use EmbeddingImporterService to import
   }
   ```

2. **Run with profile:**
   ```bash
   java -jar app.jar --spring.profiles.active=import-embeddings
   ```

### Method 3: Direct Database Import (Advanced)

If you have direct database access, you could write a custom SQL script, but this is complex due to the JSON structure of embeddings. The admin endpoint method is recommended.

## Step 4: Verify Import

```bash
# Check status
curl https://your-backend.onrender.com/api/admin/bible/status
```

Expected response:
```json
{
  "dataLoaded": true,
  "totalVerses": 31105,
  "versesWithEmbeddings": 31105,
  "versesWithoutEmbeddings": 0,
  "embeddingsReady": true,
  "embeddingsPercentage": 100.0
}
```

## Step 5: Clean Up

1. **Remove local profile** (security):
   - Remove `SPRING_PROFILES_ACTIVE=local` from environment variables
   - Or set it back to `prod`

2. **Delete file from Render** (optional, saves disk space):
   ```bash
   rm data/bible_embeddings.json
   ```

3. **Disable import endpoint** if you created a temporary one

## Troubleshooting

### File Not Found

- Verify file path: `data/bible_embeddings.json` (relative to app working directory)
- Check file exists: `ls -lh data/bible_embeddings.json` in Render Shell
- Verify file size: Should be ~662MB

### Import Fails

- Check logs in Render dashboard
- Verify Bible verses are loaded first
- Check file format matches expected structure
- Verify database connection

### Out of Memory

- The import processes in batches (1000 verses at a time)
- If memory issues occur, you may need to increase Render instance size
- Or split the import into smaller chunks

### Authentication Issues

- Admin endpoints require ADMIN role
- Check your user has ADMIN role in database
- Or use a temporary import script instead

## Security Notes

⚠️ **Important:**
- Admin endpoints should only be enabled temporarily
- Remove `local` profile after import
- Don't commit credentials
- Use environment variables for sensitive config

## Alternative: Generate Embeddings via API

If importing fails, you can generate embeddings via API:

```bash
curl -X POST https://your-backend.onrender.com/api/admin/bible/generate-embeddings
```

**Warning:** This is expensive and takes 30-60 minutes!

## File Structure

The `bible_embeddings.json` file should have this structure:

```json
{
  "version": "1.0",
  "model": "embed-multilingual-v3.0",
  "total_verses": 31105,
  "verses": [
    {
      "book": "Genesis",
      "chapter": 1,
      "verse": 1,
      "reference": "Genesis 1:1",
      "text": "In the beginning, God created...",
      "translation": "World English Bible",
      "embedding": [0.024, 0.050, -0.012, ...]
    }
  ]
}
```

## Next Steps

After importing embeddings:
1. ✅ Verify all verses have embeddings
2. ✅ Test Christian Content Analysis feature
3. ✅ Remove temporary admin access
4. ✅ Update documentation

