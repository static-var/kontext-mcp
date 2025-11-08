# MCP Kontext

MCP Kontext (“Kontext” for short) is a Retrieval-Augmented-Generation stack tailored for Android & Kotlin documentation.  
It ships as a Gradle monorepo with modular services so you can crawl doc sources, embed & index them in PostgreSQL/pgvector, and expose them as an MCP server (stdio transport) or via the crawler’s Ktor web UI.

---

## Contents

| Module | Purpose |
| --- | --- |
| `modules/shared` | Shared config + model definitions (`SearchRequest`, `Document`, etc.). |
| `modules/indexer` | Exposed/Flyway/Postgres layer, repositories, and vector search. |
| `modules/embedder` | ONNX Runtime BGE-large integration + tokenizers. |
| `modules/parser` | Parser framework + Android/Kotlin parsers + chunking utilities. |
| `modules/crawler` | Ktor admin UI, scheduling, HTTP fetching, crawl orchestration. |
| `modules/mcp-server` | MCP stdio server with `search_docs` tool powered by the indexer. |

Supporting assets live under `docs/`, `docker/`, and `config/`.

---

## Quick Start

### Prerequisites
* Java 21+
* Docker & Docker Compose (for postgres/pgvector and container builds)
* Gradle wrapper (included)

### Local Build & Test
```bash
./gradlew build         # compiles everything + runs unit + integration tests
./gradlew test          # faster test-only pass
```

Tests include:
* Unit suites for each module
* Testcontainers-backed integration tests (indexer repositories, MCP evaluation harness)
* Phase 5 evaluation dataset & metrics via `SearchEvaluationIntegrationTest`

> **Note:** Integration tests download/run `pgvector/pgvector:pg16`. Make sure Docker is running.

### Run MCP Server (stdio)
```bash
./gradlew :modules:mcp-server:run
```
The server reads `config/application.conf` by default. Override with `APP_CONFIG_PATH` or `--config`.

### Run Crawler Web UI
```bash
./gradlew :modules:crawler:run
```
Then open `http://localhost:8080` (default). Credentials are in `modules/crawler/src/main/resources/application.conf` and can be overridden with env vars (see below).

---

## Docker

Kontext ships Dockerfiles for both primary services:
* `docker/Dockerfile.mcp-server`
* `docker/Dockerfile.crawler`

Use the provided `docker-compose.yml` to run postgres + both services:
```bash
docker compose up --build
```

Key volumes:
* `postgres_data` – Postgres/pgvector storage
* `model_cache` – shared ONNX model cache between server & crawler

Important environment variables (see compose file for defaults):

| Variable | Description |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Database connectivity shared by both apps |
| `EMBEDDING_MODEL_PATH` | ONNX model identifier/path |
| `MODEL_CACHE_DIR` | Directory containing downloaded model weights (`/app/models`) |
| `APP_CONFIG_PATH` | Path to HOCON config (container default: `/app/config/application.conf`) |
| `CRAWLER_PORT` | Host/UI port for crawler |
| `CRAWLER_AUTH_USERNAME`, `CRAWLER_AUTH_PASSWORD_BCRYPT` | Dashboard credentials |
| `CRAWLER_SESSION_SECRET` | Signing secret for Ktor sessions |
| `CRAWLER_INDEXER_BASE_URL` | Base URL used internally for indexer calls |

Health checks:
* MCP server uses `docker/healthcheck-mcp-server.sh`
* Crawler exposes `GET /healthz` and `docker/healthcheck-crawler.sh`

For more deployment detail see `docs/deployment.md`.

---

## Evaluation & Monitoring (Phase 5)

* **Dataset** – `modules/mcp-server/src/test/resources/evaluation/queries.json` (24 representative Android/Kotlin/mixed queries with keyword expectations).
* **Metrics** – `RetrievalMetrics.kt` implements Precision@K, Recall@K, and MRR.
* **Integration Test** – `SearchEvaluationIntegrationTest` spins up Postgres in Docker, seeds deterministic embeddings, runs the entire `SearchService`, and logs summary metrics.
  * Last run (deterministic embeddings): `Precision@5 ≈ 0.23`, `Recall@5 ≈ 0.33`, `MRR ≈ 0.33`.
  * Thresholds are intentionally low because the test uses a deterministic embedder stub for speed; raise these once running with real ONNX embeddings.

To execute just the evaluation suite:
```bash
./gradlew :modules:mcp-server:test --tests SearchEvaluationIntegrationTest
```

---

## Configuration Notes

* `config/application.conf` holds base settings with `${?ENV_VAR}` overrides; copy/modify for local use.
* Crawler-specific configuration (auth, session, indexer base URL) lives in `modules/crawler/src/main/resources/application.conf`.
* The MCP server and crawler both honor `APP_CONFIG_PATH` to point at custom configs.
* The crawler's protected HTTP API issues JWT bearer tokens via `POST /login` when you send JSON credentials; reuse that token in `Authorization: Bearer <token>` headers for scripted clients.

---

## Phase Status Snapshot

| Phase | Status | Notes |
| --- | --- | --- |
| Phase 1 – Foundation | ✅ | Completed |
| Phase 2 – Core Infrastructure | ✅ | Indexer/Parser/Embedder ready |
| Phase 3 – Services | ✅ | Error handling + retry logic, crawler & MCP server live |
| Phase 4 – Deployment | ✅ | Dockerfiles, compose stack, deployment docs |
| Phase 5 – Testing | ⚙️ | Dataset + metrics + E2E harness done; performance tuning deferred to post-deploy |
| Phase 6 – Enhancements | 🔜 | (Future) |

Optimization tasks (similarity thresholds, chunk size tuning, etc.) are intentionally deferred until the real embedding model is exercised in staging/production.

---

## Contributing & Issue Tracking

1. Favor module-level unit tests (`./gradlew :modules:<module>:test`) while iterating.
2. Use `./gradlew build` before submitting PRs; it runs all suites including Testcontainers.
3. When editing SQL or Flyway migrations, keep `docker/init-db.sql` in sync for local dev containers.
4. For Phase 5 performance tuning, switch the evaluation harness to real embeddings before adjusting similarity thresholds.

---

## License

MIT – see `LICENSE`.
