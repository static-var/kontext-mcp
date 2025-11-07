# Android Kotlin MCP - Development Tasks

## Phase 1: Foundation (COMPLETED)
- [x] Monorepo structure with Gradle multi-module setup
- [x] Shared data models (SourceUrl, Document, DocumentChunk, SearchRequest/Response)
- [x] Configuration models (AppConfig with all subsystem configs)
- [x] Docker Compose with PostgreSQL + pgvector
- [x] Flyway migrations (V1: schema, V2: seed data)
- [x] Indexer database layer (Tables, DatabaseFactory)
- [x] Application configuration file with env var fallbacks
- [x] README with architecture and usage docs

## Phase 2: Core Infrastructure (IN PROGRESS)

### Indexer Module (60% complete)
- [x] Database tables and migrations
- [x] DatabaseFactory with HikariCP
- [x] Table definitions (Exposed)
- [x] Repository interfaces (SourceUrlRepository, DocumentRepository, EmbeddingRepository)
- [x] Repository implementations using Exposed
- [x] Vector search queries (pgvector cosine similarity)
- [x] Transaction handling
- [x] Unit tests with Testcontainers *(requires Docker runtime during execution)*

### Embedding Module (0% complete)
- [x] ONNX Runtime integration
- [x] BGE-large-en-v1.5 model download/caching
- [x] HuggingFace tokenizer integration
- [x] EmbeddingService interface
- [x] EmbeddingServiceImpl with batching
- [x] Token counting utilities
- [x] Unit tests with sample inputs

### Parser Module (0% complete)
- [x] DocumentParser interface
- [x] ParserRegistry for parser selection
- [x] Chunking utilities (structural, token-aware)
- [x] AndroidDocsParser implementation
  - [x] HTML structure analysis
  - [x] Code block preservation
  - [x] Metadata extraction (API level, etc.)
- [x] KotlinLangParser implementation
  - [x] Markdown parsing
  - [x] Stdlib API ref parsing
  - [x] Metadata extraction (Kotlin version, etc.)
- [x] Unit tests with real doc samples

## Phase 3: Services (NOT STARTED)

### MCP Server Module (0% complete)
- [x] MCP Kotlin SDK integration
- [x] Stdio transport setup
- [x] Tool definition: `search_docs`
- [x] SearchService implementation
  - [x] Query embedding generation
  - [x] Vector similarity search
  - [x] Token budget calculation
  - [x] Chunk packing algorithm
- [x] Configuration loading (HOCON)
- [x] Logging setup (Logback)
- [x] Error handling
- [x] Integration tests

### Crawler Web Service (0% complete)
- [x] Ktor Server setup with dependencies
  - [x] ktor-server-core, ktor-server-netty
  - [x] ktor-server-content-negotiation
  - [x] ktor-server-auth (session-based)
  - [x] ktor-serialization-kotlinx-json
  - [x] ktor-server-html-builder OR htmx integration
- [x] Authentication & Authorization
  - [x] Session-based auth (no registration)
  - [x] Hardcoded credentials in config (hashed)
  - [x] Login/logout endpoints
  - [x] Auth middleware for protected routes
- [x] Web UI (barebones HTML)
  - [x] Login page
  - [x] Dashboard (crawl status overview)
  - [x] Trigger crawl page (start one-time crawl)
  - [x] Schedule management page (view/edit cron schedules)
  - [x] URL management page (add/remove/list source URLs)
- [x] REST API endpoints
  - [x] POST /api/crawl/start (trigger one-time crawl)
  - [x] GET /api/crawl/status (current crawl progress)
  - [x] POST /api/crawl/schedule (add/update cron schedule)
  - [x] GET /api/crawl/schedule (list schedules)
  - [x] DELETE /api/crawl/schedule/:id (remove schedule)
  - [x] GET /api/urls (list source URLs)
  - [x] POST /api/urls (add new source URL)
  - [x] DELETE /api/urls/:id (remove source URL)
- [x] CrawlerService implementation
  - [x] URL fetching with Ktor client
  - [x] ETag/Last-Modified change detection
  - [x] Parser selection and invocation
  - [x] Embedding generation
  - [x] Indexer integration
- [x] Scheduled crawling (Krontab)
  - [x] Background job scheduler
  - [x] Cron expression parsing and execution
  - [x] Schedule persistence (database or config)
- [x] Progress tracking and logging
  - [x] Real-time crawl status updates
  - [x] Logging with Logback
  - [x] Error tracking and reporting
    - [x] Error handling and retry logic
    - [ ] Integration tests
- [x] Database migration for schedule storage (if using DB)

## Phase 4: Deployment (NOT STARTED)

### Docker Images (0% complete)
- [x] Dockerfile.mcp-server
  - [x] Multi-stage build (Gradle + JRE)
  - [x] Model caching layer
  - [x] Health check script
- [x] Dockerfile.crawler
  - [x] Multi-stage build (Gradle + JRE)
  - [x] Model caching layer
  - [x] Expose HTTP port for web UI
  - [x] Health check endpoint
- [x] docker-compose.yml enhancements
  - [x] Resource limits
  - [x] Restart policies
  - [x] Volume mounts
  - [x] Network configuration
  - [x] Expose crawler web UI port (e.g., 8080)
  - [x] Environment variables for auth credentials
- [x] Deployment docs (compose commands, health checks, logs, web UI access)

## Phase 5: Testing & Optimization (NOT STARTED)

### Testing (0% complete)
- [x] Create evaluation dataset (20-30 test queries)
  - [x] Android-specific queries (Compose, Architecture, etc.)
  - [x] Kotlin-specific queries (Coroutines, Flow, etc.)
  - [x] Mixed queries
- [x] Retrieval quality metrics
  - [x] Recall@5, Recall@10
  - [x] MRR (Mean Reciprocal Rank)
  - [x] Precision@K
- [ ] Performance benchmarks
  - [ ] Query latency (p50, p95, p99)
  - [ ] Embedding generation time
  - [ ] Memory usage profiling
- [x] End-to-end integration tests

### Optimization (deferred to post-deployment validation)
- [ ] Similarity threshold tuning *(run with real embedding model after deployment)*
- [ ] Chunk size optimization *(requires production embeddings & token stats)*
- [ ] HNSW index parameter tuning (m, ef_construction)
- [ ] Connection pool sizing
- [ ] Batch size optimization for embedding
- [ ] Token budget calibration

## Phase 6: Enhancements (FUTURE)

### Optional Features
- [ ] Cross-encoder reranking (config-gated)
- [ ] Query result caching (Redis)
- [ ] GitHub parser for repo READMEs
- [ ] MkDocs parser for custom doc sites
- [ ] HTMX for live status updates (if not implemented in Phase 3)
- [ ] API token authentication for programmatic access
- [ ] Monitoring/metrics endpoint (Prometheus)
- [ ] Distributed crawling (multi-instance)
- [ ] Webhook notifications on crawl completion

## Current Priorities (Next Steps)

1. **Complete Indexer Module**
   - Implement repositories (SourceUrlRepository, DocumentRepository, EmbeddingRepository)
   - Add vector search queries with pgvector
   - Write unit tests

2. **Build Embedding Module**
   - Integrate ONNX Runtime
   - Implement model download/caching
   - Create EmbeddingService with batching

3. **Create Parser Module**
   - Define DocumentParser interface
   - Implement chunking utilities
   - Build AndroidDocsParser

4. **Build Crawler Web Service**
   - Set up Ktor Server with authentication
   - Implement barebones web UI for crawl management
   - Create REST API endpoints
   - Build CrawlerService orchestration
   - Integrate Krontab for scheduling

## Known Issues / Risks

1. **ONNX Runtime JNI Dependencies**: May require platform-specific builds
   - Mitigation: Use universal JNI libs, test on target platforms

2. **Android Docs Dynamic Rendering**: Some pages use React/JS
   - Mitigation: Use sitemap.xml for discovery, static HTML where possible

3. **Token Counting Accuracy**: JTokkit may not match Claude's tokenizer exactly
   - Mitigation: Use conservative estimates, add safety margin

4. **Embedding Model Size**: 1.2GB download on first run
   - Mitigation: Pre-bake into Docker image or use init container

5. **PostgreSQL HNSW Index Build Time**: Can take hours for large datasets
   - Mitigation: Build index after initial bulk load, not during inserts

6. **Session Security for Crawler Web UI**: Single-user auth with hardcoded credentials
   - Mitigation: Use strong hashed passwords, HTTPS in production, secure session cookies

7. **Concurrent Crawl Requests**: Multiple simultaneous crawl triggers could conflict
   - Mitigation: Implement mutex/lock or queue-based crawl job system

## Notes

- All code follows Kotlin best practices (no `!!`, proper error handling)
- Config-driven design: no hardcoded magic values
- Clean architecture: domain layer independent of frameworks
- Each module has clear boundaries and interfaces
- Crawler web UI should be minimal/barebones—focus on functionality over aesthetics
- Consider HTMX for dynamic status updates without heavy JavaScript
- Store cron schedules in database for runtime modification flexibility
