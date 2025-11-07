-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create custom types (will be used in migrations)
DO $$ BEGIN
    CREATE TYPE parser_type AS ENUM (
        'ANDROID_DOCS',
        'KOTLIN_LANG',
        'MKDOCS',
        'GITHUB',
        'GENERIC_HTML'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE content_type AS ENUM (
        'API_REFERENCE',
        'GUIDE',
        'TUTORIAL',
        'RELEASE_NOTES',
        'CODE_SAMPLE',
        'OVERVIEW',
        'ARTICLE',
        'UNKNOWN'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE crawl_status AS ENUM (
        'PENDING',
        'IN_PROGRESS',
        'SUCCESS',
        'FAILED',
        'DISABLED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE mcp_db TO mcp_user;
GRANT ALL ON SCHEMA public TO mcp_user;
