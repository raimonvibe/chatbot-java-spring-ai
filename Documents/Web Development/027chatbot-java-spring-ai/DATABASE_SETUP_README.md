# Database Setup - README

This directory contains everything you need to set up PostgreSQL with pgvector for local development.

---

## 🎯 Start Here: QUICK_START.md

**👉 For the fastest setup, open `QUICK_START.md`**

It contains:
- 3-step quick start guide
- One-click scripts to run
- Success verification steps
- Common troubleshooting

---

## 📁 Files Overview

### Quick Start Scripts (Windows)
- **`start-database.bat`** - Start PostgreSQL with pgvector (double-click to run)
- **`stop-database.bat`** - Stop the database
- **`verify-database.bat`** - Verify everything is working

### Docker Configuration
- **`docker-compose.dev.yml`** - Docker Compose configuration for PostgreSQL + pgvector
- **`backend/data/init.sql`** - Database initialization (auto-enables pgvector)

### Documentation
- **`QUICK_START.md`** ⭐ - **Start here!** 3-step guide to get running
- **`DOCKER_DATABASE_SETUP.md`** - Detailed documentation and troubleshooting
- **`PGVECTOR_TROUBLESHOOTING_RESULTS.md`** - What issues were fixed and how
- **`PGVECTOR_TROUBLESHOOTING_PLAN.md`** - Original troubleshooting plan

### Application Configuration (Auto-updated)
- **`backend/.env`** - Environment variables (now uses Docker DB)
- **`backend/src/main/resources/application-local.yml`** - Spring config (now uses Docker DB)

---

## ⚡ Quick Setup (3 Steps)

### 1. Start Database
```bash
# Double-click this file:
start-database.bat

# OR run in terminal:
docker compose -f docker-compose.dev.yml up -d
```

### 2. Start Application
```bash
cd backend
mvn spring-boot:run -DskipTests
```

### 3. Verify Success
Look for these logs:
```
✅ CohereEmbeddingModel created successfully!
✅ PgVectorStore created successfully!
Started AiChatbotApplication
```

---

## 🔧 What Changed?

### Fixed Issues
1. ✅ **Spring AI 1.0.0 API** - Updated to correct imports and builder syntax
2. ✅ **Database Configuration** - Changed from H2 to PostgreSQL
3. ✅ **Bean Creation** - Now creates EmbeddingModel and VectorStore successfully
4. ✅ **Docker Setup** - Added PostgreSQL with pgvector extension

### New Features
- 🐳 Docker Compose configuration for local development
- 🔍 Comprehensive diagnostic logging
- 🛠️ One-click scripts for database management
- 📚 Detailed documentation

---

## 🎯 Current Status

| Component | Status | Details |
|-----------|--------|---------|
| Spring AI 1.0.0 API | ✅ Fixed | PgVectorStore uses correct imports and builder |
| Database Config | ✅ Fixed | PostgreSQL with pgvector |
| Bean Creation | ✅ Working | EmbeddingModel and VectorStore beans created |
| Docker Setup | ✅ Ready | PostgreSQL + pgvector on localhost:5432 |
| Documentation | ✅ Complete | Quick start + detailed guides |

---

## 📖 Documentation Index

**For different needs, read:**

| If you want to... | Read this file |
|-------------------|----------------|
| Get started quickly | `QUICK_START.md` ⭐ |
| Understand Docker setup | `DOCKER_DATABASE_SETUP.md` |
| Learn what was fixed | `PGVECTOR_TROUBLESHOOTING_RESULTS.md` |
| See original problem | `PGVECTOR_TROUBLESHOOTING_PLAN.md` |

---

## 🆘 Troubleshooting

**Database won't start?**
- Check Docker Desktop is running
- Run `verify-database.bat` to diagnose
- See `DOCKER_DATABASE_SETUP.md` → Troubleshooting section

**Application can't connect?**
- Verify database is healthy: `verify-database.bat`
- Check `backend/.env` has `DATABASE_URL=jdbc:postgresql://localhost:5432/chatbot_db`
- Wait 10-30 seconds after starting database

**Need detailed help?**
- See `DOCKER_DATABASE_SETUP.md` for comprehensive troubleshooting
- View database logs: `docker compose -f docker-compose.dev.yml logs -f`
- View application logs when starting Spring Boot

---

## 🎓 Learn More

- **pgvector**: https://github.com/pgvector/pgvector
- **Spring AI**: https://docs.spring.io/spring-ai/reference/
- **Docker Compose**: https://docs.docker.com/compose/

---

**Ready to start?** → Open `QUICK_START.md` and follow the 3 steps!
