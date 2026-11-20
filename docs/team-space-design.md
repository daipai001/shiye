# 团队空间与共享资料库（设计稿）

> 状态：**第一阶段已实现**（2026-11-20）：`teams` / `team_members` / `team_invites` / `team_usage_monthly`、团队角色、成员管理、单次邀请码、资料共享、团队问答、团队配额和个人/团队双配额、团队解散。尚未实现：团队标签/文件夹、团队资料所有权转移、后台团队用量页。

## 1. 目标

- 一个用户可加入多个团队；团队内共享资料库、标签、文件夹和会话。
- 团队角色：`OWNER`、`ADMIN`、`MEMBER`。
- 团队配额：团队池 + 成员上限，避免共享后绕过个人配额。
- 保留现有个人空间：个人资料不自动进入团队；需要显式共享或导入到团队。

## 2. 非目标

- 不做实时协同编辑。
- 不做跨团队公开链接（后续可加只读分享）。
- 不在本次迁移中改变前台/后台 token 体系。

## 3. 数据模型

### 3.1 `teams`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | 主键 |
| `name` | VARCHAR(64) | 团队名 |
| `owner_user_id` | BIGINT UNSIGNED | 所有者，`users.id` |
| `status` | VARCHAR(16) | ACTIVE / DISABLED |
| `monthly_token_quota` | BIGINT UNSIGNED | 团队月 token 配额 |
| `monthly_cost_quota` | DECIMAL(12,6) | 团队月成本配额 |
| `created_at` / `updated_at` / `deleted_at` | DATETIME(3) | 时间与软删除 |

索引：`idx_teams_owner (owner_user_id)`、`idx_teams_status (status)`。

### 3.2 `team_members`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | 主键 |
| `team_id` | BIGINT UNSIGNED | `teams.id` |
| `user_id` | BIGINT UNSIGNED | `users.id` |
| `role` | VARCHAR(16) | OWNER / ADMIN / MEMBER |
| `status` | VARCHAR(16) | ACTIVE / DISABLED |
| `joined_at` | DATETIME(3) | 加入时间 |
| `created_at` / `updated_at` | DATETIME(3) | 时间 |

索引：`UNIQUE uk_team_members (team_id, user_id)`、`idx_team_members_user (user_id, status)`。

约束：每个团队至少一名 ACTIVE OWNER；`teams.owner_user_id` 必须与 OWNER 成员一致。

### 3.3 `team_invites`（可选，第一版可先不做）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | 主键 |
| `team_id` | BIGINT UNSIGNED | 团队 |
| `inviter_user_id` | BIGINT UNSIGNED | 邀请人 |
| `email` | VARCHAR(190) | 被邀请邮箱 |
| `role` | VARCHAR(16) | ADMIN / MEMBER |
| `token_hash` | CHAR(64) | 邀请 token 哈希 |
| `expires_at` | DATETIME(3) | 过期时间 |
| `accepted_at` / `revoked_at` | DATETIME(3) | 接受/撤销 |
| `created_at` | DATETIME(3) | 创建 |

索引：`UNIQUE uk_team_invites_token (token_hash)`、`idx_team_invites_team (team_id, expires_at)`。

### 3.4 业务表归属

给以下表增加可空 `team_id`（NULL = 个人空间）：

- `articles`
- `bookmark_imports`
- `bookmark_folders`
- `article_folders`
- `tags`
- `conversations`
- `file_assets`（上传文件）

索引：`idx_articles_team (team_id, status, updated_at)`、`idx_conversations_team (team_id, updated_at)` 等。

`article_chunks` 不直接加 `team_id`，通过 `article_id` 归属；所有切片查询必须先校验文章归属。

## 4. 权限规则

| 操作 | OWNER | ADMIN | MEMBER |
|---|---|---|---|
| 查看/问答团队资料 | 允许 | 允许 | 允许 |
| 导入/上传/删除团队资料 | 允许 | 允许 | 允许（可配置为仅 ADMIN） |
| 管理标签/文件夹 | 允许 | 允许 | 允许 |
| 邀请/移除成员 | 允许 | 允许 | 禁止 |
| 修改成员角色 | 允许 | 仅 MEMBER 晋升/降级 | 禁止 |
| 修改团队配额/名称 | 允许 | 禁止 | 禁止 |
| 解散团队 | 允许 | 禁止 | 禁止 |

服务端必须：

1. 从 `team_members` 读取当前用户角色，不信任请求体。
2. 每次读写校验 `team_id` 归属和成员状态。
3. 个人空间查询继续只按 `user_id`；团队空间查询按 `team_id`，不能把两者混在一个未校验的 SQL 里。
4. 管理员后台只能查看团队汇总和用量，不能直接读取团队正文，除非显式授权。

## 5. API 草案

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/teams` | 我的团队列表 |
| POST | `/api/v1/teams` | 创建团队（创建者为 OWNER） |
| GET | `/api/v1/teams/{id}` | 团队详情 |
| PUT | `/api/v1/teams/{id}` | 改名/配额（OWNER） |
| GET | `/api/v1/teams/{id}/members` | 成员列表 |
| POST | `/api/v1/teams/{id}/members` | 邀请/添加成员（OWNER/ADMIN） |
| PUT | `/api/v1/teams/{id}/members/{userId}` | 改角色/状态 |
| DELETE | `/api/v1/teams/{id}/members/{userId}` | 移除成员 |
| POST | `/api/v1/teams/{id}/articles` | 将个人资料共享到团队 |
| GET | `/api/v1/teams/{id}/articles` | 团队资料列表 |
| POST | `/api/v1/chat/stream` | 增加 `teamId?`，与 `scope` 组合 |

所有列表返回分页；写接口需要幂等键或状态校验；邀请 token 只存哈希。

## 6. 配额与用量

- `ai_usage` 增加 `team_id`（可空）；团队问答同时记 `user_id` 和 `team_id`。
- 配额检查顺序：个人配额 → 团队配额 → 全局预算。
- 团队月用量按 `team_id + month` 聚合，建议新增 `team_usage_monthly`。
- 管理员后台增加团队用量/配额页面；个人页面只显示自己参与的团队。

## 7. 迁移步骤

1. 新增 `teams` / `team_members`，不改现有业务表；上线空功能。
2. 给业务表加可空 `team_id` 和索引；所有旧数据保持 NULL，行为不变。
3. 改造文章、标签、文件夹、会话查询：个人 + 团队两个明确分支；补集成测试防越权。
4. 改造导入/上传：请求可带 `teamId`；校验成员角色后写入。
5. 改造 RAG 检索和引用：team 范围过滤必须在 SQL/向量过滤层完成，不能只在前端隐藏。
6. 改造用量与配额：加入团队聚合和扣减。
7. 增加前台团队页和后台团队用量页。
8. 最后再做邀请邮件/链接。

## 8. 安全清单

- 越权测试：非成员读/写团队资料、被移除成员继续用旧 token、成员猜 `team_id`。
- 向量检索必须带 `team_id` 过滤；不能先取全局 topK 再在应用层过滤。
- 分享到团队时复制还是引用文章要明确；第一版建议“移动/复制”二选一，避免同一文章双归属。
- 删除团队成员后撤销其团队范围会话和缓存；个人 token 不必失效。
- 团队解散：软删除团队和成员，资料保留只读或转回所有者，需产品确认。

## 9. 为什么暂缓

该改动会影响 `articles`、`article_chunks` 检索、`conversations`、标签/文件夹、导入、用量和配额，属于跨模块大迁移。当前先完成多管理员角色、PWA、插件和单用户 RAG 主线；团队空间在独立分支实现并做完整越权测试后再合并。
