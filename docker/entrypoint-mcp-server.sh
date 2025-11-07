#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/mcp-server}"
CONFIG_PATH="${APP_CONFIG_PATH:-/app/config/application.conf}"
FALLBACK_CONFIG="${APP_HOME}/config/application.conf"

args=()
if [[ -f "${CONFIG_PATH}" ]]; then
  args+=(--config "${CONFIG_PATH}")
elif [[ -f "${FALLBACK_CONFIG}" ]]; then
  args+=(--config "${FALLBACK_CONFIG}")
fi

echo $$ > /tmp/mcp-server.pid

exec "${APP_HOME}/bin/mcp-server" "${args[@]}" "$@"
