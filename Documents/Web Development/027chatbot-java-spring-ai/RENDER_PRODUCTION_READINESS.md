# Render Production Deployment - Readiness Checklist

## 🎉 PgVector Integration Complete

All code changes for PgVector integration have been completed, tested, and pushed to GitHub.

**Repository**: https://github.com/raimonvibe/chatbot-java-spring-ai
**Latest Commit**: PgVector integration fixes and Docker database setup

---

## ✅ What's Ready for Production

### 1. Spring AI 1.0.0 Compatibility
- ✅ Updated PgVectorStore import to pgvector subpackage
- ✅ Fixed builder pattern for PgVectorStore
- ✅ Fixed SearchRequest API to use builder pattern
- ✅ Added 1024 dimensions for Cohere embeddings

### 2. Database Configuration
- ✅ PostgreSQL datasource properly configured
- ✅ H2 removed from runtime (test scope only)
- ✅ PgVector extension support enabled
- ✅ Application defaults to PostgreSQL

### 3. Bean Configuration
- ✅ EmbeddingModel bean with Cohere embed-multilingual-v3.0
- ✅ VectorStore bean with PgVectorStore (pgvector extension)
- ✅ ChatModel bean with Anthropic Claude
- ✅ Comprehensive diagnostic logging

### 4. Code Quality
- ✅ All tests updated for new API
- ✅ Exception handling added to bean creation
- ✅ Comprehensive error logging
- ✅ Production secrets sanitized from repository

---

## 🔧 Render Configuration Required

### Environment Variables to Set in Render

**Database (PostgreSQL with pgvector)**:
```bash
# Render will provide these - use your PostgreSQL database URL
DATABASE_URL=jdbc:postgresql://YOUR_RENDER_DB_HOST:5432/YOUR_DB_NAME
DATABASE_USERNAME=YOUR_DB_USER
DATABASE_PASSWORD=YOUR_DB_PASSWORD
DATABASE_DRIVER=org.postgresql.Driver
```

**API Keys**:
```bash
# Anthropic Claude API
ANTHROPIC_API_KEY=your_anthropic_api_key_here

# Cohere Embeddings API
COHERE_API_KEY=your_cohere_api_key_here

# Stripe (for payments)
STRIPE_API_KEY=your_stripe_secret_key_here
STRIPE_WEBHOOK_SECRET=your_stripe_webhook_secret_here
```

**OAuth Configuration**:
```bash
# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id_here
GOOGLE_CLIENT_SECRET=your_google_client_secret_here

# OAuth Redirect URI (set to your Render domain)
OAUTH2_REDIRECT_URI=https://YOUR_APP_NAME.onrender.com/login/oauth2/code/google
```

**Application Configuration**:
```bash
# Spring Profile
SPRING_PROFILES_ACTIVE=production

# Server Port (Render uses 10000 by default)
SERVER_PORT=10000

# CORS (set to your frontend domain)
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.vercel.app

# JWT Secret (generate a secure random string)
JWT_SECRET=your_secure_jwt_secret_minimum_256_bits

# Session Configuration
SPRING_SESSION_STORE_TYPE=jdbc
```

---

## 📋 Pre-Deployment Checklist

### Database Setup

- [ ] **Create PostgreSQL Database on Render**
  - Go to Render Dashboard → New → PostgreSQL
  - Choose a plan (Free tier available)
  - Note down the database connection details

- [ ] **Enable pgvector Extension**
  ```sql
  -- Connect to your Render PostgreSQL database
  psql YOUR_DATABASE_URL

  -- Enable pgvector extension
  CREATE EXTENSION IF NOT EXISTS vector;

  -- Verify extension is enabled
  \dx

  -- Exit
  \q
  ```

- [ ] **Verify Database Connection**
  - Test connection from your local machine
  - Ensure firewall allows connections from Render

### Application Deployment

- [ ] **Create Web Service on Render**
  - Go to Render Dashboard → New → Web Service
  - Connect to your GitHub repository: `raimonvibe/chatbot-java-spring-ai`
  - Select the `main` branch

- [ ] **Configure Build Settings**
  - **Build Command**:
    ```bash
    cd backend && mvn clean package -DskipTests
    ```
  - **Start Command**:
    ```bash
    cd backend && java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar
    ```
  - **Root Directory**: Leave empty (or set to `/` if needed)

- [ ] **Set Environment Variables**
  - Add all environment variables listed above
  - Use Render's PostgreSQL internal connection URL for DATABASE_URL
  - Ensure API keys are set correctly

- [ ] **Configure Health Check**
  - **Health Check Path**: `/actuator/health`
  - Spring Boot Actuator will respond with application health status

### Post-Deployment Verification

- [ ] **Check Application Logs**
  - Look for successful startup messages:
    ```
    ✅ CohereEmbeddingModel created successfully!
    ✅ PgVectorStore created successfully!
    Started AiChatbotApplication in X.XXX seconds
    ```

- [ ] **Verify Database Connection**
  - Check logs for HikariCP connection pool initialization
  - Ensure no connection errors

- [ ] **Test Endpoints**
  - Health check: `https://your-app.onrender.com/actuator/health`
  - API root: `https://your-app.onrender.com/`

- [ ] **Verify Vector Store**
  ```sql
  -- Connect to Render database
  psql YOUR_DATABASE_URL

  -- Check if vector_store table exists
  \dt vector_store

  -- View table structure
  \d vector_store

  -- Should show: id, content, metadata, embedding (vector type)
  ```

---

## 🔒 Security Checklist

- [x] **No secrets in repository**
  - All production credentials removed from code
  - `.env` files in `.gitignore`
  - Documentation sanitized

- [ ] **Environment variables secured**
  - API keys stored in Render environment variables
  - Database credentials not hardcoded
  - JWT secret is strong and unique

- [ ] **CORS configured**
  - Only allow trusted frontend origins
  - Set `CORS_ALLOWED_ORIGINS` to your frontend domain

- [ ] **OAuth2 redirect URIs updated**
  - Google OAuth consent screen updated with Render URL
  - Redirect URIs match Render deployment URL

- [ ] **HTTPS enabled**
  - Render provides automatic HTTPS
  - Ensure all API calls use HTTPS

---

## 🚀 Deployment Steps

### 1. Enable pgvector on Render Database

```bash
# Get your database connection URL from Render dashboard
# Connect using psql
psql YOUR_RENDER_DATABASE_URL

# Enable extension
CREATE EXTENSION IF NOT EXISTS vector;

# Verify
SELECT * FROM pg_available_extensions WHERE name = 'vector';

# Exit
\q
```

### 2. Deploy to Render

1. Push latest code to GitHub (already done ✅)
2. Go to Render Dashboard
3. Create new Web Service
4. Connect to GitHub repository
5. Configure build and start commands
6. Set environment variables
7. Deploy

### 3. Monitor First Deployment

Watch the deployment logs for:
- ✅ Build completion
- ✅ Application startup
- ✅ Bean creation success messages
- ✅ Database connection establishment
- ✅ Health check passing

### 4. Post-Deployment Testing

Test these endpoints:
```bash
# Health check
curl https://your-app.onrender.com/actuator/health

# Create a test chatbot (requires authentication)
curl -X POST https://your-app.onrender.com/api/chatbots \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "Test Bot",
    "description": "Production test",
    "websiteUrl": "https://example.com"
  }'
```

---

## 📊 Expected Behavior

### Successful Deployment Logs

```
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
HikariPool-1 - Start completed.

🔧 AiConfiguration constructor called
📋 Anthropic API Key present: true
📋 Cohere API Key present: true

🔧 STARTING embeddingModel() @Bean method
✅ CohereEmbeddingModel created successfully!

🔧 STARTING vectorStore() @Bean method
🔧 Creating MANUAL PgVectorStore bean
🔧 EmbeddingModel: com.prayer_chat.chatbot.config.CohereEmbeddingModel
🔧 JdbcTemplate: Available
✅ PgVectorStore created successfully!

✅ EmbeddingModel bean found: com.prayer_chat.chatbot.config.CohereEmbeddingModel
✅ VectorStore bean found: org.springframework.ai.vectorstore.pgvector.PgVectorStore

Started AiChatbotApplication in 7.174 seconds
```

### Database Tables Created

After successful deployment, these tables should exist:
- `vector_store` - Pgvector embeddings (auto-created by PgVectorStore)
- `users` - User accounts
- `chatbots` - Chatbot configurations
- `conversations` - Chat conversations
- `messages` - Chat messages
- `website_content` - Crawled website content
- `bible_verses` - Bible data
- `spring_session` - Session storage
- `subscriptions` - User subscriptions

---

## 🛠️ Troubleshooting

### Issue: "Cannot find symbol: class PgVectorStore"

**Status**: ✅ FIXED
- Updated import to `org.springframework.ai.vectorstore.pgvector.PgVectorStore`

### Issue: "Builder constructor not found"

**Status**: ✅ FIXED
- Changed to `PgVectorStore.builder()` static method

### Issue: "Connection refused to localhost:5432"

**Solution**: Ensure `DATABASE_URL` environment variable is set to Render PostgreSQL URL

### Issue: "CREATE EXTENSION vector failed"

**Solution**:
1. Connect to Render database with psql
2. Run: `CREATE EXTENSION IF NOT EXISTS vector;`
3. Verify pgvector is available in your PostgreSQL version

### Issue: "H2 database being used instead of PostgreSQL"

**Status**: ✅ FIXED
- H2 dependency scope changed to `test`
- Application defaults to PostgreSQL

---

## 📚 Documentation

All documentation is available in the repository:

- `QUICK_START.md` - Local development quick start
- `DOCKER_DATABASE_SETUP.md` - Docker database setup guide
- `SUCCESS_REPORT.md` - Integration success verification
- `PGVECTOR_TROUBLESHOOTING_RESULTS.md` - Issues fixed and solutions
- `DATABASE_SETUP_README.md` - Documentation index

---

## ✅ Production Readiness Summary

| Component | Status | Notes |
|-----------|--------|-------|
| Code Quality | ✅ Ready | All Spring AI 1.0.0 fixes applied |
| Bean Configuration | ✅ Ready | EmbeddingModel and VectorStore beans working |
| Database Config | ✅ Ready | PostgreSQL with pgvector configured |
| Tests | ✅ Passing | Updated for new API |
| Security | ✅ Ready | No secrets in repository |
| Documentation | ✅ Complete | Full setup and troubleshooting guides |
| GitHub | ✅ Pushed | Latest code available |
| Render Config | ⏳ Pending | Requires environment variables setup |

---

## 🎯 Next Steps for You

1. **Create Render PostgreSQL Database**
   - Enable pgvector extension

2. **Create Render Web Service**
   - Connect to GitHub repository
   - Configure build/start commands

3. **Set Environment Variables**
   - Database connection
   - API keys (Anthropic, Cohere, Stripe)
   - OAuth credentials
   - CORS and JWT secrets

4. **Deploy and Monitor**
   - Watch logs for successful startup
   - Verify health endpoint
   - Test vector store functionality

5. **Frontend Configuration**
   - Update frontend to use Render API URL
   - Configure OAuth redirect URIs

---

## 📞 Support

If you encounter any issues during deployment:

1. Check application logs in Render dashboard
2. Verify all environment variables are set correctly
3. Ensure pgvector extension is enabled on database
4. Review documentation in repository

---

**Status**: ✅ Code is production-ready and pushed to GitHub
**Action Required**: Configure Render environment and deploy

Generated: 2026-01-13
