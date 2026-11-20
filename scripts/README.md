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

安装 k6 后：

```bash
k6 run -e BASE_URL=http://localhost:18080/api/v1 -e VUS=20 -e DURATION=30s scripts/load-test.js
```

默认阈值：

- 请求失败率 < 1%
- P95 < 500ms

## 集成 smoke

CI 的 `integration-smoke` job 已覆盖注册、refresh 轮换、扩展授权码、书签导入和登出。
