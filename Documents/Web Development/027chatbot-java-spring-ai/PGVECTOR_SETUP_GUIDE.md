# PgVector Setup Guide

This guide explains how to enable and configure PostgreSQL with pgvector extension for persistent vector storage.

## What Changed?

We migrated from **SimpleVectorStore** (in-memory, non-persistent) to **PgVectorStore** (PostgreSQL-based, persistent).

### Benefits:
- ✅ **Persistent Storage**: Vectors survive application restarts
- ✅ **Scalability**: Works across multiple server instances
- ✅ **Production-Ready**: Scales to millions of vectors
- ✅ **Fast Similarity Search**: Uses HNSW indexing for efficient searches
- ✅ **No Code Changes**: Same Cohere embeddings, just different storage

### What Stayed the Same:
- ✅ **Cohere Embeddings**: Still using `embed-multilingual-v3.0` (excellent model!)
- ✅ **Embedding Dimensions**: 1024 dimensions
- ✅ **API**: Same VectorStore interface, no changes to existing code

---

## Prerequisites

You need PostgreSQL with the **pgvector extension** installed.

---

## Setup Instructions

### Option 1: Local Development with Docker (Recommended)

1. **Stop your current PostgreSQL** (if running):
   ```bash
   docker stop postgres
   ```

2. **Start PostgreSQL with pgvector**:
   ```bash
   docker run -d \
     --name postgres-pgvector \
     -e POSTGRES_DB=chatbotdb \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=password \
     -p 5432:5432 \
     pgvector/pgvector:pg16
   ```

3. **Update your `.env` file** (or environment variables):
   ```env
   DATABASE_URL=jdbc:postgresql://localhost:5432/chatbotdb
   DATABASE_DRIVER=org.postgresql.Driver
   DATABASE_USERNAME=postgres
   DATABASE_PASSWORD=password
   HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
   ```

4. **Enable pgvector extension**:

   Option A - Using psql:
   ```bash
   docker exec -it postgres-pgvector psql -U postgres -d chatbotdb
   ```
   Then run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   \dx  -- Verify extension is installed
   \q   -- Exit psql
   ```

   Option B - Using SQL script:
   ```bash
   docker cp backend/src/main/resources/db/migration/V1__enable_pgvector_extension.sql postgres-pgvector:/tmp/
   docker exec -it postgres-pgvector psql -U postgres -d chatbotdb -f /tmp/V1__enable_pgvector_extension.sql
   ```

5. **Start your application**:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   Spring AI's PgVectorStore will automatically create the `vector_store` table on startup!

---

### Option 2: Existing PostgreSQL (Add pgvector)

If you already have PostgreSQL running:

1. **Install pgvector extension**:

   **Ubuntu/Debian**:
   ```bash
   sudo apt install postgresql-16-pgvector
   ```

   **macOS (Homebrew)**:
   ```bash
   brew install pgvector
   ```

   **Windows**: Download from [pgvector GitHub releases](https://github.com/pgvector/pgvector/releases)

2. **Enable extension in your database**:
   ```bash
   psql -U postgres -d chatbotdb
   ```
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
   ```

3. **Update `.env`** with your PostgreSQL credentials

4. **Restart your application**

---

### Option 3: Render (Production)

Render's PostgreSQL instances **already have pgvector installed**! 🎉

1. **Go to Render Dashboard** → Your PostgreSQL instance

2. **Connect via Shell** (or use a SQL client):
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

3. **Update Environment Variables** on your Web Service:
   - `DATABASE_URL`: (already set by Render)
   - `DATABASE_DRIVER`: `org.postgresql.Driver`
   - `DATABASE_USERNAME`: (from Render PostgreSQL)
   - `DATABASE_PASSWORD`: (from Render PostgreSQL)
   - `HIBERNATE_DIALECT`: `org.hibernate.dialect.PostgreSQLDialect`

4. **Deploy**: Push to GitHub or trigger manual deploy

That's it! PgVectorStore will auto-create tables on startup.

---

### Option 4: Other Managed PostgreSQL Services

| Service | pgvector Support | Instructions |
|---------|------------------|--------------|
| **AWS RDS** | ✅ Available | Enable in Parameter Groups → `shared_preload_libraries` → Add `vector` → Reboot → `CREATE EXTENSION vector;` |
| **Supabase** | ✅ Pre-enabled | No setup needed! Already active |
| **Azure Database** | ✅ Available | Flexible Server tier → Extensions → Enable `vector` |
| **Google Cloud SQL** | ✅ Available | Cloud SQL → Extensions → Enable `vector` |
| **DigitalOcean** | ✅ Available | Database → Settings → Extensions → Enable `vector` |
| **Railway** | ✅ Available | Requires PostgreSQL 12+ plugin, then `CREATE EXTENSION vector;` |

---

## Verify Installation

### 1. Check Extension Status
```sql
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';
```

Expected output:
```
 extname | extversion
---------+------------
 vector  | 0.7.0
```

### 2. Check Table Creation
After starting your app, verify the `vector_store` table exists:
```sql
\dt vector_store
```

Expected output:
```
 Schema |     Name      | Type  |  Owner
--------+---------------+-------+----------
 public | vector_store  | table | postgres
```

### 3. View Table Schema
```sql
\d vector_store
```

Expected output:
```
                          Table "public.vector_store"
   Column   |  Type   | Collation | Nullable |           Default
------------+---------+-----------+----------+------------------------------
 id         | uuid    |           | not null | gen_random_uuid()
 content    | text    |           |          |
 metadata   | json    |           |          |
 embedding  | vector(1024) |      |          |   -- 1024 dimensions for Cohere
```

### 4. Test Vector Operations
```sql
-- Sample similarity search
SELECT id, content, 1 - (embedding <=> '[0,1,0,...]'::vector) AS similarity
FROM vector_store
ORDER BY embedding <=> '[0,1,0,...]'::vector
LIMIT 5;
```

---

## Troubleshooting

### Error: "extension 'vector' does not exist"
**Solution**: Install pgvector extension on your PostgreSQL server (see setup instructions above)

### Error: "could not load library 'vector'"
**Solution**: Your PostgreSQL version may not support pgvector. Upgrade to PostgreSQL 12+ or use Docker image `pgvector/pgvector:pg16`

### Error: "relation 'vector_store' does not exist"
**Cause**: Spring AI didn't auto-create the table

**Solution**:
1. Check logs for errors during startup
2. Ensure `.initializeSchema(true)` is set in `AiConfiguration.java`
3. Verify database connection is working: Check `/actuator/health`

### Vectors Not Persisting After Restart
**Cause**: Still using H2 in-memory database

**Solution**:
1. Verify `DATABASE_URL` points to PostgreSQL (not H2)
2. Check `application.yml` → `spring.datasource.url`
3. Look for `jdbc:postgresql://` in the URL (not `jdbc:h2:mem:`)

### Slow Similarity Searches
**Cause**: No index on embedding column

**Solution**: Create HNSW or IVFFlat index:
```sql
-- HNSW index (faster, more accurate, higher memory usage)
CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);

-- IVFFlat index (faster inserts, lower memory)
CREATE INDEX ON vector_store USING ivfflat (embedding vector_cosine_ops);
```

Spring AI auto-creates indexes, but you can optimize them based on your data size.

---

## Migration Path

### Step 1: Deploy with PgVector Enabled
1. Enable pgvector extension in PostgreSQL
2. Deploy new code
3. Application will create empty `vector_store` table

### Step 2: Re-index Website Content
Since SimpleVectorStore was in-memory, you need to re-index all chatbot websites:

**Option A - Automatic (On Next Website Analysis)**:
- When users analyze websites, content will be automatically indexed to PostgreSQL
- No manual action needed

**Option B - Manual Bulk Re-indexing**:
Create an admin endpoint to re-index all existing chatbots:

```bash
POST /api/admin/reindex-all-chatbots
Authorization: Bearer <admin-token>
```

This will:
1. Load all `WebsiteContent` from database
2. Generate embeddings via Cohere
3. Store in PgVectorStore

### Step 3: Import Bible Embeddings (If Using)
If you're using Bible verse embeddings, they're stored in the `bible_verse` table with a separate `embedding` column. **This is independent of PgVectorStore** and will continue working as before.

No migration needed for Bible verses!

---

## Performance Tuning

### Optimal Settings for Different Data Sizes

| Vector Count | Index Type | Parameters | Expected Query Time |
|--------------|------------|------------|---------------------|
| < 10,000 | None | N/A | < 10ms |
| 10K - 100K | HNSW | `m=16, ef_construction=64` | < 20ms |
| 100K - 1M | HNSW | `m=32, ef_construction=128` | < 50ms |
| > 1M | IVFFlat | `lists=100` then HNSW after | < 100ms |

### Create Optimized Index
```sql
-- For up to 100K vectors (recommended for most use cases)
CREATE INDEX vector_store_embedding_hnsw_idx
ON vector_store
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- For 100K+ vectors
CREATE INDEX vector_store_embedding_hnsw_idx
ON vector_store
USING hnsw (embedding vector_cosine_ops)
WITH (m = 32, ef_construction = 128);
```

---

## Cost Implications

### Before (SimpleVectorStore):
- Storage: RAM (lost on restart)
- Cost: $0 (but not persistent)

### After (PgVectorStore):
- Storage: PostgreSQL disk space
- Cost: ~$0.10/GB/month (e.g., Render)

**Estimate**:
- 1024-dimensional vector = ~4 KB
- 10,000 vectors = ~40 MB
- 100,000 vectors = ~400 MB
- 1,000,000 vectors = ~4 GB

**Typical Usage**:
- Small chatbot (10 pages): ~50 KB
- Medium chatbot (100 pages): ~500 KB
- Large chatbot (1000 pages): ~5 MB

**For most users, this adds < $1/month in database costs.**

---

## Rollback Plan

If you need to rollback to SimpleVectorStore:

1. **Revert code changes**:
   ```bash
   git checkout HEAD~1 backend/src/main/java/com/prayer_chat/chatbot/config/AiConfiguration.java
   git checkout HEAD~1 backend/pom.xml
   ```

2. **Restart application**

Note: You'll lose all indexed vectors (they were in-memory anyway).

---

## FAQ

**Q: Do I need to change my application code?**
A: No! VectorStore interface is the same. Spring AI handles everything.

**Q: Will my existing Bible embeddings still work?**
A: Yes! Bible verses are stored in the `bible_verse` table separately. PgVectorStore only affects website content vectors.

**Q: Can I use both SimpleVectorStore and PgVectorStore?**
A: Not recommended. Choose one. PgVectorStore is production-ready.

**Q: What about vector search performance?**
A: PgVectorStore with HNSW indexing is actually **faster** than SimpleVectorStore for large datasets!

**Q: Do I need to change my Cohere API setup?**
A: No! Same embeddings, same API key, same model. Only storage changed.

**Q: What if I'm using H2 for development?**
A: You can't use H2 with pgvector. Use Docker PostgreSQL for development (see Option 1 above).

---

## Next Steps

1. ✅ Enable pgvector extension in PostgreSQL
2. ✅ Update environment variables
3. ✅ Restart application
4. ✅ Verify `vector_store` table created
5. ✅ Test by creating a new chatbot and analyzing a website
6. ✅ Check that vectors persist after application restart

---

## Support

If you encounter issues:
1. Check application logs: `tail -f backend/logs/prayer-chat.log`
2. Verify database connection: `curl http://localhost:8081/actuator/health`
3. Check PostgreSQL logs: `docker logs postgres-pgvector`
4. Review this guide's Troubleshooting section

For production deployments on Render:
- Monitor: Render Dashboard → Logs
- Health check: `https://your-app.onrender.com/actuator/health`

---

**Migration completed! Your chatbot now has persistent, production-ready vector storage! 🎉**
