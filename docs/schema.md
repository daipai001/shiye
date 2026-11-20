# 拾页（ShiYe）数据库设计文档

| 项目 | 内容 |
|---|---|
| 版本 | **v1.0（V1 基线）** |
| 日期 | 2026-11-12 |
| 数据库 | MySQL 8.0+ |
| 字符集 | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 引擎 | InnoDB |
| 服务 | `shiye-user-api`（18080）/ `shiye-admin-api`（18081） |
| 关联文档 | `requirements.md` v0.9 / `api.md` / `prototype.html` v0.6 |

> 本文档是字段级基线。DDL 已生成：`deploy/sql/schema.sql`；初始迁移：`deploy/sql/migrations/V001__init.sql`；增量迁移放 `deploy/sql/migrations/`。

---

## 1. 设计约定

### 1.1 命名

- 表名：小写、复数、snake_case，例如 `articles`、`ai_usage`。
- 字段名：snake_case，例如 `created_at`、`article_id`。
- 索引：`idx_表_字段`；唯一索引：`uk_表_字段`。
- 枚举值：大写字符串，例如 `READY`、`FAILED`。

### 1.2 主键与类型

- 主键：`id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT`。
- 业务 ID 不对外暴露自增规律；API 以字符串返回 ID（避免 JS 精度丢失）。
- 金额/单价：`DECIMAL(12,6)` 或 `DECIMAL(14,8)`；API 以字符串返回，保留精度。
- token：`INT UNSIGNED`（单次）/ `BIGINT UNSIGNED`（累计）。
- 时间：`DATETIME(3)`，统一 UTC；API 返回 ISO-8601。
- 布尔：`TINYINT(1)`，0/1。
- JSON：仅用于低频查询的扩展字段（tags 用关系表；settings 用 JSON）。

### 1.3 约束

- **不使用物理外键**（项目约定）；归属关系由应用层校验。
- 业务唯一性用 `UNIQUE KEY` 兜底。
- 软删除表加 `deleted_at`，查询默认排除。
- 高频查询必须有索引；新增查询先看执行计划。
- 状态流转由应用层校验，数据库只存值。

### 1.4 表归属（两个服务共库）

| 表 | 写入方 | 读取方 |
|---|---|---|
| `users` | user-api | user-api / admin-api |
| `admin_users` | admin-api / seed | admin-api |
| `refresh_tokens` | user-api（USER）/ admin-api（ADMIN） | 各自 |
| `extension_auth_codes` | user-api | user-api |
| `user_plugin_settings` | user-api | user-api |
| `bookmark_folders` / `bookmark_imports` / `bookmark_items` | user-api | user-api / admin-api |
| `file_assets` | user-api | user-api / admin-api |
| `articles` / `article_chunks` / `article_folders` / `tags` / `article_tags` / `ingest_tasks` | user-api | user-api / admin-api |
| `conversations` / `messages` / `citations` | user-api | user-api |
| `ai_usage` / `ai_usage_daily` / `user_usage_monthly` | user-api（AI Gateway） | user-api / admin-api |
| `model_prices` / `system_settings` | admin-api | user-api / admin-api |
| `admin_audit_logs` | admin-api | admin-api |
| `rag_eval_cases` / `rag_eval_runs` / `rag_eval_results` | admin-api（P1） | admin-api |
| `idempotency_keys` | user-api | user-api |
| `schema_migrations` | deploy | 两个服务只读 |

> V1 两个服务共用一个 MySQL/Redis；admin-api 对用户业务表只读，写管理表。V2 如果彻底拆分，再改为 API/消息同步。

### 1.5 逻辑外键（应用层）

- `articles.user_id → users.id`
- `article_chunks.article_id → articles.id`
- `article_chunks.user_id → users.id`（冗余，检索过滤用）
- `bookmark_imports.user_id → users.id`
- `bookmark_items.import_id → bookmark_imports.id`
- `bookmark_items.article_id → articles.id`
- `conversations.user_id → users.id`
- `messages.conversation_id → conversations.id`
- `citations.message_id → messages.id`
- `citations.chunk_id → article_chunks.id`
- `ai_usage.user_id → users.id`
- `ingest_tasks.article_id → articles.id`
- `article_tags.article_id → articles.id` / `tag_id → tags.id`
- `article_folders.article_id → articles.id` / `bookmark_folder_id → bookmark_folders.id`

---

## 2. ER 概览

```
users 1 ── n bookmark_folders 1 ── n bookmark_imports 1 ── n bookmark_items
  │                                                        │
  │                                                        └── n articles
  ├── 1 ── 1 user_plugin_settings
  ├── 1 ── n refresh_tokens / extension_auth_codes
  ├── 1 ── n articles 1 ── n article_chunks
  │           │  n ── n tags (article_tags)
  │           │  n ── n bookmark_folders (article_folders)
  │           └── 1 ── n ingest_tasks
  ├── 1 ── n conversations 1 ── n messages 1 ── n citations
  ├── 1 ── n ai_usage
  ├── 1 ── n ai_usage_daily
  └── 1 ── n user_usage_monthly

admin_users 1 ── n admin_audit_logs
admin_users 1 ── n model_prices / system_settings（updated_by）
```

---

## 3. 表清单

| 域 | 表 | 说明 | 优先级 |
|---|---|---|---|
| 基础设施 | `schema_migrations` | 迁移记录 | P0 |
| 身份 | `users` | 普通用户 | P0 |
| 身份 | `admin_users` | 管理员 | P0 |
| 身份 | `refresh_tokens` | 刷新令牌 | P0 |
| 身份 | `extension_auth_codes` | 插件一次性授权码 | P0 |
| 身份 | `user_plugin_settings` | 插件/悬浮球偏好 | P0 |
| 书签 | `bookmark_folders` | 用户选中的书签文件夹 | P0 |
| 书签 | `bookmark_imports` | 导入任务 | P0 |
| 书签 | `bookmark_items` | 导入条目 | P0 |
| 文件 | `file_assets` | 上传文件资产 | P0 |
| 知识库 | `articles` | 资料 | P0 |
| 知识库 | `article_chunks` | 切片 | P0 |
| 知识库 | `article_folders` | 资料-文件夹关系 | P0 |
| 知识库 | `tags` | 标签 | P0 |
| 知识库 | `article_tags` | 资料-标签关系 | P0 |
| 知识库 | `ingest_tasks` | 解析/切片/向量化/索引任务 | 已启用 |
| 问答 | `conversations` | 会话 | P0 |
| 问答 | `messages` | 消息 | P0 |
| 问答 | `citations` | 引用 | P0 |
| 问答 | `message_feedback` | 回答反馈 | 已启用 |
| AI | `ai_usage` | 每次 AI 调用明细 | P0 |
| AI | `ai_usage_daily` | 日汇总（定时从 `ai_usage` 聚合） | 已启用 |
| AI | `user_usage_monthly` | 用户月用量（配额） | P0 |
| AI | `model_prices` | 模型价格（预留，当前单价来自 `system_settings` 并快照到 `ai_usage`） | 预留 |
| 配置 | `system_settings` | 全局配置 | P0 |
| 运维 | `admin_audit_logs` | 管理员审计 | 已启用 |
| 评测 | `rag_eval_cases` | RAG 评测问题 | 已启用 |
| 评测 | `rag_eval_runs` | 评测运行 | 已启用 |
| 评测 | `rag_eval_results` | 评测结果 | 已启用 |
| 幂等 | `idempotency_keys` | POST 幂等键 | P0 |

---

## 4. 表定义

### 4.1 `schema_migrations`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `version` | VARCHAR(32) | 否 | — | 主键，如 `V001` |
| `description` | VARCHAR(255) | 否 | — | 说明 |
| `checksum` | CHAR(64) | 否 | — | 文件 SHA-256 |
| `applied_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 执行时间 |
| `execution_ms` | INT UNSIGNED | 否 | 0 | 耗时 |

**索引**：PK `version`。

---

### 4.2 `users`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `email` | VARCHAR(190) | 否 | — | 登录邮箱 |
| `password_hash` | VARCHAR(100) | 否 | — | BCrypt |
| `display_name` | VARCHAR(64) | 是 | NULL | 显示名 |
| `avatar_url` | VARCHAR(512) | 是 | NULL | 头像 |
| `status` | VARCHAR(16) | 否 | `ACTIVE` | ACTIVE / DISABLED |
| `monthly_token_quota` | BIGINT UNSIGNED | 否 | 200000 | 月 token 配额 |
| `monthly_cost_quota` | DECIMAL(12,6) | 否 | 5.000000 | 月成本配额 |
| `quota_source` | VARCHAR(16) | 否 | `DEFAULT` | DEFAULT / NEW_USER / ADMIN |
| `email_verified_at` | DATETIME(3) | 是 | NULL | P1 |
| `last_login_at` | DATETIME(3) | 是 | NULL | 最后登录 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |
| `deleted_at` | DATETIME(3) | 是 | NULL | 软删除 |

**索引**

- `UNIQUE uk_users_email (email)`
- `idx_users_status_created (status, created_at)`
- `idx_users_deleted (deleted_at)`

**说明**

- V1 `users` 只放普通用户；管理员在 `admin_users`。
- `role` 字段不设，避免和 `admin_users` 混淆；V2 如需用户角色再加。
- 注册时从 `system_settings` 读取默认配额写入。

---

### 4.3 `admin_users`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `email` | VARCHAR(190) | 否 | — | 登录邮箱 |
| `password_hash` | VARCHAR(100) | 否 | — | BCrypt |
| `name` | VARCHAR(64) | 否 | — | 显示名 |
| `role` | VARCHAR(32) | 否 | `SUPER_ADMIN` | V1 仅 SUPER_ADMIN |
| `status` | VARCHAR(16) | 否 | `ACTIVE` | ACTIVE / DISABLED |
| `last_login_at` | DATETIME(3) | 是 | NULL | 最后登录 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_admin_users_email (email)`
- `idx_admin_users_status (status)`

**说明**

- V1 只有一个 `SUPER_ADMIN`，由 `deploy/sql/seed.sql` 初始化。
- 不开放注册；`SUPER_ADMIN` 不可禁用。
- 管理员如要使用产品，另注册普通 `users` 账号。

---

### 4.4 `refresh_tokens`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `subject_type` | VARCHAR(16) | 否 | — | USER / ADMIN |
| `subject_id` | BIGINT UNSIGNED | 否 | — | 用户/管理员 ID |
| `token_hash` | CHAR(64) | 否 | — | SHA-256(refresh token) |
| `expires_at` | DATETIME(3) | 否 | — | 过期时间 |
| `revoked_at` | DATETIME(3) | 是 | NULL | 撤销时间 |
| `replaced_by_id` | BIGINT UNSIGNED | 是 | NULL | 轮换后的新 token |
| `user_agent` | VARCHAR(512) | 是 | NULL | 客户端 UA |
| `ip` | VARCHAR(45) | 是 | NULL | IPv4/IPv6 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `UNIQUE uk_refresh_tokens_hash (token_hash)`
- `idx_refresh_tokens_subject (subject_type, subject_id, expires_at)`
- `idx_refresh_tokens_expires (expires_at)`

**说明**：刷新时轮换；旧 token 标记 `revoked_at`；退出登录撤销当前 token；定时清理过期。

---

### 4.5 `extension_auth_codes`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `code_hash` | CHAR(64) | 否 | — | SHA-256(code) |
| `expires_at` | DATETIME(3) | 否 | — | 5 分钟 |
| `used_at` | DATETIME(3) | 是 | NULL | 使用时间 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `UNIQUE uk_extension_codes_hash (code_hash)`
- `idx_extension_codes_user (user_id, expires_at)`

**说明**：一次性；使用后标记 `used_at`；过期/已用不能换 token。

---

### 4.6 `user_plugin_settings`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `ball_enabled` | TINYINT(1) | 否 | 1 | 悬浮球开关 |
| `default_mode` | VARCHAR(16) | 否 | `LIBRARY` | LIBRARY / CURRENT |
| `default_scope` | VARCHAR(16) | 否 | `ALL` | ALL / FOLDER / TAG |
| `default_scope_id` | BIGINT UNSIGNED | 是 | NULL | FOLDER/TAG 对应的文件夹/标签 ID |
| `shortcut` | VARCHAR(64) | 是 | NULL | 如 `Alt+Shift+S` |
| `site_blacklist` | JSON | 是 | NULL | 不注入的域名数组 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**：`UNIQUE uk_plugin_settings_user (user_id)`。

---

### 4.7 `bookmark_folders`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `browser` | VARCHAR(16) | 否 | — | CHROME / EDGE |
| `folder_id` | VARCHAR(128) | 否 | — | 浏览器书签 folderId |
| `folder_path` | VARCHAR(512) | 否 | — | 文件夹路径 |
| `is_primary` | TINYINT(1) | 否 | 0 | 是否主文件夹 |
| `auto_sync` | TINYINT(1) | 否 | 1 | 是否自动同步 |
| `last_synced_at` | DATETIME(3) | 是 | NULL | 最后同步 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_bookmark_folders_browser (user_id, browser, folder_id)`
- `idx_bookmark_folders_primary (user_id, is_primary)`

**说明**：每个用户最多一个 `is_primary=1`，应用层事务保证；V1 自动同步只监听主文件夹。

---

### 4.8 `bookmark_imports`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `bookmark_folder_id` | BIGINT UNSIGNED | 是 | NULL | 对应 `bookmark_folders.id` |
| `browser` | VARCHAR(16) | 否 | — | CHROME / EDGE |
| `folder_id` | VARCHAR(128) | 否 | — | 浏览器 folderId |
| `folder_path` | VARCHAR(512) | 否 | — | 文件夹路径 |
| `recursive` | TINYINT(1) | 否 | 1 | 包含子文件夹 |
| `status` | VARCHAR(16) | 否 | `PENDING` | PENDING / RUNNING / PAUSED / DONE / FAILED / CANCELLED |
| `total` | INT UNSIGNED | 否 | 0 | 总数 |
| `success` | INT UNSIGNED | 否 | 0 | 成功 |
| `failed` | INT UNSIGNED | 否 | 0 | 失败 |
| `skipped` | INT UNSIGNED | 否 | 0 | 跳过 |
| `error_code` | VARCHAR(64) | 是 | NULL | 任务级错误 |
| `started_at` | DATETIME(3) | 是 | NULL | 开始 |
| `finished_at` | DATETIME(3) | 是 | NULL | 结束 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `idx_bookmark_imports_user (user_id, created_at)`
- `idx_bookmark_imports_status (status, created_at)`
- `idx_bookmark_imports_folder (bookmark_folder_id)`

**说明**：`success + failed + skipped` 应等于已处理数，最终等于 `total`；应用层保证。

---

### 4.9 `bookmark_items`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `import_id` | BIGINT UNSIGNED | 否 | — | 导入任务 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户（冗余） |
| `title` | VARCHAR(512) | 是 | NULL | 书签标题 |
| `url` | VARCHAR(2048) | 否 | — | URL |
| `url_hash` | CHAR(64) | 否 | — | SHA-256(规范化 URL) |
| `browser_folder_id` | VARCHAR(128) | 是 | NULL | 子文件夹 ID |
| `folder_path` | VARCHAR(512) | 是 | NULL | 子文件夹路径 |
| `status` | VARCHAR(32) | 否 | `PENDING` | PENDING / FETCHING / PARSING / CHUNKING / EMBEDDING / READY / FAILED / SKIPPED |
| `article_id` | BIGINT UNSIGNED | 是 | NULL | 成功后回填 |
| `error_code` | VARCHAR(64) | 是 | NULL | 错误码 |
| `error_message` | VARCHAR(1024) | 是 | NULL | 错误信息 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_bookmark_items_import_url (import_id, url_hash)`
- `idx_bookmark_items_import_status (import_id, status)`
- `idx_bookmark_items_user_status (user_id, status)`
- `idx_bookmark_items_article (article_id)`

---

### 4.10 `file_assets`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `storage_type` | VARCHAR(16) | 否 | `LOCAL` | LOCAL / OSS / R2 |
| `bucket` | VARCHAR(128) | 是 | NULL | 对象存储 bucket |
| `object_key` | VARCHAR(512) | 否 | — | 存储 key |
| `original_name` | VARCHAR(255) | 否 | — | 原文件名 |
| `mime_type` | VARCHAR(128) | 否 | — | MIME |
| `size_bytes` | BIGINT UNSIGNED | 否 | 0 | 大小 |
| `sha256` | CHAR(64) | 否 | — | 文件哈希 |
| `status` | VARCHAR(16) | 否 | `UPLOADING` | UPLOADING / READY / FAILED / DELETED |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |
| `deleted_at` | DATETIME(3) | 是 | NULL | 软删除 |

**索引**

- `UNIQUE uk_file_assets_user_sha (user_id, sha256)`
- `idx_file_assets_user_status (user_id, status)`
- `idx_file_assets_sha (sha256)`

---

### 4.11 `articles`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `title` | VARCHAR(512) | 否 | — | 标题 |
| `url` | VARCHAR(2048) | 是 | NULL | 原始 URL |
| `url_hash` | CHAR(64) | 是 | NULL | SHA-256(规范化 URL) |
| `domain` | VARCHAR(255) | 是 | NULL | 域名 |
| `source_type` | VARCHAR(16) | 否 | — | BOOKMARK / MANUAL / UPLOAD |
| `author` | VARCHAR(255) | 是 | NULL | 作者 |
| `published_at` | DATETIME(3) | 是 | NULL | 发布时间 |
| `fetched_at` | DATETIME(3) | 是 | NULL | 抓取时间 |
| `summary` | TEXT | 是 | NULL | AI 摘要 |
| `content_text` | MEDIUMTEXT | 是 | NULL | 清洗后正文 |
| `content_hash` | CHAR(64) | 是 | NULL | 正文 SHA-256 |
| `word_count` | INT UNSIGNED | 否 | 0 | 字数 |
| `language` | VARCHAR(16) | 是 | NULL | zh/en/… |
| `status` | VARCHAR(16) | 否 | `PENDING` | PENDING / PARSING / CHUNKING / EMBEDDING / READY / FAILED |
| `file_asset_id` | BIGINT UNSIGNED | 是 | NULL | 上传文件 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |
| `deleted_at` | DATETIME(3) | 是 | NULL | 软删除 |

**索引**

- `UNIQUE uk_articles_user_url (user_id, url_hash)`
- `idx_articles_user_status_updated (user_id, status, updated_at)`
- `idx_articles_user_content_hash (user_id, content_hash)`
- `idx_articles_user_domain (user_id, domain)`
- `idx_articles_file_asset (file_asset_id)`
- `idx_articles_deleted (deleted_at)`
- `FULLTEXT KEY ft_articles_title_content (title, content_text) WITH PARSER ngram`

**说明**

- URL 资料用 `url_hash` 去重；上传资料 `url_hash=NULL`（MySQL 唯一索引允许多个 NULL），应用层用 `content_hash` 去重。
- `content_text` 只存清洗后正文；原始 HTML/PDF 放 `file_assets`。
- 软删除后异步清理 chunks/vector。
- 摘要/标签在资料进入 `READY` 后异步生成（当前由 `ArticleEnrichmentService` 调度，不单独写 `ingest_tasks.stage=ENRICH`）；失败保留抓取摘要，不阻塞入库。

---

### 4.12 `article_chunks`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `article_id` | BIGINT UNSIGNED | 否 | — | 资料 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户（冗余，检索过滤） |
| `chunk_index` | INT UNSIGNED | 否 | — | 0-based 序号 |
| `content` | TEXT | 否 | — | 切片正文 |
| `token_count` | INT UNSIGNED | 否 | 0 | token 数 |
| `char_start` | INT UNSIGNED | 否 | 0 | 原文起始 |
| `char_end` | INT UNSIGNED | 否 | 0 | 原文结束 |
| `embedding_model` | VARCHAR(64) | 否 | — | 如 `bge-m3` |
| `embedding_dim` | SMALLINT UNSIGNED | 否 | 1024 | 维度 |
| `vector_id` | VARCHAR(128) | 否 | — | Redis key/UUID |
| `content_hash` | CHAR(64) | 是 | NULL | 切片哈希 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `UNIQUE uk_chunks_article_index (article_id, chunk_index)`
- `idx_chunks_user_article (user_id, article_id)`
- `idx_chunks_vector (vector_id)`
- `FULLTEXT ft_chunks_content (content) WITH PARSER ngram`（P1 混合检索关键词召回）

**说明**：`char_start/char_end` 是引用跳转关键；重建索引先删旧 chunk/vector。全文索引由 `migrations/V002__chunk_fulltext.sql` 增量添加。

---

### 4.13 `article_folders`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `article_id` | BIGINT UNSIGNED | 否 | — | 资料 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `bookmark_folder_id` | BIGINT UNSIGNED | 否 | — | 书签文件夹 |
| `folder_path` | VARCHAR(512) | 是 | NULL | 冗余路径 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `UNIQUE uk_article_folders (article_id, bookmark_folder_id)`
- `idx_article_folders_user_folder (user_id, bookmark_folder_id)`

**说明**：用于“按文件夹提问/筛选”；一篇资料可属于多个文件夹（V1 通常一个）。

---

### 4.14 `tags`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `name` | VARCHAR(64) | 否 | — | 显示名 |
| `normalized_name` | VARCHAR(64) | 否 | — | 小写/去空格 |
| `color` | VARCHAR(16) | 是 | NULL | 前端颜色 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_tags_user_name (user_id, normalized_name)`
- `idx_tags_user (user_id)`

---

### 4.15 `article_tags`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `article_id` | BIGINT UNSIGNED | 否 | — | 资料 |
| `tag_id` | BIGINT UNSIGNED | 否 | — | 标签 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `UNIQUE uk_article_tags (article_id, tag_id)`
- `idx_article_tags_tag (tag_id, user_id)`
- `idx_article_tags_user (user_id)`

---

### 4.16 `ingest_tasks`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `article_id` | BIGINT UNSIGNED | 是 | NULL | 资料（抓取前可空） |
| `source_type` | VARCHAR(16) | 否 | — | BOOKMARK / MANUAL / UPLOAD / REINDEX |
| `stage` | VARCHAR(16) | 否 | — | FETCH / PARSE / CHUNK / EMBED / INDEX / ENRICH / DONE |
| `status` | VARCHAR(16) | 否 | `PENDING` | PENDING / RUNNING / SUCCESS / FAILED / DEAD |
| `retry_count` | INT UNSIGNED | 否 | 0 | 已重试 |
| `max_retries` | INT UNSIGNED | 否 | 2 | 最大重试 |
| `idempotency_key` | VARCHAR(128) | 否 | — | 唯一任务键 |
| `error_code` | VARCHAR(64) | 是 | NULL | 错误码 |
| `error_message` | VARCHAR(1024) | 是 | NULL | 错误信息 |
| `started_at` | DATETIME(3) | 是 | NULL | 开始 |
| `finished_at` | DATETIME(3) | 是 | NULL | 结束 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_ingest_tasks_idem (idempotency_key)`
- `idx_ingest_tasks_article_stage (article_id, stage, status)`
- `idx_ingest_tasks_status_created (status, created_at)`
- `idx_ingest_tasks_user_status (user_id, status)`

---

### 4.17 `conversations`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `title` | VARCHAR(255) | 否 | — | 标题 |
| `scope_type` | VARCHAR(16) | 否 | `ALL` | ALL / ARTICLE / FOLDER / TAG / CURRENT |
| `scope_id` | BIGINT UNSIGNED | 是 | NULL | 资料/文件夹/标签 ID |
| `model` | VARCHAR(64) | 是 | NULL | 使用的 Chat 模型 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |
| `deleted_at` | DATETIME(3) | 是 | NULL | 软删除 |

**索引**

- `idx_conversations_user_updated (user_id, updated_at)`
- `idx_conversations_scope (user_id, scope_type, scope_id)`
- `idx_conversations_deleted (deleted_at)`

---

### 4.18 `messages`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `conversation_id` | BIGINT UNSIGNED | 否 | — | 会话 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `role` | VARCHAR(16) | 否 | — | USER / ASSISTANT / SYSTEM |
| `content` | MEDIUMTEXT | 否 | — | 内容 |
| `model` | VARCHAR(64) | 是 | NULL | 助手消息模型 |
| `prompt_tokens` | INT UNSIGNED | 否 | 0 | 输入 token |
| `completion_tokens` | INT UNSIGNED | 否 | 0 | 输出 token |
| `request_id` | VARCHAR(64) | 是 | NULL | AI 请求 ID |
| `status` | VARCHAR(16) | 否 | `SUCCESS` | SUCCESS / FAILED / ABORTED |
| `error_code` | VARCHAR(64) | 是 | NULL | 错误 |
| `first_token_ms` | INT UNSIGNED | 是 | NULL | 首 token |
| `latency_ms` | INT UNSIGNED | 是 | NULL | 总耗时 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `idx_messages_conversation (conversation_id, id)`
- `idx_messages_user_created (user_id, created_at)`
- `idx_messages_request (request_id)`

---

### 4.19 `citations`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `message_id` | BIGINT UNSIGNED | 否 | — | 助手消息 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `article_id` | BIGINT UNSIGNED | 否 | — | 资料 |
| `chunk_id` | BIGINT UNSIGNED | 否 | — | 切片 |
| `rank` | INT UNSIGNED | 否 | 1 | 引用序号 |
| `score` | DECIMAL(8,6) | 是 | NULL | 检索/重排分数 |
| `quote` | VARCHAR(1024) | 是 | NULL | 引用片段 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `idx_citations_message_rank (message_id, rank)`
- `idx_citations_article (article_id)`
- `idx_citations_chunk (chunk_id)`

---

### 4.20 `ai_usage`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `request_id` | VARCHAR(64) | 否 | — | 请求唯一 ID |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `source` | VARCHAR(16) | 否 | — | WEB / EXTENSION / ADMIN / API |
| `scene` | VARCHAR(16) | 否 | — | CHAT / EMBEDDING / RERANK / SUMMARY / EVAL |
| `model` | VARCHAR(64) | 否 | — | 模型 |
| `prompt_tokens` | INT UNSIGNED | 否 | 0 | 输入 |
| `completion_tokens` | INT UNSIGNED | 否 | 0 | 输出 |
| `total_tokens` | INT UNSIGNED | 否 | 0 | 总 |
| `cached_tokens` | INT UNSIGNED | 否 | 0 | 缓存命中 |
| `input_price` | DECIMAL(12,6) | 否 | 0 | 输入单价快照 |
| `output_price` | DECIMAL(12,6) | 否 | 0 | 输出单价快照 |
| `cost` | DECIMAL(14,8) | 否 | 0 | 成本 |
| `currency` | CHAR(3) | 否 | `CNY` | 币种 |
| `latency_ms` | INT UNSIGNED | 否 | 0 | 总耗时 |
| `first_token_ms` | INT UNSIGNED | 是 | NULL | 首 token |
| `status` | VARCHAR(16) | 否 | — | SUCCESS / FAILED / ABORTED |
| `error_code` | VARCHAR(64) | 是 | NULL | 错误 |
| `estimated` | TINYINT(1) | 否 | 0 | 是否估算 |
| `conversation_id` | BIGINT UNSIGNED | 是 | NULL | 会话 |
| `article_id` | BIGINT UNSIGNED | 是 | NULL | 资料 |
| `client_version` | VARCHAR(32) | 是 | NULL | 插件版本 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 时间 |

**索引**

- `UNIQUE uk_ai_usage_request (request_id)`
- `idx_ai_usage_user_created (user_id, created_at)`
- `idx_ai_usage_source_created (source, created_at)`
- `idx_ai_usage_scene_created (scene, created_at)`
- `idx_ai_usage_model_created (model, created_at)`
- `idx_ai_usage_status_created (status, created_at)`
- `idx_ai_usage_conversation (conversation_id)`
- `idx_ai_usage_article (article_id)`

---

### 4.21 `ai_usage_daily`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `date` | DATE | 否 | — | 日期（UTC） |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `source` | VARCHAR(16) | 否 | — | 来源 |
| `scene` | VARCHAR(16) | 否 | — | 场景 |
| `model` | VARCHAR(64) | 否 | — | 模型 |
| `calls` | INT UNSIGNED | 否 | 0 | 调用数 |
| `tokens` | BIGINT UNSIGNED | 否 | 0 | tokens |
| `cost` | DECIMAL(14,8) | 否 | 0 | 成本 |
| `currency` | CHAR(3) | 否 | `CNY` | 币种 |
| `avg_latency_ms` | INT UNSIGNED | 否 | 0 | 平均耗时 |
| `error_count` | INT UNSIGNED | 否 | 0 | 错误数 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_usage_daily (date, user_id, source, scene, model)`
- `idx_usage_daily_user_date (user_id, date)`
- `idx_usage_daily_date (date)`

**说明**：user-api 每日把 `ai_usage` 聚合到本表，默认回补最近 7 天；聚合任务可重复执行。管理端总览和用量明细当前仍直接查询 `ai_usage`，本表用于日级历史汇总。

---

### 4.22 `user_usage_monthly`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `month` | CHAR(7) | 否 | — | `2026-11` |
| `tokens` | BIGINT UNSIGNED | 否 | 0 | 月 tokens |
| `cost` | DECIMAL(14,8) | 否 | 0 | 月成本 |
| `calls` | INT UNSIGNED | 否 | 0 | 月调用数 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_user_usage_monthly (user_id, month)`
- `idx_user_usage_monthly_month (month)`

**说明**：配额检查优先读这张表；从 `ai_usage` 增量更新，月末对账。

---

### 4.23 `model_prices`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `provider` | VARCHAR(64) | 否 | — | DeepSeek / 硅基流动 等 |
| `model` | VARCHAR(128) | 否 | — | 模型名 |
| `scene` | VARCHAR(16) | 否 | — | CHAT / EMBEDDING / RERANK |
| `input_price` | DECIMAL(12,6) | 否 | 0 | 每 1M 输入 token |
| `output_price` | DECIMAL(12,6) | 否 | 0 | 每 1M 输出 token |
| `currency` | CHAR(3) | 否 | `CNY` | 币种 |
| `effective_at` | DATETIME(3) | 否 | — | 生效时间 |
| `is_active` | TINYINT(1) | 否 | 1 | 当前价格 |
| `created_by` | BIGINT UNSIGNED | 是 | NULL | 管理员 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `idx_model_prices_lookup (provider, model, scene, effective_at)`
- `idx_model_prices_active (is_active)`

**说明**：价格历史保留；`ai_usage` 存调用时单价快照，不随改价变化。同一 `provider + model + scene` 只允许一个 `is_active=1`，由应用层事务保证。

---

### 4.24 `system_settings`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `setting_key` | VARCHAR(128) | 否 | — | 唯一 key |
| `setting_value` | JSON | 是 | NULL | 值 |
| `is_secret` | TINYINT(1) | 否 | 0 | 是否密钥（API 只写不读） |
| `description` | VARCHAR(255) | 是 | NULL | 说明 |
| `updated_by` | BIGINT UNSIGNED | 是 | NULL | admin_users.id |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_system_settings_key (setting_key)`

**关键 key**

- 当前已使用：`ai.model.config`（单个 JSON，包含 Chat / Embedding / Rerank 覆盖值，`is_secret=1`）
- 当前已使用：`system.settings`（注册开关、邀请码、默认配额、全局预算、告警阈值、Webhook 告警、限流、每日上限、RAG chunk/topK/混合检索/rerank/强制引用，`is_secret=0`）
- 预留：`registration_enabled`、`default_monthly_tokens`、`default_monthly_cost`、`global_monthly_tokens`、`global_monthly_cost`、`alert_threshold`、`rate_limit_per_minute`、`daily_token_limit`
- 预留：`rag_chunk_size` / `rag_chunk_overlap` / `rag_top_k` / `rag_hybrid_enabled` / `rag_rerank_enabled` / `rag_citation_required`

> `ai.model.config` 中 API Key 以 JSON 原文存库，管理端只返回掩码；生产环境应限制数据库访问权限，后续可接 KMS/字段加密。

---

### 4.25 `admin_audit_logs`（P2）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `admin_id` | BIGINT UNSIGNED | 否 | — | 管理员 |
| `action` | VARCHAR(64) | 否 | — | 如 UPDATE_MODEL |
| `resource_type` | VARCHAR(64) | 否 | — | 资源类型 |
| `resource_id` | VARCHAR(64) | 是 | NULL | 资源 ID |
| `detail` | JSON | 是 | NULL | 变更详情（脱敏） |
| `ip` | VARCHAR(45) | 是 | NULL | IP |
| `user_agent` | VARCHAR(512) | 是 | NULL | UA |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 时间 |

**索引**：`idx_audit_admin_time (admin_id, created_at)`、`idx_audit_resource (resource_type, resource_id)`。

---

### 4.26 `rag_eval_cases`（P1）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 所属用户 |
| `question` | TEXT | 否 | — | 问题 |
| `expected_article_ids` | JSON | 是 | NULL | 期望文章 |
| `expected_chunk_ids` | JSON | 是 | NULL | 期望切片 |
| `gold_answer` | TEXT | 是 | NULL | 参考答案 |
| `tags` | JSON | 是 | NULL | 分类 |
| `status` | VARCHAR(16) | 否 | `ACTIVE` | ACTIVE / ARCHIVED |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**：`idx_eval_cases_user_status (user_id, status)`。

---

### 4.27 `rag_eval_runs`（P1）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `name` | VARCHAR(128) | 否 | — | 运行名 |
| `model` | VARCHAR(64) | 是 | NULL | Chat 模型 |
| `embedding_model` | VARCHAR(64) | 是 | NULL | Embedding 模型 |
| `top_k` | INT UNSIGNED | 是 | NULL | 参数快照 |
| `rerank` | TINYINT(1) | 否 | 0 | 是否 rerank |
| `metrics` | JSON | 是 | NULL | 汇总指标 |
| `started_at` | DATETIME(3) | 是 | NULL | 开始 |
| `finished_at` | DATETIME(3) | 是 | NULL | 结束 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**：`idx_eval_runs_user_time (user_id, created_at)`。

---

### 4.28 `rag_eval_results`（P1）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `run_id` | BIGINT UNSIGNED | 否 | — | 运行 |
| `case_id` | BIGINT UNSIGNED | 否 | — | 用例 |
| `retrieved_chunk_ids` | JSON | 是 | NULL | 召回 |
| `recall_at_5` | DECIMAL(6,4) | 是 | NULL | recall@5 |
| `citation_accuracy` | DECIMAL(6,4) | 是 | NULL | 引用准确率 |
| `answer_correctness` | DECIMAL(6,4) | 是 | NULL | LLM-as-judge 答案正确率 |
| `answer_text` | TEXT | 是 | NULL | 模型回答 |
| `judge_reason` | TEXT | 是 | NULL | 判定说明 |
| `latency_ms` | INT UNSIGNED | 是 | NULL | 延迟 |
| `cost` | DECIMAL(14,8) | 是 | NULL | 成本 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**：`idx_eval_results_run (run_id)`、`idx_eval_results_case (case_id)`。

---

### 4.29 `idempotency_keys`

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `endpoint` | VARCHAR(128) | 否 | — | 接口路径 |
| `idempotency_key` | VARCHAR(128) | 否 | — | 客户端 key |
| `request_hash` | CHAR(64) | 否 | — | 请求体哈希 |
| `response_code` | INT UNSIGNED | 是 | NULL | 响应码 |
| `response_body` | JSON | 是 | NULL | 响应快照 |
| `expires_at` | DATETIME(3) | 否 | — | 过期 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |

**索引**

- `UNIQUE uk_idempotency (user_id, endpoint, idempotency_key)`
- `idx_idempotency_expires (expires_at)`

**说明**：同一 key + 不同请求体 → `40902`；24h 过期；定时清理。

---

### 4.30 `message_feedback`（P1）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| `message_id` | BIGINT UNSIGNED | 否 | — | 助手消息 |
| `user_id` | BIGINT UNSIGNED | 否 | — | 用户 |
| `rating` | VARCHAR(8) | 否 | — | UP / DOWN |
| `comment` | VARCHAR(500) | 是 | NULL | 反馈内容 |
| `created_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) | 创建 |
| `updated_at` | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新 |

**索引**

- `UNIQUE uk_message_feedback (message_id, user_id)`
- `idx_message_feedback_user (user_id, created_at)`

**说明**：一个用户对一条消息只保留一条反馈；重复提交覆盖。

---

## 5. 枚举字典

| 域 | 字段 | 值 |
|---|---|---|
| 用户 | `users.status` | `ACTIVE` / `DISABLED` |
| 管理员 | `admin_users.status` | `ACTIVE` / `DISABLED` |
| 资料 | `articles.status` | `PENDING` / `PARSING` / `CHUNKING` / `EMBEDDING` / `READY` / `FAILED` |
| 资料 | `articles.source_type` | `BOOKMARK` / `MANUAL` / `UPLOAD` |
| 导入 | `bookmark_imports.status` | `PENDING` / `RUNNING` / `PAUSED` / `DONE` / `FAILED` / `CANCELLED` |
| 导入项 | `bookmark_items.status` | `PENDING` / `FETCHING` / `PARSING` / `CHUNKING` / `EMBEDDING` / `READY` / `FAILED` / `SKIPPED` |
| 任务 | `ingest_tasks.stage` | `FETCH` / `PARSE` / `CHUNK` / `EMBED` / `INDEX` / `ENRICH` |
| 任务 | `ingest_tasks.status` | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `DEAD` |
| 会话 | `conversations.scope_type` | `ALL` / `ARTICLE` / `FOLDER` / `TAG` / `CURRENT` |
| 消息 | `messages.role` | `USER` / `ASSISTANT` / `SYSTEM` |
| 消息 | `messages.status` | `SUCCESS` / `FAILED` / `ABORTED` |
| AI | `ai_usage.source` | `WEB` / `EXTENSION` / `ADMIN` / `API` |
| AI | `ai_usage.scene` | `CHAT` / `EMBEDDING` / `RERANK` / `SUMMARY` / `EVAL` |
| AI | `ai_usage.status` | `SUCCESS` / `FAILED` / `ABORTED` |
| 反馈 | `message_feedback.rating` | `UP` / `DOWN` |
| 插件 | `user_plugin_settings.default_mode` | `LIBRARY` / `CURRENT` |
| 插件 | `user_plugin_settings.default_scope` | `ALL` / `FOLDER` / `TAG` |
| 插件 | `user_plugin_settings.default_scope_id` | FOLDER/TAG 时为对应 ID，ALL 时为 NULL |
| 文件 | `file_assets.status` | `UPLOADING` / `READY` / `FAILED` / `DELETED` |

---

## 6. 索引与查询对照

| 查询 | 索引 |
|---|---|
| 用户登录 | `uk_users_email` |
| 管理员登录 | `uk_admin_users_email` |
| 用户资料列表 | `idx_articles_user_status_updated` |
| 资料去重 | `uk_articles_user_url` / `idx_articles_user_content_hash` |
| 资料搜索 | `ft_articles_title_content`（ngram 全文） |
| 切片读取 | `uk_chunks_article_index` |
| 书签导入列表 | `idx_bookmark_imports_user` |
| 导入项状态 | `idx_bookmark_items_import_status` |
| 任务队列 | `idx_ingest_tasks_status_created` |
| 会话列表 | `idx_conversations_user_updated` |
| 消息列表 | `idx_messages_conversation` |
| 用量明细 | `idx_ai_usage_user_created` / `source` / `scene` / `model` |
| 日汇总 | `uk_usage_daily` |
| 月配额 | `uk_user_usage_monthly` |
| 幂等 | `uk_idempotency` |

---

## 7. 数据生命周期

| 数据 | 策略 |
|---|---|
| 资料 | 软删除；切片/向量异步清理 |
| 文件资产 | 软删除；对象存储异步清理 |
| 书签导入 | 保留历史；失败项可重试 |
| 会话/消息 | 用户删除软删除；可后台清理 |
| AI 用量 | 永久保留；日/月汇总长期保留 |
| 刷新令牌/授权码 | 过期清理 |
| 管理员审计 | 保留 180 天（P2） |
| 评测数据 | P1，保留最近 N 次运行 |

**清理任务**

- 已接入：过期 refresh token、extension code、idempotency key、结束超过 30 天的 ingest task。
- 默认每天 03:30 UTC 执行，配置项 `shiye.retention.*`；分批删除，单批默认 1000。
- 待接入：软删除文章超过 7 天的切片/向量/文件、日汇总归档、管理员审计 180 天保留。

---

## 8. 迁移策略

- 迁移文件：`deploy/sql/migrations/V001__init.sql`、`V002__chunk_fulltext.sql`、`V003__plugin_default_scope.sql`、`V004__eval_answer_details.sql`。
- 每个迁移必须幂等/可重复执行；记录到 `schema_migrations`。
- 不修改已发布的迁移；新增迁移。
- 大表加字段用 `ALGORITHM=INPLACE` 或分批回填。
- 回滚：每个迁移附回滚说明；不可逆操作先备份。
- 初始化：`schema.sql` + `seed.sql`（管理员）。

---

## 9. 孤儿与一致性检查

定期执行（发布后每日）：

```sql
-- 资料没有用户
SELECT a.id FROM articles a LEFT JOIN users u ON u.id = a.user_id
WHERE u.id IS NULL;

-- 切片没有资料
SELECT c.id FROM article_chunks c LEFT JOIN articles a ON a.id = c.article_id
WHERE a.id IS NULL;

-- 引用没有消息/切片
SELECT c.id FROM citations c
LEFT JOIN messages m ON m.id = c.message_id
LEFT JOIN article_chunks ch ON ch.id = c.chunk_id
WHERE m.id IS NULL OR ch.id IS NULL;

-- 导入项没有任务
SELECT i.id FROM bookmark_items i LEFT JOIN bookmark_imports b ON b.id = i.import_id
WHERE b.id IS NULL;

-- 标签关系没有标签
SELECT at.id FROM article_tags at LEFT JOIN tags t ON t.id = at.tag_id
WHERE t.id IS NULL;

-- 用量没有用户
SELECT au.id FROM ai_usage au LEFT JOIN users u ON u.id = au.user_id
WHERE u.id IS NULL;
```

要求：所有检查返回 0 行；否则阻断发布。

---

## 10. 容量估算（V1）

| 数据 | 假设 | 估算 |
|---|---|---|
| 用户 | 100 | 忽略 |
| 资料 | 1,000/用户 | 100 万行以内 |
| 切片 | 20/资料 | 2,000 万行以内 |
| 向量 | 1024 float32 ≈ 4KB/chunk | 约 80GB（含索引开销） |
| AI 用量 | 100 万/年 | 索引可控；按月归档 |
| 会话/消息 | 10 万/年 | 可接受 |

> 单机 MySQL + Redis Stack 足够 V1；向量数据量超过 500 万 chunk 再考虑独立向量库。

---

## 11. 安全与权限

- 所有用户表查询带 `user_id`；admin-api 只读用户业务表。
- 管理员表独立；`SUPER_ADMIN` 不可禁用。
- API Key 存 `system_settings.ai.model.config`，`is_secret=1`，管理端只返回掩码；当前 JSON 原文存库，生产需限制 DB 权限，后续可接 KMS/字段加密。
- `ai_usage` 不存正文；`messages` 存内容，日志不打印完整正文。
- 软删除 + 定期清理；备份加密。
- 数据库账号最小权限：user-api 只写业务表，admin-api 只写管理表 + 读用户表。

---

## 12. 待确认

- [ ] `monthly_cost_quota` 默认值是否用 5 元/月（可后台调）。
- [ ] `articles.content_text` 是否需要在详情接口完整返回，还是单独分页。
- [ ] 评测表是否 V1 就建，还是 V1 用本地脚本（表已按 P1 预留）。
- [ ] 管理员审计日志是否 V1 就启用（当前 P2）。

---

> 下一步：`docs/api.md` 按本文档逐表映射接口；DDL 在 `deploy/sql/schema.sql` 生成后用 `shiye-dev` 审计。
