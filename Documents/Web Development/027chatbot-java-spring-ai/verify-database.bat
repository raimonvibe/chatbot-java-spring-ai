@echo off
REM Verify PostgreSQL database is running and pgvector is enabled

echo ===============================================
echo Database Verification Script
echo ===============================================
echo.

cd /d "%~dp0"

REM Check if container is running
echo [1/4] Checking if container is running...
docker compose -f docker-compose.dev.yml ps | findstr "chatbot-postgres-dev"
if errorlevel 1 (
    echo ERROR: Container is not running
    echo Run start-database.bat first
    pause
    exit /b 1
)
echo OK - Container is running
echo.

REM Check health status
echo [2/4] Checking health status...
docker compose -f docker-compose.dev.yml ps
echo.

REM Check if pgvector extension is enabled
echo [3/4] Checking pgvector extension...
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
echo.

REM List all tables (will be empty until app runs)
echo [4/4] Listing database tables...
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db -c "\dt"
echo.
echo Note: Tables will be created when you start the Spring Boot application
echo.

echo ===============================================
echo Database Connection Details:
echo ===============================================
echo Host:     localhost:5432
echo Database: chatbot_db
echo Username: postgres
echo Password: postgres
echo Extension: pgvector enabled
echo.
echo To connect manually:
echo docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db
echo.
pause
