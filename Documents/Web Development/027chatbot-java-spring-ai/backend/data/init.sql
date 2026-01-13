-- Initialize PostgreSQL database for AI Chatbot
-- This script runs automatically when the Docker container starts

-- Enable pgvector extension (required for vector similarity search)
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension is installed
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- The application will auto-create tables via Spring JPA with ddl-auto=update
-- vector_store table will be created automatically by PgVectorStore with initializeSchema=true

-- Log success
DO $$
BEGIN
  RAISE NOTICE 'pgvector extension enabled successfully';
  RAISE NOTICE 'Database ready for AI Chatbot application';
END $$;
