# 拾页开发规范（详细）

## 0. 总原则

- **先文档后代码**：schema/api 先行，代码跟随。
- **模块优先**：先按业务模块分，再在模块内分三层。
- **类型清晰**：不用 `any`，不用 `Map<String,Object>` 当接口载荷。
- **高内聚低耦合**：一个模块拥有自己的表、规则、DTO；跨模块走 Service。
- **能复用就复用**：统一响应、异常、JWT、限流、AI Gateway、usage、分页、空状态。
- **不过度设计**：不为“以后可能用”提前抽象；规则出现第三次再抽。
- **可验证**：每个功能都有测试或可复现的验收步骤。

---

## 1. 后端（Java / Spring Boot / MyBatis）

### 1.1 目录与分层

```
shiye-user-api/src/main/java/com/shiye/user/
├─ ShiyeUserApplication.java
├─ common/
│  ├─ ai/             # AI Gateway、usage、SSE
│  ├─ security/       # 用户 JWT、当前用户
│  ├─ storage/        # 本地/OSS
│  ├─ ratelimit/
│  └─ web/            # CORS、TraceId
├─ config/
└─ module/
   └─ article/
      ├─ controller/
      ├─ service/
      │  └─ impl/
      ├─ mapper/
      ├─ domain/{entity,dto,vo}
      └─ support/
```

- Controller 只依赖 Service 接口。
- Service Impl 依赖 Mapper 和其他 Service 接口。
- Mapper 只做 SQL。
- Entity 不出接口；DTO/VO 分离。
- `common` 只放基础设施，不放业务。

### 1.2 Controller

```java
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public Result<PageResult<ArticleVO>> list(@Valid ArticleQueryDTO query,
                                               @CurrentUser UserPrincipal user) {
        return Result.ok(articleService.list(user.id(), query));
    }

    @PostMapping("/{id}/reindex")
    public Result<Void> reindex(@PathVariable Long id,
                                @CurrentUser UserPrincipal user) {
        articleService.reindex(user.id(), id);
        return Result.ok();
    }
}
```

规则：

- 不在 Controller 写 SQL、事务、业务判断。
- 参数用 `@Valid` + DTO。
- 当前用户从 `@CurrentUser` 取，不信任前端传的 `userId`。
- 统一返回 `Result<T>`。
- 列表返回 `PageResult<T>`。

### 1.3 Service

```java
public interface ArticleService {
    PageResult<ArticleVO> list(Long userId, ArticleQueryDTO query);
    void reindex(Long userId, Long articleId);
}

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final IngestTaskService ingestTaskService;

    @Override
    @Transactional
    public void reindex(Long userId, Long articleId) {
        ArticleEntity article = articleMapper.findOwned(userId, articleId);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ingestTaskService.enqueueReindex(userId, articleId);
    }
}
```

规则：

- 事务写在 Impl。
- 先校验归属，再写。
- 失败抛 `BizException`，不返回 null 当错误。
- 幂等：重复调用不产生重复任务/数据。
- 跨模块调用 Service 接口，不直接调 Mapper。

### 1.4 Mapper / SQL

- 方法名表达意图：`findOwned`、`countByStatus`、`updateStatus`。
- 参数用 `@Param`。
- 查询必须带归属条件：

```xml
<select id="findOwned" resultType="ArticleEntity">
  SELECT *
  FROM articles
  WHERE id = #{articleId}
    AND user_id = #{userId}
    AND deleted_at IS NULL
</select>
```

- 列表查询避免 `SELECT *`，只取需要字段。
- 分页用统一 `PageResult`；深分页优先游标。
- 不在 SQL 里做复杂业务判断；复杂聚合可放 Mapper，但要可测试。
- 索引与查询条件匹配；新增查询先看执行计划。

### 1.5 DTO / VO / Entity

- `Entity`：与表结构一一对应，不对外。
- `DTO`：接口入参，带校验注解。
- `VO`：接口出参，只包含前端需要的字段。
- 不把 `Entity` 直接序列化返回。
- 不用 `Map<String,Object>` 当请求/响应。
- 字段命名与 `docs/api.md` 一致。

### 1.6 枚举与状态

- 状态用 enum，不在业务代码里散落字符串。
- 数据库存字符串（可读）或统一编码，二选一并写清楚。
- 状态机集中在一个 `support/` 类里，例如：

```java
public final class ArticleStatus {
    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    // ...
}
```

- 状态流转必须校验来源状态，不能任意跳转。

### 1.7 金额 / Token / 时间

- token 用 `long`，字段 `BIGINT`。
- 金额/单价用 `BigDecimal`，字段 `DECIMAL(12,6)`。
- 成本计算统一在一个 `CostCalculator`，不要散落。
- 时间统一 `Instant`/UTC 存储，接口返回 ISO-8601 或明确时区。
- 单价快照写入 `ai_usage`，不引用实时价格。

### 1.8 异常与错误码

```java
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;
}
```

- 全局异常处理：`@RestControllerAdvice`。
- 错误响应不包含堆栈/SQL/密钥。
- HTTP 状态与错误码对应：
  - 400 参数
  - 401 未登录/过期
  - 403 无权限/禁用
  - 404 不存在
  - 409 冲突
  - 429 限流/配额
  - 500 未知
- 前端可读的 `message` 与调试用 `requestId` 分开。

### 1.9 安全

- 所有用户资源查询带 `user_id`。
- 管理端接口单独 `@AdminAuth`，不复用用户 token。
- 文件上传校验类型/大小/文件名。
- 抓取 URL：
  - 只允许 http/https
  - 禁止内网/环回/链路本地/云元数据
  - 限制重定向次数、响应大小、超时
- 网页正文按不可信数据处理：
  - 分隔符包裹
  - 系统提示明确忽略正文指令
  - V1 不给模型工具权限
- 日志脱敏：token、密码、API Key、完整正文。

### 1.10 AI Gateway

所有 AI 调用必须经过统一入口：

```java
AiChatResult chat(AiChatCommand command);
AiEmbeddingResult embed(AiEmbeddingCommand command);
```

职责：

- 检查配额/预算/限流。
- 生成 `request_id`。
- 调用模型。
- 解析 usage。
- 计算成本（单价快照）。
- 写 `ai_usage`。
- 返回结果 + usage。

禁止在业务 Service 里直接 `RestClient` 调模型。

### 1.11 SSE

- 事件契约固定：
  - `meta`：conversationId/messageId/requestId
  - `delta`：text
  - `citation`：index/articleId/chunkId/title/quote/score
  - `usage`：promptTokens/completionTokens/cost
  - `done`：finishReason
  - `error`：code/message
- 客户端断开时取消上游请求。
- 超时、模型错误要发 `error` 并结束。
- 不要在 SSE 线程里做阻塞 DB 大查询。

### 1.12 异步 / 队列

- 任务状态机明确。
- 重试上限 + 退避 + 死信。
- 幂等键/唯一约束防重复。
- 任务表持久化，重启可恢复。
- 失败不吞：记录 `error_code/error_message`。
- 批量导入按批处理，单条失败不影响整批。

### 1.13 日志与可观测性

- 每个请求带 `request_id`。
- 关键节点日志：导入、解析、切片、embedding、检索、AI 调用。
- 不打印完整正文/密钥。
- Actuator 健康检查。
- 失败任务在后台可查。

### 1.14 测试

- 单测：URL 规范化、成本计算、配额、状态机、解析边界。
- 集成：登录、导入、RAG、SSE、管理端权限。
- 覆盖：空、重复、超时、取消、失败、超额。
- 测试名表达行为，不写 `test1`。

---

## 2. 前端（Vue 3 + TypeScript + Vite）

### 2.1 目录

```
user-web/src/
├─ api/                 # Axios 实例、接口、types
├─ components/          # 通用组件
├─ composables/         # useChatStream、useAuth
├─ layouts/
├─ router/
├─ stores/              # Pinia
├─ styles/
├─ views/
│  ├─ auth/
│  ├─ library/
│  ├─ chat/
│  ├─ usage/
│  ├─ settings/
│  └─ plugin/
├─ App.vue
└─ main.ts
```

### 2.2 API 层

- 一个 `api/client.ts` 统一 Axios 实例。
- 拦截器处理：
  - 注入 token
  - 401 刷新/跳登录
  - 403/429/网络错误提示
  - 统一 `Result` 解包
- 每个模块一个 `xxx.api.ts`。
- 类型放 `xxx.types.ts`，与 `docs/api.md` 一致。
- 不在组件里直接 `fetch`/`axios`。

```ts
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export async function listArticles(query: ArticleQuery): Promise<PageResult<Article>> {
  return api.get('/articles', { params: query })
}
```

### 2.3 类型

- 禁止 `any`；不确定用 `unknown`。
- 组件 props/emits 明确类型。
- API 返回类型不省略。
- 枚举用 union type 或 const object。
- 表单类型与 DTO 对齐。

### 2.4 状态

- 服务端状态用 TanStack Query 或 Pinia（二选一，项目统一）。
- 本地 UI 状态放组件内，不全局化。
- 不把 token 放 Pinia 持久化到 localStorage（安全考虑）；按项目认证方案处理。
- 会话/消息等列表更新要处理乐观更新/回滚。

### 2.5 组件

- 通用组件放 `components/`，业务组件放 `views/` 内。
- 组件只做展示/交互，不直接调 API。
- 复杂逻辑抽 composable。
- 空状态、加载态、错误态三件套齐全。
- 不复制粘贴空状态/分页/错误提示。

### 2.6 SSE

- 用 `fetch` + `ReadableStream` 或 `@microsoft/fetch-event-source`。
- 处理事件：meta/delta/citation/usage/done/error。
- 取消时 abort 请求。
- 断线重连策略明确。
- 流式内容渲染注意 XSS（Markdown 转义）。

### 2.7 表单

- 统一校验（Zod / Element Plus rules）。
- 错误提示定位到字段。
- 提交中禁用按钮，防重复提交。
- 成功后刷新列表/关闭弹窗/提示。

### 2.8 样式

- 遵循 v0.6 视觉：纸感背景、细边框、排版驱动。
- 不引入新的重色块/霓虹渐变。
- 复用设计 token：颜色、间距、圆角、字号。
- 响应式：桌面优先，窄屏可用。
- 不写内联样式，除非是一次性动态值。

### 2.9 测试

- 组件测试：关键交互、空/错/加载态。
- E2E：登录 → 资料库 → 问答 → 引用 → 后台用量。
- 不测试实现细节，测试用户行为。

---

## 3. 浏览器插件（MV3）

### 3.1 目录

```
user-extension/src/
├─ background/       # service worker：书签、token、消息
├─ content/          # 悬浮球注入、正文提取
├─ panel/            # Vue 聊天面板
├─ options/          # 书签导入设置页
├─ popup/            # 可选
├─ shared/           # API client、storage、types
└─ manifest.ts
```

### 3.2 权限

- `bookmarks`：读书签。
- `storage`：token/设置。
- `activeTab`/`scripting`：按需注入。
- `host_permissions`：只放后端域名。
- 内容脚本匹配 http/https；跳过 `chrome://`、扩展商店、PDF 阅读器。

### 3.3 通信

- content ↔ background 用 `chrome.runtime.sendMessage`。
- 面板 ↔ content 用 `postMessage`/Shadow DOM 事件。
- 消息类型集中定义，避免魔法字符串。
- 异步响应用 `sendResponse` + `return true`。

### 3.4 存储

- token 存 `chrome.storage.local`，不存密码。
- 悬浮球位置、开关、默认模式存本地。
- 账号偏好可同步后端。
- 退出登录清理 token。

### 3.5 页面提取

- 收藏：Readability → 回退 `document.body.innerText`。
- 当前页面问答：标题、URL、选中文本、正文。
- SPA 路由变化更新上下文。
- 不注入到受限页面。
- 样式用 Shadow DOM 隔离。

### 3.6 书签

- `chrome.bookmarks.getSubTree` 递归读。
- 主文件夹只监听一个。
- 新增书签 `onCreated` 自动入队（P1）。
- 删除 `onRemoved` 标记归档，不自动删资料。
- 批量发送 ≤ 50 条/批。

### 3.7 安全

- MV3 不执行远程 JS；Vue 打包进插件或 iframe。
- API Key 不在插件里；只存用户 token。
- 不在 content script 里执行页面脚本。
- 消息校验来源/类型。

---

## 4. 复用规则

优先复用：

- 后端：`Result`、`PageResult`、`BizException`、`ErrorCode`、JWT、`@CurrentUser`、限流、AI Gateway、成本计算、URL 规范化、分页。
- 前端：`api/client`、`useChatStream`、`EmptyState`、`ErrorState`、`Pagination`、`ConfirmDialog`。
- 插件：`shared/api`、`shared/storage`、`shared/types`、面板消息组件。

判断标准：

- 出现第 2 次：先复制并标注 TODO。
- 出现第 3 次：抽到共享层。
- 共享层不放业务规则；业务规则留在模块。

---

## 5. Review 清单

### 后端

- [ ] 目录符合模块优先 + 三层
- [ ] Controller 无 SQL/业务
- [ ] Service 有事务/归属校验
- [ ] Mapper 只做 SQL
- [ ] DTO/VO/Entity 分离
- [ ] 错误码/HTTP 状态正确
- [ ] 配额/usage 记录
- [ ] 幂等/重试/状态机
- [ ] 安全（SSRF/注入/密钥/CORS）
- [ ] 测试覆盖关键路径

### 前端

- [ ] API 走统一 client
- [ ] 类型完整，无 `any`
- [ ] 空/加载/错误态
- [ ] 401/403/429 处理
- [ ] SSE 取消/错误处理
- [ ] 复用组件/composable
- [ ] 视觉符合 v0.6
- [ ] 关键交互测试

### 插件

- [ ] 权限最小化
- [ ] Shadow DOM 隔离
- [ ] token 存储安全
- [ ] 书签批量/去重
- [ ] 受限页面跳过
- [ ] 消息类型明确

---

## 6. 常见反例

- ❌ Controller 里直接 `articleMapper.selectById()`。
- ❌ Entity 直接返回给前端。
- ❌ `Map<String,Object>` 当请求/响应。
- ❌ 前端 `any` 一把梭。
- ❌ 复制粘贴一份新的 AI 调用逻辑。
- ❌ 导入失败只打日志，不落库。
- ❌ 查询不带 `user_id`。
- ❌ 管理端接口复用用户 token。
- ❌ 加字段不更新 schema/api。
- ❌ 没有空状态/错误态。
- ❌ 所有逻辑塞进一个 1000 行 Service。
