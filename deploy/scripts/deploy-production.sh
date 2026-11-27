#!/usr/bin/env bash
# 拾页生产部署：备份 -> schema/seed -> 迁移 -> 构建/启动 -> 健康检查。
# 需要 Docker Compose v2，且在仓库根目录或任意目录执行均可。
#
# 用法：
#   cp deploy/.env.production.example deploy/.env.production
#   # 修改密钥、域名、SMTP、AI key
#   bash deploy/scripts/deploy-production.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/.env.production}"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.yml"

if [ ! -f "$ENV_FILE" ]; then
  echo "missing $ENV_FILE; copy deploy/.env.production.example and edit it" >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (docker compose)" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

COMPOSE=(docker compose --project-directory "$ROOT_DIR/deploy" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
MYSQL_CONTAINER=shiye-mysql
DB_NAME=shiye
DB_USERNAME=root

split_host_port() {
  local value="$1"
  if [[ "$value" == *:* ]]; then
    printf '%s %s\n' "${value%:*}" "${value##*:}"
  else
    printf '127.0.0.1 %s\n' "$value"
  fi
}
read -r DB_HOST DB_PORT <<< "$(split_host_port "${MYSQL_PORT:-127.0.0.1:23306}")"
read -r REDIS_HOST REDIS_PORT <<< "$(split_host_port "${REDIS_PORT:-127.0.0.1:26379}")"

mysql_exec() {
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" \
    mysql -u"$DB_USERNAME" "$@"
}

echo "[deploy] start mysql/redis"
"${COMPOSE[@]}" up -d mysql redis

echo "[deploy] wait for mysql"
for i in $(seq 1 60); do
  if docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" \
    mysqladmin ping -u"$DB_USERNAME" --silent >/dev/null 2>&1; then
    break
  fi
  if [ "$i" = "60" ]; then
    echo "mysql did not become healthy" >&2
    exit 1
  fi
  sleep 2
done

TABLES=$(mysql_exec -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'" 2>/dev/null || echo 0)
if [ "${BACKUP_BEFORE_DEPLOY:-true}" = "true" ] && [ "${TABLES:-0}" -gt 0 ]; then
  echo "[deploy] backup database/redis -> ${BACKUP_DIR:-deploy/backups}"
  DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_NAME="$DB_NAME" DB_USERNAME="$DB_USERNAME" \
  DB_PASSWORD="$DB_PASSWORD" REDIS_HOST="$REDIS_HOST" REDIS_PORT="$REDIS_PORT" \
  BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/deploy/backups}" \
  bash "$ROOT_DIR/deploy/scripts/backup.sh"
else
  echo "[deploy] skip backup (empty database or BACKUP_BEFORE_DEPLOY=false)"
fi

echo "[deploy] apply schema/seed and migrations"
mysql_exec "$DB_NAME" < "$ROOT_DIR/deploy/sql/schema.sql"
mysql_exec "$DB_NAME" < "$ROOT_DIR/deploy/sql/seed.sql"

migration_done() {
  local version="$1"
  local count
  count=$(mysql_exec -N "$DB_NAME" -e \
    "SELECT COUNT(*) FROM schema_migrations WHERE version='$version'" 2>/dev/null || echo 0)
  [ "${count:-0}" = "1" ]
}

mark_migration() {
  local version="$1"
  local file="$2"
  local checksum
  checksum=$(sha256sum "$file" | awk '{print $1}')
  mysql_exec "$DB_NAME" -e \
    "INSERT IGNORE INTO schema_migrations (version, description, checksum, execution_ms)
     VALUES ('$version', '$(basename "$file")', '$checksum', 0)"
}

run_migration() {
  local version="$1"
  local file="$2"
  if migration_done "$version"; then
    echo "[deploy]   $version already applied"
    return
  fi
  echo "[deploy]   $version"
  mysql_exec "$DB_NAME" < "$file"
  mark_migration "$version" "$file"
}

# v0.32.0+ 的团队空间迁移不是幂等的：只在缺少 team_id 列时执行。
TEAM_SPACE_FILE="$ROOT_DIR/deploy/sql/migrations/2026-11-20-team-space.sql"
TEAM_COLUMNS=$(mysql_exec -N "$DB_NAME" -e "
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema='$DB_NAME'
    AND ((table_name='articles' AND column_name='team_id')
      OR (table_name='ai_usage' AND column_name='team_id'))
")
if [ "$TEAM_COLUMNS" = "2" ]; then
  mark_migration "2026-11-20-team-space" "$TEAM_SPACE_FILE"
else
  run_migration "2026-11-20-team-space" "$TEAM_SPACE_FILE"
fi

run_migration "2026-11-26-global-usage" \
  "$ROOT_DIR/deploy/sql/migrations/2026-11-26-global-usage.sql"
run_migration "2026-11-26-invite-cleanup" \
  "$ROOT_DIR/deploy/sql/migrations/2026-11-26-invite-cleanup.sql"

# eval-progress 的 ADD COLUMN 不是幂等的，先检查列是否存在。
EVAL_PROGRESS_FILE="$ROOT_DIR/deploy/sql/migrations/2026-11-26-eval-progress.sql"
EVAL_COLUMN=$(mysql_exec -N "$DB_NAME" -e "
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema='$DB_NAME' AND table_name='rag_eval_runs'
    AND column_name='last_progress_at'
")
if [ "$EVAL_COLUMN" = "1" ]; then
  mark_migration "2026-11-26-eval-progress" "$EVAL_PROGRESS_FILE"
else
  run_migration "2026-11-26-eval-progress" "$EVAL_PROGRESS_FILE"
fi

run_migration "2026-11-27-usage-reservations" \
  "$ROOT_DIR/deploy/sql/migrations/2026-11-27-usage-reservations.sql"
run_migration "2026-11-27-team-audit" \
  "$ROOT_DIR/deploy/sql/migrations/2026-11-27-team-audit.sql"

echo "[deploy] build/start services"
SERVICES=(mysql redis user-api admin-api user-web admin-web)
if [ "${INCLUDE_MONITORING:-false}" = "true" ]; then
  SERVICES+=(prometheus alertmanager)
fi
if [ "${BUILD_IMAGES:-true}" = "true" ]; then
  "${COMPOSE[@]}" up -d --build "${SERVICES[@]}"
else
  echo "[deploy] pull prebuilt images"
  "${COMPOSE[@]}" pull "${SERVICES[@]}"
  "${COMPOSE[@]}" up -d "${SERVICES[@]}"
fi

wait_http() {
  local url="$1"
  local name="$2"
  for i in $(seq 1 60); do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
      echo "[deploy] $name OK: $url"
      return 0
    fi
    sleep 2
  done
  echo "[deploy] $name health check failed: $url" >&2
  return 1
}

wait_http "http://$DB_HOST:${USER_API_PORT##*:}/api/v1/health" "user-api"
wait_http "http://$DB_HOST:${ADMIN_API_PORT##*:}/actuator/health" "admin-api"
wait_http "http://$DB_HOST:${USER_WEB_PORT##*:}/" "user-web"
wait_http "http://$DB_HOST:${ADMIN_WEB_PORT##*:}/" "admin-web"

echo "[deploy] done"
echo "  user-web : http://${USER_WEB_PORT##*:} (宝塔反代 https://${DOMAIN:-shiye.myhyw.asia})"
echo "  admin-web: http://${ADMIN_WEB_PORT##*:} (宝塔反代 https://admin.${DOMAIN:-shiye.myhyw.asia})"
echo "  下一步：在宝塔创建两个站点并反代到上面的 127.0.0.1 端口，申请 SSL。"
