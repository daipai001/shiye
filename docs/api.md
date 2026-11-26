# 拾页（ShiYe）接口文档

| 项目 | 内容 |
|---|---|
| 版本 | **v1.0（V1 基线）** |
| 日期 | 2026-11-12 |
| 用户 API | `http://localhost:18080/api/v1` |
| 管理 API | `http://localhost:18081/admin/api/v1` |
| 内容类型 | `application/json; charset=utf-8` |
| 流式 | `text/event-stream` |
| 关联文档 | `requirements.md` v0.9 / `schema.md` v1.0 / `prototype.html` v0.6 |

> 本文档是接口基线。字段变更必须同步 `schema.md` 与前端类型；破坏性变更升 `/v2`。

---

## 1. 通用约定

### 1.1 响应结构

成功：

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "requestId": "req_01J..."
}
```

失败：

```json
{
  "code": 40001,
  "message": "参数错误",
  "details": [
    { "field": "email", "message": "邮箱格式不正确" }
  ],
  "requestId": "req_01J..."
}
```

- `code`：业务错误码；0 表示成功。
- `message`：给用户看的中文提示。
- `details`：字段级错误（可选）。
- `requestId`：全链路追踪 ID。

### 1.2 数据类型

| 类型 | 约定 |
|---|---|
| ID | 字符串，如 `"1024"`（避免 JS 精度丢失） |
| 时间 | ISO-8601 UTC，如 `2026-11-12T08:00:00.000Z` |
| 金额/单价 | 字符串十进制，如 `"0.003200"` |
| token | 数字 |
| 枚举 | 大写字符串，如 `READY` |
| 布尔 | `true/false` |
| 空值 | `null`，不返回空字符串代替 |

### 1.3 分页

请求：

- `page`：默认 1，最小 1。
- `pageSize`：默认 20，最大 100。

响应：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 128
}
```

排序：

- `sort`：`field,asc|desc`，例如 `updatedAt,desc`。
- 默认排序在接口中注明。

### 1.4 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 是（登录后） | `Bearer <accessToken>` |
| `X-Client` | 否 | `web` / `extension` / `admin` |
| `X-Client-Version` | 否 | 插件/前端版本 |
| `Idempotency-Key` | 写接口建议 | 幂等键，见 1.6 |
| `X-Request-Id` | 否 | 客户端追踪 ID；不传由服务端生成 |

### 1.5 认证与令牌

- 用户 API 只接受用户 token（`aud=user`）。
- 管理 API 只接受管理员 token（`aud=admin`）。
- 两套密钥：`USER_JWT_SECRET` / `ADMIN_JWT_SECRET`。
- Access token 短期（15 分钟），Refresh token 长期（30 天）。
- Refresh 轮换；旧 refresh token 立即撤销。
- 前台 token 访问 `/admin/api/v1/**` → 403；后台 token 访问 `/api/v1/**` → 403。

### 1.6 幂等

需要幂等的写接口：

- `POST /api/v1/bookmark-imports`
- `POST /api/v1/articles/collect`
- `POST /api/v1/articles/upload`

规则：

- 客户端生成 `Idempotency-Key`（UUID/ULID），24 小时内有效。
- 同 key + 同请求体：返回首次响应。
- 同 key + 不同请求体：`40902`。
- 服务端存 `idempotency_keys`；过期清理。

### 1.7 限流

| 接口组 | 限制 |
|---|---|
| 注册/登录 | 10 次/分钟/IP |
| 问答 | 10 次/分钟/用户 |
| 书签导入 | 5 次/分钟/用户 |
| 文件上传 | 10 次/分钟/用户 |
| 管理端 | 60 次/分钟/管理员 |

> 已接入：注册/登录（IP）、问答（用户）、书签导入创建（用户）、文件上传（用户）和管理端操作（管理员）限流。
> 限流失败时降级放行；超限返回 `42901`，并在响应头返回当前窗口信息。

响应头：

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`
- 超限返回 `42901`。

### 1.8 错误码

| code | HTTP | 常量 | 场景 |
|---|---|---|---|
| 0 | 200 | OK | 成功 |
| 40001 | 400 | VALIDATION_ERROR | 参数校验失败 |
| 40002 | 400 | INVALID_URL | URL 非法/不安全 |
| 40003 | 400 | FILE_TOO_LARGE | 文件过大 |
| 40004 | 400 | UNSUPPORTED_FILE_TYPE | 文件类型不支持 |
| 40005 | 400 | INVALID_STATUS_TRANSITION | 状态流转非法 |
| 40101 | 401 | UNAUTHORIZED | 未登录 |
| 40102 | 401 | TOKEN_EXPIRED | token 过期 |
| 40103 | 401 | INVALID_TOKEN_AUDIENCE | token 类型不匹配 |
| 40104 | 401 | REFRESH_REVOKED | refresh token 已撤销 |
| 40105 | 401 | INVALID_AUTH_CODE | 插件授权码无效/过期 |
| 40301 | 403 | FORBIDDEN | 无权限 |
| 40302 | 403 | USER_DISABLED | 用户被禁用 |
| 40303 | 403 | ADMIN_DISABLED | 管理员被禁用 |
| 40401 | 404 | NOT_FOUND | 资源不存在 |
| 40402 | 404 | ARTICLE_NOT_FOUND | 资料不存在 |
| 40403 | 404 | CONVERSATION_NOT_FOUND | 会话不存在 |
| 40404 | 404 | TASK_NOT_FOUND | 任务不存在 |
| 40501 | 405 | METHOD_NOT_ALLOWED | 请求方法不支持 |
| 40901 | 409 | CONFLICT | 唯一冲突/重复 |
| 40902 | 409 | IDEMPOTENCY_CONFLICT | 幂等键冲突 |
| 40903 | 409 | IMPORT_ALREADY_RUNNING | 导入任务运行中 |
| 42901 | 429 | RATE_LIMITED | 限流 |
| 42902 | 429 | USER_QUOTA_EXCEEDED | 用户配额超限 |
| 42903 | 429 | GLOBAL_BUDGET_EXCEEDED | 全局预算超限 |
| 42904 | 429 | DAILY_TOKEN_LIMIT | 用户日 token 超限 |
| 42905 | 429 | TEAM_QUOTA_EXCEEDED | 团队配额超限 |
| 50000 | 500 | INTERNAL_ERROR | 未知错误 |
| 50001 | 500 | DB_ERROR | 数据库错误 |
| 50002 | 500 | QUEUE_ERROR | 队列错误 |
| 50201 | 502 | AI_UNAVAILABLE | 模型不可用 |
| 50202 | 502 | AI_TIMEOUT | 模型超时 |
| 50203 | 502 | EMBEDDING_FAILED | Embedding 失败 |
| 50204 | 502 | PARSE_FAILED | 解析失败 |
| 50205 | 502 | VECTOR_STORE_UNAVAILABLE | 向量库不可用 |
| 50301 | 503 | MAINTENANCE | 维护中 |

### 1.9 SSE 约定

- `Content-Type: text/event-stream; charset=utf-8`
- `Cache-Control: no-cache`
- `X-Accel-Buffering: no`（Nginx）
- 每 15 秒发送 `: ping` 心跳。
- 事件格式：

```
event: delta
data: {"text":"你收藏的资料里..."}

```

事件类型：

| event | data | 说明 |
|---|---|---|
| `meta` | `{requestId, conversationId, messageId}` | 流开始 |
| `delta` | `{text}` | 增量文本 |
| `citation` | `{index, articleId, chunkId, title, quote, score, charStart, charEnd}` | 引用 |
| `usage` | `{promptTokens, completionTokens, totalTokens, cost, currency, estimated}` | 用量 |
| `done` | `{finishReason}` | 结束 |
| `error` | `{code, message, retryable}` | 错误 |

> 启用 BGE-reranker 时，`citation.score` 为 reranker 的 `relevance_score`（0~1），`citation` 顺序即重排后的顺序；未启用或 rerank 失败时回退 RRF 混合检索分数。rerank token 写入 `ai_usage.scene=RERANK`，不影响 SSE 事件结构。

> 问答线程池队列满时会返回 `error`（`code=SERVER_BUSY`, `retryable=true`）后结束。客户端如果收到 EOF 但没有 `done`/`error`，应视为异常中断，不要显示为空回答。

客户端断开：服务端取消上游请求，记录 `ABORTED`。

### 1.10 页面/客户端映射

| 客户端 | API 前缀 | 说明 |
|---|---|---|
| `user-web` | `/api/v1` | 前台 Web |
| `user-extension` | `/api/v1` | 插件，复用用户 token |
| `admin-web` | `/admin/api/v1` | 管理端，独立 token |

### 1.11 CORS

- 用户 API 允许：`http://localhost:15173`、`http://127.0.0.1:15173`、`chrome-extension://<extension-id>`。
- 管理 API 允许：`http://localhost:15174`、`http://127.0.0.1:15174`。
- 生产只填实际域名/扩展 ID 白名单，不要使用 `*`。
- 预检请求允许 `Authorization`、`Content-Type`、`Idempotency-Key`、`X-Client`、`X-Client-Version`。

---

## 2. 认证流程

### 2.1 用户注册/登录

```
注册/登录 → 返回 access + refresh
  → access 放内存/安全存储
  → refresh 安全存储；401 时刷新
  → 刷新成功轮换 refresh
```

### 2.2 插件连接

```
Web 已登录
  → POST /api/v1/auth/extension-code
  → 返回一次性 code（5 分钟）
  → 插件 POST /api/v1/auth/extension-token { code }
  → 返回 access + refresh
  → 插件存 chrome.storage.local
```

### 2.3 管理员登录

```
admin-web → POST /admin/api/v1/auth/login
  → 独立 admin token
  → 只能访问 /admin/api/v1/**
```

### 2.4 邮箱验证与找回密码

```
注册 → 发送验证邮件（MAIL_ENABLED=true 时）
  → 用户打开 /verify-email?token=... → POST /auth/email/verify
  → users.email_verified_at 写入

忘记密码 → POST /auth/password/forgot {email}
  → 若邮箱存在，发送 /reset-password?token=...
  → POST /auth/password/reset {token,newPassword}
  → 撤销该用户全部 refresh token
```

> `MAIL_ENABLED=false` 时不会签发邮件 token，但注册/邀请主流程仍成功；邮件发送失败也不会阻断主流程。token 只存 SHA-256，单次使用。验证邮件默认 24 小时，重置邮件默认 30 分钟。

---

## 3. 用户端 API

### 3.1 认证与账号

#### POST `/auth/register`

**认证**：公开

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | string | 是 | 邮箱，≤190 |
| `password` | string | 是 | 8~72 位，至少字母+数字 |
| `displayName` | string | 否 | ≤64 |
| `invitationCode` | string | 否 | 开启邀请码注册时必填 |

**响应 `data`**

`TokenResponse`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `accessToken` | string | 用户 access token |
| `refreshToken` | string | refresh token |
| `expiresIn` | number | 秒 |
| `tokenType` | string | `Bearer` |
| `user` | UserSummary | 用户信息；含 `emailVerified` |

**错误**：40001、40901、42901

---

#### POST `/auth/login`

**认证**：公开

**请求**：`email`、`password`

**响应**：`TokenResponse`

**错误**：40001、40101、40302

---

#### POST `/auth/refresh`

**认证**：公开（带 refresh token）

**请求**：`refreshToken`

**响应**：`TokenResponse`（轮换后的新 refresh）

**错误**：40102、40104

---

#### POST `/auth/logout`

**认证**：用户

**请求**：`refreshToken`（可选；不传撤销全部当前设备）

**响应**：`null`

---

#### POST `/auth/email/verify`

**认证**：公开

**请求**：`token`

**响应**：`UserSummary`（`emailVerified=true`）

**错误**：40105

---

#### POST `/auth/email/resend`

**认证**：用户

**请求**：无

**响应**：`null`。已配置 SMTP 时发送验证邮件；已验证则静默成功。

---

#### POST `/auth/password/forgot`

**认证**：公开

**请求**：`email`

**响应**：`null`。无论邮箱是否存在都返回成功，避免枚举用户。

---

#### POST `/auth/password/reset`

**认证**：公开

**请求**：`token`、`newPassword`（8~72 位，含字母和数字）

**响应**：`null`。成功后撤销该用户全部 refresh token。

**错误**：40001、40105

---

#### POST `/auth/extension-code`

**认证**：用户

**请求**：无

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | string | 一次性授权码 |
| `expiresIn` | number | 秒（300） |

**错误**：40101、40302、42901

---

#### POST `/auth/extension-token`

**认证**：公开

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | string | 是 | 一次性授权码 |
| `clientInfo` | string | 否 | 插件版本/UA |

**响应**：`TokenResponse`

**错误**：40105、42901

---

#### GET `/users/me`

**认证**：用户

**响应 `data`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 用户 ID |
| `email` | string | 邮箱 |
| `displayName` | string | 显示名 |
| `avatarUrl` | string/null | 头像 |
| `status` | string | ACTIVE/DISABLED |
| `createdAt` | string | 创建时间 |
| `quota` | Quota | 当月配额（用量模块 M3 接入） |

> 已接入账户资料、密码修改和插件偏好持久化；`quota` 通过 `/usage/quota` 获取。

**Quota**

| 字段 | 类型 | 说明 |
|---|---|---|
| `month` | string | `2026-11` |
| `tokenQuota` | number | 月 token 配额 |
| `tokensUsed` | number | 已用 |
| `tokenRemaining` | number | 剩余 |
| `costQuota` | string | 月成本配额 |
| `costUsed` | string | 已用 |
| `costRemaining` | string | 剩余 |
| `resetAt` | string | 下月重置时间 |

---

#### PATCH `/users/me`

**认证**：用户

**请求**：`displayName?`、`avatarUrl?`

**响应**：UserSummary

**错误**：40001

---

#### POST `/users/me/password`

**认证**：用户

**请求**：`oldPassword`、`newPassword`

**响应**：`null`；其他 refresh token 全部撤销。

**错误**：40001、40101

---

#### GET `/users/me/plugin-settings`

**认证**：用户

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `ballEnabled` | boolean | 悬浮球开关 |
| `defaultMode` | string | LIBRARY/CURRENT/GENERAL |
| `defaultScope` | string | ALL/FOLDER/TAG |
| `defaultScopeId` | string/null | FOLDER/TAG 范围对应的文件夹/标签 ID |
| `shortcut` | string/null | 快捷键 |
| `siteBlacklist` | string[] | 不注入的域名 |

---

#### PUT `/users/me/plugin-settings`

**认证**：用户

**请求**：同响应字段

> `defaultScope=FOLDER/TAG` 时必须提供本人名下的 `defaultScopeId`；`ALL` 时忽略。`GENERAL` 为通用问答模式。

**响应**：更新后的设置

**错误**：40001

---

### 3.2 书签文件夹与导入

#### GET `/bookmark-folders`

**认证**：用户

**响应 `data.items`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 主键 |
| `browser` | string | CHROME/EDGE |
| `folderId` | string | 浏览器 folderId |
| `folderPath` | string | 路径 |
| `isPrimary` | boolean | 主文件夹 |
| `autoSync` | boolean | 自动同步 |
| `lastSyncedAt` | string/null | 最后同步 |

---

#### POST `/bookmark-folders`

**认证**：用户

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `browser` | string | 是 | CHROME/EDGE |
| `folderId` | string | 是 | ≤128 |
| `folderPath` | string | 是 | ≤512 |
| `isPrimary` | boolean | 否 | 默认 false |
| `autoSync` | boolean | 否 | 默认 true |

**响应**：文件夹对象

**错误**：40001、40901

---

#### PATCH `/bookmark-folders/{id}`

**认证**：用户

**请求**：`isPrimary?`、`autoSync?`

**响应**：文件夹对象

**错误**：40401、40301

---

#### POST `/bookmark-imports`

**认证**：用户

**幂等**：`Idempotency-Key` 建议必传

**请求**

```json
{
  "folder": {
    "browser": "CHROME",
    "folderId": "1",
    "folderPath": "书签栏/技术文章",
    "recursive": true
  },
  "items": [
    {
      "title": "RAG 切片策略",
      "url": "https://example.com/rag-chunking",
      "parentPath": "书签栏/技术文章/RAG"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `folder` | object | 是 | 文件夹元数据 |
| `folder.browser` | string | 是 | CHROME/EDGE |
| `folder.folderId` | string | 是 | 浏览器 folderId |
| `folder.folderPath` | string | 是 | 路径 |
| `folder.recursive` | boolean | 否 | 默认 true |
| `items` | array | 是 | 1~50 条 |
| `items[].title` | string | 否 | ≤512 |
| `items[].url` | string | 是 | http/https，≤2048 |
| `items[].parentPath` | string | 否 | ≤512 |
| `importId` | string | 否 | 追加到已有的 `PENDING` 导入任务；用于超过 50 条时按批追加 |

**响应 `data`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `importId` | string | 导入任务 ID |
| `status` | string | PENDING/RUNNING |
| `total` | number | 收到条数 |

**错误**：40001、40002、40902、40903、42901

> 服务端按 `(user_id, browser, folderId)` upsert `bookmark_folders`；如需设为主文件夹，先调用 `POST /bookmark-folders`。
>
> **分批与去重**：单请求最多 50 条；更大的文件夹由插件先创建任务，再携带 `importId` 逐批追加。服务端对 URL 做规范化（去锚点、`utm_*` 等追踪参数、尾斜杠）后按 `url_hash` 去重；同一批内重复只保留一条，追加时已存在的条目不会重复创建。
>
> **并发**：不传 `importId` 创建新任务时，同一 `(user_id, browser, folderId)` 已有 `PENDING/RUNNING/PAUSED` 任务返回 `40903`。
>
> **幂等**：`Idempotency-Key` 24 小时内重复提交且请求体一致时返回首次响应；相同 key 不同请求体返回 `40902`。追加批次建议每批使用独立 key。
>
> **当前实现分期**：本阶段创建任务与逐条记录（`PENDING`），抓取、解析、切片、embedding 由后续异步 worker 消费；状态字段已按最终状态机预留。

---

#### GET `/bookmark-imports`

**认证**：用户

**Query**：`page`、`pageSize`、`status?`

**响应**：分页 `ImportSummary`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 任务 ID |
| `folderPath` | string | 文件夹 |
| `status` | string | 状态 |
| `total` / `success` / `failed` / `skipped` | number | 计数 |
| `createdAt` / `finishedAt` | string | 时间 |

---

#### GET `/bookmark-imports/{id}`

**认证**：用户

**响应**：`ImportSummary` + `errorCode`

**错误**：40401、40301

---

#### GET `/bookmark-imports/{id}/items`

**认证**：用户

**Query**：`page`、`pageSize`、`status?`

**响应**：分页 `ImportItem`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 条目 ID |
| `title` | string | 标题 |
| `url` | string | URL |
| `folderPath` | string/null | 子文件夹路径 |
| `status` | string | 状态 |
| `articleId` | string/null | 成功后 |
| `errorCode` / `errorMessage` | string/null | 失败原因 |

---

#### POST `/bookmark-imports/{id}/retry`

**认证**：用户

**请求**：`itemIds?: string[]`（不传=全部失败项）

**响应**：`{ "retried": 2 }`

**错误**：40401、40005、40903

---

#### POST `/bookmark-imports/{id}/pause`

**认证**：用户

**响应**：`{ "status": "PAUSED" }`

**错误**：40401、40005

---

#### POST `/bookmark-imports/{id}/resume`

**认证**：用户

**响应**：`{ "status": "RUNNING" }`

**错误**：40401、40005

---

#### POST `/bookmark-imports/{id}/cancel`

**认证**：用户

**响应**：`{ "status": "CANCELLED" }`

**错误**：40401、40005

---

### 3.3 资料与标签

> **M2b 当前实现范围**：`GET /articles`、`GET /articles/stats`、`GET /articles/{id}`、`GET /articles/{id}/chunks`、`DELETE /articles/{id}`；书签导入 worker 自动抓取/解析/切片/向量化。
> `POST /articles/collect`、`POST /articles/upload`、`POST /articles/{id}/reindex` 已接入；资料 READY 后异步生成 AI 摘要和标签，`tags` 和 `folders` 已在资料列表/详情返回，`tagId` / `folderId` 筛选已接入。
> 上传 PDF 默认用 PDFBox 抽取文本；配置 `OCR_ENABLED=true` 且安装 tesseract 后，扫描版 PDF 会渲染页面并走 OCR。OCR 失败返回 `50204`。
> `GET /tags` 已接入；`tagId` / `folderId` 筛选已接入；`folders` 已返回 `{id,path}`。所有时间字段为 UTC，前端负责本地化显示。

#### POST `/articles/collect`

**认证**：用户

**幂等**：建议 `Idempotency-Key`

**请求**

```json
{
  "mode": "URL",
  "url": "https://example.com/rag",
  "title": "可选标题",
  "content": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `mode` | string | 是 | `URL` / `CURRENT_PAGE` |
| `url` | string | URL 模式必填 | http/https |
| `title` | string | 否 | ≤512 |
| `content` | string | CURRENT_PAGE 模式必填 | 当前页正文，≤2MB |

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `articleId` | string | 资料 ID |
| `status` | string | PENDING/PARSING |

**错误**：40001、40002、40901、40902、42901

---

#### POST `/articles/upload`

**认证**：用户

**Content-Type**：`multipart/form-data`

**表单**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | PDF / Markdown / TXT，≤20MB |
| `title` | string | 否 | 默认取文件名（去扩展名） |

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileAssetId` | string | 文件资产 |
| `articleId` | string | 资料 ID |
| `status` | string | 固定 `READY`（同步解析/切片/向量化完成后返回） |
| `duplicate` | boolean | 同一用户相同文件已存在时为 true |

**错误**：40001、40003、40004、42901、50204

> 文件保存到 `FILE_UPLOAD_DIR`（默认 `./data/uploads`），正文提取后进入现有切片/向量化/摘要标签流程。PDF 解析失败返回 50204；相同 SHA-256 文件直接复用已有资料。

---

#### GET `/articles`

**认证**：用户

**Query**

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` / `pageSize` | number | 分页 |
| `q` | string | 标题/正文关键词 |
| `status` | string | 状态 |
| `sourceType` | string | BOOKMARK/MANUAL/UPLOAD |
| `tagId` | string | 标签；传 `teamId` 时为团队标签 |
| `folderId` | string | 文件夹；传 `teamId` 时为团队文件夹 |
| `teamId` | string | 团队资料；只能传当前用户所在团队 |
| `from` / `to` | string | ISO 时间 |
| `sort` | string | 默认 `updatedAt,desc` |

**响应**：分页 `ArticleSummary`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | ID |
| `teamId` | string/null | 共享到的团队；个人未共享为空 |
| `title` | string | 标题 |
| `url` / `domain` | string | 来源 |
| `sourceType` | string | 来源类型 |
| `summary` | string | 摘要 |
| `status` | string | 状态 |
| `wordCount` | number | 字数 |
| `chunkCount` | number | 切片数 |
| `tags` | array | `{id,name,color}` |
| `folders` | array | `{id,path,articleCount}`；团队资料对应团队文件夹 |
| `createdAt` / `updatedAt` | string | 时间 |

---

#### GET `/articles/stats`

**认证**：用户

**Query**：`teamId?`；传团队 ID 时返回团队资料统计。

**响应 `data`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | number | 资料总数 |
| `ready` | number | 已索引 |
| `processing` | number | 处理中 |
| `failed` | number | 失败 |

---

#### GET `/articles/{id}`

**认证**：用户

**响应**：`ArticleDetail`

```json
{
  "id": "1024",
  "title": "RAG 切片策略",
  "url": "https://example.com/rag",
  "domain": "example.com",
  "sourceType": "BOOKMARK",
  "author": null,
  "publishedAt": null,
  "fetchedAt": "2026-11-12T08:00:00.000Z",
  "summary": "……",
  "contentText": "……",
  "contentTruncated": false,
  "wordCount": 1842,
  "status": "READY",
  "chunkCount": 24,
  "tags": [{"id":"1","name":"RAG","color":"blue"}],
  "folders": [{"id":"1","path":"技术文章/RAG"}],
  "createdAt": "2026-11-12T08:00:00.000Z",
  "updatedAt": "2026-11-12T08:00:00.000Z"
}
```

**错误**：40402、40301

> `contentText` 最大返回 1MB；超过时截断并返回 `contentTruncated=true`。完整正文通过 `chunks` 分页读取，避免单个 JSON 过大。
>
> 当前实现把正文上限设为 500k 字符（`shiye.article.max-text-chars`），因此 `contentTruncated` 暂为 `false`；`publishedAt` 暂未解析，返回 `null`。

---

#### GET `/articles/{id}/chunks`

**认证**：用户

**Query**：`page`、`pageSize`

**响应**：分页 `Chunk`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 切片 ID |
| `chunkIndex` | number | 序号 |
| `content` | string | 内容 |
| `tokenCount` | number | token |
| `charStart` / `charEnd` | number | 原文位置 |
| `embeddingModel` | string | 模型 |

---

#### POST `/articles/{id}/share`

**认证**：资料所有者

**请求**：`{"teamId":"1"}`

**响应**：`null`。把个人资料共享到团队；团队问答可检索，个人库保留副本。已共享到其他团队返回 40901。

---

#### DELETE `/articles/{id}/share`

**认证**：资料所有者或团队 OWNER/ADMIN

**响应**：`null`。取消团队共享，保留个人资料。

---

#### POST `/articles/{id}/reindex`

**认证**：用户（资料所有者）

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `articleId` | string | 资料 ID |
| `status` | string | 固定返回 `PENDING`，后台重新抓取 |

**错误**：40402、40005、40903

> 重新抓取成功后会删除旧切片和 Redis 向量，已有引用记录保留历史引用片段。如果抓取/解析阶段失败，旧正文和切片仍然有效，文章恢复为 `READY`，本次 `ingest_task` 记录为 `FAILED`；Embedding/向量阶段失败才会把文章标记为 `FAILED`。

---

#### DELETE `/articles/{id}`

**认证**：用户

**响应**：`null`（软删除；清理 chunk/vector/标签关系）

**错误**：40402、40301

---

#### POST `/articles/delete-by-url`

**认证**：用户

**请求**

```json
{ "url": "https://example.com/rag" }
```

**说明**：按规范化 URL 查找当前用户的资料并软删除，同时清理切片、向量和标签关系；URL 不存在时直接返回成功（幂等）。插件书签删除同步使用此接口。

**响应**：`null`

---

#### GET `/tags`

**认证**：用户

**响应 `data.items`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 标签 ID |
| `name` | string | 名称 |
| `color` | string/null | 颜色 |
| `articleCount` | number | 资料数 |

---

#### PATCH `/tags/{id}`

**认证**：用户

**请求**：`name?`、`color?`

**响应**：标签对象

**错误**：40401、40901

---

#### DELETE `/tags/{id}`

**认证**：用户

**响应**：`null`（移除标签关系，不删资料）

---

#### POST `/articles/{id}/tags`

**认证**：用户

**请求**：`tagId` 或 `name`（二选一）

**响应**：标签对象

---

#### DELETE `/articles/{id}/tags/{tagId}`

**认证**：用户

**响应**：`null`

---

### 3.4 问答

> **当前实现**：`POST /chat/stream` 已接入 DeepSeek SSE、混合检索和 rerank，支持 `ALL / ARTICLE / FOLDER / TAG / GENERAL / TEAM / TEAM_TAG / TEAM_FOLDER` 范围；`GENERAL` 不检索资料库，直接回答；`TEAM` / `TEAM_TAG` / `TEAM_FOLDER` 只检索对应团队资料。检索 query 会结合最近用户问题做轻量改写，改善多轮追问。`POST /chat/page-stream` 支持插件当前页临时问答。
> 引用、usage、会话/消息持久化已接通；默认使用联调 embedding，真实语义检索需配置 BGE-M3。

#### POST `/chat/stream`

**认证**：用户

**Content-Type**：`application/json`

**请求**

```json
{
  "conversationId": null,
  "scope": { "type": "ALL", "id": null },
  "question": "我收藏的文章里，chunk size 一般建议多大？",
  "model": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string/null | 否 | 不传则新建会话 |
| `scope.type` | string | 是 | ALL/ARTICLE/FOLDER/TAG/GENERAL/TEAM/TEAM_TAG/TEAM_FOLDER |
| `scope.id` | string/null | 条件 | ARTICLE/FOLDER/TAG/TEAM/TEAM_TAG/TEAM_FOLDER 时必填；ALL/GENERAL 为空 |
| `question` | string | 是 | 1~4000 字 |
| `model` | string/null | 否 | 不传用默认 |

**响应**：SSE，事件见 1.9。

**错误（流开始前）**：40001、40101、40302、42902、42903、42904、42905

**错误（流开始后）**：`error` 事件，`retryable` 表示是否可重试。

---

#### POST `/chat/page-stream`

**认证**：用户

**Content-Type**：`application/json`

**说明**：插件当前页面问答。正文只作为本次请求的临时上下文，不写入会话、消息、引用或资料库；usage 记 `scene=CHAT`、`conversation_id=NULL`。

**请求**

```json
{
  "url": "https://example.com/article",
  "title": "页面标题",
  "content": "当前页正文，最多 200000 字符",
  "question": "这个页面主要讲了什么？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `url` | string/null | 否 | ≤2048 |
| `title` | string/null | 否 | ≤512 |
| `content` | string | 是 | 1~200000 字符，服务端截取前 15000 字符送入模型 |
| `question` | string | 是 | 1~4000 字 |

**响应**：SSE，事件见 1.9；`meta.conversationId` / `meta.messageId` 为 `null`，无 `citation` 事件。

**错误（流开始前）**：40001、40101、40302、42902、42903、42904

**错误（流开始后）**：`error` 事件，`retryable` 表示是否可重试。

---

#### POST `/chat/{requestId}/cancel`

**认证**：用户

**响应**：`{ "cancelled": true }`

---

#### GET `/conversations`

**认证**：用户

**Query**：`page`、`pageSize`

**响应**：分页 `Conversation`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 会话 ID |
| `title` | string | 标题 |
| `scopeType` / `scopeId` | string | 范围 |
| `model` | string/null | 模型 |
| `lastMessage` | string/null | 最近消息摘要 |
| `createdAt` / `updatedAt` | string | 时间 |

---

#### GET `/conversations/{id}`

**认证**：用户

**响应**：`Conversation` + `messageCount`

**错误**：40403、40301

---

#### PATCH `/conversations/{id}`

**认证**：用户

**请求**：`title`（≤255）

**响应**：`Conversation`

---

#### DELETE `/conversations/{id}`

**认证**：用户

**响应**：`null`（软删除）

---

#### GET `/conversations/{id}/messages`

**认证**：用户

**Query**：`page`、`pageSize`、`order=asc|desc`（默认 asc）

**响应**：分页 `Message`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 消息 ID |
| `role` | string | USER/ASSISTANT/SYSTEM |
| `content` | string | 内容 |
| `model` | string/null | 模型 |
| `status` | string | SUCCESS/FAILED/ABORTED |
| `promptTokens` / `completionTokens` | number | token |
| `firstTokenMs` / `latencyMs` | number/null | 耗时 |
| `citations` | array | 引用 |
| `createdAt` | string | 时间 |

**Citation**

```json
{
  "rank": 1,
  "articleId": "1024",
  "chunkId": "2048",
  "title": "RAG 切片策略",
  "quote": "单块 500~800 字，重叠 10%~15%",
  "score": 0.87,
  "charStart": 1200,
  "charEnd": 1812
}
```

---

#### POST `/messages/{id}/feedback`

**认证**：用户

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rating` | string | 是 | UP/DOWN |
| `comment` | string | 否 | ≤500 |

**响应**：`null`

> 写入 `message_feedback`，同一用户对同一消息只保留一条，重复提交覆盖。

---

### 3.5 用量

> **当前实现**：`GET /usage/quota`、`GET /usage/records`、`GET /usage/summary` 已接入；用量页展示月度配额、近 7 天趋势和调用明细。
> 聚合按 UTC 日期计算，趋势缺失日期补 0。

#### GET `/usage/summary`

**认证**：用户

**Query**：`days=1..30`（默认 7）

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `from` / `to` | string | UTC 起始/结束日期 |
| `days` | number | 实际聚合天数 |
| `calls` | number | 调用数 |
| `tokens` | number | 总 token |
| `cost` | number | 总成本 |
| `errors` | number | 失败调用数 |
| `scenes` | array | `{label,calls,tokens,cost}`，按 token 降序 |
| `sources` | array | `{label,calls,tokens,cost}`，按 token 降序 |
| `daily` | array | `{date,calls,tokens,cost,errors}`，连续日期补 0 |

---

#### GET `/usage/records`

**认证**：用户

**Query**：`page`、`pageSize`、`source?`、`scene?`、`model?`、`status?`、`from?`、`to?`

**响应**：分页 `UsageRecord`

```json
{
  "id": "1",
  "requestId": "req_...",
  "source": "EXTENSION",
  "scene": "CHAT",
  "model": "deepseek-chat",
  "promptTokens": 1200,
  "completionTokens": 640,
  "totalTokens": 1840,
  "cost": "0.003200",
  "currency": "CNY",
  "latencyMs": 1800,
  "firstTokenMs": 900,
  "status": "SUCCESS",
  "createdAt": "2026-11-12T08:00:00.000Z"
}
```

---

#### GET `/usage/quota`

**认证**：用户

**响应**：`Quota`（见 3.1）

---

#### GET `/usage/export`

**认证**：用户

**Query**：同 `/usage/records`

**响应**：`text/csv`，`Content-Disposition: attachment`。

---

### 3.6 团队

> 团队资料通过 `articles.team_id` 共享；团队问答使用 `scope.type=TEAM`。团队配额与个人配额同时生效。

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/teams` | 用户 | 我加入的团队 |
| GET | `/teams/taxonomy` | 用户 | 我所有团队的标签/文件夹选项（用于团队范围问答） |
| POST | `/teams` | 用户 | 创建团队，创建者为 OWNER |
| GET | `/teams/{id}` | 成员 | 团队详情 + 成员 |
| PUT | `/teams/{id}` | OWNER | 修改名称、token/成本配额 |
| GET | `/teams/{id}/members` | 成员 | 成员列表 |
| POST | `/teams/{id}/members` | OWNER/ADMIN | 按邮箱添加成员；ADMIN 只能加 MEMBER，OWNER 可加 ADMIN |
| POST | `/teams/{id}/invites` | OWNER/ADMIN | 生成单次邀请码（7 天）；ADMIN 只能生成 MEMBER 邀请 |
| POST | `/teams/join` | 用户 | 用邀请码加入团队；邀请码单次使用 |
| PUT | `/teams/{id}/members/{userId}` | OWNER/ADMIN | 修改角色；ADMIN 只能管理 MEMBER |
| DELETE | `/teams/{id}/members/{userId}` | OWNER/ADMIN | 移除成员；不能移除 OWNER |
| POST | `/teams/{id}/leave` | 成员 | 退出团队；OWNER 不能退出 |
| POST | `/teams/{id}/transfer` | OWNER | 转让所有权；目标必须是 ACTIVE 成员，原 OWNER 变为 ADMIN |
| DELETE | `/teams/{id}` | OWNER | 解散团队；共享资料回到各自个人库，成员和邀请码清理 |
| GET/POST | `/teams/{id}/tags` | 成员 | 团队标签列表/创建 |
| PUT/DELETE | `/teams/{id}/tags/{tagId}` | OWNER/ADMIN | 更新/删除团队标签 |
| GET/POST | `/teams/{id}/folders` | 成员 | 团队文件夹列表/创建 |
| PUT/DELETE | `/teams/{id}/folders/{folderId}` | OWNER/ADMIN | 更新/删除团队文件夹 |
| POST/DELETE | `/articles/{id}/team-tags`、`/articles/{id}/team-tags/{tagId}` | 成员 | 给团队资料加/移除团队标签 |
| POST/DELETE | `/articles/{id}/team-folders`、`/articles/{id}/team-folders/{folderId}` | 成员 | 给团队资料加/移除团队文件夹 |

**Team 字段**：`id`、`name`、`ownerUserId`、`myRole`、`status`、`memberCount`、`monthlyTokenQuota`、`monthlyCostQuota`、`createdAt`。

**TeamMember 字段**：`userId`、`email`、`displayName`、`role`、`status`、`joinedAt`。

**说明**

- `POST /teams/{id}/invites` 返回 `{code, role, expiresAt}`；请求可带可选 `email`，配置 SMTP 后会发送邀请邮件。`code` 只返回一次，数据库只存 SHA-256。
- `POST /teams/join` 请求 `{code}`，返回团队详情；无效/过期/已使用返回 `40105`，已在团队返回 `40901`。
- `POST /teams/{id}/transfer` 请求 `{userId}`；转让后原 OWNER 改为 ADMIN，目标成员改为 OWNER。
- `DELETE /teams/{id}` 仅 OWNER；解散会把 `articles.team_id` 置空，清除团队成员、邀请码、团队标签/文件夹和团队向量集合，团队软删除。
- 团队成员可以创建标签/文件夹并给资料添加；重命名、删除需要 OWNER/ADMIN。
- 团队标签/文件夹只作用于团队资料；`GET /articles?teamId=...&tagId=...&folderId=...` 的 `tagId`/`folderId` 对应团队标签/文件夹 ID。
- 资料详情和团队列表返回的 `tags`/`folders` 对团队资料来自团队标签/文件夹。
- 团队范围问答支持 `TEAM`（整个团队）、`TEAM_TAG`（按团队标签）、`TEAM_FOLDER`（按团队文件夹）；`scope.id` 分别为团队 ID、团队标签 ID、团队文件夹 ID。
- `GET /teams/taxonomy` 返回 `{tags:[{id,name,color,teamId,teamName}], folders:[{id,name,teamId,teamName}]}`，供问答范围下拉使用。
- 团队问答会同时检查个人配额、团队月配额和全局预算；团队配额超限返回 `42905`。TEAM_TAG/TEAM_FOLDER 会先解析所属团队再检查配额。
- 每次流式问答开始前会预留个人配额（默认 8,000 tokens / ¥0.06）、全站预算额度和团队问答的 12,000 tokens / ¥0.12；结束后释放预留，按实际 token/成本记账。并发请求不会同时挤过配额检查；个人失败返回 42902，团队失败返回 42905，全站预算失败返回 42903。
- `ai_usage.team_id` 和 `team_usage_monthly` 记录团队问答/RERANK 用量。
- 资料共享/取消共享接口见 3.3。

---

## 4. 管理端 API

> 前缀：`/admin/api/v1`。只接受管理员 token；除登录/刷新/健康检查外，按角色执行权限矩阵。

### 4.1 管理员认证

#### POST `/auth/login`

**认证**：公开

**请求**：`email`、`password`

**响应**

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "admin": {
    "id": "1",
    "email": "admin@shiye.local",
    "name": "拾页管理员",
    "role": "SUPER_ADMIN"
  }
}
```

**错误**：40001、40101、40303

> `admin.role`：`SUPER_ADMIN` / `OPERATOR` / `VIEWER`。后续请求的角色以数据库当前值为准，不信任 token 中的旧角色。

---

#### POST `/auth/refresh`

**认证**：公开（refresh token）

**请求**：`refreshToken`

**响应**：管理员 token

---

#### POST `/auth/logout`

**认证**：管理员

**请求**：`refreshToken?`

**响应**：`null`

---

#### GET `/auth/me`

**认证**：管理员

**响应**：管理员信息

---

### 4.2 总览与用量

> **当前实现**：总览、用量列表/汇总/CSV 导出、用户列表与配额/状态、导入任务与条目、索引任务、模型在线配置、系统设置/预算在线编辑、Webhook 告警测试、RAG 评测、审计日志已接入。

#### GET `/overview`

**认证**：管理员

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `users` | number | 用户数 |
| `articles` | number | 资料数 |
| `chunks` | number | 切片数 |
| `todayCalls` | number | 今日调用 |
| `todayTokens` | number | 今日 token |
| `todayCost` | string | 今日成本 |
| `errorRate` | number | 错误率 |
| `p95LatencyMs` | number | p95 延迟 |
| `trends` | array | `{date,calls,tokens,cost}` |
| `sourceSplit` | array | `{source,calls,tokens,cost}` |
| `recentFailures` | array | `{type, resourceId, userName, errorCode, errorMessage, createdAt}` |

`recentFailures[].type`：`IMPORT` / `INGEST` / `AI`。

---

#### GET `/usage`

**认证**：管理员

**Query**：`page`、`pageSize`、`userId?`、`source?`、`scene?`、`model?`、`status?`、`from?`、`to?`

**响应**

```json
{
  "items": [UsageRecord],
  "page": 1,
  "pageSize": 20,
  "total": 100,
  "summary": {
    "tokens": 1280000,
    "cost": "4.20",
    "calls": 86,
    "errorRate": 0.012,
    "sourceSplit": [
      {"source":"WEB","tokens":793600,"cost":"2.60"},
      {"source":"EXTENSION","tokens":486400,"cost":"1.60"}
    ],
    "modelSplit": [
      {"model":"deepseek-chat","tokens":980000,"cost":"3.12"},
      {"model":"bge-m3","tokens":300000,"cost":"1.08"}
    ]
  }
}
```

---

#### GET `/usage/export`

**认证**：管理员

**Query**：同 `/usage`

**响应**：`text/csv`。

---

### 4.3 用户与配额

#### GET `/users`

**认证**：管理员

**Query**：`page`、`pageSize`、`q?`、`status?`、`sort?`

**响应**

```json
{
  "items": [
    {
      "id": "1",
      "email": "jt@example.com",
      "displayName": "JT",
      "status": "ACTIVE",
      "role": "USER",
      "monthlyTokenQuota": 200000,
      "tokensUsed": 128400,
      "monthlyCostQuota": "5.000000",
      "costUsed": "3.42",
      "articleCount": 128,
      "lastLoginAt": "2026-11-12T08:00:00.000Z",
      "createdAt": "2026-10-01T08:00:00.000Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 12
}
```

> 用户侧 `role` 在 V1 固定为 `USER`；管理员在 `admin_users`，不混在 `users` 表。

---

#### GET `/users/{id}`

**认证**：管理员

**响应**：用户详情 + 配额 + 最近用量 + 导入/任务统计。

**错误**：40401

---

#### PUT `/users/{id}/status`

**认证**：管理员

**请求**：`status`（ACTIVE/DISABLED）

**响应**：更新后的用户

**错误**：40401、40005（用户不存在/状态非法）

---

#### PUT `/users/{id}/quota`

**认证**：管理员

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `monthlyTokenQuota` | number | 是 | ≥1000 |
| `monthlyCostQuota` | string | 是 | ≥0 |

**响应**：Quota

**错误**：40401、40001

---

#### PUT `/users/{id}/password`

**认证**：管理员

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `newPassword` | string | 是 | 8~72 位，需同时包含字母和数字 |

**响应**：`null`

> 重置后该用户全部 refresh token 撤销，需要重新登录。操作写入审计日志 `USER_PASSWORD_RESET`。

**错误**：40401、40001

---

### 4.4 书签导入与索引任务

#### GET `/imports`

**认证**：管理员

**Query**：`page`、`pageSize`、`userId?`、`status?`

**响应**：分页 `ImportAdminSummary`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 任务 |
| `userId` / `userEmail` | string | 用户 |
| `folderPath` | string | 文件夹 |
| `status` | string | 状态 |
| `total` / `success` / `failed` / `skipped` | number | 计数 |
| `createdAt` / `finishedAt` | string | 时间 |

---

#### GET `/imports/{id}`

**认证**：管理员

**响应**：任务详情 + 错误统计。

---

#### GET `/imports/{id}/items`

**认证**：管理员

**Query**：`page`、`pageSize`、`status?`

**响应**：分页导入项；字段同用户端 `ImportItem`，额外返回 `userId`。

---

#### POST `/imports/{id}/retry`

**认证**：管理员

**请求**：`itemIds?`

**响应**：`{ "retried": 2 }`

---

#### POST `/imports/{id}/pause`

**认证**：管理员

**响应**：`{ "status": "PAUSED" }`

**错误**：40401、40005

---

#### POST `/imports/{id}/resume`

**认证**：管理员

**响应**：`{ "status": "RUNNING" }`

**错误**：40401、40005

---

#### POST `/imports/{id}/cancel`

**认证**：管理员

**响应**：`{ "status": "CANCELLED" }`

**错误**：40401、40005

---

#### GET `/tasks`

**认证**：管理员

**Query**：`page`、`pageSize`、`status?`、`userId?`

**响应**：分页书签导入任务（`bookmark_items`）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 书签条目 ID |
| `articleId` | string | 资料 |
| `userId` | string | 用户 |
| `stage` | string | 阶段（书签任务等于导入状态） |
| `sourceType` | string | `BOOKMARK` |
| `status` | string | 状态 |
| `retryCount` | number | 重试次数 |
| `errorCode` / `errorMessage` | string/null | 错误 |
| `createdAt` / `finishedAt` | string | 时间 |

---

#### GET `/tasks/{id}`

**认证**：管理员

**响应**：任务详情。

---

#### POST `/tasks/{id}/retry`

**认证**：管理员

**响应**：`{ "status": "PENDING" }`

**错误**：40404、40005

---

#### GET `/ingest-tasks`

**认证**：管理员

**Query**：`page`、`pageSize`、`status?`、`userId?`

**响应**：分页资料入库任务（`ingest_tasks`）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 入库任务 ID |
| `articleId` | string | 资料 |
| `userId` | string | 用户 |
| `title` / `url` | string/null | 资料标题和 URL |
| `sourceType` | string | BOOKMARK / MANUAL / UPLOAD / REINDEX |
| `stage` | string | FETCH / PARSE / CHUNK / EMBED / INDEX / DONE |
| `status` | string | RUNNING / SUCCESS / FAILED / DEAD |
| `retryCount` | number | 重试次数 |
| `errorCode` / `errorMessage` | string/null | 错误 |
| `createdAt` / `finishedAt` | string | 时间 |

> 入库任务当前用于管理端排查和观测；失败重试通过重新上传或在资料库重建索引完成。

---

### 4.5 模型与配置

#### GET `/models`

**认证**：管理员

**响应**

```json
{
  "chat": {
    "provider": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/v1",
    "model": "deepseek-chat",
    "apiKeyMasked": "sk-****",
    "maxTokens": 2048,
    "temperature": 0.3,
    "inputPrice": "1.000000",
    "outputPrice": "2.000000",
    "currency": "CNY"
  },
  "embedding": {
    "provider": "http",
    "baseUrl": "https://api.siliconflow.cn/v1",
    "model": "BAAI/bge-m3",
    "dimension": 1024,
    "batchSize": 16,
    "apiKeyMasked": "sk-****",
    "inputPrice": "0.000000",
    "currency": "CNY"
  },
  "rerank": {
    "enabled": true,
    "baseUrl": "https://api.siliconflow.cn/v1",
    "model": "BAAI/bge-reranker-v2-m3",
    "apiKeyMasked": "sk-****",
    "configured": true
  },
  "budget": {
    "globalMonthlyTokens": 2000000,
    "globalMonthlyCost": "20.000000",
    "defaultUserTokens": 200000,
    "defaultUserCost": "5.000000"
  },
  "source": "DATABASE"
}
```

> `source=DATABASE` 表示存在 `system_settings.ai.model.config` 覆盖值；`ENV` 表示仍使用服务端环境变量。API Key 只返回掩码。

---

#### PUT `/models`

**认证**：管理员

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `chat.baseUrl` | string | 是 | http/https |
| `chat.model` | string | 是 | |
| `chat.apiKey` | string | 否 | 只写不读；留空=保持原值 |
| `chat.maxTokens` | number | 是 | 64~8192 |
| `chat.temperature` | number | 是 | 0~2 |
| `chat.inputPrice` | string | 是 | 每 1M |
| `chat.outputPrice` | string | 是 | 每 1M |
| `embedding.baseUrl` | string | 是 | http/https |
| `embedding.model` | string | 是 | |
| `embedding.apiKey` | string | 否 | 只写不读；留空=保持原值 |
| `embedding.dimension` | number | 是 | 8~4096 |
| `embedding.batchSize` | number | 是 | 1~64 |
| `rerank.enabled` | boolean | 是 | |
| `rerank.baseUrl` | string | 否 | 留空用当前值 |
| `rerank.model` | string | 否 | 留空用当前值 |
| `rerank.apiKey` | string | 否 | 只写不读；留空=保持原值 |

> 配置写入 `system_settings.ai.model.config`（JSON，`is_secret=1`）。user-api 缓存 5 秒，保存后最迟 5 秒生效。Embedding provider 仍由启动配置决定，切换 dev/http 需要重启；修改模型或维度后需重建索引。

**响应**：更新后的配置（key 掩码）

**错误**：40001、50001

---

#### POST `/models/test`

**认证**：管理员

**请求**

```json
{ "type": "CHAT" }
```

`type`：CHAT / EMBEDDING / RERANK。

**响应**

```json
{ "ok": true, "latencyMs": 320, "model": "deepseek-chat" }
```

**错误**：50201、50202、50203

---

#### GET `/settings`

**认证**：管理员

**响应**

```json
{
  "registrationEnabled": true,
  "defaultMonthlyTokens": 200000,
  "defaultMonthlyCost": "5.000000",
  "globalMonthlyTokens": 2000000,
  "globalMonthlyCost": "20.000000",
  "alertThreshold": 0.8,
  "chatRateLimitPerMinute": 10,
  "dailyTokenLimit": 0,
  "ragChunkTargetSize": 700,
  "ragChunkOverlap": 100,
  "ragTopK": 8,
  "ragHybridEnabled": true,
  "ragRerankEnabled": true,
  "ragCitationRequired": true,
  "registrationInvitationRequired": false,
  "registrationInvitationCode": null,
  "alertWebhookEnabled": false,
  "alertWebhookUrl": null,
  "source": "DATABASE"
}
```

> `source=DATABASE` 表示存在 `system_settings.system.settings` 覆盖值；`ENV` 表示使用服务端环境变量。

---

#### PUT `/settings`

**认证**：管理员

**请求**：完整设置对象（去掉 `source`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `registrationEnabled` | boolean | 是否开放新用户注册 |
| `defaultMonthlyTokens` | number | 新用户默认月 token 配额 |
| `defaultMonthlyCost` | string | 新用户默认月成本配额 |
| `globalMonthlyTokens` | number | 全站月 token 预算 |
| `globalMonthlyCost` | string | 全站月成本预算 |
| `alertThreshold` | number | 0~1，达到比例发 WARNING，预算用尽发 EXCEEDED |
| `chatRateLimitPerMinute` | number | 问答限流，1~1000 |
| `dailyTokenLimit` | number | 每日 token 上限，0=不限制 |
| `ragChunkTargetSize` | number | 切片目标字数，200~2000 |
| `ragChunkOverlap` | number | 切片重叠字数，0~1000 |
| `ragTopK` | number | 检索 topK，1~20 |
| `ragHybridEnabled` | boolean | 向量 + 关键词混合检索 |
| `ragRerankEnabled` | boolean | RAG rerank 开关（模型配置中的 rerank 也要开启） |
| `ragCitationRequired` | boolean | 是否强制模型输出引用编号 |
| `registrationInvitationRequired` | boolean | 注册是否需要邀请码 |
| `registrationInvitationCode` | string/null | 邀请码；为空时关闭邀请校验 |
| `alertWebhookEnabled` | boolean | 是否启用预算告警 Webhook |
| `alertWebhookUrl` | string/null | http/https Webhook URL，最长 512 |

**响应**：更新后的设置（含 `source`）

> 保存到 `system_settings.system.settings`，user-api 5 秒内生效。注册开关和默认配额只影响之后注册的新用户；全站预算、问答限流、topK、混合检索、rerank、强制引用和每日上限作用于之后的 AI 调用。chunk 字数/重叠只影响之后新入库或重新索引的资料。

**错误**：40001

---

#### POST `/settings/alert-test`

**认证**：管理员

**请求**：无

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `ok` | boolean | 是否发送成功（2xx） |
| `status` | number/null | Webhook 返回的 HTTP 状态码 |
| `message` | string | 结果说明 |

> 使用当前已保存的 `alertWebhookUrl` 发送一条 `event=ALERT_TEST` 的 JSON；未启用或未配置 URL 时返回 `ok=false`。操作会写入审计日志 `ALERT_WEBHOOK_TEST`。

**告警正文**

| 字段 | 类型 | 说明 |
|---|---|---|
| `event` | string | `BUDGET_ALERT` |
| `level` | string | `WARNING` / `EXCEEDED` |
| `month` | string | `YYYY-MM`（UTC） |
| `tokens` / `cost` | number | 当月全站已用 |
| `tokenBudget` / `costBudget` | number | 当月预算 |
| `tokenRatio` / `costRatio` | number | 已用比例，4 位小数 |
| `alertThreshold` | number | 当前告警阈值 |
| `message` | string | 中文说明 |
| `sentAt` | string | UTC ISO 时间 |

> 同一月份同一级别只发送一次；发送失败不写去重键，下个调度周期重试。

**错误**：40001

---

#### PUT `/budget`

**认证**：管理员

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `globalMonthlyTokens` | number | 是 | |
| `globalMonthlyCost` | string | 是 | |
| `alertThreshold` | number | 否 | 0~1 |

**响应**：更新后的完整系统设置

**错误**：40001

---

### 4.6 审计与评测

#### GET `/audit-logs`

**认证**：管理员

**Query**：`page`、`pageSize`、`adminId?`、`action?`、`from?`、`to?`

**响应**：分页审计日志。

> `action` 常见值：模型/设置/用户/导入/任务/评测操作，以及 `ADMIN_CREATE`、`ADMIN_UPDATE`、`ADMIN_STATUS_UPDATE`、`ADMIN_PASSWORD_RESET`。
>
> `GET /audit-logs/export` 支持同样的 `adminId?`、`action?`、`from?`、`to?` 筛选，返回 CSV（最多 10000 条）。
>
> 保留策略：admin-api 每日按 `AUDIT_RETENTION_DAYS`（默认 180 天）清理旧审计日志。

---

#### GET `/eval/cases`

**认证**：管理员

**Query**：`page`、`pageSize`、`userId?`、`status?`

**响应**：分页评测用例。

---

#### POST `/eval/cases`

**认证**：管理员

**请求**：`userId`（必填）、`question`、`expectedArticleIds?`、`expectedChunkIds?`、`goldAnswer?`、`tags?`、`status?`

**响应**：用例。

---

#### GET `/eval/runs`

**认证**：管理员

**Query**：`page`、`pageSize`、`userId?`

**响应**：分页评测运行（状态 PENDING/RUNNING/DONE）。

---

#### POST `/eval/runs`

**认证**：管理员

**请求**：`userId`（必填）、`name`、`topK?`、`rerank?`、`model?`

**响应**：`{ "id": "...", "status": "PENDING" }`

> 运行由 user-api 的评测 worker 异步消费：复用线上检索链路，生成回答并计算 recall@5、引用准确率、LLM-as-judge 答案正确率、平均延迟和成本；`model` 留空时使用运行时 Chat 模型。

---

#### GET `/eval/runs/{id}`

**认证**：管理员

**响应**：运行详情 + 指标 + 结果。

运行指标字段：`cases`、`scoredCases`、`avgRecallAt5`、`citationScoredCases`、`avgCitationAccuracy`、`correctnessScoredCases`、`avgAnswerCorrectness`、`avgLatencyMs`、`totalCost`、`model`、`topK`。

结果字段：`caseId`、`question`、`retrievedChunkIds`、`recallAt5`、`citationAccuracy`、`answerCorrectness`、`answerText`、`judgeReason`、`latencyMs`、`cost`。

> 引用准确率 = 回答引用来源中命中期望切片/资料的比例；未提供 `goldAnswer` 时 `answerCorrectness` 为空。评测调用以 `source=ADMIN`、`scene=EVAL` 记入 `ai_usage` 和用户当月用量。

---

### 4.7 健康检查

#### GET `/actuator/health`

两个服务均暴露；返回 DB/Redis/队列状态。

- 用户 API：`http://localhost:18080/actuator/health`
- 管理 API：`http://localhost:18081/actuator/health`

---

### 4.8 管理员账号与角色

> 仅 `SUPER_ADMIN` 可访问以下接口。管理端每次请求都会从数据库读取当前角色和状态，降权或禁用立即生效。

角色：

| 角色 | 权限 |
|---|---|
| `SUPER_ADMIN` | 全部；包括管理员、模型配置、系统设置 |
| `OPERATOR` | 总览、用量、用户、导入、任务、评测、审计的读写；不能访问管理员/模型/系统设置/预算 |
| `VIEWER` | 上述业务模块只读；不能执行 POST/PUT/DELETE |

> 管理端登录和刷新按 IP 限流（默认 10 次/分钟），超限返回 42901；管理端操作另按管理员限流（默认 60 次/分钟）。

#### GET `/admins`

**认证**：`SUPER_ADMIN`

**响应**

```json
[
  {
    "id": "1",
    "email": "admin@shiye.local",
    "name": "拾页管理员",
    "role": "SUPER_ADMIN",
    "status": "ACTIVE",
    "lastLoginAt": "2026-11-20T06:00:00.000Z",
    "createdAt": "2026-10-01T08:00:00.000Z",
    "updatedAt": "2026-11-20T06:00:00.000Z"
  }
]
```

---

#### POST `/admins`

**认证**：`SUPER_ADMIN`

**请求**：`email`、`name`、`role`（SUPER_ADMIN/OPERATOR/VIEWER）、`password`（8-72 位，含字母和数字）

**响应**：创建后的管理员。

**错误**：40001、40901（邮箱已存在）

---

#### PUT `/admins/{id}`

**认证**：`SUPER_ADMIN`

**请求**：`name`、`role`

**响应**：更新后的管理员。

**错误**：40001、40401、40901（不能修改自己的角色，或移除最后一名启用中的超级管理员）

---

#### PUT `/admins/{id}/status`

**认证**：`SUPER_ADMIN`

**请求**：`status`（ACTIVE/DISABLED）

**响应**：更新后的管理员。禁用会撤销该管理员全部 refresh token。

**错误**：40001、40401、40901（不能禁用自己，或禁用最后一名启用中的超级管理员）

---

#### PUT `/admins/{id}/password`

**认证**：`SUPER_ADMIN`

**请求**：`newPassword`（8-72 位，含字母和数字）

**响应**：`null`。会撤销该管理员全部 refresh token。

**错误**：40001、40401

---

### 4.9 团队与配额

#### GET `/admin/api/v1/teams`

**认证**：管理员（所有角色可读）

**Query**：`page`、`pageSize`、`q?`（团队名/所有者邮箱）、`status?`

**响应**

```json
{
  "items": [
    {
      "id": "1",
      "name": "内容研究组",
      "ownerUserId": "12",
      "ownerEmail": "owner@example.com",
      "status": "ACTIVE",
      "memberCount": 4,
      "monthlyTokenQuota": 1000000,
      "tokensUsed": 128400,
      "monthlyCostQuota": "20.000000",
      "costUsed": "1.4200",
      "calls": 36,
      "createdAt": "2026-11-20T08:00:00.000Z"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

---

#### PUT `/admin/api/v1/teams/{id}/quota`

**认证**：`SUPER_ADMIN` / `OPERATOR`

**请求**：`monthlyTokenQuota`、`monthlyCostQuota`

**响应**：更新后的团队。

**错误**：40001、40401

> 写入审计 `TEAM_QUOTA_UPDATE`。团队问答同时受个人、团队和全局预算限制。

---

## 5. 页面 ↔ API 映射

| # | 页面 | 关键接口 |
|---|---|---|
| 01 | 登录/注册 | `POST /auth/register`、`POST /auth/login`、`POST /auth/refresh` |
| 02 | 资料库 | `GET /articles`、`POST /articles/collect`、`POST /articles/upload`、`GET /tags` |
| 03 | 文章详情 | `GET /articles/{id}`、`GET /articles/{id}/chunks`、`POST /articles/{id}/reindex` |
| 04 | 问答 | `POST /chat/stream`、`GET /conversations`、`GET /conversations/{id}/messages` |
| 05 | 用量 | `GET /usage/summary`、`GET /usage/records`、`GET /usage/quota` |
| 06 | 设置 | `GET/PUT /users/me/plugin-settings`、`PATCH /users/me`、`POST /users/me/password` |
| 07 | 管理后台-总览 | `GET /admin/api/v1/overview` |
| 08 | 管理后台-用量 | `GET /admin/api/v1/usage`、`GET /admin/api/v1/usage/export` |
| 09 | 管理后台-用户与配额 | `GET /admin/api/v1/users`、`PUT /admin/api/v1/users/{id}/status`、`PUT /admin/api/v1/users/{id}/quota`、`PUT /admin/api/v1/users/{id}/password` |
| 10 | 管理后台-书签导入 | `GET /admin/api/v1/imports`、`POST /admin/api/v1/imports/{id}/retry` |
| 11 | 管理后台-索引任务 | `GET /admin/api/v1/tasks`、`POST /admin/api/v1/tasks/{id}/retry`、`GET /admin/api/v1/ingest-tasks` |
| 12 | 管理后台-模型配置 | `GET/PUT /admin/api/v1/models`、`POST /admin/api/v1/models/test` |
| 13 | 管理后台-系统设置 | `GET/PUT /admin/api/v1/settings`、`PUT /admin/api/v1/budget`、`POST /admin/api/v1/settings/alert-test` |
| 14 | 管理后台-RAG 评测 | `GET/POST /admin/api/v1/eval/cases`、`GET/POST /admin/api/v1/eval/runs`、`GET /admin/api/v1/eval/runs/{id}` |
| 15 | 管理后台-审计日志 | `GET /admin/api/v1/audit-logs` |
| 16 | 插件-悬浮球 | `POST /chat/stream`、`POST /chat/page-stream`、`POST /articles/collect`、`GET/PUT /users/me/plugin-settings` |
| 17 | 插件-书签导入 | `POST /bookmark-imports`、`GET /bookmark-imports/{id}/items` |
| 18 | 插件-登录授权 | `POST /auth/extension-code`、`POST /auth/extension-token` |
| 19 | Web-插件管理 | `GET/PUT /users/me/plugin-settings`、`POST /auth/extension-code` |
| 20 | 管理后台-登录 | `POST /admin/api/v1/auth/login` |
| 21 | 管理后台-管理员 | `GET/POST /admin/api/v1/admins`、`PUT /admin/api/v1/admins/{id}`、`PUT /admin/api/v1/admins/{id}/status`、`PUT /admin/api/v1/admins/{id}/password` |
| 22 | Web-团队 | `GET/POST /teams`、`GET/PUT/DELETE /teams/{id}`、`GET/POST /teams/{id}/members`、`PUT/DELETE /teams/{id}/members/{userId}`、`POST /teams/{id}/leave`、`POST /teams/{id}/invites`、`POST /teams/join`、`POST /teams/{id}/transfer`、团队标签/文件夹 CRUD |
| 23 | Web-团队资料 | `GET /articles?teamId=&tagId=&folderId=`、`POST/DELETE /articles/{id}/share`、团队标签/文件夹关联、`POST /chat/stream`（TEAM/TEAM_TAG/TEAM_FOLDER）、`GET /teams/taxonomy` |
| 24 | 管理后台-团队与配额 | `GET /admin/api/v1/teams`、`PUT /admin/api/v1/teams/{id}/quota` |

---

## 6. 权限矩阵

| 接口组 | 用户 token | 管理员 token | 归属校验 |
|---|---|---|---|
| `/api/v1/auth/*` | 部分公开 | 禁止 | — |
| `/api/v1/users/me/*` | 允许 | 禁止 | 当前用户 |
| `/api/v1/bookmark-*` | 允许 | 禁止 | user_id |
| `/api/v1/articles/*` | 允许 | 禁止 | user_id |
| `/api/v1/chat/*` | 允许 | 禁止 | user_id |
| `/api/v1/usage/*` | 允许 | 禁止 | user_id |
| `/api/v1/teams/*` | 允许 | 禁止 | 团队成员 + 角色 |
| `/admin/api/v1/auth/*` | 禁止 | 部分公开 | — |
| `/admin/api/v1/overview` | 禁止 | 允许 | — |
| `/admin/api/v1/usage` | 禁止 | 允许 | — |
| `/admin/api/v1/users` | 禁止 | 允许 | — |
| `/admin/api/v1/imports` | 禁止 | 允许 | — |
| `/admin/api/v1/tasks` | 禁止 | 允许 | — |
| `/admin/api/v1/models` | 禁止 | 仅 `SUPER_ADMIN` | — |
| `/admin/api/v1/settings` | 禁止 | 仅 `SUPER_ADMIN` | — |
| `/admin/api/v1/budget` | 禁止 | 仅 `SUPER_ADMIN` | — |
| `/admin/api/v1/admins` | 禁止 | 仅 `SUPER_ADMIN` | — |
| `/admin/api/v1/teams` | 禁止 | 所有角色可读，`SUPER_ADMIN` / `OPERATOR` 可改配额 | — |
| 其他 `/admin/api/v1/*` | 禁止 | `SUPER_ADMIN` / `OPERATOR` 读写，`VIEWER` 只读 | — |

---

## 7. 幂等、重试与并发

- `POST /bookmark-imports`、`POST /articles/collect`、`POST /articles/upload` 建议 `Idempotency-Key`。
- 重试接口幂等：重复调用不会创建重复任务；返回当前状态。
- 状态流转校验：非法流转返回 `40005`。
- 并发导入同一文件夹：返回 `40903`。
- 软删除资料重复删除：幂等返回成功。
- 分页查询使用稳定排序（`updatedAt,id` 或 `createdAt,id`），避免翻页重复/遗漏。

---

## 8. 示例

### 8.1 注册

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "jt@example.com",
  "password": "Shiye@2026!",
  "displayName": "JT"
}
```

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "rt_...",
    "expiresIn": 900,
    "tokenType": "Bearer",
    "user": {
      "id": "1",
      "email": "jt@example.com",
      "displayName": "JT",
      "status": "ACTIVE",
      "createdAt": "2026-11-12T08:00:00.000Z",
      "quota": {
        "month": "2026-11",
        "tokenQuota": 200000,
        "tokensUsed": 0,
        "tokenRemaining": 200000,
        "costQuota": "5.000000",
        "costUsed": "0.000000",
        "costRemaining": "5.000000",
        "resetAt": "2026-12-01T00:00:00.000Z"
      }
    }
  },
  "requestId": "req_01J..."
}
```

### 8.2 书签导入

```http
POST /api/v1/bookmark-imports
Authorization: Bearer <user-token>
Idempotency-Key: 01J8Z...
Content-Type: application/json
```

```json
{
  "folder": {
    "browser": "CHROME",
    "folderId": "1",
    "folderPath": "书签栏/技术文章",
    "recursive": true
  },
  "items": [
    {
      "title": "RAG 切片策略",
      "url": "https://example.com/rag-chunking",
      "parentPath": "书签栏/技术文章/RAG"
    }
  ]
}
```

```json
{
  "code": 0,
  "message": "OK",
  "data": { "importId": "1001", "status": "PENDING", "total": 1 },
  "requestId": "req_01J..."
}
```

### 8.3 问答 SSE

```http
POST /api/v1/chat/stream
Authorization: Bearer <user-token>
Content-Type: application/json
Accept: text/event-stream
```

```json
{
  "conversationId": null,
  "scope": { "type": "ALL", "id": null },
  "question": "我收藏的文章里，chunk size 一般建议多大？"
}
```

```
event: meta
data: {"requestId":"req_01J...","conversationId":"2001","messageId":"3001"}

event: delta
data: {"text":"你收藏的资料里主要有三种倾向："}

event: citation
data: {"index":1,"articleId":"1024","chunkId":"2048","title":"RAG 切片策略","quote":"单块 500~800 字，重叠 10%~15%","score":0.87,"charStart":1200,"charEnd":1812}

event: usage
data: {"promptTokens":1200,"completionTokens":640,"totalTokens":1840,"cost":"0.003200","currency":"CNY","estimated":false}

event: done
data: {"finishReason":"stop"}
```

### 8.4 管理员调整配额

```http
PUT /admin/api/v1/users/1/quota
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{
  "monthlyTokenQuota": 300000,
  "monthlyCostQuota": "8.000000"
}
```

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "month": "2026-11",
    "tokenQuota": 300000,
    "tokensUsed": 128400,
    "tokenRemaining": 171600,
    "costQuota": "8.000000",
    "costUsed": "3.420000",
    "costRemaining": "4.580000",
    "resetAt": "2026-12-01T00:00:00.000Z"
  },
  "requestId": "req_01J..."
}
```

---

## 9. 已确认 / 仍待确认

- [x] 文件上传上限 20MB；支持 PDF / Markdown / TXT。
- [x] 用户端提供 CSV 导出。
- [x] 标签手动编辑已开放。
- [x] 管理端评测接口已实现。
- [x] 管理端路径统一为 `/admin/api/v1`。
- [ ] SSE 是否允许客户端传入 `requestId` 用于幂等/重连（当前由服务端生成）。

---

> 本文档与 `schema.md` 必须同步修改；接口新增/字段变更未同步文档视为未完成。
