#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE_URL:-http://127.0.0.1:18080/api/v1}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
STAMP="$(date +%s)-$RANDOM"
EMAIL_A="hnsw-a-$STAMP@shiye.local"
EMAIL_B="hnsw-b-$STAMP@shiye.local"
PASSWORD="HnswSmoke123"

json_get() {
  node -e '
let raw = ""
process.stdin.on("data", (chunk) => { raw += chunk })
process.stdin.on("end", () => {
  const value = JSON.parse(raw)
  const path = process.argv[1].split(".")
  let current = value
  for (const key of path) current = current[key]
  if (current === undefined || current === null) process.exit(1)
  process.stdout.write(String(current))
})
' "$1"
}

json_post() {
  curl -sS -X POST "$1" -H "Content-Type: application/json" "${@:3}" --data "$2"
}

redis_cli() {
  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "$@"
}

mysql_cli() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
    -u"$MYSQL_USER" --default-character-set=utf8mb4 shiye "$@"
}

echo "[hnsw-ci] register users"
A=$(json_post "$BASE/auth/register" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\",\"displayName\":\"HNSW A\"}")
B=$(json_post "$BASE/auth/register" "{\"email\":\"$EMAIL_B\",\"password\":\"$PASSWORD\",\"displayName\":\"HNSW B\"}")
TOKEN_A=$(echo "$A" | json_get data.accessToken)
TOKEN_B=$(echo "$B" | json_get data.accessToken)

echo "[hnsw-ci] create team and join"
TEAM=$(json_post "$BASE/teams" '{"name":"HNSW Team"}' -H "Authorization: Bearer $TOKEN_A")
TEAM_ID=$(echo "$TEAM" | json_get data.id)
INVITE=$(json_post "$BASE/teams/$TEAM_ID/invites" '{"role":"MEMBER"}' -H "Authorization: Bearer $TOKEN_A")
CODE=$(echo "$INVITE" | json_get data.code)
json_post "$BASE/teams/join" "{\"code\":\"$CODE\"}" -H "Authorization: Bearer $TOKEN_B" >/dev/null

echo "[hnsw-ci] ingest and share article"
CONTENT="HNSW team scope validation. $(printf 'team knowledge vector %.0s' $(seq 1 40)) Team knowledge chunk retrieval."
ARTICLE=$(json_post "$BASE/articles/collect" \
  "{\"mode\":\"CURRENT_PAGE\",\"url\":\"https://example.com/hnsw-team-$STAMP\",\"title\":\"HNSW Team Article\",\"content\":\"$CONTENT\"}" \
  -H "Authorization: Bearer $TOKEN_A")
ARTICLE_ID=$(echo "$ARTICLE" | json_get data.articleId) || {
  echo "collect failed: $ARTICLE" >&2
  exit 1
}
json_post "$BASE/articles/$ARTICLE_ID/share" "{\"teamId\":\"$TEAM_ID\"}" \
  -H "Authorization: Bearer $TOKEN_A" >/dev/null

CHUNK_ID=$(mysql_cli -N -e "SELECT id FROM article_chunks WHERE article_id=$ARTICLE_ID ORDER BY id LIMIT 1")
test -n "$CHUNK_ID"

echo "[hnsw-ci] verify index rebuild and team fields"
redis_cli FT.INFO idx:kb_chunks | tr -d '\r' > /tmp/hnsw-index.info
grep -qx 'team_id' /tmp/hnsw-index.info
grep -qx '1024' /tmp/hnsw-index.info
test "$(redis_cli HGET "kb:chunk:$CHUNK_ID" team_id | tr -d '\r')" = "$TEAM_ID"
test "$(redis_cli HEXISTS "kb:chunk:$CHUNK_ID" vector_bin | tr -d '\r')" = "1"
TEAM_HITS=0
for i in $(seq 1 180); do
  TEAM_HITS=$(redis_cli FT.SEARCH idx:kb_chunks "@team_id:{$TEAM_ID}" LIMIT 0 1 \
    | head -n1 | tr -d '\r')
  if [ "${TEAM_HITS:-0}" -ge 1 ]; then
    break
  fi
  sleep 1
done
test "${TEAM_HITS:-0}" -ge 1

echo "[hnsw-ci] team scope chat retrieves a citation"
CHAT_STATUS=$(curl -sS -o /tmp/hnsw-chat.sse -w '%{http_code}' -X POST "$BASE/chat/stream" \
  -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  --data "{\"scope\":{\"type\":\"TEAM\",\"id\":\"$TEAM_ID\"},\"question\":\"HNSW team knowledge retrieval\"}")
test "$CHAT_STATUS" = "200"
CITATIONS=$(grep -c '^event:citation' /tmp/hnsw-chat.sse || true)
test "$CITATIONS" -ge 1

echo "[hnsw-ci] hnsw smoke passed: team=$TEAM_ID article=$ARTICLE_ID chunk=$CHUNK_ID citations=$CITATIONS"
