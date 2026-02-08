# Quick Guide: Import Bible Embeddings on Render

## Where to Set Environment Variables

⚠️ **IMPORTANT:** Set `SPRING_PROFILES_ACTIVE=local` in the **Backend Service**, NOT in the database!

### Steps:

1. **Go to Render Dashboard**
2. **Select your Backend Service** (the Java/Spring Boot service, not PostgreSQL database)
3. **Click "Environment" tab**
4. **Add Environment Variable:**
   - Key: `SPRING_PROFILES_ACTIVE`
   - Value: `local`
5. **Save** - Render will restart automatically

### Visual Guide:

```
Render Dashboard
├── Your Backend Service (Java/Spring Boot) ← SET IT HERE
│   ├── Environment tab
│   │   └── Add: SPRING_PROFILES_ACTIVE=local
│   └── Shell tab (for file uploads)
│
└── Your Database (PostgreSQL) ← NOT HERE!
    └── (No environment variables needed)
```

## Quick Import Steps

1. **Upload file to cloud storage** (Google Drive, Dropbox, etc.)
2. **Set `SPRING_PROFILES_ACTIVE=local`** in backend service environment
3. **Wait for service restart**
4. **In Render Shell:**
   ```bash
   mkdir -p data
   cd data
   curl -L "YOUR_CLOUD_STORAGE_URL" -o bible_embeddings.json
   ```
5. **Import via API:**
   ```bash
   curl -X POST "https://your-backend.onrender.com/api/admin/bible/import-embeddings?filePath=data/bible_embeddings.json"
   ```
6. **Verify:**
   ```bash
   curl "https://your-backend.onrender.com/api/admin/bible/status"
   ```
7. **Remove `SPRING_PROFILES_ACTIVE=local`** from environment variables

## Why Backend Service, Not Database?

- The environment variable controls which **Spring Boot application profile** is active
- This affects which **Java code** runs (admin endpoints)
- The database is just storage - it doesn't run Spring Boot code
- Environment variables in the backend service are read by the Java application at startup

## Troubleshooting

**Q: I set it in the database, but it's not working?**  
A: Set it in the **backend service** environment variables, not the database.

**Q: Where do I find the backend service?**  
A: In Render dashboard, look for your Java/Spring Boot service (usually named something like "chatbot-backend" or similar).

**Q: How do I know if it worked?**  
A: After setting the variable, Render will restart your service. Check the logs - you should see Spring Boot starting with profile "local".

