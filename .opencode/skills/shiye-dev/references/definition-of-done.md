# 完成定义（DoD）与 Review 清单

## 1. 编码前（Ready）

- [ ] 已读 `docs/requirements.md` 当前版本。
- [ ] `docs/schema.md` 存在，且本功能涉及的表/字段已定义。
- [ ] `docs/api.md` 存在，且本功能涉及的接口/错误码已定义。
- [ ] 文档审计无未关闭的 P0 阻塞项。
- [ ] 目录归属明确：user-api / admin-api / user-web / admin-web / extension。
- [ ] 模块归属明确：auth/bookmark/article/chat/usage/settings 等。
- [ ] 验收标准可测试（不是“体验好”这种空话）。
- [ ] 涉及 AI 的功能已明确：模型、scene、source、限额、usage。

## 2. 后端 DoD

### 结构

- [ ] 代码在 `module/{module}/` 下，目录符合 controller/service/impl/mapper/domain/support。
- [ ] 没有把 `admin` 业务放进 user-api。
- [ ] 没有跨模块直接访问 Mapper/Entity。
- [ ] common 没有业务逻辑。

### 分层

- [ ] Controller 只做 HTTP/校验/鉴权/响应。
- [ ] Service 接口表达业务契约。
- [ ] Service Impl 有事务、归属校验、幂等。
- [ ] Mapper 只做 SQL。
- [ ] Entity 不出接口，DTO/VO 分离。

### 类型与数据

- [ ] 请求/响应 DTO 字段与 `docs/api.md` 一致。
- [ ] 枚举/状态机集中定义。
- [ ] token 用 long/BIGINT，金额用 BigDecimal/DECIMAL。
- [ ] 时间字段统一时区。
- [ ] 分页/排序/筛选有明确参数。
- [ ] 唯一键/索引与查询匹配。
- [ ] 软删除查询默认排除。
- [ ] 迁移 SQL 可重复执行/有回滚说明。

### 权限与安全

- [ ] 每个用户资源查询/写入都带 `user_id`。
- [ ] 管理端接口只接受管理员 token。
- [ ] 禁用用户不能调用。
- [ ] API Key 不进前端/日志。
- [ ] SSRF 防护（URL 抓取）。
- [ ] 提示词注入防护（网页正文分隔）。
- [ ] XSS/HTML 清洗。
- [ ] CORS 白名单。

### AI / 用量

- [ ] 所有 AI 调用经过统一 Gateway。
- [ ] 记录 request_id/source/scene/model/tokens/price/cost/latency/status。
- [ ] 失败/取消也记录。
- [ ] 调用前检查配额/预算/限流。
- [ ] 超限返回明确错误。
- [ ] 单价快照，不随改价变化。
- [ ] 插件来源可单独统计。

### 异步 / SSE

- [ ] 状态机完整，非法流转被拒绝。
- [ ] 重试/退避/死信/幂等。
- [ ] 重启可恢复。
- [ ] SSE 事件契约完整。
- [ ] 客户端断开/取消释放资源。
- [ ] 超时/模型错误有 error 事件。

### 测试

- [ ] 单测覆盖关键规则（成本/配额/状态机/URL/解析）。
- [ ] 集成测试覆盖主链路与权限。
- [ ] 失败/空/重复/超时/取消/超额有测试。
- [ ] 测试可本地一键运行。

## 3. 前端 DoD

- [ ] 页面/组件归属正确（views/components/composables）。
- [ ] API 走统一 client，不在组件里裸 fetch/axios。
- [ ] 请求/响应类型与 api.md 一致。
- [ ] 无 `any`；props/emits 有类型。
- [ ] 加载态、空状态、错误态齐全。
- [ ] 401/403/429/网络错误有处理。
- [ ] 表单校验/提交中禁用/防重复提交。
- [ ] SSE 流式渲染、取消、错误处理。
- [ ] 引用点击能跳原文/高亮。
- [ ] 复用现有组件/composable，没有复制粘贴。
- [ ] 视觉符合 v0.6（纸感、细边框、排版驱动）。
- [ ] 窄屏可用，不横向溢出。
- [ ] 关键交互有测试（或手工验收步骤）。

## 4. 插件 DoD

- [ ] 权限最小化：bookmarks/storage/activeTab/scripting + 后端 host。
- [ ] content script 只注入 http/https，受限页面跳过。
- [ ] 悬浮球用 Shadow DOM 隔离。
- [ ] token 存 chrome.storage.local，不存密码。
- [ ] 未登录显示连接入口，不暴露资料库。
- [ ] 书签导入分批 ≤ 50，去重/状态/失败可见。
- [ ] 当前页提取 Readability + 回退 innerText。
- [ ] SPA 路由变化更新上下文。
- [ ] 消息类型集中定义，校验来源。
- [ ] 不执行远程 JS；Vue 打包进插件或 iframe。
- [ ] 退出登录清理 token。

## 5. 安全 DoD

- [ ] 没有明文密钥/密码进入仓库。
- [ ] 日志没有 token/密码/API Key/完整正文。
- [ ] 用户资源全部归属校验。
- [ ] 管理端/前台 token 隔离。
- [ ] SSRF/提示词注入/XSS/CORS 已处理。
- [ ] 错误响应不泄露堆栈/SQL。
- [ ] 文件上传有类型/大小限制。
- [ ] 依赖没有明显高危漏洞（如可用 `npm audit`/`mvn dependency-check`）。

## 6. 文档 DoD

- [ ] schema.md 与实际表一致。
- [ ] api.md 与实际接口一致。
- [ ] 新错误码已补。
- [ ] 新状态/枚举已补。
- [ ] 新配置项已补（`.env.example` / 部署文档）。
- [ ] 迁移 SQL 已补。
- [ ] 需求变更已更新 requirements.md 或决策记录。
- [ ] 前端类型与 api.md 一致。

## 7. 交付 DoD

- [ ] 代码可构建：后端 `mvn package`，前端 `npm run build`。
- [ ] lint/format 通过。
- [ ] 测试通过。
- [ ] Docker Compose 可启动（如涉及）。
- [ ] 健康检查通过。
- [ ] 关键流程手工走通：
  - 注册/登录
  - 书签导入
  - 资料库/详情
  - 问答 + SSE + 引用
  - 插件收藏
  - 后台用量/配额
- [ ] README/演示脚本已更新（如涉及）。
- [ ] 没有临时调试代码/TODO 遗留（或明确记录）。
- [ ] 变更说明/已知问题写清楚。

## 8. Review 输出模板

```md
## 变更摘要

- 

## 文档同步

- [ ] schema.md
- [ ] api.md
- [ ] requirements.md / 决策记录

## 测试

- 命令：
- 结果：

## 自检

- [ ] 目录/分层
- [ ] 类型
- [ ] 归属/权限
- [ ] 配额/usage
- [ ] 安全
- [ ] 空/加载/错误态
- [ ] 测试
- [ ] 构建/lint

## 已知缺口 / 偏离

- 

## 结论

- [ ] 可以合并
- [ ] 需要补文档
- [ ] 需要补测试
- [ ] 需要修安全/权限
```

## 9. 发布前额外检查

- [ ] 环境变量齐全（DB/Redis/JWT/AI/OSS）。
- [ ] `ADMIN_JWT_SECRET` 与 `USER_JWT_SECRET` 不同。
- [ ] 管理员 seed 已执行，默认密码已修改。
- [ ] CORS 只放实际域名/扩展 ID。
- [ ] 生产关闭 mock 登录/调试接口。
- [ ] HTTPS。
- [ ] 数据库备份/迁移脚本。
- [ ] 日志/监控/告警可用。
- [ ] 配额/预算阈值符合预期。
- [ ] 插件商店隐私政策/权限说明（如发布）。
