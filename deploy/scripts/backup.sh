#!/usr/bin/env sh
set -eu

BACKUP_DIR="${BACKUP_DIR:-$(dirname "$0")/../backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-shiye}"
DB_USERNAME="${DB_USERNAME:-root}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
DB_FILE="$BACKUP_DIR/shiye-db-$STAMP.sql"
REDIS_FILE="$BACKUP_DIR/redis-$STAMP.rdb"

MYSQL_PWD="${DB_PASSWORD:-}" mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
  --single-transaction --routines --triggers --no-tablespaces \
  --default-character-set=utf8mb4 \
  --result-file="$DB_FILE" "$DB_NAME"

if [ -n "${REDIS_PASSWORD:-}" ]; then
  REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --rdb "$REDIS_FILE"
else
  redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --rdb "$REDIS_FILE"
fi

find "$BACKUP_DIR" -type f -mtime +"$KEEP_DAYS" -delete

echo "backup completed:"
echo "  $DB_FILE"
echo "  $REDIS_FILE"
