# 部署运维

## 数据库迁移

- 新库：执行 `deploy/sql/schema.sql`，再执行 `deploy/sql/seed.sql`。
- 已运行旧版 schema 的库：执行 `deploy/sql/migrations/2026-11-20-team-space.sql`（团队空间、`articles.team_id`、`ai_usage.team_id`）。
- 迁移文件按日期顺序执行；执行前先备份。

## HTTPS 入口

`deploy/docker-compose.https.yml` 叠加 Caddy：

```bash
# 本地：https://localhost / https://admin.localhost（内部 CA，浏览器会提示证书）
DOMAIN=localhost docker compose -f docker-compose.yml -f docker-compose.https.yml up -d

# 生产：域名 A/AAAA 指向服务器，开放 80/443
DOMAIN=shiye.example.com ACME_EMAIL=ops@example.com \
  docker compose -f docker-compose.yml -f docker-compose.https.yml up -d
```

Caddy 负责 TLS 和 80→443 跳转；`user-web` / `admin-web` 的 nginx 继续把 `/api/`、`/admin/api/` 反代到各自 API。生产不要直接暴露 15173/15174/18080/18081。

## SMTP

邮箱验证、邮件找回密码和团队邀请邮件通过 SMTP 发送。至少配置：

```text
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_FROM=no-reply@example.com
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
APP_PUBLIC_URL=https://shiye.example.com
```

未配置时 `MAIL_ENABLED=false`，相关接口仍返回成功，但只记录日志、不实际发信。生产建议使用独立发信账号并配置 SPF/DKIM。

## 健康检查

- user-api：`http://localhost:18080/actuator/health`
- admin-api：`http://localhost:18081/actuator/health`
- Prometheus 指标：
  - `http://localhost:18080/actuator/prometheus`
  - `http://localhost:18081/actuator/prometheus`

建议 Prometheus 每分钟抓取一次，并关注：

- `http_server_requests_seconds_*`：请求量、错误率、P95
- `jvm_memory_used_bytes` / `jvm_gc_*`
- `hikaricp_connections_*`：数据库连接池
- `redis_*`：Redis 客户端指标
- `http_server_requests_seconds_count{status="500"}`：5xx

`deploy/docker-compose.yml` 已包含 Prometheus（默认 `http://localhost:9090`）和 Alertmanager（默认 `http://localhost:9093`），规则在 `monitoring/alerts.yml`：

- API 5xx
- P95 > 2s
- Chat AI 错误率 > 10%
- 预算 Webhook 发送失败

Alertmanager 使用 `monitoring/alertmanager.yml.tmpl`，通过 `ALERTMANAGER_WEBHOOK_URL` 注入转发地址。生产环境把该变量替换为真实 Webhook（Slack/飞书/企业微信/自建接收器）；如需邮件，可在模板中追加 `email_configs`。

本地转发演练（2026-11-21）：

```powershell
$env:ALERTMANAGER_WEBHOOK_URL='http://webhook-sink:19090/shiye-alerts'
docker compose -f deploy/docker-compose.yml --profile monitoring-test up -d alertmanager webhook-sink
# 发送测试告警后，webhook-sink 收到 firing 事件并打印 alertname/severity
```

已验证 Alertmanager 加载配置、接收测试告警并通过 Webhook 转发成功。测试接收器只在 `monitoring-test` profile 中启动，生产不要启用。

## 备份

默认使用 `deploy/scripts/backup.ps1`（Windows）或 `backup.sh`（Linux/macOS）。

环境变量：

```text
DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
BACKUP_DIR（可选，默认 deploy/backups）
KEEP_DAYS（可选，默认 7）
```

Windows 示例：

```powershell
$env:DB_PASSWORD='...'
$env:REDIS_PASSWORD='...'
.\deploy\scripts\backup.ps1
```

Linux 示例：

```bash
DB_PASSWORD='...' REDIS_PASSWORD='...' ./deploy/scripts/backup.sh
```

备份内容：

- MySQL：`shiye-db-*.sql`（单事务、含 routines/triggers）
- Redis：`redis-*.rdb`（`redis-cli --rdb` 快照）

恢复建议：

1. 停止 user-api/admin-api，避免写入。
2. 恢复 MySQL：`mysql ... shiye < shiye-db-*.sql`。
3. 停止 Redis，将 RDB 放到 Redis 数据目录后再启动。
4. 启动服务并检查 `/actuator/health`。

## 恢复演练

同机演练建议恢复到独立的空库，不要直接覆盖生产库：

```powershell
$env:RESTORE_DB_NAME='shiye_restore_drill'
$env:RESTORE_DB_USER='root'
$env:RESTORE_DB_PASSWORD='...'
.\deploy\scripts\restore-verify.ps1 -BackupFile .\deploy\backups\shiye-db-*.sql
```

脚本会拒绝非空目标库，导入后检查表数量和 `users` 行数。Windows 下必须使用 `cmd` 重定向并带 `--default-character-set=utf8mb4`，否则中文注释可能被控制台编码破坏。

2026-11-21 本地演练结果：

- MySQL dump 约 54 KB（空业务库，38 张表）
- 在临时库/同库空数据场景导入成功，表数量 38，marker 行校验通过
- Redis RDB 生成成功（空库 172 bytes；生产有数据时会更大）
- 说明：本次是本机空库演练，生产环境还需要在真实数据量上再做一次，并验证 Redis RDB 恢复流程

> 备份目录不要提交到 Git；生产环境建议使用对象存储并定期做恢复演练。
