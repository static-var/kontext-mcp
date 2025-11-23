#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="changeme"
URL_TO_ADD="$1"

if [ -z "$URL_TO_ADD" ]; then
  echo "Usage: $0 <url>"
  exit 1
fi

echo "Logging in..."
TOKEN_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.token')

if [ "$TOKEN" == "null" ]; then
  echo "Login failed: $TOKEN_RESPONSE"
  exit 1
fi

echo "Adding URL: $URL_TO_ADD"
curl -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"url\": \"$URL_TO_ADD\"}"
echo ""
