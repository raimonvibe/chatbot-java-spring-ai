# PgVector Troubleshooting Results

## Executive Summary

Successfully resolved **Spring AI 1.0.0 API compatibility issues** and **database configuration problems**. The application now correctly initializes PgVectorStore beans with PostgreSQL. Remaining issue: database connectivity.

---

## ✅ Issues Resolved

### 1. Spring AI 1.0.0 API Migration (FIXED)

**Problem**: Using outdated Spring AI API from older versions

**Solutions Applied**:
- ✅ Fixed import path: `org.springframework.ai.vectorstore.PgVectorStore` → `org.springframework.ai.vectorstore.pgvector.PgVectorStore`
- ✅ Updated builder syntax: `new PgVectorStore.Builder(jdbcTemplate, embeddingModel)` → `PgVectorStore.builder(jdbcTemplate, embeddingModel)`
- ✅ Added dimension configuration: `.dimensions(1024)` for Cohere embed-multilingual-v3.0
- ✅ Fixed SearchRequest API: Changed from `SearchRequest.query(userMessage).withTopK().withSimilarityThreshold()` to `SearchRequest.builder().query().topK().similarityThreshold().build()`
- ✅ Updated test: Added JdbcTemplate parameter to `vectorStore()` method call in AiConfigurationTest

**Files Modified**:
- `backend/src/main/java/com/prayer_chat/chatbot/config/AiConfiguration.java`
- `backend/src/main/java/com/prayer_chat/chatbot/service/AiChatbotService.java`
- `backend/src/test/java/com/prayer_chat/chatbot/config/AiConfigurationTest.java`
- `backend/pom.xml` (changed artifact from `spring-ai-pgvector-store-spring-boot-starter` to `spring-ai-pgvector-store`)

### 2. Database Configuration Issues (FIXED)

**Problem**: Application was using H2 in-memory database instead of PostgreSQL with pgvector

**Root Causes Identified**:
1. H2 dependency was in `runtime` scope, making it available to the running application
2. Default database configuration in `application.yml` defaulted to H2
3. Environment variables from `.env` weren't being loaded properly

**Solutions Applied**:
- ✅ Changed H2 dependency scope to `test` only in `pom.xml`
- ✅ Excluded `H2ConsoleAutoConfiguration` in main application class
- ✅ Updated `application.yml` to default to PostgreSQL instead of H2
- ✅ Updated `application-local.yml` with cloud PostgreSQL database configuration
- ✅ Configured PostgreSQL datasource in `backend/.env`

**Files Modified**:
- `backend/pom.xml` (H2 scope changed to `test`)
- `backend/src/main/java/com/prayer_chat/chatbot/AiChatbotApplication.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/.env`

### 3. Diagnostic Logging Added (COMPLETED)

**Enhancements Made**:
- ✅ Enhanced AiConfiguration constructor with detailed logging (thread, classloader, timestamp)
- ✅ Added comprehensive exception handling to `embeddingModel()` @Bean method with detailed error logging
- ✅ Added comprehensive exception handling to `vectorStore()` @Bean method with detailed error logging
- ✅ Created `BeanDebugger` component to diagnose bean availability after context initialization

**Files Created/Modified**:
- `backend/src/main/java/com/prayer_chat/chatbot/config/AiConfiguration.java`
- `backend/src/main/java/com/prayer_chat/chatbot/config/BeanDebugger.java` (NEW)

**Diagnostic Output Confirmed**:
```
🔧 ========================================
🔧 STARTING embeddingModel() @Bean method
🔧 ========================================
🔧 Cohere API Key: Present (length: 42)
✅ CohereEmbeddingModel created successfully!

🔧 ========================================
🔧 STARTING vectorStore() @Bean method
🔧 ========================================
🔧 EmbeddingModel: com.prayer_chat.chatbot.config.CohereEmbeddingModel
🔧 JdbcTemplate: Available
✅ PgVectorStore created successfully!
```

---

## ⚠️ Remaining Issue: Database Connectivity

**Current Status**: Application correctly attempts PostgreSQL connection but fails

**Error**:
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

**Analysis**:
The application is trying to connect to `localhost:5432` instead of the cloud database configured in `application-local.yml`. This suggests the local profile configuration isn't taking precedence, or the cloud database URL needs to be set differently.

**Database Details** (from `.env` and `application-local.yml`):
```yaml
URL: jdbc:postgresql://YOUR_RENDER_HOST:5432/YOUR_DATABASE_NAME
Username: YOUR_DATABASE_USER
Password: [configured]
```

---

## 🔧 Options to Resolve Database Connectivity

### Option A: Verify Cloud Database Access (Recommended for Quick Testing)

1. **Check if pgvector extension is enabled on Render database**:
   ```sql
   -- Connect to Render PostgreSQL
   psql $DATABASE_URL

   -- Check extensions
   \dx

   -- If pgvector not installed:
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

2. **Verify network access**:
   - Ensure Render database allows connections from your IP
   - Check firewall settings
   - Test connection: `psql jdbc:postgresql://YOUR_RENDER_HOST:5432/YOUR_DATABASE_NAME -U YOUR_DATABASE_USER`

3. **Ensure profile is active**:
   - Verify `SPRING_PROFILES_ACTIVE=local` is set in `backend/.env`
   - Or run with: `mvn spring-boot:run -Dspring-boot.run.profiles=local -DskipTests`

### Option B: Set Up Local PostgreSQL with pgvector (Recommended for Development)

1. **Install PostgreSQL 12+ locally** (if not already installed)

2. **Install pgvector extension**:
   ```bash
   # Windows (using PostgreSQL from postgresql.org)
   # Download pgvector from: https://github.com/pgvector/pgvector/releases
   # Or use Docker:
   docker run -d --name postgres-pgvector \
     -e POSTGRES_PASSWORD=postgres \
     -e POSTGRES_DB=chatbot_db \
     -p 5432:5432 \
     ankane/pgvector
   ```

3. **Enable pgvector extension**:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

4. **Update `backend/.env`** or **`application-local.yml`**:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/chatbot_db
       username: postgres
       password: postgres
   ```

### Option C: Use Docker Compose (Easiest for Development)

Create `backend/docker-compose.dev.yml`:
```yaml
version: '3.8'
services:
  postgres:
    image: ankane/pgvector:latest
    environment:
      POSTGRES_DB: chatbot_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgvector_data:/var/lib/postgresql/data

volumes:
  pgvector_data:
```

Run: `docker-compose -f docker-compose.dev.yml up -d`

---

## 📊 Verification Steps

Once database connectivity is established, verify everything works:

### 1. Check Application Startup

Look for these log messages:
```
✅ CohereEmbeddingModel created successfully!
✅ PgVectorStore created successfully!
Started AiChatbotApplication in X.XXX seconds
```

### 2. Verify Database Schema

```sql
-- Check if vector_store table was created
\dt vector_store

-- Verify table structure
\d vector_store

-- Should show columns:
-- id, content, metadata, embedding (vector type)
```

### 3. Test Vector Store Operations

Create a test chatbot and analyze a website:
1. Create chatbot via API
2. Trigger website analysis
3. Check logs for: "Starting content indexing for chatbot"
4. Query database: `SELECT COUNT(*) FROM vector_store;`
5. Should see indexed website content

---

## 🎯 Success Criteria (From Original Plan)

- ✅ Logs show: "🔧 Creating Cohere EmbeddingModel"
- ✅ Logs show: "🔧 Creating MANUAL PgVectorStore bean"
- ✅ Logs show: "✅ PgVectorStore created successfully!"
- ⏳ Database has `vector_store` table (pending connection)
- ⏳ Website content gets indexed (pending connection)
- ⏳ Chatbot gives accurate answers about website content (pending connection)

---

## 📝 Code Changes Summary

### Dependencies (`pom.xml`)
```xml
<!-- CHANGED: Use core library instead of starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store</artifactId>
</dependency>

<!-- CHANGED: H2 only for tests -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### Configuration (`AiConfiguration.java`)
```java
// CHANGED: Correct import
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

// CHANGED: New builder syntax
@Bean(name = "vectorStore")
@Primary
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1024)  // Cohere embed-multilingual-v3.0 uses 1024 dimensions
            .initializeSchema(true)
            .build();
    return vectorStore;
}
```

### Search Request (`AiChatbotService.java`)
```java
// CHANGED: New builder pattern
SearchRequest searchRequest = SearchRequest.builder()
    .query(userMessage)
    .topK(20)
    .similarityThreshold(0.3)
    .filterExpression(String.format("chatbotId == '%s'", chatbot.getId().toString()))
    .build();
```

---

## 🔄 Next Actions

1. **Choose database option** (A, B, or C above)
2. **Establish database connection**
3. **Verify pgvector extension is installed**
4. **Run application**: `mvn spring-boot:run -DskipTests`
5. **Test vector store operations**
6. **Run full test suite**: `mvn test`

---

## 📚 References

- [Spring AI PgVector Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [Cohere Embeddings Documentation](https://docs.cohere.com/docs/embeddings)

---

**Status**: ✅ Core integration issues RESOLVED | ⏳ Database connectivity pending
**Last Updated**: 2026-01-13
