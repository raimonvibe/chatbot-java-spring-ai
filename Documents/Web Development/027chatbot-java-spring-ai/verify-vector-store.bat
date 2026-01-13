@echo off
REM Verify vector_store table was created

echo ===============================================
echo Verifying PgVector Integration
echo ===============================================
echo.

cd /d "%~dp0"

echo [1/3] Checking vector_store table exists...
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db -c "\dt vector_store"
echo.

echo [2/3] Viewing vector_store table structure...
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db -c "\d vector_store"
echo.

echo [3/3] Counting vectors in database...
docker exec -it chatbot-postgres-dev psql -U postgres -d chatbot_db -c "SELECT COUNT(*) as vector_count FROM vector_store;"
echo.

echo ===============================================
echo PgVector Verification Complete!
echo ===============================================
echo.
echo The vector_store table should show:
echo - id (uuid)
echo - content (text)
echo - metadata (jsonb)
echo - embedding (vector)
echo.
echo After you analyze a website, run this script again
echo to see the vectors that were stored.
echo.
pause
