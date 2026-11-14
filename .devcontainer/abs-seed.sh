#!/usr/bin/env bash
# First-run setup of the local Audiobookshelf server: a root user and a library holding the
# sample audiobook. Safe to run again.
set -euo pipefail

ABS_URL="${ABS_URL:-http://localhost:13378}"
ABS_USER="${ABS_USER:-root}"
ABS_PASSWORD="${ABS_PASSWORD:-lissen}"

echo "Waiting for Audiobookshelf at $ABS_URL"
for _ in $(seq 1 60); do
  curl -fsS "$ABS_URL/ping" > /dev/null 2>&1 && break
  sleep 2
done
curl -fsS "$ABS_URL/ping" > /dev/null || { echo "Audiobookshelf is not answering at $ABS_URL" >&2; exit 1; }

if [ "$(curl -fsS "$ABS_URL/status" | jq -r '.isInit')" != "true" ]; then
  echo "Creating root user '$ABS_USER'"
  curl -fsS -X POST "$ABS_URL/init" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg u "$ABS_USER" --arg p "$ABS_PASSWORD" '{newRoot: {username: $u, password: $p}}')" > /dev/null
fi

token="$(curl -fsS -X POST "$ABS_URL/login" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg u "$ABS_USER" --arg p "$ABS_PASSWORD" '{username: $u, password: $p}')" \
  | jq -r '.user.accessToken // .user.token')"

auth=(-H "Authorization: Bearer $token")

library_id="$(curl -fsS "${auth[@]}" "$ABS_URL/api/libraries" | jq -r '.libraries[]? | select(.name == "Test Books") | .id' | head -n1)"

if [ -z "$library_id" ]; then
  echo "Creating library 'Test Books'"
  library_id="$(curl -fsS -X POST "$ABS_URL/api/libraries" "${auth[@]}" -H 'Content-Type: application/json' \
    -d '{"name":"Test Books","folders":[{"fullPath":"/audiobooks"}],"mediaType":"book"}' | jq -r '.id')"
fi

curl -fsS -X POST "${auth[@]}" "$ABS_URL/api/libraries/$library_id/scan" > /dev/null || true

echo "Audiobookshelf ready: $ABS_URL (user '$ABS_USER', password '$ABS_PASSWORD')"
echo "From the emulator the server is http://10.0.2.2:13378"
