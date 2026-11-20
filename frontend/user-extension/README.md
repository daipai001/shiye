# 拾页浏览器插件

MV3 插件，连接拾页 user-api，提供悬浮球资料库问答、当前页问答、收藏当前页和书签导入/自动同步。

## 本地加载

1. Chrome/Edge 打开 `chrome://extensions`（Edge 为 `edge://extensions`）。
2. 打开“开发者模式”。
3. 点击“加载已解压的扩展程序”，选择 `frontend/user-extension`。
4. 打开插件设置，确认后端地址：
   - 本地：`http://localhost:18080/api/v1`
   - 线上：`https://你的域名/api/v1`
5. 在拾页网页端「插件」页生成一次性授权码，回到插件设置粘贴连接。

## 已接通

- 授权码登录、refresh token 轮换
- 书签文件夹读取、分批导入、暂停/恢复/取消、失败重试
- 主文件夹新增/修改/移动/删除自动同步
- 悬浮球：资料库问答、当前页问答、通用问答、收藏当前页、站点黑名单、默认模式/范围、快捷键
- 服务端插件偏好（悬浮球开关、默认范围、站点黑名单、快捷键）
- 后端地址可配置；非 localhost 域名首次保存时请求 host 权限
- popup：连接状态、悬浮球开关、打开网页端/设置、后端检查

## 打包

```powershell
.\scripts\package-extension.ps1
```

```bash
./scripts/package-extension.sh
```

产物：`dist/shiye-extension-<version>.zip`。

## 发布说明

见 `docs/extension-release.md`。
