#!/usr/bin/env bash
set -euo pipefail

PID_FILE="/tmp/mcp-crawler.pid"
PORT="${CRAWLER_PORT:-8080}"

if [[ ! -f "${PID_FILE}" ]]; then
  echo "pid file missing"
  exit 1
fi

if ! kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
  echo "process not running"
  exit 1
fi

if ! curl -fsS "http://127.0.0.1:${PORT}/healthz" > /dev/null; then
  echo "health endpoint failed"
  exit 1
fi

exit 0
