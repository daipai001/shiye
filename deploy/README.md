# 部署运维

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

> 备份目录不要提交到 Git；生产环境建议使用对象存储并定期做恢复演练。
