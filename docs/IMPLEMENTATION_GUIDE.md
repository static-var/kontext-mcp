# Implementation Guide

This document provides step-by-step implementation guidance for completing the Android Kotlin MCP project.

## Project Overview

**Goal**: Create a specialized MCP server for Android and Kotlin documentation retrieval using RAG.

**Core Principle**: Config-driven, no redeployment for tuning. Master of one domain (Android/Kotlin), not jack of all trades.

**Tech Stack**:
- Kotlin 1.9.22 + Coroutines
- PostgreSQL 16 + pgvector
- ONNX Runtime (BGE-large-en-v1.5 embeddings)
- MCP Kotlin SDK
- Ktor (HTTP client for crawling)
- Exposed (SQL DSL)
- Flyway (migrations)

## Architecture Decisions

### Why BGE-large-en-v1.5 over Ollama?
- Superior performance on code/technical docs
- 1024 dimensions (good balance)
- Can run via ONNX Runtime (no Python dependency)
- Reproducible across deployments

### Why Structural Chunking?
- Preserves semantic boundaries (headers, code blocks)
- Better context for LLM consumption
- Prevents mid-sentence/mid-code splits

### Why HNSW over IVF for pgvector?
- Better recall for our scale (10K-50K chunks)
- More stable performance
- Acceptable build time for dataset size

### Why Token Budget over Fixed Chunk Count?
- Adapts to query complexity
- Respects client's context window constraints
- Prevents wasting tokens on low-confidence results

## Implementation Phases

### Phase 2A: Complete Indexer Module

#### 2A.1: Repository Interfaces

Create `modules/indexer/src/main/kotlin/dev/staticvar/mcp/indexer/repository/`:

**SourceUrlRepository.kt**:
```kotlin
interface SourceUrlRepository {
    suspend fun insert(url: String, parserType: ParserType): SourceUrl
    suspend fun findById(id: Int): SourceUrl?
    suspend fun findByUrl(url: String): SourceUrl?
    suspend fun findEnabled(): List<SourceUrl>
    suspend fun findPending(): List<SourceUrl>
    suspend fun updateStatus(id: Int, status: CrawlStatus, errorMessage: String? = null)
    suspend fun updateCrawlMetadata(id: Int, etag: String?, lastModified: String?)
    suspend fun markCrawled(id: Int)
}
```

**DocumentRepository.kt**:
```kotlin
interface DocumentRepository {
    suspend fun insert(doc: Document): Int // Returns doc ID
    suspend fun findById(id: Int): Document?
    suspend fun findBySourceUrl(url: String): List<Document>
    suspend fun deleteBySourceUrl(url: String)
}
```

**EmbeddingRepository.kt**:
```kotlin
interface EmbeddingRepository {
    suspend fun insertBatch(embeddings: List<EmbeddedChunk>)
    suspend fun search(
        queryEmbedding: FloatArray,
        limit: Int,
        similarityThreshold: Float,
        filters: Map<String, String>? = null
    ): List<ScoredChunk>
    suspend fun deleteByDocumentId(documentId: Int)
}

data class ScoredChunk(
    val content: String,
    val sourceUrl: String,
    val similarity: Float,
    val metadata: Map<String, String>,
    val tokenCount: Int
)
```

#### 2A.2: Repository Implementations

Use Exposed's DSL for SQL queries. Key points:
- Wrap all DB operations in `transaction { }`
- Use `.map { }` to convert ResultRows to domain models
- For pgvector queries, use raw SQL via `exec()`

**Example vector search query**:
```kotlin
override suspend fun search(
    queryEmbedding: FloatArray,
    limit: Int,
    similarityThreshold: Float,
    filters: Map<String, String>?
): List<ScoredChunk> = dbQuery {
    val filterClause = buildMetadataFilter(filters)
    val query = """
        SELECT e.content, d.source_url,
               1 - (e.embedding <=> ?::vector) as similarity,
               e.metadata, e.token_count
        FROM embeddings e
        JOIN documents d ON e.document_id = d.id
        WHERE 1 - (e.embedding <=> ?::vector) >= ?
        $filterClause
        ORDER BY e.embedding <=> ?::vector
        LIMIT ?
    """.trimIndent()

    // Execute with pgvector extension
    // Return mapped results
}
```

#### 2A.3: Transaction Helper

Create `modules/indexer/src/main/kotlin/dev/staticvar/mcp/indexer/database/TransactionHelper.kt`:

```kotlin
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

### Phase 2B: Embedding Module

#### 2B.1: Model Download Utility

Create `modules/embedder/src/main/kotlin/dev/staticvar/mcp/embedder/model/ModelDownloader.kt`:

- Check if model exists in cache
- If not, download from HuggingFace Hub
- Verify SHA256 checksum
- Extract to model cache directory

Use Ktor client for HTTP downloads with progress tracking.

#### 2B.2: ONNX Runtime Integration

Create `modules/embedder/src/main/kotlin/dev/staticvar/mcp/embedder/EmbeddingService.kt`:

```kotlin
interface EmbeddingService {
    suspend fun embed(text: String): FloatArray
    suspend fun embedBatch(texts: List<String>): List<FloatArray>
}
```

**Implementation notes**:
- Load ONNX model once at initialization
- Use HuggingFace tokenizer for preprocessing
- Normalize embeddings (L2 normalization)
- Support batching for efficiency

**ONNX Session Setup**:
```kotlin
private val session = OrtEnvironment.getEnvironment().createSession(
    modelPath,
    OrtSession.SessionOptions()
)
```

**Tokenization**:
- Use `ai.djl.huggingface:tokenizers` library
- Load BGE tokenizer config
- Apply padding/truncation to max_tokens

#### 2B.3: Token Counting

Create `modules/embedder/src/main/kotlin/dev/staticvar/mcp/embedder/TokenCounter.kt`:

```kotlin
interface TokenCounter {
    fun count(text: String): Int
}
```

Use JTokkit with cl100k_base encoding (approximates Claude's tokenizer).

### Phase 2C: Parser Module

#### 2C.1: Document Parser Interface

Create `modules/parser/src/main/kotlin/dev/staticvar/mcp/parser/DocumentParser.kt`:

```kotlin
interface DocumentParser {
    suspend fun parse(url: String, html: String): ParsedDocument
    fun supports(url: String): Boolean
}

data class ParsedDocument(
    val title: String,
    val contentType: ContentType,
    val chunks: List<DocumentChunk>,
    val metadata: Map<String, String>
)
```

#### 2C.2: Chunking Utilities

Create `modules/parser/src/main/kotlin/dev/staticvar/mcp/parser/chunking/StructuralChunker.kt`:

**Algorithm**:
1. Parse HTML into DOM tree (JSoup)
2. Traverse DOM, identify structural boundaries:
   - Headers (h1-h6)
   - Code blocks (pre, code)
   - Lists (ul, ol)
   - Paragraphs
3. Extract text preserving hierarchy
4. Split into chunks respecting boundaries
5. Add overlap between chunks
6. Estimate token count per chunk
7. Merge small chunks, split large chunks

**Key considerations**:
- Never split code blocks
- Preserve section headers in chunk metadata
- Maintain parent-child relationships (breadcrumbs)

#### 2C.3: Android Docs Parser

Create `modules/parser/src/main/kotlin/dev/staticvar/mcp/parser/impl/AndroidDocsParser.kt`:

**Specific logic**:
- Detect API level from page metadata or URL
- Extract code samples with language tags
- Identify content type (API ref vs guide vs codelab)
- Parse "See also" links for crawling discovery
- Handle dynamic content (prefer sitemap.xml for URLs)

**Metadata to extract**:
- `api_level`: "34", "33", etc.
- `package`: "androidx.compose.runtime"
- `content_type`: "API_REFERENCE", "GUIDE", etc.

#### 2C.4: Kotlin Lang Parser

Create `modules/parser/src/main/kotlin/dev/staticvar/mcp/parser/impl/KotlinLangParser.kt`:

**Specific logic**:
- Detect Kotlin version from page
- Parse markdown-based pages
- Extract stdlib API signatures
- Identify content type (language ref vs tutorial)

**Metadata to extract**:
- `kotlin_version`: "1.9", "2.0", etc.
- `module`: "kotlin-stdlib", "kotlinx-coroutines"
- `content_type`: "API_REFERENCE", "GUIDE", etc.

#### 2C.5: Parser Registry

Create `modules/parser/src/main/kotlin/dev/staticvar/mcp/parser/ParserRegistry.kt`:

```kotlin
class ParserRegistry(private val parsers: List<DocumentParser>) {
    fun getParser(url: String): DocumentParser {
        return parsers.firstOrNull { it.supports(url) }
            ?: throw IllegalArgumentException("No parser for URL: $url")
    }
}
```

Register:
- AndroidDocsParser
- KotlinLangParser
- GenericHtmlParser (fallback)

### Phase 3A: MCP Server Module

#### 3A.1: MCP SDK Integration

Create `modules/mcp-server/src/main/kotlin/dev/staticvar/mcp/server/McpServerApp.kt`:

```kotlin
fun main() = runBlocking {
    val config = loadConfig()
    val database = DatabaseFactory.init(config.database)
    val embeddingService = EmbeddingServiceImpl(config.embedding)
    val searchService = SearchServiceImpl(
        embeddingService,
        EmbeddingRepositoryImpl(),
        config.retrieval
    )

    val server = McpServer(
        name = "android-kotlin-docs",
        version = "0.1.0"
    )

    server.addTool(SearchDocsTool(searchService))

    // Start stdio server
    server.start()
}
```

#### 3A.2: Search Tool Implementation

Create `modules/mcp-server/src/main/kotlin/dev/staticvar/mcp/server/tools/SearchDocsTool.kt`:

```kotlin
class SearchDocsTool(private val searchService: SearchService) : McpTool {
    override val name = "search_docs"
    override val description = "Search Android and Kotlin documentation"

    override suspend fun execute(params: JsonObject): JsonObject {
        val request = Json.decodeFromJsonElement<SearchRequest>(params)
        val response = searchService.search(request)
        return Json.encodeToJsonElement(response).jsonObject
    }
}
```

#### 3A.3: Search Service

Create `modules/mcp-server/src/main/kotlin/dev/staticvar/mcp/server/service/SearchService.kt`:

```kotlin
interface SearchService {
    suspend fun search(request: SearchRequest): SearchResponse
}
```

**Algorithm**:
1. Generate query embedding
2. Search embeddings table (top K candidates)
3. Filter by similarity threshold
4. Pack chunks within token budget:
   - Sort by similarity descending
   - Add chunks until budget reached
   - Track cumulative tokens
5. Calculate confidence (avg similarity)
6. Return response

**Token budget logic**:
```kotlin
val budget = request.tokenBudget ?: config.defaultTokenBudget
val selected = mutableListOf<ScoredChunk>()
var usedTokens = 0

for (chunk in candidates) {
    if (usedTokens + chunk.tokenCount > budget) break
    selected.add(chunk)
    usedTokens += chunk.tokenCount
}
```

### Phase 3B: Crawler Service

#### 3B.1: CLI Interface

Create `modules/crawler/src/main/kotlin/dev/staticvar/mcp/crawler/CrawlerApp.kt`:

Use Clikt for commands:
- `crawl`: One-time crawl (all pending URLs or specific URL)
- `schedule`: Start daemon mode with cron schedule
- `status`: Show crawl status and stats

#### 3B.2: Crawler Service

Create `modules/crawler/src/main/kotlin/dev/staticvar/mcp/crawler/service/CrawlerService.kt`:

```kotlin
interface CrawlerService {
    suspend fun crawlUrl(sourceUrl: SourceUrl)
    suspend fun crawlAll()
}
```

**Implementation flow**:
1. Mark URL as IN_PROGRESS
2. Fetch URL with HTTP client
3. Check ETag/Last-Modified (skip if unchanged)
4. Select parser via ParserRegistry
5. Parse HTML → ParsedDocument
6. Generate embeddings for all chunks
7. Begin transaction:
   - Insert document record
   - Insert embedding records
   - Update source_url (SUCCESS, metadata)
8. On error: Mark FAILED, log error_message

#### 3B.3: Change Detection

Before fetching full content:
```kotlin
val headResponse = client.head(url) {
    expectSuccess = false
}

val newEtag = headResponse.headers["ETag"]
val newLastModified = headResponse.headers["Last-Modified"]

if (newEtag == sourceUrl.etag && newLastModified == sourceUrl.lastModified) {
    // No changes, skip
    return
}
```

#### 3B.4: Scheduled Crawling

Use Krontab library:
```kotlin
val scheduler = Krontab(cronExpression) {
    crawlerService.crawlAll()
}
```

### Phase 4: Docker Images

#### 4.1: Dockerfile.mcp-server

```dockerfile
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :modules:mcp-server:installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/modules/mcp-server/build/install/mcp-server /app
RUN apk add --no-cache curl

# Download embedding model (cached layer)
ENV MODEL_URL=https://huggingface.co/BAAI/bge-large-en-v1.5/resolve/main/onnx/model.onnx
RUN mkdir -p /app/models && \
    curl -L $MODEL_URL -o /app/models/model.onnx

HEALTHCHECK --interval=30s --timeout=5s \
    CMD curl -f http://localhost:8080/health || exit 1

CMD ["/app/bin/mcp-server"]
```

#### 4.2: Dockerfile.crawler

Similar structure, but:
- Entry point: crawler CLI
- Support daemon mode (foreground process)
- No health check (cron-based)

### Phase 5: Testing

#### 5.1: Evaluation Dataset

Create `modules/mcp-server/src/test/resources/evaluation/queries.json`:

```json
[
  {
    "query": "How to use LaunchedEffect in Jetpack Compose?",
    "expected_keywords": ["LaunchedEffect", "compose", "coroutine", "side-effect"],
    "min_similarity": 0.8
  },
  {
    "query": "What's the difference between Flow and Channel?",
    "expected_keywords": ["Flow", "Channel", "coroutines", "cold", "hot"],
    "min_similarity": 0.75
  }
]
```

#### 5.2: Retrieval Metrics

Create `modules/mcp-server/src/test/kotlin/dev/staticvar/mcp/server/evaluation/RetrievalMetrics.kt`:

Implement:
- Recall@K: Did we retrieve relevant docs in top K?
- MRR: Mean Reciprocal Rank
- Precision@K: How many of top K are relevant?

Run against evaluation dataset, tune thresholds.

## Configuration Management

### Environment Variable Hierarchy
1. `.env` file (local dev)
2. Environment variables (Docker)
3. `config/application.conf` (defaults)

### Hot-Reload Strategy
For knobs that can change without restart:
- Load config from file at request time (cached with TTL)
- For crawler: Check config on each cron run

For knobs requiring restart:
- Database connection
- Embedding model path
- MCP server bindings

## Error Handling Principles

1. **Never use `!!`**: Use `requireNotNull("reason")` or `?: throw`
2. **Log at boundaries**: Service entry/exit, external calls
3. **Transaction rollback**: Wrap multi-step DB ops in transactions
4. **Retry logic**: Crawler HTTP failures (exponential backoff)
5. **User-friendly errors**: MCP responses should explain failures

## Logging Strategy

Use structured logging (kotlin-logging):
```kotlin
private val logger = KotlinLogging.logger {}

logger.info { "Crawling URL: $url" }
logger.error(e) { "Failed to parse document: $url" }
```

Levels:
- DEBUG: Verbose (embedding vectors, SQL queries)
- INFO: Operations (crawl start/end, searches)
- WARN: Recoverable errors (retry attempts)
- ERROR: Unrecoverable errors (parsing failures)

## Performance Optimization Checklist

### Database
- [ ] Use connection pooling (HikariCP)
- [ ] Create indexes on foreign keys
- [ ] Use HNSW with appropriate m/ef_construction
- [ ] Batch inserts (embeddings)

### Embedding
- [ ] Batch inference (32 items)
- [ ] Reuse ONNX session
- [ ] Cache query embeddings (optional)

### Crawler
- [ ] Rate limiting (requestDelayMs)
- [ ] Concurrent requests (maxConcurrentRequests)
- [ ] HTTP client keep-alive
- [ ] Connection pooling

### MCP Server
- [ ] Response streaming (if large)
- [ ] Lazy chunk loading
- [ ] Database connection pooling

## Testing Strategy

### Unit Tests
- All parsers with real doc samples
- Chunking logic with edge cases
- Token counting accuracy
- Embedding normalization

### Integration Tests
- Database operations (Testcontainers)
- End-to-end crawl pipeline
- Search with real embeddings

### Performance Tests
- Query latency benchmarks
- Concurrent request handling
- Memory profiling (embedding model)

## Deployment Checklist

- [ ] Test with production-like data volume
- [ ] Verify model downloads correctly
- [ ] Test change detection (ETag handling)
- [ ] Validate token budgets with real queries
- [ ] Ensure logs are visible (docker logs)
- [ ] Test restart behavior (data persistence)
- [ ] Verify pgvector extension enabled
- [ ] Test crawler cron scheduling

## Common Pitfalls

1. **Token counting mismatch**: JTokkit ≈ Claude, not exact
   - Solution: Add 10% safety margin to budgets

2. **ONNX model platform issues**: ARM vs x64
   - Solution: Use multi-platform ONNX runtime libs

3. **Flyway conflicts with Exposed**: Enum types
   - Solution: Create enums in init-db.sql before migrations

4. **pgvector index build time**: Hours for 50K vectors
   - Solution: Build after bulk load, not during insert

5. **Android docs React rendering**: Empty HTML
   - Solution: Use sitemap.xml for URL discovery

## Next Immediate Steps

1. Complete indexer repositories (1 day)
2. Build embedding service with ONNX (2 days)
3. Implement parsers (2 days)
4. Create MCP server (1 day)
5. Build crawler service (1 day)
6. Create Docker images (1 day)
7. End-to-end testing (1 day)

**Total estimated time**: 9-10 days for MVP
