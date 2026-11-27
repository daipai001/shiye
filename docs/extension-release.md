# 浏览器插件发布说明

## 先回答：插件一定要部署到服务器吗？

分开看：

1. **插件文件本身**
   - 自己用 / 内测：不需要服务器。Chrome/Edge「加载已解压的扩展程序」即可。
   - 给别人用：推荐 Chrome Web Store / Edge Add-ons，商店托管插件文件并负责更新，不需要自己部署插件文件服务器。
2. **拾页后端 API**
   - 自己用：本机 `http://localhost:18080/api/v1` 即可。
   - 给别人用：**必须有公网可访问的 HTTPS 后端**，否则别人无法登录、导入书签或问答。
3. **自动更新**
   - 上架商店：商店自动更新。
   - 私有分发：可发 zip 手动加载；自建 CRX 自动更新需要签名、update_url 和托管，不推荐。

所以：**插件不一定要自己部署到服务器，但公开给别人使用时，后端必须部署到服务器。**

## 本地加载

1. 打开 `chrome://extensions` 或 `edge://extensions`。
2. 开启开发者模式。
3. 加载 `frontend/user-extension`。
4. 设置页填写 `http://localhost:18080/api/v1`。
5. 网页端「插件」页生成授权码，粘贴连接。

## 生产环境配置

1. 后端部署到 HTTPS 域名，例如 `https://shiye.myhyw.asia`。
2. 配置 `CORS_ALLOWED_ORIGINS`：
   ```text
   https://shiye.myhyw.asia,chrome-extension://*
   ```
3. 插件设置页填写 `https://shiye.myhyw.asia/api/v1`。
4. 首次保存会请求该域名的 host 权限；用户同意后才能访问。

## 打包

```powershell
.\scripts\package-extension.ps1
```

```bash
./scripts/package-extension.sh
```

产物：`dist/shiye-extension-<version>.zip`。

## 上架检查清单

- [ ] 修改 `manifest.json` 版本号并重新打包
- [ ] 128/48/32/16 图标（`icons/`）
- [ ] 商店截图：资料库问答、当前页问答、书签导入、设置页
- [ ] 隐私政策 URL
- [ ] 权限说明：bookmarks、storage、activeTab/scripting、host permissions
- [ ] 单一用途说明：收藏与问答
- [ ] 测试账号或审核说明
- [ ] 后端 HTTPS 可用，CORS 包含商店扩展来源

## 权限说明

- `bookmarks`：读取用户选择的浏览器书签文件夹，导入拾页。
- `storage`：保存 token、后端地址、悬浮球开关和插件偏好。
- `activeTab` / `scripting`：在用户主动使用时读取当前页正文。
- `host_permissions`：本地开发 API。
- `optional_host_permissions`：用户配置自建 HTTPS 后端时按需授权。
- `content_scripts`：在网页注入悬浮球；站点黑名单可关闭。

## 隐私

- 不保存用户密码；授权码换取 token，token 存在 `chrome.storage.local`。
- 当前页正文只在用户主动点击“问当前页”或“收藏当前页”时发送。
- 站点黑名单中的域名不注入悬浮球。
- 插件不包含远程代码，所有逻辑随扩展包发布。
