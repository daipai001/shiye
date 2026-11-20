# 拾页（ShiYe）

[![CI](https://github.com/daipai001/shiye/actions/workflows/ci.yml/badge.svg)](https://github.com/daipai001/shiye/actions/workflows/ci.yml)
[![CD](https://github.com/daipai001/shiye/actions/workflows/cd.yml/badge.svg)](https://github.com/daipai001/shiye/actions/workflows/cd.yml)

> 收藏不是终点，能问才是。

把浏览器书签文件夹变成可对话的个人知识库：任意网页点悬浮球，基于收藏内容提问，回答带原文引用，AI 用量和成本可查。

## 产品形态

- **前台 Web**：资料库、文章详情、问答、设置、用量
- **前台插件**：任意网页悬浮球、书签文件夹导入、收藏当前页
- **后台管理端**：独立 `admin-web`，用量与成本、导入/索引任务、模型与系统设置、RAG 评测、审计日志
- **前台 API**：`shiye-user-api`，Web + 插件共用
- **后台 API**：`shiye-admin-api`，管理端专用

## 技术栈

| 层 | 技术 |
|---|---|
| 前台 Web | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus |
| 前台插件 | Chrome MV3 + Vue 3 + TypeScript + Vite + Shadow DOM |
| 后台 Web | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus |
| 前台 API | Spring Boot 3 + Java 21 + MyBatis + Spring AI |
| 后台 API | Spring Boot 3 + Java 21 + MyBatis |
| 数据库 | MySQL 8 |
| 向量/缓存 | Redis Stack（RediSearch） |
| 流式 | SSE（`SseEmitter`） |
| 部署 | Docker Compose + Nginx |

## 文档

- [需求文档](docs/requirements.md)
- [数据库设计](docs/schema.md)
- [接口文档](docs/api.md)
- [可点击原型](docs/prototype.html)（最新，演示用这个）
- [静态草图](docs/wireframes.html)（早期归档）

## 目录

```
shiye/
├─ frontend/
│  ├─ user-web/          # 前台 Web（Vue 3）
│  ├─ user-extension/    # 前台插件（悬浮球 + 书签导入）
│  └─ admin-web/         # 后台管理端（独立 Vue 3）
├─ backend/
│  ├─ shiye-common/      # 共享基础库
│  ├─ shiye-user-api/    # 前台 API（Web + 插件共用）
│  └─ shiye-admin-api/   # 后台 API（管理端专用）
├─ docs/                 # 需求、接口、数据库设计
├─ deploy/               # Docker Compose、Nginx、SQL
└─ README.md
```

## 本地启动

### 1. 数据库

```sql
CREATE DATABASE IF NOT EXISTS shiye
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE shiye;
```

```bash
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/seed.sql
```

已有数据库升级时，按顺序执行未执行的迁移：

```bash
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/migrations/V002__chunk_fulltext.sql
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/migrations/V003__plugin_default_scope.sql
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/migrations/V004__eval_answer_details.sql
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/migrations/V005__plugin_general_mode.sql
```

- 管理员：`admin@shiye.local`
- 初始密码：见项目负责人的私密记录；首次登录后修改
- 迁移：`deploy/sql/migrations/`
- Docker 方式：`mvn -f backend/pom.xml package -DskipTests` 后 `docker compose -f deploy/docker-compose.yml up -d --build`

### 2. 后端

```bash
cd backend
mvn -DskipTests package
java -jar shiye-user-api/target/shiye-user-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar shiye-admin-api/target/shiye-admin-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

- 前台 API：`http://localhost:18080/api/v1`
- 后台 API：`http://localhost:18081/admin/api/v1`
- 健康检查：
  - `http://localhost:18080/api/v1/health`
  - `http://localhost:18081/admin/api/v1/health`
- 本地配置：`backend/config/application-local.yml`（已被 `.gitignore` 忽略），包含 MySQL、Redis 和两个 JWT 密钥；不要提交真实密钥。
- 本地 MySQL/Redis 使用 3306/6379；Docker Compose 映射到 13306/16379，避免冲突。

### 3. 前端

```bash
cd frontend/user-web
npm install
npm run dev        # http://localhost:15173

cd ../admin-web
npm install
npm run dev        # http://localhost:15174
```

### 4. 浏览器插件

1. Chrome/Edge 打开 `chrome://extensions`
2. 打开“开发者模式”
3. “加载已解压的扩展程序” → 选择 `frontend/user-extension`
4. 任意网页右下角会出现“拾”悬浮球；插件设置页可读取书签数量
5. 在网页端「插件」页生成授权码，粘贴到插件设置页连接
6. 连接后悬浮球支持资料库问答（流式 + 引用）、当前页问答（临时上下文，不落库）和收藏当前页
7. 书签导入后可开启「新增/修改/删除时自动同步」；移动进所选文件夹的书签会补导入

### 5. Docker Compose

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env：DB_PASSWORD、JWT、DeepSeek / Embedding key
docker compose -f deploy/docker-compose.yml up -d --build
```

- 用户端：`http://localhost:15173`
- 管理端：`http://localhost:15174`
- 用户 API：`http://localhost:18080`
- 管理 API：`http://localhost:18081`
- MySQL：`13306`，Redis Stack：`16379`

### 6. CI / CD

- `.github/workflows/ci.yml`：push/PR 时跑后端单测、两个前端构建、插件语法检查，并启动 MySQL/Redis 跑真实 API smoke。
- `.github/workflows/cd.yml`：推送 `v*` tag 或手动触发，构建并推送四个镜像到 GHCR。
- 可选 SSH 部署：配置仓库变量 `ENABLE_DEPLOY=true`、`IMAGE_PREFIX=ghcr.io/<owner>`，以及 secrets `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY` / `DEPLOY_PATH`。

## 当前状态

需求、数据库、接口、原型已定稿；Maven 多模块、两个 API、两个 Web、插件悬浮球骨架均已完成并可构建。

认证模块已打通：

- 用户注册、登录、刷新、登出；资料/密码修改；插件偏好
- 注册开关、邀请码和默认新用户配额可在管理端在线配置
- 管理员独立登录、刷新、登出
- 插件一次性授权码 → 用户 token
- 前端登录页接真实接口，access token 过期自动刷新
- 注册/登录 IP 限流（默认 10 次/分钟/IP），返回 `X-RateLimit-*` 响应头
- 用户端与管理端令牌按 audience + 独立密钥隔离

书签导入 M2a 已打通：

- `bookmark_folders` / `bookmark_imports` / `bookmark_items`
- 文件夹创建/列表/设为唯一主文件夹
- 导入任务创建、分批追加、列表、详情、条目分页
- 暂停/恢复/取消/重试失败项
- URL 规范化去重（锚点、`utm_*` 等追踪参数、尾斜杠）
- `Idempotency-Key` 幂等、Redis 创建限流（5 次/分钟/用户）
- 插件设置页：授权码连接、书签树、包含子文件夹、分批导入、最近任务与失败原因
- 插件 v1.0.0：专用图标、可配置 HTTPS 后端、按需 host 权限、popup 连接状态和网页端入口、打包脚本与上架说明（`docs/extension-release.md`）
- 用户端「插件」页生成授权码；资料库页显示最近导入任务

书签导入 M2b（抓取/解析/切片/向量化）已打通：

- 定时 worker 消费 `PENDING` 条目：`FETCHING → 解析 → 切片 → embedding → READY / FAILED / SKIPPED`
- Jsoup 正文提取、标题/摘要/语言/字数、段落/句子边界切片（500~800 字，约 100 字重叠）
- `charStart/charEnd` 保存到切片，供引用跳转
- SSRF 校验（协议/内网 IP/DNS）、超时、重定向上限、页面大小和正文长度限制
- 跨导入按 `(user_id, url_hash)` 去重，已存在 `READY` 资料时条目标记 `SKIPPED / DUPLICATE_URL`
- 向量写入 Redis：`kb:chunk:{chunkId}` + `kb:user:{userId}:chunks`
- 资料 API：列表/统计/详情/切片/删除/重新索引；资料库页面支持搜索、状态/标签/文件夹筛选、详情抽屉和切片查看
- 标签手动编辑：单篇加/删标签；标签管理支持重命名、改色、删除
- 问答取消、回答反馈、用户资料/密码、插件偏好、个人用量 CSV 导出
- 设置页已接入账户资料、密码修改和插件默认模式/范围（指定文件夹或标签）/站点黑名单；服务端偏好会应用到悬浮球

RAG 问答闭环已打通：

- `POST /api/v1/chat/stream`：SSE 事件 `meta / citation / delta / usage / done / error`
- DeepSeek 流式回答，复用本地 OfferDesk 配置（`shiye.ai.chat`，已 gitignore）
- 问答范围 ALL / ARTICLE / FOLDER / TAG / GENERAL；资料库问答页支持全部、单篇、文件夹、标签和通用问答，检索结果按 `user_id` 过滤，单篇最多 3 个切片
- 多轮追问会结合最近用户问题做轻量检索改写；通用问答不检索资料库、不返回引用
- 混合检索：BGE-M3 向量召回 + MySQL ngram FULLTEXT 关键词召回 + RRF 融合（`migrations/V002__chunk_fulltext.sql`）
- BGE-reranker 二次重排：对 RRF 候选池（默认最多 20 条）调用 `BAAI/bge-reranker-v2-m3`，失败或未配置 key 时自动回退 RRF；引用分数为 rerank relevance score
- 回答引用与 `citations` 落库，包含标题、片段、分数、`charStart/charEnd`
- 会话/消息/引用持久化；问答页支持流式输出、引用卡片、会话列表
- AI 用量记账：`ai_usage` + `user_usage_monthly`，记录 token、单价快照、成本、耗时；每日定时聚合到 `ai_usage_daily`（默认回补 7 天）
- 用户月度配额和全站月度预算检查；问答页显示 token/成本进度
- `/usage/quota`、`/usage/records`、`/usage/summary` 已接入「用量」页面；趋势按 UTC 日期聚合，缺失日期补 0
- 资料上传：`POST /articles/upload` 支持 PDF / Markdown / TXT（≤20MB），保存到 `FILE_UPLOAD_DIR`，提取正文后进入切片、BGE-M3 向量化和 AI 摘要/标签流程；相同文件按 SHA-256 去重
- 扫描版 PDF：可选 Tesseract OCR（`OCR_ENABLED=true`，需要安装 `tesseract` 和 `chi_sim`/`eng` 语言包），默认关闭
- `POST /articles/{id}/reindex`：换 embedding 模型后重新抓取/切片/向量化

> **Embedding 默认是联调模式**：新环境未配置 key 时 `shiye.ai.embedding.provider=dev` 会生成确定性向量（模型名 `DEV-HASH-1024`），只用于跑通状态机，**不是 BGE-M3 语义向量**。
> 配置真实 BGE-M3（OpenAI 兼容 `/embeddings`）：
>
> ```powershell
> $env:EMBEDDING_PROVIDER="http"
> $env:EMBEDDING_BASE_URL="https://api.siliconflow.cn/v1"
> $env:EMBEDDING_API_KEY="你的 key"
> $env:EMBEDDING_MODEL="BAAI/bge-m3"
> $env:EMBEDDING_DIMENSION="1024"
> ```
>
> 本机已切换到 BGE-M3 并验证：切片 `embedding_model=BAAI/bge-m3`、维度 1024，中文查询能命中相关英文切片（相似度约 0.60/0.49/0.46）。key 只存在于本地忽略配置，不提交仓库。
>
> 切换模型后旧切片需要重建索引；`POST /articles/{id}/reindex` 已接入。本机 Redis 8 没有 RediSearch，当前用 Hash 存向量，检索阶段先走应用层余弦；部署 Redis Stack 后可替换为 HNSW 索引。
>
> 可选开启 BGE-reranker（配置 `RERANK_*`，本地配置已开启）：
>
> ```powershell
> $env:RERANK_ENABLED="true"
> $env:RERANK_BASE_URL="https://api.siliconflow.cn/v1"
> $env:RERANK_API_KEY="你的 key"
> $env:RERANK_MODEL="BAAI/bge-reranker-v2-m3"
> $env:RETRIEVAL_RERANK_CANDIDATES="20"
> ```
>
> rerank 用量写入 `ai_usage.scene=RERANK`（当前成本记 0，token 计入月度用量）；接口失败只记 WARN 并回退 RRF，不影响问答。

资料整理（AI enrichment）已接入：

- 资料进入 `READY` 后异步调用 DeepSeek JSON 模式，生成 80-160 字中文摘要和 2-5 个标签
- 摘要覆盖抓取阶段的 meta/正文摘要；标签写入 `tags` / `article_tags`，同一用户复用同名标签
- 资料列表和详情返回 `tags`，`GET /api/v1/tags` 返回标签及资料数
- enrichment 失败、未配置 key 或配额不足时保留原摘要，不影响资料入库和检索
- 用量写入 `ai_usage.scene=SUMMARY`，记录 token、成本、耗时和 `article_id`

> 配置开关：`AI_ENRICHMENT_ENABLED`（默认 true，同时要求 `AI_ENABLED=true` 且配置 DeepSeek Key）、`AI_ENRICHMENT_MAX_INPUT_CHARS=6000`、`AI_ENRICHMENT_MAX_TAGS=5`。

管理后台已接入：

- 总览：用户/资料/切片数、今日调用/tokens/成本/错误率/P95、7 天趋势、来源拆分、最近失败
- 用户与配额：搜索、用量排序、调整月度 token/成本配额、重置密码（撤销 refresh token）、禁用/启用（禁用会撤销 refresh token）
- 用量与成本：按用户/来源/场景/状态筛选、汇总、来源/模型拆分、CSV 导出
- 书签导入：任务列表、条目详情、暂停/恢复/取消/失败重试
- 索引任务：书签导入条目状态；资料入库任务（上传/手动/URL/重建索引）展示阶段、状态和错误
- 模型配置：展示 Chat / Embedding / Rerank，在线编辑 base URL、模型、key（留空保持）、价格和开关；保存到 `system_settings`，user-api 5 秒内生效；保留连接测试
- 系统设置：注册开关、邀请码、默认新用户配额、全站月度预算、告警阈值、问答限流、每日 token 上限，以及 chunk 字数/重叠、topK、混合检索、rerank、强制引用；保存后 user-api 5 秒内生效
- RAG 评测：维护评测用例、创建异步运行，复用线上检索生成回答，计算 recall@5、引用准确率、LLM-as-judge 答案正确率、延迟和成本
- 审计日志：模型、系统设置、用户、导入、任务和评测操作的审计记录

> 模型配置已支持在线编辑并写入 `system_settings`；key 在数据库中以 JSON 存储（管理端只返回掩码），生产环境应限制数据库访问权限。Embedding provider 切换仍需重启，修改模型/维度后需重建索引。

前端页面只有在 dev server 运行时才能打开；API 也不在时登录会提示“无法连接后端服务”。

## 监控与备份

- 健康检查：user-api/admin-api 的 `/actuator/health`
- Prometheus：`/actuator/prometheus`，已暴露 HTTP/JVM/Hikari/Redis 指标
- `deploy/docker-compose.yml` 含 Prometheus 与告警规则：5xx、P95、AI 错误率、Webhook 失败
- 备份脚本：`deploy/scripts/backup.ps1` / `backup.sh`，备份 MySQL 和 Redis RDB
- 测试与压测说明：`scripts/README.md`
- 插件打包/上架：`docs/extension-release.md`

## 已知缺口（下一阶段）

- 仓库默认 embedding 仍是 `dev`；新环境需按上面说明配置 BGE-M3 API Key。本机已配置并验证。
- 本机 Redis 没有 RediSearch，向量检索走应用层余弦；数据量大后应换 Redis Stack HNSW。
- 已做向量 + 关键词 + RRF 混合检索、BGE-reranker 二次重排、资料 AI 摘要/自动标签、插件当前页问答、书签新增/修改/删除自动同步和管理端模型在线配置。
- 管理端模型和系统设置均已支持在线编辑；预算告警已支持 Webhook，可在管理端测试发送。
- 邀请码注册已接入；邮箱验证尚未接入。
- 扫描版 PDF：可选 Tesseract OCR 已接入，默认关闭；启用需要安装 tesseract 和中文语言包。
- 插件商店上架：插件 1.0.0 包、图标和上架文档已就绪；还需要开发者账号、商店截图、隐私政策 URL 和审核。
- RediSearch HNSW 仍待数据量上来后接入；本机向量检索仍为应用层余弦。
- 数据清理任务已接入：过期 refresh token、授权码、幂等键和结束超过 30 天的入库任务。
- 监控指标、告警规则、备份脚本、Playwright 登录 E2E 和 k6 压测脚本已接入；仍缺完整业务 E2E、压力基线和 Alertmanager 转发。
- 问答限流（10 次/分钟/用户）和管理端操作限流（60 次/分钟/管理员）已接入。

## 下一步

1. 邮箱验证（需要 SMTP）
2. 部署到服务器（可选 SSH CD 已就绪，需要配置 secrets）
