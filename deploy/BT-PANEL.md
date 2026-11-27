# 宝塔 + Docker 生产部署（myhyw.asia）

这台服务器已经运行 `offerdesk` 项目，并且使用宝塔 Linux 面板。拾页使用独立的 Docker Compose 项目名 `shiye`、独立容器名 `shiye-*`、独立网络和卷，不改动 `offerdesk`。

## 1. 端口规划

所有端口只绑定 `127.0.0.1`，公网只开放 80/443 和 SSH/宝塔端口：

| 服务 | 宿主机地址 | 说明 |
|---|---|---|
| user-web | `127.0.0.1:25173` | 宝塔反代 `shiye.myhyw.asia` |
| admin-web | `127.0.0.1:25174` | 宝塔反代 `admin.shiye.myhyw.asia` |
| user-api | `127.0.0.1:28080` | 仅本机/调试，web 容器内网访问 |
| admin-api | `127.0.0.1:28081` | 仅本机/调试 |
| MySQL | `127.0.0.1:23306` | 不对公网 |
| Redis | `127.0.0.1:26379` | 不对公网 |
| Prometheus/Alertmanager | `127.0.0.1:29090/29093` | 不对公网 |

如果这些端口仍与现有项目冲突，修改 `deploy/.env.production` 后重新 `docker compose up -d` 即可。

## 2. DNS

在域名服务商添加两条 A 记录，指向 `8.163.136.113`：

```text
shiye.myhyw.asia        A  8.163.136.113
admin.shiye.myhyw.asia  A  8.163.136.113
```

不要占用 `myhyw.asia` 根域名，避免与已有项目冲突。等待 DNS 生效后再申请证书。

## 3. 服务器预检

首次部署前先执行只读预检，把输出发回确认端口/内存/容器：

```bash
bash deploy/scripts/server-preflight.sh
```

重点确认：

- 25173/25174/28080/28081/23306/26379 没有被 `offerdesk` 或其他项目占用。
- 80/443 由宝塔 Nginx 占用（预期）。
- 剩余内存足够：拾页 MySQL + Redis + 两个 API + 两个 web 约 1.5～2GB；4GB 服务器不建议启用 HNSW。

## 4. 部署

```bash
cd /opt/shiye   # 代码目录
cp deploy/.env.production.example deploy/.env.production
vi deploy/.env.production

# 必改：DB_PASSWORD、USER_JWT_SECRET、ADMIN_JWT_SECRET、
# DEEPSEEK_API_KEY、EMBEDDING_API_KEY、RERANK_API_KEY、SMTP
bash deploy/scripts/deploy-production.sh
```

镜像默认使用 `ghcr.io/daipai001/shiye-*:latest`（GitHub Actions CD 推送）。如果 GHCR 包是私有的：

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin
```

没有 GHCR 访问权限时，把 `BUILD_IMAGES` 改为 `true` 并在服务器准备 Maven/Node 产物；不推荐在 2C4G 服务器上现场构建前端。

脚本顺序：启动 MySQL/Redis → 备份（已有库）→ schema/seed → 迁移 → 构建/启动 → 健康检查。4GB 服务器默认 `INCLUDE_MONITORING=false`，不启动 Prometheus/Alertmanager；需要时再设 true。

首次启动后立即登录 `https://admin.shiye.myhyw.asia` 修改管理员初始密码。

## 5. 宝塔反向代理

在宝塔“网站”中新建两个站点（不要删除/修改 offerdesk 站点）：

### 4.1 用户端

- 域名：`shiye.myhyw.asia`
- 反向代理目标：`http://127.0.0.1:25173`
- 开启 SSL（Let's Encrypt），强制 HTTPS
- 配置：

```nginx
location / {
    proxy_pass http://127.0.0.1:25173;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_read_timeout 300s;
    client_max_body_size 20m;
}
```

### 4.2 管理端

- 域名：`admin.shiye.myhyw.asia`
- 反向代理目标：`http://127.0.0.1:25174`
- 同样开启 SSL、强制 HTTPS，配置同上（只改端口）。

`proxy_buffering off` 必须保留，否则 SSE 流式问答会被缓冲。

## 6. 防火墙/安全组

只放行：

- `80/tcp`、`443/tcp`
- SSH/宝塔面板端口（按现有配置）
- 不要放行 `25173/25174/28080/28081/23306/26379`

在宝塔“安全”和阿里云安全组同时限制。

## 7. 验证

```bash
curl -fsS https://shiye.myhyw.asia/api/v1/health
curl -fsS https://admin.shiye.myhyw.asia/actuator/health
curl -fsS https://shiye.myhyw.asia/
```

浏览器：

- 用户端登录/注册 → 上传资料 → 问答 → 引用 → 用量
- 管理端登录 → 团队审计 → 审计日志 → 团队与配额
- 流式回答应逐字输出，不能一次性返回

## 8. 备份

`deploy-production.sh` 默认在部署前备份到 `BACKUP_DIR`（示例 `/opt/shiye/backups`）。也可以单独执行：

```bash
DB_HOST=127.0.0.1 DB_PORT=23306 DB_PASSWORD='***' \
REDIS_HOST=127.0.0.1 REDIS_PORT=26379 \
BACKUP_DIR=/opt/shiye/backups \
bash deploy/scripts/backup.sh
```

建议加宝塔计划任务每天执行，并把备份同步到对象存储。

## 9. 与 offerdesk 共存的注意事项

- 不要执行 `docker system prune -a`，会删除其他项目镜像。
- 不要修改 `offerdesk` 的网络/卷/端口。
- 拾页的 Compose 项目名固定 `shiye`，容器名前缀 `shiye-`。
- 宝塔只新增两个站点，不改已有站点。
- 如果 80/443 已被宝塔占用，不要启动 `docker-compose.https.yml`（Caddy 会抢端口）。
