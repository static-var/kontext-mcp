#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/mcp-crawler}"
CONFIG_PATH="${APP_CONFIG_PATH:-/app/config/application.conf}"
FALLBACK_CONFIG="${APP_HOME}/config/application.conf"

if [[ -f "${CONFIG_PATH}" ]]; then
  export APP_CONFIG_PATH="${CONFIG_PATH}"
elif [[ -f "${FALLBACK_CONFIG}" ]]; then
  export APP_CONFIG_PATH="${FALLBACK_CONFIG}"
fi

echo $$ > /tmp/mcp-crawler.pid

exec "${APP_HOME}/bin/crawler" "$@"
