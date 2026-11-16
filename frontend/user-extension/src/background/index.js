// 拾页插件 background service worker（骨架）
// 负责：默认设置、消息转发、后续书签同步/登录态/token 管理。

const DEFAULTS = {
  ballEnabled: true,
  defaultMode: 'LIBRARY',
  defaultScope: 'ALL',
  apiBaseUrl: 'http://localhost:18080/api/v1',
}

chrome.runtime.onInstalled.addListener(async () => {
  const current = await chrome.storage.local.get(DEFAULTS)
  await chrome.storage.local.set({ ...DEFAULTS, ...current })
  console.log('[shiye] installed, defaults ready')
})

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'OPEN_OPTIONS') {
    chrome.runtime.openOptionsPage()
    sendResponse({ ok: true })
    return true
  }

  if (message?.type === 'PING') {
    sendResponse({ ok: true, service: 'shiye-extension' })
    return true
  }

  return false
})
