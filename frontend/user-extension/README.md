# 拾页浏览器插件（骨架）

## 本地加载

1. Chrome/Edge 打开 `chrome://extensions`（Edge 为 `edge://extensions`）。
2. 打开“开发者模式”。
3. 点击“加载已解压的扩展程序”，选择本目录 `frontend/user-extension`。
4. 打开任意 `http/https` 网页，右下角会出现“拾”悬浮球。

## 已接通

- MV3 manifest
- background service worker
- content script 悬浮球（Shadow DOM）
- popup：悬浮球开关、检查后端
- options：读取书签、统计文件夹/链接数、后端地址

## 后续接入

- Vue 面板与资料库问答
- 当前页面问答
- 收藏当前页
- 书签文件夹选择与分批导入
- 登录授权码 / token 存储
- SSE 流式与引用卡片
