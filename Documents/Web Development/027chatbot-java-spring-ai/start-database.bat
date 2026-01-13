@echo off
REM Start PostgreSQL with pgvector using Docker Compose
REM This script starts the development database

echo ===============================================
echo Starting PostgreSQL Database with pgvector
echo ===============================================
echo.

REM Navigate to project directory
cd /d "%~dp0"

REM Check if Docker is running
docker --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not running or not installed
    echo.
    echo Please:
    echo 1. Install Docker Desktop from https://www.docker.com/products/docker-desktop
    echo 2. Start Docker Desktop
    echo 3. Run this script again
    echo.
    pause
    exit /b 1
)

echo Docker found. Starting database...
echo.

REM Start the database
docker compose -f docker-compose.dev.yml up -d

if errorlevel 1 (
    echo.
    echo ERROR: Failed to start database
    echo.
    echo Troubleshooting:
    echo - Check if Docker Desktop is running
    echo - Check if port 5432 is available
    echo - View logs: docker compose -f docker-compose.dev.yml logs
    echo.
    pause
    exit /b 1
)

echo.
echo ===============================================
echo SUCCESS! Database started successfully
echo ===============================================
echo.
echo Database Details:
echo - Container: chatbot-postgres-dev
echo - Database: chatbot_db
echo - Host: localhost:5432
echo - Username: postgres
echo - Password: postgres
echo - Extension: pgvector (auto-enabled)
echo.
echo Waiting for database to be ready...
timeout /t 10 /nobreak >nul

REM Check health status
docker compose -f docker-compose.dev.yml ps

echo.
echo ===============================================
echo Next Steps:
echo ===============================================
echo 1. Verify database health above shows "healthy"
echo 2. Start the application:
echo    cd backend
echo    mvn spring-boot:run -DskipTests
echo.
echo Useful commands:
echo - View logs:    docker compose -f docker-compose.dev.yml logs -f
echo - Stop DB:      docker compose -f docker-compose.dev.yml stop
echo - Connect DB:   docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db
echo.
pause
