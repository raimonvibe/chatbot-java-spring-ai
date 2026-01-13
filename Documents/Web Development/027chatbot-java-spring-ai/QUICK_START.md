# Quick Start Guide - Docker Database Setup

## 🚀 3-Step Quick Start

### Step 1: Start the Database

**Double-click** `start-database.bat` in the project root

OR run in terminal:
```bash
cd "C:\Users\rober\Documents\Web Development\027chatbot-java-spring-ai"
start-database.bat
```

**What it does**:
- Starts PostgreSQL with pgvector extension in Docker
- Creates `chatbot_db` database
- Enables pgvector extension
- Maps to `localhost:5432`

**Expected output**:
```
SUCCESS! Database started successfully

Database Details:
- Container: chatbot-postgres-dev
- Database: chatbot_db
- Host: localhost:5432
- Username: postgres
- Password: postgres
- Extension: pgvector (auto-enabled)
```

---

### Step 2: Verify Database (Optional)

**Double-click** `verify-database.bat`

This checks:
- ✅ Container is running
- ✅ Container is healthy
- ✅ pgvector extension is enabled
- ✅ Database is accessible

---

### Step 3: Start the Application

Open a new terminal and run:
```bash
cd "C:\Users\rober\Documents\Web Development\027chatbot-java-spring-ai\backend"
mvn spring-boot:run -DskipTests
```

**What to look for in logs**:
```
✅ CohereEmbeddingModel created successfully!
✅ PgVectorStore created successfully!
✅ EmbeddingModel bean found
✅ VectorStore bean found
Started AiChatbotApplication in X.XXX seconds
```

---

## ✅ Success Verification

After the application starts, verify everything works:

### 1. Check Application Logs

Look for these SUCCESS messages:
- `🔧 STARTING embeddingModel() @Bean method`
- `✅ CohereEmbeddingModel created successfully!`
- `🔧 STARTING vectorStore() @Bean method`
- `✅ PgVectorStore created successfully!`
- `Started AiChatbotApplication`

### 2. Check Database Tables

Open a new terminal:
```bash
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db
```

In psql, run:
```sql
-- List all tables
\dt

-- Should see:
-- vector_store (for embeddings)
-- users, chatbots, conversations, messages, etc.

-- Check vector_store structure
\d vector_store

-- Exit
\q
```

### 3. Test the Application

1. Open browser: `http://localhost:3000` (frontend) or `http://localhost:8081` (backend API)
2. Create a test chatbot
3. Analyze a website
4. Ask questions about the website

### 4. Verify Vectors are Stored

```bash
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db -c "SELECT COUNT(*) FROM vector_store;"
```

Should show number of indexed document chunks.

---

## 🛠️ Common Tasks

### View Database Logs
```bash
docker compose -f docker-compose.dev.yml logs -f postgres
```

### Stop Database
**Double-click** `stop-database.bat`

OR:
```bash
docker compose -f docker-compose.dev.yml stop
```

### Restart Database
```bash
docker compose -f docker-compose.dev.yml restart
```

### Connect to Database (psql)
```bash
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db
```

### Remove Database and Data
⚠️ **Warning**: This deletes all data!
```bash
docker compose -f docker-compose.dev.yml down -v
```

---

## ❓ Troubleshooting

### Docker Not Found

**Problem**: `docker: command not found`

**Solution**:
1. Install Docker Desktop: https://www.docker.com/products/docker-desktop
2. Start Docker Desktop
3. Wait for Docker to fully start (whale icon in system tray)
4. Run `start-database.bat` again

### Port 5432 Already in Use

**Problem**: `port is already allocated`

**Solution**:

**Option A**: Stop other PostgreSQL service
1. Open Services (Win + R → `services.msc`)
2. Find "PostgreSQL" service
3. Right-click → Stop

**Option B**: Change port in `docker-compose.dev.yml`:
```yaml
ports:
  - "5433:5432"  # Use 5433 instead
```

Then update database URL in:
- `backend/.env`
- `backend/src/main/resources/application-local.yml`

Change `localhost:5432` to `localhost:5433`

### Container Not Healthy

**Problem**: Container status shows "unhealthy"

**Solution**:
```bash
# View logs
docker compose -f docker-compose.dev.yml logs postgres

# Restart container
docker compose -f docker-compose.dev.yml restart

# If still failing, recreate
docker compose -f docker-compose.dev.yml down
docker compose -f docker-compose.dev.yml up -d
```

### Application Can't Connect

**Problem**: `Connection refused` or `Connection timeout`

**Checklist**:
1. ✅ Is Docker Desktop running?
2. ✅ Is container healthy? Run `verify-database.bat`
3. ✅ Wait 10-30 seconds after starting container
4. ✅ Check `backend/.env` has correct URL: `jdbc:postgresql://localhost:5432/chatbot_db`
5. ✅ Check `SPRING_PROFILES_ACTIVE=local` in `backend/.env`

---

## 📋 What Was Set Up

### Files Created

```
027chatbot-java-spring-ai/
├── docker-compose.dev.yml          # Docker Compose configuration
├── start-database.bat              # Start database script
├── stop-database.bat               # Stop database script
├── verify-database.bat             # Verify database script
├── QUICK_START.md                  # This file
├── DOCKER_DATABASE_SETUP.md        # Detailed documentation
└── backend/
    ├── data/
    │   └── init.sql                # Database initialization
    ├── .env                        # Environment variables (Docker DB)
    └── src/main/resources/
        └── application-local.yml   # Spring config (Docker DB)
```

### Configuration Updated

- ✅ H2 removed from runtime (test only)
- ✅ PostgreSQL set as default database
- ✅ pgvector extension auto-enabled
- ✅ Spring AI 1.0.0 API compatibility fixed
- ✅ Comprehensive diagnostics added
- ✅ BeanDebugger component added

---

## 🎯 Next Steps

After successful setup:

1. **Test vector search** - Create a chatbot and analyze a website
2. **Check indexed content** - Query `vector_store` table
3. **Run tests** - `mvn test` in backend directory
4. **Develop features** - Database persists between restarts

---

## 📚 Additional Documentation

- **Detailed Setup**: See `DOCKER_DATABASE_SETUP.md`
- **Troubleshooting Results**: See `PGVECTOR_TROUBLESHOOTING_RESULTS.md`
- **Original Plan**: See `PGVECTOR_TROUBLESHOOTING_PLAN.md`

---

## 🆘 Need Help?

If issues persist:

1. Check Docker Desktop is running (whale icon in system tray)
2. Run `verify-database.bat` and share output
3. View application logs when starting Spring Boot
4. Check database logs: `docker compose -f docker-compose.dev.yml logs postgres`

---

**Status**: ✅ Ready to use
**Time to setup**: ~5 minutes
**Data persistence**: Yes (Docker volume)
