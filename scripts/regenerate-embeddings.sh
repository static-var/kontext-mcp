#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="changeme"

echo "Logging in..."
TOKEN_RESPONSE=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.token')

if [ "$TOKEN" == "null" ]; then
  echo "Login failed"
  exit 1
fi

echo "1. Resetting all URLs to PENDING..."
curl -X POST "$BASE_URL/api/crawl/reset" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
echo ""

echo "2. Triggering Crawler..."
curl -X POST "$BASE_URL/api/crawl/start" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
echo ""

echo "Done! The system is now re-crawling and re-embedding all sources."
