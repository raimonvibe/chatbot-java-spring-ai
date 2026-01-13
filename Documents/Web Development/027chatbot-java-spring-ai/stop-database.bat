@echo off
REM Stop PostgreSQL database

echo ===============================================
echo Stopping PostgreSQL Database
echo ===============================================
echo.

cd /d "%~dp0"

docker compose -f docker-compose.dev.yml stop

echo.
echo Database stopped. Data is preserved in Docker volume.
echo.
echo To start again: run start-database.bat
echo To remove all data: docker compose -f docker-compose.dev.yml down -v
echo.
pause
