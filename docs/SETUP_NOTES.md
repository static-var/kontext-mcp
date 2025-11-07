# Setup Notes

## Gradle Wrapper Setup

The project currently doesn't have Gradle wrapper files. To add them:

```bash
# If you have Gradle installed locally
gradle wrapper --gradle-version=8.5

# Or download wrapper manually
curl -L https://services.gradle.org/distributions/gradle-8.5-bin.zip -o gradle.zip
unzip gradle.zip
./gradle-8.5/bin/gradle wrapper --gradle-version=8.5
rm -rf gradle-8.5 gradle.zip
```

This will create:
- `gradlew` (Unix wrapper script)
- `gradlew.bat` (Windows wrapper script)
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

Once wrapper is set up, you can use `./gradlew` instead of `gradle`:

```bash
./gradlew build
./gradlew test
./gradlew :modules:mcp-server:run
```

## MCP Kotlin SDK Note

The MCP Kotlin SDK dependency in `modules/mcp-server/build.gradle.kts` is currently:

```kotlin
implementation("org.modelcontextprotocol:kotlin-sdk:0.1.0")
```

**IMPORTANT**: Verify this artifact exists in Maven Central when you start implementation. If not available:

1. Check the official repo: https://github.com/modelcontextprotocol/kotlin-sdk
2. Clone and build locally:
   ```bash
   git clone https://github.com/modelcontextprotocol/kotlin-sdk.git
   cd kotlin-sdk
   ./gradlew publishToMavenLocal
   ```
3. Update `mcp-server/build.gradle.kts` to use local maven:
   ```kotlin
   repositories {
       mavenLocal()
       mavenCentral()
   }
   ```

Alternatively, you may need to implement MCP protocol from spec directly if SDK is not ready.

## First Build

After adding Gradle wrapper, your first build will:

1. Download Gradle dependencies (~500MB)
2. Compile all Kotlin modules
3. Run Flyway migrations (requires PostgreSQL running)

Expected output:
```
BUILD SUCCESSFUL in 1m 30s
```

If build fails due to missing database:
```bash
# Start PostgreSQL first
docker-compose up -d postgres

# Wait 10 seconds, then build
./gradlew build
```

## Development Sequence

Recommended order for implementation (after foundation is complete):

### Week 1: Core Infrastructure
**Day 1-2**: Indexer repositories
- Implement SourceUrlRepository, DocumentRepository, EmbeddingRepository
- Add pgvector search queries
- Write unit tests with Testcontainers

**Day 3-4**: Embedding service
- Download BGE-large-en-v1.5 via ONNX
- Implement EmbeddingService with batching
- Add token counter
- Write unit tests

**Day 5**: Parser foundations
- DocumentParser interface
- StructuralChunker utility
- ParserRegistry
- Unit tests for chunking

### Week 2: Parsers & Services
**Day 6-7**: Implement parsers
- AndroidDocsParser with metadata extraction
- KotlinLangParser
- Unit tests with real doc samples

**Day 8**: MCP Server
- MCP SDK integration
- SearchDocsTool
- SearchService with token budget
- Integration tests

**Day 9**: Crawler
- CrawlerService
- HTTP fetching with change detection
- Scheduled crawling
- Integration tests

**Day 10**: Docker & Deployment
- Dockerfiles
- Full deployment test
- End-to-end testing
- Performance tuning

## Known Dependencies to Verify

Before starting implementation, verify these dependencies exist and versions are correct:

1. **MCP Kotlin SDK** (see note above)
2. **pgvector for PostgreSQL**: Docker image `pgvector/pgvector:pg16` ✅
3. **BGE-large-en-v1.5 ONNX model**: HuggingFace Hub ✅
4. **ONNX Runtime Java**: Maven Central ✅
5. **HuggingFace Tokenizers Java**: Maven Central ✅

All others are stable and available.

## Initial Testing Strategy

Once modules are implemented:

### Unit Tests
Run for each module independently:
```bash
./gradlew :modules:shared:test
./gradlew :modules:embedder:test
./gradlew :modules:parser:test
./gradlew :modules:indexer:test
```

### Integration Tests
Require database running:
```bash
docker-compose up -d postgres
./gradlew :modules:crawler:test
./gradlew :modules:mcp-server:test
```

### Manual Testing
1. Start services:
   ```bash
   docker-compose up -d
   ```

2. Trigger initial crawl:
   ```bash
   docker-compose exec crawler java -jar /app/crawler.jar crawl --mode=full
   ```

3. Test MCP server:
   ```bash
   echo '{"query": "How to use LaunchedEffect?"}' | docker-compose run --rm mcp-server
   ```

## Performance Baseline

After initial implementation, measure:

1. **Embedding Generation**: Time for 100 chunks
   ```bash
   time ./gradlew :modules:embedder:test --tests "EmbeddingServiceTest.testBatchEmbedding"
   ```

2. **Vector Search**: Time for 10 queries
   ```bash
   time ./gradlew :modules:indexer:test --tests "EmbeddingRepositoryTest.testSearch"
   ```

3. **Full Crawl**: Time to index 10 pages
   ```bash
   time docker-compose exec crawler java -jar /app/crawler.jar crawl --limit=10
   ```

Expected baselines:
- Embedding: ~1.5s per 100 chunks (batch of 32)
- Search: ~50-100ms per query
- Crawl: ~10-20s per page (fetch + parse + embed + index)

## Troubleshooting Common Issues

### 1. "NoClassDefFoundError: org/postgresql/Driver"

**Cause**: PostgreSQL driver not in runtime classpath

**Fix**: Add runtime dependency:
```kotlin
runtimeOnly("org.postgresql:postgresql:42.7.1")
```

### 2. "UnsatisfiedLinkError: onnxruntime"

**Cause**: ONNX Runtime native libs not found

**Fix**: Ensure platform-specific ONNX lib is included:
```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime:1.17.0")
// Automatically includes native libs for your platform
```

### 3. "Vector dimension mismatch"

**Cause**: Embedding dimension doesn't match pgvector column

**Fix**: Verify config and migration match:
- `application.conf`: `embedding.dimension = 1024`
- Migration: `embedding vector(1024)`

### 4. "Flyway migration checksum mismatch"

**Cause**: Migration file changed after being applied

**Fix**: Reset database or repair:
```bash
docker-compose down -v
docker-compose up -d postgres
```

### 5. "Connection refused: localhost:5432"

**Cause**: PostgreSQL not running or using Docker host networking

**Fix**:
```bash
# Check PostgreSQL is running
docker-compose ps

# If using Docker, connection string should use service name:
# DB_HOST=postgres (not localhost)
```

## Next Steps

1. ✅ Foundation complete
2. ⏳ Add Gradle wrapper
3. ⏳ Verify MCP SDK availability
4. ⏳ Start implementation (see docs/IMPLEMENTATION_GUIDE.md)
5. ⏳ Follow development sequence above
6. ⏳ Test incrementally
7. ⏳ Deploy and tune
