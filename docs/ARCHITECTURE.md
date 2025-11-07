# System Architecture

## High-Level Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MCP Client (Claude Desktop, IDEs, etc.)                 │  │
│  │  - Sends queries with optional token budgets            │  │
│  │  - Receives formatted documentation chunks              │  │
│  └────────────────────┬─────────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────────┘
                          │ stdio (MCP Protocol)
                          │ JSON-RPC 2.0
┌─────────────────────────▼────────────────────────────────────────┐
│                      MCP Server Layer                             │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MCP Server (Ktor + MCP Kotlin SDK)                     │  │
│  │                                                          │  │
│  │  ┌────────────────┐  ┌─────────────────┐              │  │
│  │  │ SearchDocsTool │  │  Future Tools   │              │  │
│  │  └────────┬───────┘  └─────────────────┘              │  │
│  │           │                                             │  │
│  │  ┌────────▼─────────────────────────────────────────┐  │  │
│  │  │         SearchService                            │  │  │
│  │  │  1. Generate query embedding                     │  │  │
│  │  │  2. Search pgvector (similarity + filters)      │  │  │
│  │  │  3. Pack chunks within token budget             │  │  │
│  │  │  4. Calculate confidence score                  │  │  │
│  │  └──────────────────────────────────────────────────┘  │  │
│  └───────────────┬──────────────────────────────────────────┘  │
└──────────────────┼─────────────────────────────────────────────┘
                   │
                   │ Uses
                   │
┌──────────────────▼─────────────────────────────────────────────┐
│                     Service Layer                               │
│                                                                 │
│  ┌───────────────────────┐  ┌──────────────────────────────┐  │
│  │  EmbeddingService     │  │  IndexerService              │  │
│  │  - ONNX Runtime       │  │  - EmbeddingRepository       │  │
│  │  - BGE-large-1024d    │  │  - DocumentRepository        │  │
│  │  - Batch processing   │  │  - SourceUrlRepository       │  │
│  │  - Token counting     │  │  - Transaction management    │  │
│  └───────────────────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Queries
                              │
┌─────────────────────────────▼──────────────────────────────────┐
│                    Persistence Layer                            │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  PostgreSQL 16 + pgvector Extension                      │  │
│  │                                                          │  │
│  │  Tables:                                                 │  │
│  │  ┌─────────────────┐  ┌──────────────┐  ┌────────────┐│  │
│  │  │  source_urls    │  │  documents   │  │ embeddings ││  │
│  │  │  - url          │  │  - title     │  │ - vector   ││  │
│  │  │  - parser_type  │  │  - metadata  │  │ - content  ││  │
│  │  │  - etag         │  │  - type      │  │ - metadata ││  │
│  │  │  - status       │  │              │  │            ││  │
│  │  └─────────────────┘  └──────────────┘  └────────────┘│  │
│  │                                                          │  │
│  │  Indexes:                                                │  │
│  │  - HNSW (vector_cosine_ops) on embeddings.embedding    │  │
│  │  - GIN on metadata JSONB columns                        │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ Writes
                              │
┌─────────────────────────────┴──────────────────────────────────┐
│                     Crawler Service                             │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  CrawlerService                                          │  │
│  │                                                          │  │
│  │  1. Fetch URLs from source_urls (pending/enabled)      │  │
│  │  2. Check change detection (ETag, Last-Modified)       │  │
│  │  3. Download HTML content                              │  │
│  │  4. Select appropriate parser                          │  │
│  │  5. Parse → chunks with metadata                       │  │
│  │  6. Generate embeddings (batch)                        │  │
│  │  7. Upsert to database                                 │  │
│  │  8. Update crawl status                                │  │
│  └────────────┬─────────────────────────────────────────────┘  │
│               │                                                 │
│  ┌────────────▼─────────────────────────────────────────────┐  │
│  │  ParserRegistry                                          │  │
│  │  ┌─────────────────┐  ┌──────────────────┐             │  │
│  │  │ AndroidDocs     │  │ KotlinLang       │  + Future   │  │
│  │  │ Parser          │  │ Parser           │    Parsers  │  │
│  │  └─────────────────┘  └──────────────────┘             │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

### Query Flow (MCP Server)

```
User Query
    │
    ▼
[MCP Server receives SearchRequest]
    │
    ├─ query: "How to use LaunchedEffect?"
    ├─ tokenBudget: 2000 (optional)
    ├─ filters: {"content_type": "GUIDE"} (optional)
    └─ similarityThreshold: 0.7 (optional)
    │
    ▼
[SearchService.search()]
    │
    ├─ Step 1: Generate query embedding
    │   └─ EmbeddingService.embed(query) → FloatArray(1024)
    │
    ├─ Step 2: Search vectors
    │   └─ EmbeddingRepository.search(
    │         queryEmbedding,
    │         topK=30,
    │         threshold=0.7,
    │         filters={"content_type": "GUIDE"}
    │       ) → List<ScoredChunk>
    │
    ├─ Step 3: Pack chunks within budget
    │   │
    │   ├─ Sort by similarity DESC
    │   ├─ selectedChunks = []
    │   ├─ usedTokens = 0
    │   │
    │   └─ For each chunk:
    │       ├─ If usedTokens + chunk.tokens > budget: STOP
    │       ├─ selectedChunks.add(chunk)
    │       └─ usedTokens += chunk.tokens
    │
    ├─ Step 4: Calculate confidence
    │   └─ average(selectedChunks.map { it.similarity })
    │
    └─ Return SearchResponse
        ├─ chunks: List<RetrievedChunk>
        ├─ totalTokens: usedTokens
        └─ confidence: averageSimilarity
```

### Indexing Flow (Crawler)

```
Scheduled Cron / Manual Trigger
    │
    ▼
[CrawlerService.crawlAll()]
    │
    ├─ Fetch enabled URLs from source_urls table
    │
    └─ For each URL:
        │
        ▼
    [CrawlerService.crawlUrl(sourceUrl)]
        │
        ├─ Step 1: Mark as IN_PROGRESS
        │
        ├─ Step 2: HTTP HEAD request
        │   └─ Check ETag / Last-Modified
        │       ├─ If unchanged: Skip, return
        │       └─ If changed: Continue
        │
        ├─ Step 3: HTTP GET request
        │   └─ Download full HTML content
        │
        ├─ Step 4: Select parser
        │   └─ ParserRegistry.getParser(url)
        │       ├─ Matches "developer.android.com" → AndroidDocsParser
        │       ├─ Matches "kotlinlang.org" → KotlinLangParser
        │       └─ Fallback → GenericHtmlParser
        │
        ├─ Step 5: Parse HTML
        │   └─ Parser.parse(url, html) → ParsedDocument
        │       ├─ title: "LaunchedEffect - Compose Runtime"
        │       ├─ contentType: GUIDE
        │       ├─ chunks: List<DocumentChunk>
        │       │   ├─ chunk[0]: { content, sectionHierarchy, metadata }
        │       │   ├─ chunk[1]: { content, ... }
        │       │   └─ ...
        │       └─ metadata: {"api_level": "34"}
        │
        ├─ Step 6: Generate embeddings
        │   └─ EmbeddingService.embedBatch(chunks.map { it.content })
        │       → List<FloatArray>
        │
        ├─ Step 7: Persist to database (TRANSACTION)
        │   │
        │   ├─ documentId = DocumentRepository.insert(
        │   │     Document(sourceUrl, title, contentType, metadata)
        │   │   )
        │   │
        │   └─ EmbeddingRepository.insertBatch(
        │         chunks.mapIndexed { i, chunk →
        │           EmbeddedChunk(
        │             documentId,
        │             chunkIndex=i,
        │             content=chunk.content,
        │             embedding=embeddings[i],
        │             metadata=chunk.metadata,
        │             tokenCount=countTokens(chunk.content)
        │           )
        │         }
        │       )
        │
        ├─ Step 8: Update source_url
        │   └─ SourceUrlRepository.updateStatus(
        │         id,
        │         status=SUCCESS,
        │         etag=newEtag,
        │         lastModified=newLastModified
        │       )
        │
        └─ On error: Mark FAILED, log error_message
```

## Module Dependencies

```
┌─────────────────────────────────────────────────────────────┐
│                          Modules                             │
└─────────────────────────────────────────────────────────────┘

                          shared
                            │
              ┌─────────────┼─────────────┐
              │             │             │
              ▼             ▼             ▼
          embedder       parser       indexer
              │             │             │
              │             │             │
              └─────────────┼─────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
          crawler                    mcp-server

Dependencies:
- shared: No dependencies (base models)
- embedder: shared
- parser: shared
- indexer: shared
- crawler: shared, embedder, parser, indexer
- mcp-server: shared, embedder, indexer
```

## Technology Stack

### Core
- **Language**: Kotlin 1.9.22
- **JVM Target**: 17
- **Build Tool**: Gradle 8.5
- **Coroutines**: kotlinx-coroutines 1.7.3

### MCP Server
- **MCP SDK**: kotlin-sdk 0.1.0
- **Server Framework**: Ktor 2.3.7 (optional, stdio primary)
- **Serialization**: kotlinx-serialization-json 1.6.2

### Database
- **RDBMS**: PostgreSQL 16
- **Vector Extension**: pgvector 0.1.4
- **Connection Pool**: HikariCP 5.1.0
- **SQL DSL**: Exposed 0.47.0
- **Migrations**: Flyway 10.4.1

### Embeddings
- **Runtime**: ONNX Runtime 1.17.0
- **Model**: BGE-large-en-v1.5 (1024 dimensions)
- **Tokenizer**: HuggingFace Tokenizers 0.26.0
- **Token Counting**: JTokkit 1.0.0

### Parsing
- **HTML**: JSoup 1.17.2
- **Markdown**: JetBrains Markdown 0.6.1
- **HTTP Client**: Ktor Client 2.3.7

### Crawler
- **CLI**: Clikt 4.2.2
- **Scheduling**: Krontab 2.2.8
- **HTTP Client**: Ktor Client 2.3.7

### Deployment
- **Containerization**: Docker + Docker Compose
- **Base Images**: eclipse-temurin:17-jre-alpine

### Testing
- **Unit Tests**: Kotlin Test
- **Integration**: Testcontainers 1.19.3
- **Mocking**: MockK 1.13.9

## Configuration Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Configuration Hierarchy                     │
└─────────────────────────────────────────────────────────────┘

Priority (highest to lowest):

1. Environment Variables (Docker, runtime)
   ├─ DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   ├─ EMBEDDING_MODEL_PATH, MODEL_CACHE_DIR
   ├─ LOG_LEVEL
   └─ CRAWLER_MODE, CRON_SCHEDULE

2. application.conf (HOCON format)
   ├─ database { host, port, ... }
   ├─ embedding { modelPath, dimension, ... }
   ├─ chunking { targetTokens, overlap, ... }
   ├─ retrieval { defaultTokenBudget, threshold, ... }
   └─ crawler { maxConcurrentRequests, ... }

3. Defaults (hardcoded in data classes)
   └─ Used if not specified in conf or env

Config Loading:
- Use Typesafe Config library
- Parse application.conf
- Substitute ${?VAR_NAME} with env vars
- Validate and construct AppConfig data class
```

## Security Considerations

### Database
- ✅ Connection credentials via environment variables
- ✅ Connection pooling prevents connection exhaustion
- ✅ Prepared statements (Exposed) prevent SQL injection
- ✅ Transaction isolation (REPEATABLE READ)

### MCP Server
- ✅ Stdio transport (local only, no network exposure)
- ⚠️ Future HTTP transport: Add authentication/authorization
- ✅ Input validation on SearchRequest parameters

### Crawler
- ✅ Rate limiting (requestDelayMs)
- ✅ User-Agent header for identification
- ✅ Timeout limits (30s default)
- ✅ Retry limits (3 attempts)
- ⚠️ Validate URLs before fetching (prevent SSRF)

### Docker
- ✅ Non-root user in containers (future enhancement)
- ✅ Resource limits (memory, CPU)
- ✅ Read-only config mounts
- ✅ Secrets via environment variables (not in images)

## Performance Characteristics

### Query Latency
- **Embedding Generation**: ~50ms (CPU)
- **Vector Search**: 50-100ms (10K-50K vectors)
- **Chunk Packing**: <10ms (sorting + iteration)
- **Total**: 100-200ms end-to-end

### Indexing Throughput
- **HTML Parsing**: ~100ms per page
- **Embedding Generation**: ~50ms per chunk (batch of 32)
- **Database Insert**: ~10ms per chunk
- **Total**: ~5-10 pages/minute (depending on page complexity)

### Storage
- **Embeddings**: ~4KB per chunk (1024 floats × 4 bytes)
- **Metadata**: ~1KB per chunk
- **Total**: ~5KB per chunk
- **Estimate**: 50K chunks = ~250MB

### Memory
- **ONNX Model**: 1.2GB (loaded once)
- **Connection Pool**: ~50MB (10 connections)
- **JVM Heap**: 512MB-2GB (depending on batch sizes)
- **Total**: 2-4GB recommended

## Scaling Strategies

### Horizontal Scaling (Future)
1. **MCP Server**: Stateless, can run multiple instances
2. **Crawler**: Coordinate via database (distributed lock on source_urls)
3. **Database**: Read replicas for search, single writer for indexing

### Vertical Scaling
1. **Increase connection pool size** (maxPoolSize)
2. **Increase batch sizes** (embedding.batchSize)
3. **Tune HNSW parameters** (m, ef_construction for build; ef_search for query)

### Caching (Future Enhancement)
1. **Query embeddings**: Cache frequent queries (Redis)
2. **Search results**: Cache with TTL for identical queries
3. **Model artifacts**: Pre-load in Docker image

## Monitoring & Observability (Future)

### Metrics to Track
- Query latency (p50, p95, p99)
- Embedding generation time
- Vector search time
- Crawl success/failure rates
- Database connection pool utilization
- Memory usage

### Logging Strategy
- **DEBUG**: SQL queries, embedding vectors, detailed flow
- **INFO**: Queries, crawl operations, configuration
- **WARN**: Retries, recoverable errors
- **ERROR**: Failures, exceptions

### Health Checks
- Database connectivity
- Embedding model loaded
- Disk space available (model cache)
