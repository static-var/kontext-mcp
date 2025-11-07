-- Source URLs table
CREATE TABLE source_urls (
    id SERIAL PRIMARY KEY,
    url TEXT NOT NULL UNIQUE,
    parser_type parser_type NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_crawled TIMESTAMP WITH TIME ZONE,
    etag TEXT,
    last_modified TEXT,
    status crawl_status NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_source_urls_status ON source_urls(status);
CREATE INDEX idx_source_urls_enabled ON source_urls(enabled);
CREATE INDEX idx_source_urls_last_crawled ON source_urls(last_crawled);

-- Documents table
CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    source_url TEXT NOT NULL,
    title TEXT NOT NULL,
    content_type content_type NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    last_indexed TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_url) REFERENCES source_urls(url) ON DELETE CASCADE
);

CREATE INDEX idx_documents_source_url ON documents(source_url);
CREATE INDEX idx_documents_content_type ON documents(content_type);
CREATE INDEX idx_documents_metadata ON documents USING GIN (metadata);
CREATE INDEX idx_documents_last_indexed ON documents(last_indexed);

-- Embeddings table with vector column
-- Using 1024 dimensions for BGE-large-en-v1.5
CREATE TABLE embeddings (
    id SERIAL PRIMARY KEY,
    document_id INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    token_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_embeddings_document_id ON embeddings(document_id);
CREATE INDEX idx_embeddings_metadata ON embeddings USING GIN (metadata);

-- Create HNSW index for vector similarity search
-- Using cosine distance (most common for embeddings)
-- m=16, ef_construction=64 are good defaults
CREATE INDEX idx_embeddings_vector_hnsw ON embeddings
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for auto-updating updated_at
CREATE TRIGGER update_source_urls_updated_at BEFORE UPDATE ON source_urls
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_documents_updated_at BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
