-- ============================================================================
-- Enable pgvector extension for PostgreSQL
-- ============================================================================
-- This migration enables the pgvector extension which provides vector data
-- types and similarity search functions for PostgreSQL.
--
-- IMPORTANT: This requires the pgvector extension to be installed on your
-- PostgreSQL server. For local development with Docker, use:
--   docker run -d --name postgres-pgvector -e POSTGRES_PASSWORD=password \
--     -p 5432:5432 pgvector/pgvector:pg16
--
-- For managed PostgreSQL services:
--   - AWS RDS: pgvector is available as an extension
--   - Render: pgvector is pre-installed on PostgreSQL instances
--   - Supabase: pgvector is enabled by default
--   - Azure: Available via Azure Database for PostgreSQL Flexible Server
--
-- After running this migration, Spring AI's PgVectorStore will automatically
-- create the vector_store table with the following schema:
--
--   CREATE TABLE IF NOT EXISTS vector_store (
--     id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
--     content text,
--     metadata json,
--     embedding vector(1024)  -- 1024 dimensions for Cohere embed-multilingual-v3.0
--   );
--
--   CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
--     ON vector_store USING ivfflat (embedding vector_cosine_ops);
--
-- ============================================================================

-- Enable pgvector extension (idempotent - safe to run multiple times)
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension is enabled
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

-- Log success message
DO $$
BEGIN
    RAISE NOTICE 'pgvector extension enabled successfully!';
    RAISE NOTICE 'Vector data type is now available for similarity search.';
    RAISE NOTICE 'Spring AI PgVectorStore will auto-create vector_store table on startup.';
END $$;
