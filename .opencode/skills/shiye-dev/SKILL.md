---
name: ShiYe Dev
description: Use for any ShiYe (拾页) development task. Before coding, audit docs/schema.md and docs/api.md for missing fields, endpoints, states, permissions and indexes; during coding, enforce module-first + 3-layer structure, explicit typing, high cohesion/low coupling, reuse, security, tests and the definition of done.
---

# 拾页开发守则（ShiYe Dev Guard）

## 何时使用

在以下任一情况开始前加载本技能：

- 编写或修改 `docs/schema.md`、`docs/api.md`
- 实现/修改后端、前台 Web、管理后台、浏览器插件的任何功能
- 做代码 review、重构、加表、加接口
- 准备把任务标记为“完成”

## 先读什么

按顺序读：

1. `docs/requirements.md`（V1 基线，当前 v0.9；3.4 是 AI/RAG 技术方案）
2. `docs/schema.md`
3. `docs/api.md`
4. `docs/prototype.html`（v0.6 视觉基线）
5. `README.md` + 需求文档 3.2/3.3（目录与前后台边界）
6. 目标模块现有代码（遵循既有风格，除非违反本技能）

缺少 schema/api 时：先补文档，不写代码。不要凭记忆猜字段或接口。

## 项目事实

- 根目录：`D:\项目\shiye`
- 前台：`frontend/user-web`（15173）+ `frontend/user-extension` → `backend/shiye-user-api`（18080）
- 后台：`frontend/admin-web`（15174） → `backend/shiye-admin-api`（18081）
- 共享：`backend/shiye-common`（只放基础设施）
- 技术栈：Vue 3 + TS + Vite；Spring Boot 3 + Java 21 + MyBatis；MySQL 8；Redis Stack；Spring AI；SSE
- 账号：`users`（开放注册）与 `admin_users`（SQL 初始化，`SUPER_ADMIN`）分离
- JWT：`USER_JWT_SECRET` / `ADMIN_JWT_SECRET`，互不通用
- 模型：Chat DeepSeek；Embedding BGE-M3
- 成本：全局预算 + 单用户配额 + 限流；所有 AI 调用必须记 usage
- 视觉：v0.6 Linear / Notion 风；纸感、细边框、排版驱动，不再做重色块
- 当前已完成：认证（含登录/注册 IP 限流、邀请码、资料/密码/插件偏好）、书签导入 M2a/M2b、资料 API/资料库 UI（标签/文件夹筛选、标签管理、单篇/文件夹/标签范围问答）、PDF/Markdown/TXT 上传（可选 Tesseract OCR）、RAG 问答（DeepSeek SSE + 引用 + 会话/消息、问答取消、反馈）、BGE-M3 + FULLTEXT + RRF 混合检索、BGE-reranker 重排、资料 AI 摘要/自动标签、当前页临时问答、用量趋势/CSV 导出、日用量汇总、reindex、ingest_tasks 入库任务与数据清理、插件悬浮球问答/收藏当前页/书签自动同步/默认模式与范围、管理后台（总览/用户配额/用户密码重置/用量/导入/书签与入库任务/模型在线配置、系统设置/预算/RAG 参数、预算 Webhook 告警、RAG 评测 recall/引用/正确率/延迟/成本、审计日志、操作限流）、Prometheus 指标与告警规则、备份脚本、Playwright 登录 E2E、k6 基础压测、CI/CD。邮箱验证未接入

## A. 编码前：接口/数据库文档审计

使用 `references/doc-audit.md` 的完整清单。至少做 4 个交叉检查：

1. **需求 → 表**：每个 P0 功能都有落库方案。
2. **表 → 接口**：每张表都有创建/读取/更新入口，没有孤儿表。
3. **接口 → 页面/流程**：每个页面动作都有接口，每个接口都有调用方。
4. **权限 → 数据**：每个查询/写入都有 `userId` / admin / 归属 / 租户过滤。

AI/RAG 专项（见 `references/doc-audit.md` 3.8）：

- Embedding 模型/维度是否固定；换模型是否有重建方案。
- chunk 是否有 charStart/charEnd/tokenCount/embeddingModel/vectorId。
- 向量索引字段、userId 过滤、topK、阈值、同文章限流。
- Prompt 是否要求引用、无答案说不知道、正文不可信。
- SSE 事件是否完整；usage 是否全量记账。
- 评测集/指标/回归是否明确。
- 失败降级：抓取/解析/embedding/向量库/模型/超时/配额。

重点检查：

- 字段：类型、长度、可空、默认值、精度（token/金额）
- 主键、唯一键、查询索引、软删除
- 状态枚举、状态机、失败/重试/取消
- 分页、筛选、排序、幂等、并发
- 错误码与 HTTP 状态
- 登录态：user/admin 两套；禁用用户；配额/预算
- AI usage：source/scene/model/tokens/单价快照/cost/latency/status
- 书签导入：batch、folder、状态机、失败项、跳过项
- RAG：chunk、charStart/charEnd、vectorId、embeddingModel、citation
- 安全：SSRF、提示词注入、XSS、密钥、CORS
- 可观测性：request_id、日志、审计

输出格式（编码前必须给出）：

| # | 问题 | 影响 | 建议 | 是否阻塞 |
|---|---|---|---|---|

规则：

- P0 缺口未解决 → 不开始编码。
- 有意延后的缺口 → 写进文档的“待确认/决策记录”，不能口头放过。
- 不允许静默新增字段/接口；必须同步 schema 和 api。

## B. 开发规范

### B1. 目录：模块优先 + 模块内三层

```
backend/shiye-user-api/src/main/java/com/shiye/user/
├─ common/                 # 仅用户端基础设施
├─ config/
└─ module/
   ├─ auth/                # 注册/登录/插件授权码
   ├─ bookmark/            # 书签导入
   ├─ article/             # 资料/切片/收藏
   ├─ chat/                # RAG/会话/引用
   ├─ usage/               # 用量/配额
   └─ settings/            # 用户偏好
```

每个 module 固定：

```
module/{module}/
├─ controller/             # HTTP 参数、权限、响应
├─ service/                # 业务接口
│  └─ impl/                # 业务实现（@Service/@Transactional）
├─ mapper/                 # MyBatis
├─ domain/
│  ├─ entity/              # 数据库行映射
│  ├─ dto/                 # 入参
│  └─ vo/                  # 出参
├─ support/                # 无状态规则/工具
├─ config/                 # 可选
├─ event/                  # 可选
└─ scheduler/              # 可选
```

- 不按 `controller/service/mapper` 做顶层目录。
- 不在 `user-api` 里放 `admin` 模块；管理端在 `shiye-admin-api`。
- `shiye-common` 只放基础设施：Result、异常、JWT、CORS、TraceId、AI 客户端、工具、枚举；不放业务。
- `user-api` 与 `admin-api` 不互相 import。

### B2. 三层职责

- **Controller**：HTTP 参数、校验、鉴权、响应；不写 SQL/业务。
- **Service 接口**：业务契约；**Impl**：事务、编排、规则；不依赖 HTTP。
- **Mapper**：只做 SQL；不写业务判断。
- **Entity**：数据库映射；不作为 API 出参；DTO/VO 分离。
- 跨模块只调 Service 接口，不直接调对方 Mapper/Entity。

### B3. 类型清晰

- TS 禁止 `any`；不确定用 `unknown` + 收窄。
- 公共函数写返回类型；组件 props/emits 用类型。
- API 请求/响应类型与 `docs/api.md` 一致；集中放 `api/types.ts` 或 `shared/types`。
- Java 用 DTO/VO/record/enum；不用 `Map<String,Object>` 当接口载荷。
- 金额/token 用 `long` / `BigDecimal` / 明确单位；时间统一 UTC 或明确时区。

### B4. 高内聚低耦合

- 一个模块拥有自己的表、规则、DTO。
- 不共享可变实体；跨模块通过 Service。
- 不形成循环依赖；common 不反向依赖业务。
- 不为“以后可能用”提前抽象；规则出现第三次再抽。

### B5. 复用

优先复用：

- 后端：统一响应/异常、JWT、校验、限流、AI Gateway、usage 记账、URL 规范化、分页。
- 前端：`api` 封装、`useChatStream`、空状态、分页、错误处理、表单校验。
- 插件：API client、storage、类型、面板组件。

禁止复制粘贴 SQL/接口/类型；需要时抽到共享层。但不要过度抽象：一个地方用的逻辑先放本地。

### B6. 命名

- Java：`XxxController` / `XxxService` / `XxxServiceImpl` / `XxxMapper` / `XxxEntity` / `XxxDTO` / `XxxVO`；包名小写。
- TS：组件 PascalCase；函数/变量 camelCase；composable `useXxx`；文件 `xxx.api.ts` / `xxx.types.ts`。
- DB：表/字段 snake_case；`created_at` / `updated_at` / `deleted_at`；状态值大写。
- API：资源用复数名词；动作用 POST/PUT/PATCH/DELETE 表达；SSE 事件名小写。

### B7. 错误与状态码

- 统一 `Result` + 错误码；不把异常/SQL/密钥直接返回前端。
- 400 参数；401 未登录；403 无权限/禁用；404 不存在；409 冲突；429 限流/超额；500 未知。
- 前端统一处理 401 刷新、403、429、网络错误；不吞异常。
- 所有列表返回分页结构；所有写接口考虑幂等。

### B8. 安全

- 所有查询带归属校验；管理员接口单独鉴权。
- API Key 只在服务端；日志脱敏。
- 抓取 URL 做 SSRF 校验（内网/IP/重定向/协议/大小）。
- 网页内容按不可信数据处理；提示词注入防护。
- 前端转义/清洗；CORS 白名单。

### B9. 异步 / SSE / 队列

- 状态机明确：`PENDING → ... → READY / FAILED / SKIPPED`。
- 重试有上限、退避、死信；幂等键。
- SSE 事件契约固定：`meta / delta / citation / usage / done / error`。
- 客户端断开、超时、取消要释放资源。
- 所有 AI 调用走统一 Gateway；usage 不遗漏。

### B10. 测试

- 单测：URL 规范化、匹配/评分、成本计算、配额、状态机、解析边界。
- 集成：登录、导入、RAG、SSE、管理端权限。
- E2E：登录 → 导入 → 问答 → 引用 → 后台用量。
- 覆盖失败/空/重复/超时/取消/超额。

### B11. 文档同步

- 改表 → 更新 `schema.md` + 迁移 SQL。
- 改接口 → 更新 `api.md` + 前端类型。
- 改行为 → 更新 `requirements.md` 或决策记录。
- 不把“代码即文档”当借口。

## C. 完成前自检

使用 `references/definition-of-done.md`。输出：

- 改动摘要
- 文档是否同步
- 测试命令与结果
- 已知缺口/偏离
- DoD 勾选结果

## 硬性禁止

- 没有 schema/api 就开始写代码。
- Controller 直接调 Mapper。
- Entity 直接作为接口出参。
- `any` / `Map<String,Object>` 当接口载荷。
- 跨模块直接访问对方 Mapper/Entity。
- 密钥、密码、API Key 进入代码/日志/仓库。
- 没有归属校验的查询/写入。
- AI 调用绕过统一 Gateway / 不记 usage。
- 加表/加接口不同步文档。

## 参考文件

- `references/doc-audit.md`：接口/数据库审计清单与矩阵
- `references/coding-standards.md`：后端/前端/插件详细规范
- `references/definition-of-done.md`：完成定义与 review 清单
