# 接口 / 数据库文档审计清单

> 在写代码前使用。目标不是“文档看起来完整”，而是“需求、表、接口、页面、权限能一一对上”。

## 1. 四个交叉检查

### 1.1 需求 → 表

- [ ] 每个 P0 功能都有落库方案。
- [ ] 每个 P0 页面动作都有对应数据。
- [ ] 没有“只存在前端内存、刷新就丢”的关键数据。
- [ ] 没有“只存日志、无法查询”的关键状态。

### 1.2 表 → 接口

- [ ] 每张表都有创建入口。
- [ ] 每张表都有读取入口（列表/详情/统计）。
- [ ] 每张表都有更新/删除/归档入口（如适用）。
- [ ] 没有孤儿表。
- [ ] 没有“接口需要但表里没有字段”的情况。

### 1.3 接口 → 页面 / 流程

- [ ] 每个页面按钮/操作都有接口。
- [ ] 每个接口都能指出调用方（页面/插件/定时任务/后台）。
- [ ] 请求字段与页面表单一致。
- [ ] 响应字段与页面展示一致。
- [ ] 列表接口有分页、筛选、排序。
- [ ] 详情接口有完整字段。
- [ ] 错误提示有对应错误码。

### 1.4 权限 → 数据

- [ ] 用户接口每个查询都带 `user_id` 过滤。
- [ ] 用户只能读写自己的资料/会话/导入任务。
- [ ] 管理端接口只接受管理员 token。
- [ ] 前台 token 不能访问 `/admin/**`。
- [ ] 后台 token 不能访问 `/api/v1/**`。
- [ ] 禁用用户不能登录/调用 AI。
- [ ] 管理员不能禁用 `SUPER_ADMIN`。

## 2. 表设计检查

### 2.1 通用

- [ ] 表名/字段名 snake_case，复数表名。
- [ ] 主键 `id BIGINT UNSIGNED AUTO_INCREMENT`。
- [ ] 业务唯一键用 `UNIQUE KEY`，不只靠应用层。
- [ ] 业务表有 `user_id`（用户侧）或明确的归属字段。
- [ ] `created_at` / `updated_at`；需要软删除的加 `deleted_at`。
- [ ] 状态字段用明确枚举值，不用 0/1 猜。
- [ ] 金额/token 精度明确：token 用 BIGINT；金额用 DECIMAL；单价用 DECIMAL。
- [ ] 时间统一 UTC 或明确时区。
- [ ] 不建物理外键（项目约定）；应用层校验 + 孤儿巡检 SQL。
- [ ] 大文本/JSON 字段说明用途与查询方式。
- [ ] 高频查询有索引：`user_id + created_at`、`status`、`article_id + chunk_index` 等。
- [ ] 软删除后的查询都默认排除 `deleted_at`。
- [ ] 归档/清理策略明确（向量、切片、临时文件）。

### 2.2 逐表检查

#### users

- [ ] email 唯一。
- [ ] password_hash 长度足够（BCrypt）。
- [ ] status：ACTIVE / DISABLED。
- [ ] monthly_token_quota / monthly_cost_quota / quota_source。
- [ ] last_login_at、created_at、updated_at。
- [ ] role 仅保留 USER（V1），管理员在 admin_users。
- [ ] 配额按月重置的口径明确（自然月/滚动 30 天）。

#### admin_users

- [ ] email 唯一。
- [ ] password_hash。
- [ ] role：SUPER_ADMIN（V1 只有一个）。
- [ ] status：ACTIVE / DISABLED；SUPER_ADMIN 不可禁用。
- [ ] last_login_at、created_at、updated_at。
- [ ] 不开放注册；seed.sql 初始化。
- [ ] 管理员 token 与用户 token 分离。

#### bookmark_imports

- [ ] user_id、browser、folder_id、folder_path。
- [ ] is_primary：是否主文件夹。
- [ ] recursive：是否包含子文件夹。
- [ ] status：PENDING / RUNNING / PAUSED / DONE / FAILED / CANCELLED。
- [ ] total / success / failed / skipped。
- [ ] created_at / started_at / finished_at。
- [ ] 同一用户同一 folder 的重复导入策略。

#### bookmark_items

- [ ] import_id、title、url、url_hash。
- [ ] status：PENDING / FETCHING / PARSING / CHUNKING / EMBEDDING / READY / FAILED / SKIPPED。
- [ ] article_id（成功后回填）。
- [ ] error_code / error_message。
- [ ] 唯一键 `(import_id, url_hash)`。
- [ ] 批量插入/更新的性能。

#### articles

- [ ] user_id、title、url、domain、source_type。
- [ ] summary、tags（JSON 或关联表）、content_text。
- [ ] status：PENDING / PARSING / CHUNKING / EMBEDDING / READY / FAILED。
- [ ] word_count、content_hash。
- [ ] created_at / updated_at / deleted_at。
- [ ] 去重键：`(user_id, content_hash)` 或 `(user_id, url)`，二选一并说明。
- [ ] 原文位置字段是否足够支撑引用。

#### article_chunks

- [ ] article_id、chunk_index、content。
- [ ] token_count、char_start、char_end。
- [ ] vector_id、embedding_model。
- [ ] 唯一键 `(article_id, chunk_index)`。
- [ ] 重建索引时旧 chunk/vector 的清理顺序。
- [ ] 切片大小/重叠参数是否记录在文章或任务上。

#### ingest_tasks

- [ ] article_id、source_type。
- [ ] status：PENDING / RUNNING / SUCCESS / FAILED / DEAD。
- [ ] stage：FETCH / PARSE / CHUNK / EMBED。
- [ ] retry_count、error_code、error_message。
- [ ] started_at / finished_at。
- [ ] 死信/人工重试入口。
- [ ] 幂等键：同一文章同一阶段不重复跑。

#### conversations / messages / citations

- [ ] conversations：user_id、title、scope_type、scope_id。
- [ ] scope_type：ALL / ARTICLE / FOLDER / TAG。
- [ ] messages：conversation_id、role、content、tokens_in/out。
- [ ] citations：message_id、chunk_id、article_id、score、quote。
- [ ] 删除会话时消息/引用的级联策略（应用层）。
- [ ] 消息顺序稳定（id/created_at）。
- [ ] 引用能否跳回 `char_start/char_end`。

#### ai_usage

- [ ] request_id 唯一。
- [ ] user_id、source（WEB/EXTENSION/ADMIN/API）、scene（CHAT/EMBEDDING/RERANK/SUMMARY）。
- [ ] model、prompt_tokens、completion_tokens、total_tokens、cached_tokens。
- [ ] input_price / output_price（调用时快照）、cost、currency。
- [ ] latency_ms、first_token_ms。
- [ ] status（SUCCESS/FAILED/ABORTED）、error_code、estimated。
- [ ] conversation_id、article_id、client_version。
- [ ] created_at。
- [ ] 索引：`(user_id, created_at)`、`(source, created_at)`、`(scene, created_at)`、`(model, created_at)`。
- [ ] 失败/取消也记录。
- [ ] 流式 usage 的获取/估算策略。

#### ai_usage_daily

- [ ] date、user_id、source、scene、model。
- [ ] calls、tokens、cost、avg_latency_ms、error_count。
- [ ] 唯一键 `(date, user_id, source, scene, model)`。
- [ ] 与 `ai_usage` 的对账口径。

#### model_prices / system_settings

- [ ] provider、model、input_price、output_price、currency、effective_at。
- [ ] 价格快照 vs 实时价格的区分。
- [ ] system_settings key 唯一。
- [ ] registration_enabled、默认配额、全局预算、告警阈值、限流参数。
- [ ] updated_by 引用 admin_users.id。

## 3. API 检查

### 3.1 认证

- [ ] 用户注册/登录/刷新/登出。
- [ ] 插件一次性授权码生成 + 换取 token。
- [ ] 管理员独立登录/刷新/登出（`/admin/auth/*`）。
- [ ] 两套 JWT secret/audience。
- [ ] 401 / 403 的语义。
- [ ] token 过期、刷新失败、禁用用户的处理。

### 3.2 书签导入

- [ ] `POST /api/v1/bookmark-imports` 的请求体：folder 元数据 + items（每批 ≤ 50）。
- [ ] 幂等键/重复导入策略。
- [ ] `GET /bookmark-imports` 分页、状态筛选。
- [ ] `GET /bookmark-imports/{id}/items` 分页、失败筛选。
- [ ] retry / pause / cancel / primary 的语义。
- [ ] 单条失败不拖垮整批。
- [ ] 限速/重试/错误码。

### 3.3 资料

- [ ] `GET /articles` 分页、搜索、状态/来源/标签筛选、排序。
- [ ] `GET /articles/{id}` 返回摘要、标签、切片数、状态。
- [ ] `GET /articles/{id}/chunks` 返回 charStart/charEnd。
- [ ] `DELETE /articles/{id}` 软删除 + 异步清理。
- [ ] `POST /articles/{id}/reindex` 幂等。
- [ ] `POST /articles/collect` 支持 URL 或当前页正文。
- [ ] **缺上传接口？** PDF/Markdown 上传需要 `POST /articles/upload` 或预签名上传；文档里必须有。
- [ ] 文件类型/大小限制。

### 3.4 问答

- [ ] `POST /chat/stream` 请求：conversationId、scope、question。
- [ ] SSE 事件：meta / delta / citation / usage / done / error。
- [ ] 取消/超时/断开的处理。
- [ ] 会话列表/消息/删除。
- [ ] 引用字段与前端卡片一致。
- [ ] 检索参数（topK/混合/rerank）是否暴露给调试面板。

### 3.5 用量

- [ ] summary 支持 today/7d/30d。
- [ ] records 分页 + 来源/场景/模型筛选。
- [ ] quota / budget。
- [ ] 与 ai_usage 字段一致。
- [ ] 导出 CSV 的字段与权限。

### 3.6 管理端

- [ ] overview 指标口径。
- [ ] usage 筛选/聚合/导出。
- [ ] imports/tasks 列表/详情/重试。
- [ ] models GET/PUT，API Key 只写不读。
- [ ] users 列表/禁用/配额。
- [ ] settings 注册开关/全局预算。
- [ ] 所有接口只接受管理员 token。

### 3.7 错误码

建议至少覆盖：

| 场景 | HTTP | 错误码 |
|---|---|---|
| 参数校验 | 400 | 40001 |
| 未登录 | 401 | 40101 |
| token 过期 | 401 | 40102 |
| 无权限 | 403 | 40301 |
| 用户被禁用 | 403 | 40302 |
| 资源不存在 | 404 | 40401 |
| 重复/冲突 | 409 | 40901 |
| 限流 | 429 | 42901 |
| 配额超限 | 429 | 42902 |
| 全局预算超限 | 429 | 42903 |
| AI 不可用 | 502 | 50201 |
| 解析失败 | 502 | 50202 |
| 未知错误 | 500 | 50000 |

- [ ] 前端能区分这些错误并给出提示。
- [ ] 错误响应不包含 SQL/密钥/堆栈。

## 3.8 AI / RAG 专项

- [ ] `docs/requirements.md` 3.4 AI 技术方案存在，并与 schema/api 一致。
- [ ] Embedding 模型/维度固定（BGE-M3 / 1024），换模型有全量重建索引方案。
- [ ] chunk 有 `char_start` / `char_end` / `token_count` / `embedding_model` / `vector_id`。
- [ ] Redis 索引字段：`user_id` / `article_id` / `chunk_id` / `folder_id` / `tags` / `content` / `vector`。
- [ ] 检索必须带 `user_id` 过滤；topK、分数阈值、同文章 chunk 上限明确。
- [ ] Prompt 要求：只基于 context、必须引用、无答案说不知道、正文不可信。
- [ ] SSE 事件：`meta / delta / citation / usage / done / error` 完整。
- [ ] usage 记录：source/scene/model/tokens/单价快照/cost/latency/status；失败/取消也记。
- [ ] 评测集/指标/回归流程明确（recall@5、引用准确率、答案正确率、延迟、成本）。
- [ ] 失败降级：抓取/解析/embedding/向量库/模型/超时/配额/引用缺失。
- [ ] 安全：提示词注入、SSRF、密钥、日志脱敏、V1 不给模型工具权限。
- [ ] 当前页面模式（P1）不写资料库、不建向量，引用标注“当前页面”。

## 4. 跨切面检查

### 4.1 配额与成本

- [ ] 调用前检查：全局预算 + 用户配额 + 限流。
- [ ] 超限返回明确错误。
- [ ] 失败/取消也记录 usage。
- [ ] 单价快照，不随改价变化。
- [ ] 插件来源 `source=EXTENSION` 可单独统计。

### 4.2 安全

- [ ] SSRF：协议、内网 IP、重定向、响应大小。
- [ ] 提示词注入：正文分隔、系统提示、无工具权限。
- [ ] XSS：Markdown/HTML 清洗。
- [ ] CORS：前台/后台/插件白名单。
- [ ] 密钥：只存后端，日志脱敏。
- [ ] 归属校验：所有用户资源。

### 4.3 异步与可靠性

- [ ] 状态机完整。
- [ ] 重试上限/退避/死信。
- [ ] 幂等键/唯一约束。
- [ ] 重启恢复。
- [ ] 软删除/向量清理。

### 4.4 可观测性

- [ ] request_id 贯穿。
- [ ] AI usage 全量。
- [ ] 失败任务可查。
- [ ] 健康检查。
- [ ] 关键日志脱敏。

### 4.5 文档

- [ ] OpenAPI 与实际接口一致。
- [ ] 示例请求/响应。
- [ ] 错误码表。
- [ ] 分页/排序/筛选说明。
- [ ] SSE 事件示例。
- [ ] 数据库迁移与回滚说明。

## 5. 审计输出模板

```md
## 文档审计结果

### 阻塞项（P0）

| # | 位置 | 问题 | 影响 | 建议 | 状态 |
|---|---|---|---|---|---|

### 非阻塞项（P1/P2）

| # | 位置 | 问题 | 影响 | 建议 | 状态 |
|---|---|---|---|---|---|

### 已确认

- [ ] 需求 → 表
- [ ] 表 → 接口
- [ ] 接口 → 页面
- [ ] 权限 → 数据

### 结论

- [ ] 可以开始编码
- [ ] 需要先补文档（列出文件）
```

## 6. 通过标准

- 所有 P0 阻塞项关闭或明确记录为“V1 不做”。
- schema.md 和 api.md 的字段/接口一一对应。
- 每个页面动作都能找到接口。
- 每个接口都能找到调用方。
- 每个用户资源都有归属校验。
- 每次 AI 调用都有 usage 记录方案。
