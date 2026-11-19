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
| 40901 | 409 | CONFLICT | 唯一冲突/重复 |
| 40902 | 409 | IDEMPOTENCY_CONFLICT | 幂等键冲突 |
| 40903 | 409 | IMPORT_ALREADY_RUNNING | 导入任务运行中 |
| 42901 | 429 | RATE_LIMITED | 限流 |
| 42902 | 429 | USER_QUOTA_EXCEEDED | 用户配额超限 |
| 42903 | 429 | GLOBAL_BUDGET_EXCEEDED | 全局预算超限 |
| 42904 | 429 | DAILY_TOKEN_LIMIT | 用户日 token 超限 |
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
| `invitationCode` | string | 否 | P1 |

**响应 `data`**

`TokenResponse`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `accessToken` | string | 用户 access token |
| `refreshToken` | string | refresh token |
| `expiresIn` | number | 秒 |
| `tokenType` | string | `Bearer` |
| `user` | UserSummary | 用户信息 |

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

> 当前认证里程碑先返回基础资料；`quota` 字段随用量记账模块一起实现，前端不要依赖它一定存在。

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
| `shortcut` | string/null | 快捷键 |
| `siteBlacklist` | string[] | 不注入的域名 |

---

#### PUT `/users/me/plugin-settings`

**认证**：用户

**请求**：同响应字段

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
> `POST /articles/collect`、`POST /articles/upload`、`POST /articles/{id}/reindex`、标签与文件夹筛选在后续里程碑接入。
> 标签 `tags`、文件夹 `folders` 字段暂不返回。所有时间字段为 UTC，前端负责本地化显示。

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

**幂等**：建议 `Idempotency-Key`

**表单**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | PDF/Markdown/TXT，≤20MB |
| `title` | string | 否 | 默认文件名 |

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileAssetId` | string | 文件资产 |
| `articleId` | string | 资料 ID |
| `status` | string | PENDING/PARSING |

**错误**：40003、40004、40902、42901、50204

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
| `tagId` | string | 标签 |
| `folderId` | string | 书签文件夹 |
| `from` / `to` | string | ISO 时间 |
| `sort` | string | 默认 `updatedAt,desc` |

**响应**：分页 `ArticleSummary`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | ID |
| `title` | string | 标题 |
| `url` / `domain` | string | 来源 |
| `sourceType` | string | 来源类型 |
| `summary` | string | 摘要 |
| `status` | string | 状态 |
| `wordCount` | number | 字数 |
| `chunkCount` | number | 切片数 |
| `tags` | array | `{id,name,color}` |
| `folders` | array | `{id,path}` |
| `createdAt` / `updatedAt` | string | 时间 |

---

#### GET `/articles/stats`

**认证**：用户

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

#### POST `/articles/{id}/reindex`

**认证**：用户

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `articleId` | string | 资料 ID |
| `status` | string | 固定返回 `PENDING`，后台重新抓取 |

**错误**：40402、40005、40903

> 重新抓取会删除旧切片和 Redis 向量；已有引用记录保留历史引用片段。

---

#### DELETE `/articles/{id}`

**认证**：用户

**响应**：`null`（软删除；异步清理 chunk/vector）

**错误**：40402、40301

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

#### PATCH `/tags/{id}`（P1）

**认证**：用户

**请求**：`name?`、`color?`

**响应**：标签对象

**错误**：40401、40901

---

#### DELETE `/tags/{id}`（P1）

**认证**：用户

**响应**：`null`（移除标签关系，不删资料）

---

#### POST `/articles/{id}/tags`（P1）

**认证**：用户

**请求**：`tagId` 或 `name`（二选一）

**响应**：标签对象

---

#### DELETE `/articles/{id}/tags/{tagId}`（P1）

**认证**：用户

**响应**：`null`

---

### 3.4 问答

> **当前实现**：`POST /chat/stream` 已接入 DeepSeek SSE，支持 `ALL / ARTICLE / FOLDER` 范围；`TAG / CURRENT` 后续接入。
> 引用、usage、会话/消息持久化已接通。默认使用联调 embedding，真实语义检索需配置 BGE-M3。

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
| `scope.type` | string | 是 | ALL/ARTICLE/FOLDER/TAG/CURRENT |
| `scope.id` | string/null | 条件 | ARTICLE/FOLDER/TAG 时必填 |
| `question` | string | 是 | 1~4000 字 |
| `model` | string/null | 否 | 不传用默认 |

**响应**：SSE，事件见 1.9。

**错误（流开始前）**：40001、40101、40302、42902、42903、42904

**错误（流开始后）**：`error` 事件，`retryable` 表示是否可重试。

---

#### POST `/chat/{requestId}/cancel`（P1）

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

#### POST `/messages/{id}/feedback`（P1）

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

> **当前实现**：`GET /usage/quota`、`GET /usage/records` 已接入；`/usage/summary` 的聚合和趋势图在下一阶段实现。
> 现有页面直接展示月度配额 + 调用明细。

#### GET `/usage/summary`

**认证**：用户

**Query**：`range=today|7d|30d`（默认 7d）

**响应**

| 字段 | 类型 | 说明 |
|---|---|---|
| `range` | string | 范围 |
| `tokens` | number | 总 token |
| `cost` | string | 总成本 |
| `calls` | number | 调用数 |
| `avgFirstTokenMs` | number | 平均首 token |
| `avgLatencyMs` | number | 平均总耗时 |
| `errorRate` | number | 0~1 |
| `sourceBreakdown` | array | `{source,calls,tokens,cost}` |
| `sceneBreakdown` | array | `{scene,calls,tokens,cost}` |
| `series` | array | `{date,tokens,cost,calls}` |

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

#### GET `/usage/export`（P1）

**认证**：用户

**Query**：同 `/usage/records`

**响应**：`text/csv`，`Content-Disposition: attachment`。

---

## 4. 管理端 API

> 前缀：`/admin/api/v1`。只接受管理员 token。

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

> **当前实现**：总览、用量列表/汇总/CSV 导出、用户列表与配额/状态、导入任务与条目、索引任务、模型只读配置与连接测试已接入。
> `PUT /models`、`/settings`、`/budget` 和用量趋势聚合仍在后续阶段。

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

**Query**：`page`、`pageSize`、`status?`、`stage?`、`userId?`

**响应**：分页 `Task`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 任务 |
| `articleId` | string | 资料 |
| `userId` | string | 用户 |
| `stage` | string | 阶段 |
| `status` | string | 状态 |
| `retryCount` | number | 重试 |
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
    "provider": "硅基流动",
    "baseUrl": "https://api.siliconflow.cn/v1",
    "model": "BAAI/bge-m3",
    "dimension": 1024,
    "batchSize": 16,
    "apiKeyMasked": "sk-****",
    "inputPrice": "0.100000",
    "currency": "CNY"
  },
  "budget": {
    "globalMonthlyTokens": 2000000,
    "globalMonthlyCost": "20.000000",
    "defaultUserTokens": 200000,
    "alertThreshold": 0.8,
    "rateLimitPerMinute": 10
  }
}
```

---

#### PUT `/models`

**认证**：管理员

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `chat.provider` | string | 是 | |
| `chat.baseUrl` | string | 是 | |
| `chat.model` | string | 是 | |
| `chat.apiKey` | string | 否 | 只写不读；不传=不变 |
| `chat.maxTokens` | number | 否 | 默认 2048 |
| `chat.temperature` | number | 否 | 0~1，默认 0.3 |
| `chat.inputPrice` | string | 是 | 每 1M |
| `chat.outputPrice` | string | 是 | 每 1M |
| `embedding.provider` | string | 是 | |
| `embedding.baseUrl` | string | 是 | |
| `embedding.model` | string | 是 | |
| `embedding.apiKey` | string | 否 | 只写不读 |
| `embedding.dimension` | number | 是 | 1024 |
| `embedding.batchSize` | number | 否 | 默认 16 |
| `embedding.inputPrice` | string | 是 | 每 1M |

**响应**：更新后的配置（key 掩码）

**错误**：40001、50001

---

#### POST `/models/test`

**认证**：管理员

**请求**

```json
{ "type": "CHAT" }
```

`type`：CHAT/EMBEDDING。

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
  "rateLimitPerMinute": 10,
  "dailyTokenLimit": 50000,
  "ragChunkSize": 600,
  "ragChunkOverlap": 80,
  "ragTopK": 8,
  "ragHybridEnabled": false,
  "ragRerankEnabled": false,
  "ragCitationRequired": true
}
```

---

#### PUT `/settings`

**认证**：管理员

**请求**：同响应字段（均可选，包含 RAG 参数）

**响应**：更新后的设置

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

**响应**：预算对象

---

### 4.6 审计与评测（P1/P2）

#### GET `/audit-logs`（P2）

**认证**：管理员

**Query**：`page`、`pageSize`、`adminId?`、`action?`、`from?`、`to?`

**响应**：分页审计日志。

---

#### GET `/eval/cases`（P1）

**认证**：管理员

**Query**：`page`、`pageSize`、`status?`

**响应**：分页评测用例。

---

#### POST `/eval/cases`（P1）

**认证**：管理员

**请求**：`question`、`expectedArticleIds?`、`expectedChunkIds?`、`goldAnswer?`、`tags?`

**响应**：用例。

---

#### POST `/eval/runs`（P1）

**认证**：管理员

**请求**：`name`、`topK?`、`rerank?`、`model?`

**响应**：`{ "runId": "...", "status": "RUNNING" }`

---

#### GET `/eval/runs/{id}`（P1）

**认证**：管理员

**响应**：运行详情 + 指标 + 结果。

---

### 4.7 健康检查

#### GET `/actuator/health`

两个服务均暴露；返回 DB/Redis/队列状态。

- 用户 API：`http://localhost:18080/actuator/health`
- 管理 API：`http://localhost:18081/actuator/health`

---

## 5. 页面 ↔ API 映射

| # | 页面 | 关键接口 |
|---|---|---|
| 01 | 登录/注册 | `POST /auth/register`、`POST /auth/login`、`POST /auth/refresh` |
| 02 | 资料库 | `GET /articles`、`POST /articles/collect`、`POST /articles/upload`、`GET /tags` |
| 03 | 文章详情 | `GET /articles/{id}`、`GET /articles/{id}/chunks`、`POST /articles/{id}/reindex` |
| 04 | 问答 | `POST /chat/stream`、`GET /conversations`、`GET /conversations/{id}/messages` |
| 05 | 用量 | `GET /usage/summary`、`GET /usage/records`、`GET /usage/quota` |
| 06 | 设置 | `GET/PUT /users/me/plugin-settings`、`PUT /users/me/password` |
| 07 | 管理后台-总览 | `GET /admin/api/v1/overview` |
| 08 | 管理后台-用量 | `GET /admin/api/v1/usage`、`GET /admin/api/v1/usage/export` |
| 09 | 管理后台-用户与配额 | `GET /admin/api/v1/users`、`PUT /admin/api/v1/users/{id}/status`、`PUT /admin/api/v1/users/{id}/quota` |
| 10 | 管理后台-书签导入 | `GET /admin/api/v1/imports`、`POST /admin/api/v1/imports/{id}/retry` |
| 11 | 管理后台-索引任务 | `GET /admin/api/v1/tasks`、`POST /admin/api/v1/tasks/{id}/retry` |
| 12 | 管理后台-模型配置 | `GET/PUT /admin/api/v1/models`、`POST /admin/api/v1/models/test`、`PUT /admin/api/v1/budget` |
| 13 | 插件-悬浮球 | `POST /chat/stream`、`POST /articles/collect`、`GET/PUT /users/me/plugin-settings` |
| 14 | 插件-书签导入 | `POST /bookmark-imports`、`GET /bookmark-imports/{id}/items` |
| 15 | 插件-登录授权 | `POST /auth/extension-code`、`POST /auth/extension-token` |
| 16 | Web-插件管理 | `GET/PUT /users/me/plugin-settings`、`POST /auth/extension-code` |
| 17 | 管理后台-登录 | `POST /admin/api/v1/auth/login` |

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
| `/admin/api/v1/auth/*` | 禁止 | 部分公开 | — |
| `/admin/api/v1/overview` | 禁止 | 允许 | — |
| `/admin/api/v1/usage` | 禁止 | 允许 | — |
| `/admin/api/v1/users` | 禁止 | 允许 | — |
| `/admin/api/v1/imports` | 禁止 | 允许 | — |
| `/admin/api/v1/tasks` | 禁止 | 允许 | — |
| `/admin/api/v1/models` | 禁止 | 允许 | — |
| `/admin/api/v1/settings` | 禁止 | 允许 | — |

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

## 9. 待确认

- [ ] 文件上传上限是否 20MB；是否允许 Markdown/TXT 之外的类型。
- [ ] 用户端是否提供 CSV 导出（当前 P1）。
- [ ] 标签手动编辑是否 V1 就开放（当前 P1）。
- [ ] 管理端评测接口是否 V1 就做（当前 P1）。
- [ ] 管理端路径是否统一为 `/admin/api/v1`（本文档采用）。
- [ ] SSE 是否允许客户端传入 `requestId` 用于幂等/重连。

---

> 本文档与 `schema.md` 必须同步修改；接口新增/字段变更未同步文档视为未完成。
