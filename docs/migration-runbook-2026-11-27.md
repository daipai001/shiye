# 2026-11-27 生产迁移与回滚 Runbook

适用版本：`v0.43.x`（从 `v0.42.x` 或更早升级到当前版本）。

## 1. 迁移范围

按顺序执行：

```text
2026-11-26-global-usage.sql        # global_usage_monthly + 历史月份回填
2026-11-26-invite-cleanup.sql      # 清理已移除成员留下的未使用邀请码
2026-11-26-eval-progress.sql       # rag_eval_runs.last_progress_at
2026-11-27-usage-reservations.sql  # usage_reservations 预留流水
2026-11-27-team-audit.sql          # team_audit_logs 团队审计
```

这些迁移只新增表/列；`invite-cleanup` 会删除无效邀请码，执行前必须备份。

## 2. 升级前检查

- [ ] 已通知用户维护窗口，停止新写入。
- [ ] 已备份：`mysqldump --single-transaction --routines --triggers --no-tablespaces shiye > shiye-YYYYmmdd.sql`。
- [ ] 确认 `USER_JWT_SECRET` 与 `ADMIN_JWT_SECRET` 不同且至少 32 字节。
- [ ] 确认 `TRUSTED_PROXIES` 与实际反向代理网段一致。
- [ ] 确认 HNSW 是否启用；如启用，准备 `VECTOR_BACKFILL=true` 的一次性窗口。
- [ ] 记录当前镜像 tag，便于回滚。

## 3. 本地/预发演练

在数据库副本上执行，不要直接对生产库演练：

```bash
CONFIRM=yes \
SOURCE_DB=shiye \
DRILL_DB=shiye_migration_drill \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 \
MYSQL_USER=root MYSQL_PASSWORD='***' \
bash deploy/scripts/migration-drill.sh
```

脚本会：

1. dump 源库到 `/tmp/shiye_migration_drill-*.sql`；
2. 恢复到一个 `*_drill` 库；
3. 删除新表/列，模拟旧版本；
4. 依次执行 5 个迁移；
5. 校验表、列和全站月度回填；
6. 删除 drill 库并用备份恢复，验证回滚路径。

预期输出：`passed: restored tables=N ...`。

## 4. 生产执行

```bash
# 1) 备份
mysqldump -h "$DB_HOST" -u "$DB_USER" -p \
  --single-transaction --routines --triggers --no-tablespaces \
  --default-character-set=utf8mb4 shiye > "/backup/shiye-$(date +%Y%m%d%H%M).sql"

# 2) 按顺序执行
for f in \
  2026-11-26-global-usage.sql \
  2026-11-26-invite-cleanup.sql \
  2026-11-26-eval-progress.sql \
  2026-11-27-usage-reservations.sql \
  2026-11-27-team-audit.sql
do
  mysql -h "$DB_HOST" -u "$DB_USER" -p --default-character-set=utf8mb4 shiye \
    < "deploy/sql/migrations/$f"
done
```

Docker 部署时：

```bash
docker compose exec -T mysql mysql -uroot -p"$DB_PASSWORD" shiye \
  < deploy/sql/migrations/2026-11-27-usage-reservations.sql
```

## 5. 迁移后校验

```sql
SHOW TABLES LIKE 'usage_reservations';
SHOW TABLES LIKE 'team_audit_logs';
SHOW COLUMNS FROM rag_eval_runs LIKE 'last_progress_at';
SELECT month, tokens, cost FROM global_usage_monthly
ORDER BY month DESC LIMIT 3;
SELECT COUNT(*) FROM usage_reservations WHERE status='ACTIVE';
```

应用侧：

- `/actuator/health` 为 `UP`。
- 登录、资料列表、问答、团队创建/邀请/移除、管理端团队审计正常。
- 新问答结束后 `usage_reservations` 出现 `RELEASED` 记录。
- 团队操作后 `team_audit_logs` 出现对应 action。

## 6. 回滚

### 6.1 数据回滚

本次迁移是加法式；如果只是应用异常，可先保留新表，回滚应用镜像。若要彻底回滚：

```bash
# 停止新版本 API，避免继续写新表
docker compose stop user-api admin-api

# 用升级前备份恢复
mysql -h "$DB_HOST" -u "$DB_USER" -p \
  --default-character-set=utf8mb4 shiye < "/backup/shiye-YYYYmmddHHmm.sql"

# 启动上一版本镜像
IMAGE_TAG=<previous-tag> docker compose up -d user-api admin-api
```

### 6.2 注意

- `global_usage_monthly` 回滚后会停止更新；再次升级时必须重新回填并对账，不能只建表。
- `usage_reservations`、`team_audit_logs` 回滚时随备份恢复；不要只 `DROP TABLE` 而保留新代码。
- `invite-cleanup` 删除的邀请码不可恢复，必须依赖备份。
- 回滚后检查 `usage_reservations` 是否有 ACTIVE 残留，避免新版本再次启动后重复扣减。

## 7. 演练记录

本机已使用 `scripts/migration-drill.sh` 对 `shiye` 副本执行并通过；结果写入本文件或发布记录。
