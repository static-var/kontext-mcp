# Deployment Guide

This project ships multi-stage Docker images for both the MCP server (stdio transport) and the crawler web service. The containers are defined under `docker/` and orchestrated via the root `docker-compose.yml`.

## Building Images

```bash
# Build everything
docker compose build

# Build a single service
docker compose build mcp-server
docker compose build crawler
```

Both Dockerfiles compile the corresponding Gradle module via `installDist`, copy the default configuration, and switch to a non-root runtime user. Model artifacts are stored under `/app/models` and should be mounted as a volume for persistence/shared caching.

## Runtime Configuration

Key environment variables (see `docker-compose.yml` for defaults):

| Variable | Description |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL/pgvector connection used by both services. |
| `EMBEDDING_MODEL_PATH` | Model identifier or local path understood by the embedding module. |
| `MODEL_CACHE_DIR` | Directory used to store downloaded ONNX models (`/app/models`). Mount a persistent volume here. |
| `APP_CONFIG_PATH` | Optional override for `config/application.conf`. The compose file mounts `./config` to `/app/config` for live edits. |
| `CRAWLER_PORT` | HTTP port exposed by the crawler UI (default `8080`). |
| `CRAWLER_AUTH_USERNAME`, `CRAWLER_AUTH_PASSWORD_BCRYPT` | Credentials for the crawler dashboard/API (bcrypt hash required). |
| `CRAWLER_SESSION_SECRET` | 32-byte secret used to sign crawler sessions. |
| `CRAWLER_INDEXER_BASE_URL` | Base URL used by crawler actions to talk to the MCP/indexer stack. |

You can extend these via an `.env` file that Docker Compose automatically reads.

## Health Checks

- **MCP server**: `docker/healthcheck-mcp-server.sh` verifies that the server process is running and that the PostgreSQL socket is reachable. Docker declares the container unhealthy when retries fail.
- **Crawler**: `docker/healthcheck-crawler.sh` calls the new `/healthz` endpoint, ensuring the Ktor server is accepting traffic before marking the container healthy.

## Running the Stack

```bash
docker compose up -d postgres
docker compose up -d mcp-server crawler
```

The crawler dashboard is exposed at `http://localhost:8080`. Model artifacts under `/app/models` are cached across both services through the shared `model_cache` volume. The MCP server continues to operate over stdio (bind it to your MCP client by pointing to the container’s `bin/mcp-server` entrypoint or exposing via `docker exec`).
