#!/usr/bin/env bash
set -euo pipefail

PID_FILE="/tmp/mcp-server.pid"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"

if [[ ! -f "${PID_FILE}" ]]; then
  echo "pid file missing"
  exit 1
fi

if ! kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
  echo "process not running"
  exit 1
fi

if ! timeout 2 bash -c "</dev/tcp/${DB_HOST}/${DB_PORT}" 2>/dev/null; then
  echo "database unreachable"
  exit 1
fi

exit 0
