#!/usr/bin/env bash
# 数据库迁移演练：从源库复制到一个 *_drill 库，模拟旧版本后执行全部新迁移，
# 再通过恢复备份验证回滚路径。不会修改 SOURCE_DB。
#
# 用法：
#   CONFIRM=yes SOURCE_DB=shiye DRILL_DB=shiye_migration_drill \
#   MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD=... \
#   bash deploy/scripts/migration-drill.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATIONS_DIR="$ROOT_DIR/deploy/sql/migrations"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
SOURCE_DB="${SOURCE_DB:-shiye}"
DRILL_DB="${DRILL_DB:-shiye_migration_drill}"
BACKUP_FILE="${BACKUP_FILE:-/tmp/${DRILL_DB}-$(date +%Y%m%d%H%M%S).sql}"

if [ "${CONFIRM:-}" != "yes" ]; then
  echo "Refusing to run without CONFIRM=yes" >&2
  exit 1
fi
if [ "$SOURCE_DB" = "$DRILL_DB" ]; then
  echo "SOURCE_DB and DRILL_DB must differ" >&2
  exit 1
fi
case "$DRILL_DB" in
  *_drill) ;;
  *) echo "DRILL_DB must end with _drill for safety" >&2; exit 1 ;;
esac

export MYSQL_PWD="$MYSQL_PASSWORD"
mysql_cli() {
  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
    --default-character-set=utf8mb4 "$@"
}
mysqldump_cli() {
  mysqldump -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
    --single-transaction --routines --triggers --no-tablespaces \
    --default-character-set=utf8mb4 "$@"
}

echo "[drill] dump source $SOURCE_DB -> $BACKUP_FILE"
mysqldump_cli "$SOURCE_DB" > "$BACKUP_FILE"

echo "[drill] recreate $DRILL_DB"
mysql_cli -e "DROP DATABASE IF EXISTS \`$DRILL_DB\`; CREATE DATABASE \`$DRILL_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql_cli "$DRILL_DB" < "$BACKUP_FILE"

echo "[drill] simulate pre-upgrade schema"
mysql_cli "$DRILL_DB" -e "
  DROP TABLE IF EXISTS usage_reservations;
  DROP TABLE IF EXISTS team_audit_logs;
  DROP TABLE IF EXISTS global_usage_monthly;
"
if [ "$(mysql_cli -N "$DRILL_DB" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DRILL_DB' AND table_name='rag_eval_runs' AND column_name='last_progress_at'")" != "0" ]; then
  mysql_cli "$DRILL_DB" -e "ALTER TABLE rag_eval_runs DROP COLUMN last_progress_at;"
fi

echo "[drill] apply migrations"
for file in \
  2026-11-26-global-usage.sql \
  2026-11-26-invite-cleanup.sql \
  2026-11-26-eval-progress.sql \
  2026-11-27-usage-reservations.sql \
  2026-11-27-team-audit.sql
do
  echo "[drill]   $file"
  mysql_cli "$DRILL_DB" < "$MIGRATIONS_DIR/$file"
done

echo "[drill] verify schema and backfill"
TABLES=$(mysql_cli -N "$DRILL_DB" -e "
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema='$DRILL_DB'
    AND table_name IN ('global_usage_monthly','usage_reservations','team_audit_logs');
")
test "$TABLES" = "3"
COLUMN=$(mysql_cli -N "$DRILL_DB" -e "
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema='$DRILL_DB' AND table_name='rag_eval_runs'
    AND column_name='last_progress_at';
")
test "$COLUMN" = "1"
GLOBAL_ROW=$(mysql_cli -N "$DRILL_DB" -e "
  SELECT COUNT(*) FROM global_usage_monthly
  WHERE month = DATE_FORMAT(UTC_TIMESTAMP(), '%Y-%m');
")
echo "[drill] current month global row: $GLOBAL_ROW (0 only when source had no current-month usage)"

echo "[drill] verify rollback path by restoring backup"
mysql_cli -e "DROP DATABASE IF EXISTS \`$DRILL_DB\`; CREATE DATABASE \`$DRILL_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql_cli "$DRILL_DB" < "$BACKUP_FILE"
RESTORED=$(mysql_cli -N "$DRILL_DB" -e "
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema='$DRILL_DB' AND table_type='BASE TABLE';
")
test "$RESTORED" -ge 30

echo "[drill] passed: restored tables=$RESTORED backup=$BACKUP_FILE"
echo "[drill] production rollback: stop new API -> restore this backup -> start previous image"
