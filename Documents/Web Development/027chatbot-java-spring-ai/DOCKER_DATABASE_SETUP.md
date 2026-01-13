# Docker Database Setup Guide

Quick guide to set up PostgreSQL with pgvector extension using Docker Compose for local development.

---

## Prerequisites

- Docker Desktop installed and running
- Docker Compose (included with Docker Desktop)

**Check if Docker is running**:
```bash
docker --version
docker-compose --version
```

---

## Quick Start (3 Commands)

### 1. Start the Database
```bash
cd "C:\Users\rober\Documents\Web Development\027chatbot-java-spring-ai"
docker-compose -f docker-compose.dev.yml up -d
```

### 2. Verify Database is Running
```bash
docker-compose -f docker-compose.dev.yml ps
```

Expected output:
```
NAME                    STATUS
chatbot-postgres-dev    Up X seconds (healthy)
```

### 3. Start the Application
```bash
cd backend
mvn spring-boot:run -DskipTests
```

---

## What Gets Created

- **Container**: `chatbot-postgres-dev`
- **Database**: `chatbot_db`
- **Username**: `postgres`
- **Password**: `postgres`
- **Port**: `5432` (mapped to host)
- **Extension**: `pgvector` (auto-enabled)
- **Volume**: `pgvector_data` (persists data between restarts)

---

## Useful Commands

### View Database Logs
```bash
docker-compose -f docker-compose.dev.yml logs -f postgres
```

### Connect to Database (psql)
```bash
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db
```

Once connected, try these SQL commands:
```sql
-- Check if pgvector extension is enabled
\dx

-- List all tables
\dt

-- View vector_store table structure (after app creates it)
\d vector_store

-- Count vectors in database
SELECT COUNT(*) FROM vector_store;

-- Exit psql
\q
```

### Stop the Database
```bash
docker-compose -f docker-compose.dev.yml stop
```

### Start the Database (after stopping)
```bash
docker-compose -f docker-compose.dev.yml start
```

### Stop and Remove Everything (including volume)
```bash
docker-compose -f docker-compose.dev.yml down -v
```
⚠️ **Warning**: This deletes all data!

### Restart Database (reload init.sql)
```bash
docker-compose -f docker-compose.dev.yml down
docker-compose -f docker-compose.dev.yml up -d
```

---

## Verify PgVector Extension

After starting the database, verify pgvector is installed:

```bash
# Connect to database
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db

# Check extensions
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
```

Expected output:
```
 extname | extversion
---------+------------
 vector  | 0.8.1
```

---

## Troubleshooting

### Port 5432 Already in Use

If you have another PostgreSQL instance running:

**Option A**: Stop the other PostgreSQL service
- Windows: Services → PostgreSQL → Stop

**Option B**: Change the port in `docker-compose.dev.yml`:
```yaml
ports:
  - "5433:5432"  # Use port 5433 on host instead
```

Then update `backend/.env` and `application-local.yml`:
```properties
DATABASE_URL=jdbc:postgresql://localhost:5433/chatbot_db
```

### Container Won't Start

Check Docker Desktop is running and restart it if needed.

View detailed logs:
```bash
docker-compose -f docker-compose.dev.yml logs postgres
```

### Connection Refused Error

1. Check container is healthy:
   ```bash
   docker-compose -f docker-compose.dev.yml ps
   ```

2. Check logs for errors:
   ```bash
   docker-compose -f docker-compose.dev.yml logs postgres
   ```

3. Wait for healthcheck to pass (may take 10-30 seconds on first start)

### Data Persistence

Data is stored in a Docker volume named `pgvector_data`. This persists between container restarts.

To view volumes:
```bash
docker volume ls | findstr pgvector
```

To remove data (if needed):
```bash
docker-compose -f docker-compose.dev.yml down -v
```

---

## Application Integration

The application is already configured to use this Docker database:

**Configuration Files Updated**:
- ✅ `backend/.env` - Docker PostgreSQL connection
- ✅ `backend/src/main/resources/application-local.yml` - Docker PostgreSQL connection
- ✅ `backend/data/init.sql` - Enables pgvector extension

**Spring Boot Application**:
- Uses `SPRING_PROFILES_ACTIVE=local` profile (set in `backend/.env`)
- Connects to `localhost:5432/chatbot_db`
- Auto-creates tables with `ddl-auto=update`
- PgVectorStore creates `vector_store` table with `initializeSchema=true`

---

## Expected Application Startup

When you run `mvn spring-boot:run -DskipTests`, you should see:

```
🔧 ========================================
🔧 STARTING embeddingModel() @Bean method
✅ CohereEmbeddingModel created successfully!

🔧 ========================================
🔧 STARTING vectorStore() @Bean method
✅ PgVectorStore created successfully!

🔍 ========================================
🔍 BEAN DEBUGGER START
✅ EmbeddingModel bean found: com.prayer_chat.chatbot.config.CohereEmbeddingModel
✅ VectorStore bean found: org.springframework.ai.vectorstore.pgvector.PgVectorStore
🔍 ========================================

Started AiChatbotApplication in X.XXX seconds
```

---

## Database Schema Verification

After the application starts, check that tables were created:

```bash
# Connect to database
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db

# List all tables
\dt

# You should see:
# - spring_session (for OAuth2 sessions)
# - spring_session_attributes
# - vector_store (for embeddings)
# - users
# - chatbots
# - conversations
# - messages
# - website_content
# - ... and more

# Check vector_store table
\d vector_store

# Should show:
# - id (uuid)
# - content (text)
# - metadata (jsonb)
# - embedding (vector)
```

---

## Next Steps After Setup

1. ✅ **Start Docker database**: `docker-compose -f docker-compose.dev.yml up -d`
2. ✅ **Start Spring Boot app**: `cd backend && mvn spring-boot:run -DskipTests`
3. ✅ **Verify startup logs** show both beans created successfully
4. ✅ **Test the application**:
   - Create a chatbot
   - Analyze a website
   - Ask questions about the website content
5. ✅ **Check database** to see indexed vectors:
   ```sql
   SELECT COUNT(*) FROM vector_store;
   SELECT chatbot_id, url FROM website_content WHERE is_indexed = true;
   ```

---

## Production vs Development

**Development (Docker Compose)**:
- Database: `localhost:5432/chatbot_db`
- Data: Stored in Docker volume
- User: `postgres/postgres`
- Profile: `local`

**Production (Render)**:
- Database: Cloud PostgreSQL on Render
- Data: Stored in cloud
- User: Secure credentials from environment variables
- Profile: `production`

---

## Files Created/Modified

```
027chatbot-java-spring-ai/
├── docker-compose.dev.yml          # NEW: Docker Compose configuration
├── backend/
│   ├── data/
│   │   └── init.sql                # NEW: Database initialization script
│   ├── .env                        # UPDATED: Docker database config
│   └── src/main/resources/
│       └── application-local.yml   # UPDATED: Docker database config
```

---

**Status**: ✅ Ready to use
**Support**: Run `docker-compose -f docker-compose.dev.yml logs -f` for live logs
