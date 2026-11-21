# 测试与压测

## Playwright E2E

```bash
cd frontend/user-web
npm install
npx playwright install chromium   # 首次运行；本机也可用系统 Chrome
npm run test:e2e
```

默认启动/复用 `http://localhost:15173` 的 Vite dev server，覆盖：

- 登录页桌面布局
- 登录/注册切换
- 表单校验
- 移动端单列布局

CI 的 `playwright-e2e` job 会自动安装 Chromium 并执行。

## 基础压测

不依赖 k6，直接用 Node 20+：

```powershell
$env:NODE_TLS_REJECT_UNAUTHORIZED='0'   # 本地 Caddy 自签证书时
$env:BASE_URL='https://localhost/api/v1'
$env:EMAIL='load-user@shiye.local'
$env:PASSWORD='LoadTest123'
$env:VUS='20'
$env:DURATION='20s'
node scripts/load-test.mjs
```

也可以继续用 k6：

```bash
k6 run -e BASE_URL=http://localhost:18080/api/v1 -e VUS=20 -e DURATION=30s scripts/load-test.js
```

2026-11-21 本地结果见 `docs/load-test-2026-11-21.md`（2,000 篇/6,000 切片，246 RPS，P95 159ms）。

## AI/RAG 流式问答压测（mock 模型）

不消耗外部 API：`scripts/mock-ai.mjs` 提供 OpenAI 兼容的 chat/embedding/rerank，`deploy/docker-compose.loadtest.yml` 把它接入 user-api。

```powershell
$env:DOMAIN='localhost'
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.https.yml `
  -f deploy/docker-compose.loadtest.yml up -d --build

$env:NODE_TLS_REJECT_UNAUTHORIZED='0'
$env:BASE_URL='https://localhost/api/v1'
$env:EMAIL='chatload@shiye.local'
$env:PASSWORD='ChatLoad123'
$env:VUS='10'
$env:DURATION='15s'
node scripts/load-test-chat.mjs
```

2026-11-21 结果：10 VUs / 15s，229 请求，0 失败，15.27 RPS，P50 606ms，P95 1111ms；服务端记录延迟平均 118ms。详见 `docs/load-test-2026-11-21.md`。

> mock 只验证链路和资源使用，不代表真实模型质量/延迟；生产不要启用该 overlay。

## RAG 评测回归

```powershell
.\scripts\seed-eval-cases.ps1
```

固定语料在 `scripts/eval-fixtures.json`，会创建 30 条用例并跑评测。2026-11-21 结果见 `docs/rag-eval-2026-11-21.md`。

## 本地 SMTP 收信

没有 SMTP 服务时，可以用内置 sink 验证邮件链路：

```bash
node scripts/dev-smtp-sink.mjs 1025
```

然后在 user-api 启动时设置：

```text
MAIL_ENABLED=true
MAIL_HOST=127.0.0.1
MAIL_PORT=1025
MAIL_FROM=no-reply@shiye.local
APP_PUBLIC_URL=http://localhost:15173
```

收到的邮件以 JSON 行写入 `mail-sink.log`（或第二个参数指定的文件），可用于验证邮箱验证、找回密码和团队邀请。

## 备份恢复演练

```powershell
$env:RESTORE_DB_NAME='shiye_restore_drill'
$env:RESTORE_DB_USER='root'
$env:RESTORE_DB_PASSWORD='...'
.\deploy\scripts\restore-verify.ps1 -BackupFile .\deploy\backups\shiye-db-*.sql
```

## 插件打包

```powershell
.\scripts\package-extension.ps1
```

```bash
./scripts/package-extension.sh
```

产物在 `dist/`，上架说明见 `docs/extension-release.md`。

## 集成 smoke

CI 的 `integration-smoke` job 已覆盖注册、refresh 轮换、扩展授权码、书签导入和登出。

`scripts/ci-team-smoke.sh` 额外覆盖：

- 创建团队
- 邀请码加入
- 共享资料
- 团队标签/文件夹
- 团队资料筛选
- 团队配额 42905
- 解散团队后 404
