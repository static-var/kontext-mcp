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

# Optionally load credentials and other vars from .env files without shell-expanding values.
# This allows setting CRAWLER_AUTH_USERNAME/CRAWLER_AUTH_PASSWORD_BCRYPT in a mounted .env file.
load_env_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    # Skip comments and blank lines
    [[ -z "${line//[[:space:]]/}" || "$line" =~ ^[[:space:]]*# ]] && continue
    # KEY=VALUE pattern
    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      local key="${BASH_REMATCH[1]}"
      local val="${BASH_REMATCH[2]}"
      # Trim surrounding quotes if present
      if [[ "$val" =~ ^"(.*)"$ ]]; then val="${BASH_REMATCH[1]}"; fi
      if [[ "$val" =~ ^'(.*)'$ ]]; then val="${BASH_REMATCH[1]}"; fi
      # Only export if not already set in environment
      if [[ -z "${!key:-}" ]]; then
        export "$key=$val"
      fi
    fi
  done < "$file"
}

# Prefer project config mount for .env, then app home.
load_env_file "/app/config/.env"
load_env_file "${APP_HOME}/.env"

echo $$ > /tmp/mcp-crawler.pid

exec "${APP_HOME}/bin/crawler" "$@"
