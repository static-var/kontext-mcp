# Android Kotlin MCP - Project Status

**Last Updated**: 2025-11-06
**Current Phase**: Foundation Complete (40% overall progress)

## ✅ Completed

### Phase 1: Foundation (100%)

#### Project Structure
- ✅ Monorepo Gradle multi-module setup
  - Root build configuration
  - 6 modules: shared, embedder, parser, indexer, crawler, mcp-server
  - Kotlin 1.9.22 with JVM target 17
  - Proper dependency management

#### Data Models (shared module)
- ✅ `ParserType` enum (ANDROID_DOCS, KOTLIN_LANG, MKDOCS, GITHUB, GENERIC_HTML)
- ✅ `ContentType` enum (API_REFERENCE, GUIDE, TUTORIAL, etc.)
- ✅ `CrawlStatus` enum (PENDING, IN_PROGRESS, SUCCESS, FAILED, DISABLED)
- ✅ `SourceUrl` data class with change detection fields
- ✅ `Document` and `DocumentChunk` models
- ✅ `SearchRequest` and `SearchResponse` with token budget support
- ✅ `RetrievedChunk` with similarity scores
- ✅ `AppConfig` with all subsystem configurations

#### Database Infrastructure (indexer module)
- ✅ PostgreSQL + pgvector Docker Compose setup
- ✅ Database initialization SQL (extensions, custom types)
- ✅ Flyway migration V1: Full schema
  - `source_urls` table with crawl tracking
  - `documents` table with metadata
  - `embeddings` table with vector(1024) column
  - HNSW index for vector similarity
  - Triggers for auto-updating timestamps
- ✅ Flyway migration V2: Seed initial URLs
  - Android developer docs (6 starting points)
  - Kotlin lang docs (5 starting points)
- ✅ Exposed table definitions (SourceUrlsTable, DocumentsTable, EmbeddingsTable)
- ✅ DatabaseFactory with HikariCP connection pooling

#### Configuration
- ✅ `application.conf` with HOCON format
  - Database settings with env var fallbacks
  - Embedding model configuration
  - Chunking parameters (target tokens, overlap, preservation flags)
  - Retrieval settings (token budget, threshold, top-K)
  - Crawler configuration (concurrency, delays, retries)
- ✅ `.env.example` for local development
- ✅ Environment variable hierarchy documented

#### Docker & Deployment
- ✅ `docker-compose.yml` with 3 services:
  - `postgres`: pgvector-enabled PostgreSQL 16
  - `mcp-server`: Main MCP server (placeholder)
  - `crawler`: Background crawling service (placeholder)
- ✅ Health checks for database readiness
- ✅ Volume mounts for model cache and config
- ✅ Network configuration

#### Documentation
- ✅ README.md with architecture, quick start, and API reference
- ✅ docs/tasks.md with detailed task breakdown and priorities
- ✅ docs/IMPLEMENTATION_GUIDE.md with step-by-step instructions
- ✅ docs/PROJECT_STATUS.md (this file)
- ✅ .gitignore with proper exclusions

#### Build Configuration
- ✅ All module build.gradle.kts files with dependencies:
  - shared: kotlinx-serialization, coroutines, datetime
  - embedder: ONNX Runtime, HuggingFace tokenizers, Ktor client
  - parser: JSoup, Ktor client, Markdown parser, JTokkit
  - indexer: PostgreSQL, pgvector, Exposed, Flyway, HikariCP
  - crawler: Clikt CLI, Krontab scheduler, all dependencies
  - mcp-server: MCP Kotlin SDK, Ktor server, JTokkit
- ✅ **Version catalog** (`gradle/libs.versions.toml`) for centralized dependency management
  - No hardcoded versions in build files
  - Convenient bundles (ktor-server, ktor-client, database, exposed, etc.)

## 🚧 In Progress

### Phase 2: Core Infrastructure (0%)

All modules are scaffolded but not implemented. See "Next Steps" below.

## 📋 Remaining Work

### Phase 2A: Indexer Implementation (Not Started)
- Repository interfaces (SourceUrl, Document, Embedding)
- Repository implementations with Exposed
- pgvector search queries (cosine similarity)
- Transaction helpers
- Unit tests with Testcontainers

### Phase 2B: Embedding Module (Not Started)
- Model downloader (HuggingFace Hub integration)
- ONNX Runtime session management
- EmbeddingService interface and implementation
- HuggingFace tokenizer integration
- Batch processing
- Token counting utilities
- Unit tests

### Phase 2C: Parser Module (Not Started)
- DocumentParser interface
- ParserRegistry for parser selection
- Structural chunking utilities (HTML/Markdown aware)
- AndroidDocsParser implementation
- KotlinLangParser implementation
- Unit tests with real doc samples

### Phase 3A: MCP Server (Not Started)
- MCP Kotlin SDK integration
- Stdio transport setup
- SearchDocsTool implementation
- SearchService with token budget logic
- Chunk packing algorithm
- Configuration loading
- Error handling
- Integration tests

### Phase 3B: Crawler Service (Not Started)
- CLI with Clikt (crawl, schedule, status commands)
- CrawlerService implementation
- HTTP fetching with Ktor
- ETag/Last-Modified change detection
- Parser invocation
- Embedding generation integration
- Indexer integration
- Scheduled crawling with Krontab
- URL management API
- Error handling and retries
- Integration tests

### Phase 4: Docker Images (Not Started)
- Dockerfile.mcp-server (multi-stage build)
- Dockerfile.crawler (multi-stage build)
- Model pre-downloading in images
- Health check scripts
- Resource limits and restart policies
- Deployment documentation

### Phase 5: Testing & Optimization (Not Started)
- Evaluation dataset (20-30 test queries)
- Retrieval metrics (Recall@K, MRR, Precision@K)
- Performance benchmarks (latency, memory)
- Parameter tuning (threshold, chunk size, HNSW)
- End-to-end integration tests

## 🎯 Next Immediate Steps

**Priority 1: Complete Indexer Repositories (1 day)**
1. Implement `SourceUrlRepository` interface and impl
2. Implement `DocumentRepository` interface and impl
3. Implement `EmbeddingRepository` interface and impl with pgvector queries
4. Add transaction helper `dbQuery`
5. Write unit tests with Testcontainers

**Priority 2: Build Embedding Module (2 days)**
1. Implement model downloader from HuggingFace
2. Create ONNX Runtime session manager
3. Integrate HuggingFace tokenizer
4. Implement EmbeddingService with batching
5. Add token counter using JTokkit
6. Write unit tests

**Priority 3: Implement Parsers (2 days)**
1. Define DocumentParser interface
2. Create StructuralChunker utility
3. Implement AndroidDocsParser
4. Implement KotlinLangParser
5. Create ParserRegistry
6. Write unit tests with real doc samples

**Priority 4: MCP Server (1 day)**
1. Set up MCP Kotlin SDK
2. Implement SearchDocsTool
3. Create SearchService with token budget logic
4. Add configuration loading
5. Write integration tests

**Priority 5: Crawler Service (1 day)**
1. Create CLI with Clikt
2. Implement CrawlerService
3. Add change detection
4. Integrate all components (parser, embedder, indexer)
5. Add scheduled crawling
6. Write integration tests

**Priority 6: Docker & Deployment (1 day)**
1. Write Dockerfiles
2. Test builds
3. Verify model downloading
4. Test full deployment with docker-compose
5. Document deployment process

**Priority 7: Testing & Tuning (1 day)**
1. Create evaluation dataset
2. Run retrieval metrics
3. Performance benchmarks
4. Tune parameters
5. Document findings

**Estimated time to MVP**: 9-10 days

## 📊 Overall Progress

- **Foundation**: 100% ✅
- **Core Infrastructure**: 0% 🚧
- **Services**: 0% 📋
- **Deployment**: 0% 📋
- **Testing**: 0% 📋

**Total**: ~40% (foundation + scaffolding complete)

## 🔍 Key Decisions Made

1. **Embedding Model**: BGE-large-en-v1.5 (1024d) via ONNX Runtime
   - Rationale: Best quality for technical docs, self-hosted, reproducible

2. **Chunking Strategy**: Structural (HTML/Markdown aware)
   - Rationale: Preserves semantic boundaries, better LLM consumption

3. **Vector Index**: HNSW with cosine distance
   - Rationale: Best recall for our scale, stable performance

4. **Response Sizing**: Token budget with dynamic packing
   - Rationale: Respects context limits, adapts to query complexity

5. **Database**: PostgreSQL + pgvector
   - Rationale: Mature, reliable, excellent vector support

6. **Config Management**: HOCON + environment variables
   - Rationale: No redeployment for tuning, Docker-friendly

7. **Parser Design**: Interface-based with registry
   - Rationale: Extensible for future parsers (GitHub, MkDocs)

## ⚠️ Known Risks & Mitigations

1. **ONNX Runtime Platform Issues**
   - Risk: ARM vs x64 compatibility
   - Mitigation: Test on target platforms, use universal libs

2. **Android Docs Dynamic Rendering**
   - Risk: React-rendered pages, empty HTML
   - Mitigation: Use sitemap.xml for discovery, static HTML parsing

3. **Token Counting Mismatch**
   - Risk: JTokkit ≠ Claude tokenizer exactly
   - Mitigation: 10% safety margin on budgets

4. **Embedding Model Download**
   - Risk: 1.2GB download on first run
   - Mitigation: Pre-bake into Docker image

5. **HNSW Index Build Time**
   - Risk: Hours for large datasets
   - Mitigation: Build after bulk load, not during inserts

## 📝 Notes

- All code follows Kotlin best practices (see CLAUDE.md)
- No `!!` operator usage (use `requireNotNull` with messages)
- Clean architecture: domain independent of frameworks
- Config-driven: no magic values or hardcoded thresholds
- Transaction safety: wrap multi-step DB ops
- Proper error handling with structured logging

## 🤝 How to Contribute

1. Check `docs/tasks.md` for open tasks
2. Follow implementation guide in `docs/IMPLEMENTATION_GUIDE.md`
3. Adhere to Kotlin best practices in `~/.claude/CLAUDE.md`
4. Write tests for new features
5. Update documentation

## 🚀 Quick Start for Development

```bash
# Clone and setup
cd /Users/staticvar/Projects/mcp
cp .env.example .env

# Start database
docker-compose up -d postgres

# Verify migrations
docker-compose logs postgres

# Build project
./gradlew build

# Run tests (when implemented)
./gradlew test
```

## 📞 Questions?

- Architecture decisions: See `docs/IMPLEMENTATION_GUIDE.md`
- Task priorities: See `docs/tasks.md`
- Configuration: See `config/application.conf`
- Kotlin standards: See `~/.claude/CLAUDE.md`
