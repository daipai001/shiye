#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE_URL:-http://127.0.0.1:18080/api/v1}"
CURL_OPTS=()
if [ "${CURL_INSECURE:-false}" = "true" ]; then
  CURL_OPTS+=(-k)
fi
STAMP="$(date +%s)-$RANDOM"
EMAIL_A="team-a-$STAMP@shiye.local"
EMAIL_B="team-b-$STAMP@shiye.local"
PASSWORD="TeamSmoke123"

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
  curl "${CURL_OPTS[@]}" -sS -X POST "$1" -H "Content-Type: application/json" "${@:3}" --data "$2"
}

json_put() {
  curl "${CURL_OPTS[@]}" -sS -X PUT "$1" -H "Content-Type: application/json" "${@:3}" --data "$2"
}

json_delete() {
  curl "${CURL_OPTS[@]}" -sS -X DELETE "$1" -H "Authorization: Bearer $2"
}

auth_header() {
  printf 'Authorization: Bearer %s' "$1"
}

echo "[team-ci] register users"
A=$(json_post "$BASE/auth/register" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\",\"displayName\":\"Team A\"}")
B=$(json_post "$BASE/auth/register" "{\"email\":\"$EMAIL_B\",\"password\":\"$PASSWORD\",\"displayName\":\"Team B\"}")
TOKEN_A=$(echo "$A" | json_get data.accessToken)
TOKEN_B=$(echo "$B" | json_get data.accessToken)
USER_B=$(echo "$B" | json_get data.user.id)
test -n "$TOKEN_A"
test -n "$TOKEN_B"

echo "[team-ci] create team and invite"
TEAM=$(json_post "$BASE/teams" '{"name":"CI Team"}' -H "$(auth_header "$TOKEN_A")")
TEAM_ID=$(echo "$TEAM" | json_get data.id)
INVITE=$(json_post "$BASE/teams/$TEAM_ID/invites" '{"role":"MEMBER"}' -H "$(auth_header "$TOKEN_A")")
CODE=$(echo "$INVITE" | json_get data.code)
JOIN=$(json_post "$BASE/teams/join" "{\"code\":\"$CODE\"}" -H "$(auth_header "$TOKEN_B")")
test "$(echo "$JOIN" | json_get data.memberCount)" = "2"

echo "[team-ci] share article and add team taxonomy"
ARTICLE=$(json_post "$BASE/articles/collect" \
  "{\"mode\":\"CURRENT_PAGE\",\"url\":\"https://example.com/ci-team-$STAMP\",\"title\":\"CI Team Article\",\"content\":\"CI team article. Vector retrieval and citation validation for team scope. $(printf 'team knowledge %.0s' $(seq 1 30))\"}" \
  -H "$(auth_header "$TOKEN_A")")
ARTICLE_ID=$(echo "$ARTICLE" | json_get data.articleId)
json_post "$BASE/articles/$ARTICLE_ID/share" "{\"teamId\":\"$TEAM_ID\"}" -H "$(auth_header "$TOKEN_A")" >/dev/null
TAG=$(json_post "$BASE/teams/$TEAM_ID/tags" '{"name":"CI-Tag","color":"#3b82f6"}' -H "$(auth_header "$TOKEN_A")")
TAG_ID=$(echo "$TAG" | json_get data.id)
FOLDER=$(json_post "$BASE/teams/$TEAM_ID/folders" '{"name":"CI-Folder"}' -H "$(auth_header "$TOKEN_A")")
FOLDER_ID=$(echo "$FOLDER" | json_get data.id)
json_post "$BASE/articles/$ARTICLE_ID/team-tags" "{\"tagId\":\"$TAG_ID\"}" -H "$(auth_header "$TOKEN_B")" >/dev/null
json_post "$BASE/articles/$ARTICLE_ID/team-folders" "{\"folderId\":\"$FOLDER_ID\"}" -H "$(auth_header "$TOKEN_B")" >/dev/null

echo "[team-ci] team article filters"
BY_TAG=$(curl "${CURL_OPTS[@]}" -sS "$BASE/articles?teamId=$TEAM_ID&tagId=$TAG_ID" -H "$(auth_header "$TOKEN_B")")
test "$(echo "$BY_TAG" | json_get data.total)" = "1"
test "$(echo "$BY_TAG" | json_get data.items.0.tags.0.name)" = "CI-Tag"
DETAIL=$(curl "${CURL_OPTS[@]}" -sS "$BASE/articles/$ARTICLE_ID" -H "$(auth_header "$TOKEN_B")")
test "$(echo "$DETAIL" | json_get data.teamId)" = "$TEAM_ID"

echo "[team-ci] team quota blocks chat"
json_put "$BASE/teams/$TEAM_ID" \
  "{\"name\":\"CI Team\",\"monthlyTokenQuota\":0,\"monthlyCostQuota\":20}" \
  -H "$(auth_header "$TOKEN_A")" >/dev/null
CHAT_STATUS=$(curl "${CURL_OPTS[@]}" -sS -o /tmp/team-chat.json -w '%{http_code}' -X POST "$BASE/chat/stream" \
  -H "$(auth_header "$TOKEN_B")" -H 'Content-Type: application/json' \
  --data "{\"scope\":{\"type\":\"TEAM_TAG\",\"id\":\"$TAG_ID\"},\"question\":\"team quota test\"}")
test "$CHAT_STATUS" = "429"
test "$(json_get code < /tmp/team-chat.json)" = "42905"

echo "[team-ci] dissolve"
json_delete "$BASE/teams/$TEAM_ID" "$TOKEN_A" >/dev/null
TEAM_STATUS=$(curl "${CURL_OPTS[@]}" -sS -o /tmp/team-after.json -w '%{http_code}' "$BASE/teams/$TEAM_ID" -H "$(auth_header "$TOKEN_B")")
test "$TEAM_STATUS" = "404"

echo "[team-ci] team smoke passed"
