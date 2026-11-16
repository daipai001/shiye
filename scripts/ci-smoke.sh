#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE_URL:-http://127.0.0.1:18080/api/v1}"
EMAIL="ci-$(date +%s)-$RANDOM@shiye.local"
PASSWORD="CiTest123"
FOLDER_ID="ci-$(date +%s)"

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

json_code_zero() {
  node -e '
let raw = ""
process.stdin.on("data", (chunk) => { raw += chunk })
process.stdin.on("end", () => {
  const value = JSON.parse(raw)
  if (value.code !== 0) process.exit(1)
})
'
}

json_post() {
  local response code body
  response=$(curl -sS -X POST "$1" -H "Content-Type: application/json" "${@:3}" --data "$2" -w $'\n%{http_code}')
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [ "$code" -ge 400 ]; then
    echo "HTTP $code: $body" >&2
    return 1
  fi
  printf '%s' "$body"
}

echo "[ci] health"
curl -fsS "$BASE/health" | json_code_zero

echo "[ci] register"
REGISTER=$(json_post "$BASE/auth/register" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"CI\"}")
TOKEN=$(echo "$REGISTER" | json_get data.accessToken)
REFRESH=$(echo "$REGISTER" | json_get data.refreshToken)
test -n "$TOKEN"
test -n "$REFRESH"

echo "[ci] users/me"
curl -fsS "$BASE/users/me" -H "Authorization: Bearer $TOKEN" | json_code_zero

echo "[ci] refresh rotation"
REFRESHED=$(json_post "$BASE/auth/refresh" "{\"refreshToken\":\"$REFRESH\"}")
NEW_TOKEN=$(echo "$REFRESHED" | json_get data.accessToken)
NEW_REFRESH=$(echo "$REFRESHED" | json_get data.refreshToken)
test -n "$NEW_TOKEN"
test -n "$NEW_REFRESH"
OLD_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" \
  -H 'Content-Type: application/json' --data "{\"refreshToken\":\"$REFRESH\"}")
test "$OLD_STATUS" = "401"

echo "[ci] plugin auth code"
CODE_RESPONSE=$(json_post "$BASE/auth/extension-code" '{}' -H "Authorization: Bearer $NEW_TOKEN")
AUTH_CODE=$(echo "$CODE_RESPONSE" | json_get data.code)
test -n "$AUTH_CODE"
EXT_TOKEN=$(json_post "$BASE/auth/extension-token" "{\"code\":\"$AUTH_CODE\",\"clientInfo\":\"ci\"}" | json_get data.accessToken)
test -n "$EXT_TOKEN"
REUSE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/extension-token" \
  -H 'Content-Type: application/json' --data "{\"code\":\"$AUTH_CODE\"}")
test "$REUSE_STATUS" = "401"

echo "[ci] bookmark import"
json_post "$BASE/bookmark-folders" \
  "{\"browser\":\"CHROME\",\"folderId\":\"$FOLDER_ID\",\"folderPath\":\"CI/娴嬭瘯\"}" \
  -H "Authorization: Bearer $NEW_TOKEN" | json_code_zero
json_post "$BASE/bookmark-imports" \
  "{\"folder\":{\"browser\":\"CHROME\",\"folderId\":\"$FOLDER_ID\",\"folderPath\":\"CI/娴嬭瘯\"},\"items\":[{\"title\":\"CI\",\"url\":\"https://example.com/ci\"}]}" \
  -H "Authorization: Bearer $NEW_TOKEN" -H "Idempotency-Key: ci-$(date +%s)-$RANDOM" | json_code_zero

echo "[ci] logout and refresh rejection"
json_post "$BASE/auth/logout" "{\"refreshToken\":\"$NEW_REFRESH\"}" -H "Authorization: Bearer $NEW_TOKEN" | json_code_zero
LOGOUT_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" \
  -H 'Content-Type: application/json' --data "{\"refreshToken\":\"$NEW_REFRESH\"}")
test "$LOGOUT_STATUS" = "401"

echo "[ci] smoke passed"
