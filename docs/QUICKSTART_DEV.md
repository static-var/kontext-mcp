# Quick Start - Development Guide

This guide gets you up and running for development in under 10 minutes.

## Prerequisites

- ✅ JDK 17+ ([Download](https://adoptium.net/))
- ✅ Docker & Docker Compose ([Download](https://www.docker.com/products/docker-desktop))
- ✅ Git
- ✅ Your favorite Kotlin IDE (IntelliJ IDEA recommended)

## Initial Setup

### 1. Project Structure Check

```bash
cd /Users/staticvar/Projects/mcp
ls -la
```

You should see:
- `build.gradle.kts` - Root build file
- `settings.gradle.kts` - Module configuration
- `modules/` - All project modules
- `docker-compose.yml` - Infrastructure setup
- `config/` - Application configuration
- `docs/` - Documentation

### 2. Environment Configuration

```bash
# Create local environment file
cp .env.example .env

# Edit if needed (default values work for local dev)
nano .env
```

Default values:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mcp_db
DB_USER=mcp_user
DB_PASSWORD=mcp_password
```

### 3. Start Database

```bash
# Start PostgreSQL with pgvector
docker-compose up -d postgres

# Wait for it to be ready (10-15 seconds)
docker-compose logs -f postgres

# Look for: "database system is ready to accept connections"
# Press Ctrl+C to exit logs
```

### 4. Verify Database Setup

```bash
# Connect to database
docker-compose exec postgres psql -U mcp_user -d mcp_db

# Check pgvector extension
mcp_db=# \dx
# You should see "vector" extension listed

# Check tables (from Flyway migrations)
mcp_db=# \dt
# You should see: source_urls, documents, embeddings

# Check initial seed data
mcp_db=# SELECT url, parser_type, status FROM source_urls LIMIT 5;
# You should see Android and Kotlin doc URLs

# Exit
mcp_db=# \q
```

### 5. Build Project

```bash
# Build all modules
./gradlew build

# Skip tests for faster build
./gradlew build -x test
```

Expected output:
```
BUILD SUCCESSFUL in 30s
```

### 6. IDE Setup (IntelliJ IDEA)

1. Open IntelliJ IDEA
2. File → Open → Select `/Users/staticvar/Projects/mcp`
3. Wait for Gradle sync to complete
4. Verify modules appear in Project panel:
   - shared
   - embedder
   - parser
   - indexer
   - crawler
   - mcp-server

## Development Workflow

### Running Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :modules:shared:test

# With logging
./gradlew test --info
```

### Code Style

```bash
# Format code (if you have ktlint plugin)
./gradlew ktlintFormat

# Check style
./gradlew ktlintCheck
```

### Database Migrations

```bash
# Migrations run automatically on app start via Flyway
# To manually run migrations:

# Clean database (CAUTION: Deletes all data)
docker-compose down -v postgres
docker-compose up -d postgres

# Migrations run on first connection
# OR manually via Flyway CLI (if installed)
flyway -configFiles=flyway.conf migrate
```

### Adding a New Migration

```bash
# Create new migration file
touch modules/indexer/src/main/resources/db/migration/V3__your_description.sql

# Edit with SQL changes
nano modules/indexer/src/main/resources/db/migration/V3__your_description.sql

# Restart app to apply
```

## Working on Specific Modules

### Embedder Module

```bash
# Navigate to module
cd modules/embedder

# Run tests
../../gradlew test

# Build
../../gradlew build
```

**Implementation tasks**:
1. Model downloader (`ModelDownloader.kt`)
2. ONNX session manager (`EmbeddingServiceImpl.kt`)
3. Token counter (`TokenCounter.kt`)

**Test approach**:
- Use small test models (not full BGE-large)
- Mock ONNX session for unit tests
- Integration tests with real model (optional)

### Parser Module

```bash
cd modules/parser
```

**Implementation tasks**:
1. DocumentParser interface
2. StructuralChunker utility
3. AndroidDocsParser
4. KotlinLangParser
5. ParserRegistry

**Test approach**:
- Save sample HTML files in `src/test/resources/samples/`
- Test parsing with real doc samples
- Verify chunk boundaries and metadata

### Indexer Module

```bash
cd modules/indexer
```

**Implementation tasks**:
1. Repository interfaces
2. Repository implementations (Exposed)
3. pgvector search queries
4. Transaction helpers

**Test approach**:
- Use Testcontainers for PostgreSQL
- Test vector search with known embeddings
- Verify HNSW index usage (EXPLAIN ANALYZE)

### MCP Server

```bash
cd modules/mcp-server
```

**Implementation tasks**:
1. MCP SDK integration
2. SearchDocsTool
3. SearchService with token budget
4. Configuration loading

**Test approach**:
- Mock embedding and repository layers
- Test token budget packing algorithm
- Integration test with real database

### Crawler

```bash
cd modules/crawler
```

**Implementation tasks**:
1. CLI with Clikt
2. CrawlerService
3. HTTP fetching with change detection
4. Scheduled crawling

**Test approach**:
- Mock HTTP responses
- Test change detection logic
- Integration test with real URLs (limited)

## Common Development Tasks

### Check Database Contents

```bash
# Connect to database
docker-compose exec postgres psql -U mcp_user -d mcp_db

# Count documents
SELECT COUNT(*) FROM documents;

# Count embeddings
SELECT COUNT(*) FROM embeddings;

# Check crawl status
SELECT status, COUNT(*) FROM source_urls GROUP BY status;

# Search example (after embeddings are populated)
SELECT content, 1 - (embedding <=> '[0.1, 0.2, ...]'::vector) as similarity
FROM embeddings
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;
```

### Reset Database

```bash
# Stop and remove volumes
docker-compose down -v

# Start fresh
docker-compose up -d postgres

# Migrations will run automatically on next connection
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f postgres
docker-compose logs -f mcp-server
docker-compose logs -f crawler

# Last 100 lines
docker-compose logs --tail=100 postgres
```

### Rebuild Docker Images

```bash
# After code changes
docker-compose build

# Force rebuild (ignore cache)
docker-compose build --no-cache

# Rebuild and restart
docker-compose up -d --build
```

## Debugging

### IntelliJ IDEA Remote Debugging

Add to `build.gradle.kts` (application modules):

```kotlin
tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    )
}
```

Then:
1. Run → Edit Configurations
2. Add New → Remote JVM Debug
3. Port: 5005
4. Click Debug

### Database Query Debugging

Enable SQL logging in `application.conf`:

```hocon
exposed {
    logSqlStatements = true
}
```

Or set environment variable:
```bash
export EXPOSED_LOG_STATEMENTS=true
```

### HTTP Request Debugging

Enable Ktor client logging in `parser` or `crawler` modules:

```kotlin
val client = HttpClient(CIO) {
    install(Logging) {
        level = LogLevel.ALL
    }
}
```

## Useful Gradle Commands

```bash
# List all tasks
./gradlew tasks

# Dependency tree
./gradlew :modules:mcp-server:dependencies

# Clean build
./gradlew clean build

# Build without daemon (faster for CI)
./gradlew build --no-daemon

# Parallel builds
./gradlew build --parallel

# Continuous build (auto-rebuild on changes)
./gradlew build --continuous
```

## Next Steps

Once you're set up:

1. **Read Architecture**: `docs/ARCHITECTURE.md`
2. **Check Tasks**: `docs/tasks.md`
3. **Implementation Guide**: `docs/IMPLEMENTATION_GUIDE.md`
4. **Pick a Task**: Start with indexer repositories or embedder

## Troubleshooting

### "Could not connect to database"

```bash
# Check PostgreSQL is running
docker-compose ps

# Check logs
docker-compose logs postgres

# Verify port not in use
lsof -i :5432

# Restart
docker-compose restart postgres
```

### "pgvector extension not found"

```bash
# Verify image
docker-compose exec postgres psql -U mcp_user -d mcp_db -c "\dx"

# Should show pgvector extension
# If not, you might have wrong image

# Fix: Update docker-compose.yml
# image: pgvector/pgvector:pg16
```

### "Gradle build fails"

```bash
# Clean Gradle cache
./gradlew clean
rm -rf ~/.gradle/caches

# Re-sync
./gradlew build --refresh-dependencies
```

### "Out of memory during build"

```bash
# Increase Gradle memory in gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m

# Or use Gradle daemon
./gradlew build --daemon
```

### "Can't find module sources in IDE"

1. File → Invalidate Caches → Invalidate and Restart
2. Right-click project → Gradle → Reload Gradle Project
3. Ensure Kotlin plugin is installed and up-to-date

## Development Best Practices

### Before Committing

```bash
# Format code
./gradlew ktlintFormat

# Run tests
./gradlew test

# Build
./gradlew build

# Check no unintended changes
git status
git diff
```

### Code Review Checklist

- [ ] No `!!` operator (use `requireNotNull` with message)
- [ ] Proper error handling (try-catch with logging)
- [ ] Transaction wrapping for multi-step DB ops
- [ ] No magic numbers or hardcoded URLs
- [ ] KDoc comments for public APIs
- [ ] Unit tests for new features
- [ ] Integration tests for critical flows

### Documentation Updates

When adding features:
- [ ] Update `docs/tasks.md` (mark as completed)
- [ ] Update `docs/PROJECT_STATUS.md` (progress)
- [ ] Add usage examples to README
- [ ] Update `docs/ARCHITECTURE.md` if design changes

## Resources

- **Kotlin Docs**: https://kotlinlang.org/docs/home.html
- **Exposed SQL**: https://github.com/JetBrains/Exposed/wiki
- **pgvector**: https://github.com/pgvector/pgvector
- **MCP Spec**: https://spec.modelcontextprotocol.io/
- **ONNX Runtime**: https://onnxruntime.ai/docs/get-started/with-java.html

## Getting Help

- Check `docs/IMPLEMENTATION_GUIDE.md` for detailed steps
- Review `docs/tasks.md` for task breakdown
- See `docs/ARCHITECTURE.md` for system design
- Read project rules in `~/.claude/CLAUDE.md`

Happy coding! 🚀
