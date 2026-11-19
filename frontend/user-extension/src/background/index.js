// 拾页插件 background service worker
// 负责：默认设置、消息转发、登录态刷新、书签新增/修改自动同步。

const DEFAULTS = {
  ballEnabled: true,
  defaultMode: 'LIBRARY',
  defaultScope: 'ALL',
  apiBaseUrl: 'http://localhost:18080/api/v1',
  autoSyncEnabled: false,
  autoSyncFolder: null,
  shiyeAccessToken: '',
  shiyeRefreshToken: '',
}

const pendingSync = new Set()

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

function getSettings() {
  return chrome.storage.local.get(DEFAULTS)
}

function getBookmark(id) {
  return new Promise((resolve) => {
    chrome.bookmarks.get(id, (nodes) => {
      if (chrome.runtime.lastError) {
        resolve(null)
        return
      }
      resolve(nodes && nodes[0] ? nodes[0] : null)
    })
  })
}

async function isUnderFolder(parentId, folderId) {
  if (!folderId || folderId === '0') {
    return true
  }
  let current = parentId
  while (current) {
    if (current === folderId) {
      return true
    }
    const node = await getBookmark(current)
    current = node?.parentId || null
  }
  return false
}

async function refreshToken(settings) {
  if (!settings.shiyeRefreshToken) {
    return null
  }
  try {
    const response = await fetch(`${settings.apiBaseUrl}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Client': 'extension' },
      body: JSON.stringify({ refreshToken: settings.shiyeRefreshToken }),
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok || !payload || payload.code !== 0) {
      return null
    }
    const next = {
      shiyeAccessToken: payload.data.accessToken,
      shiyeRefreshToken: payload.data.refreshToken,
      shiyeUser: payload.data.user,
    }
    await chrome.storage.local.set(next)
    return next
  } catch {
    return null
  }
}

async function postBookmark(settings, folder, bookmark, retried = false) {
  const body = JSON.stringify({
    folder: {
      browser: folder.browser || 'CHROME',
      folderId: folder.folderId,
      folderPath: folder.folderPath || '',
      recursive: Boolean(folder.recursive),
    },
    items: [
      {
        title: bookmark.title || bookmark.url,
        url: bookmark.url,
        parentPath: folder.folderPath || '',
      },
    ],
  })
  const response = await fetch(`${settings.apiBaseUrl}/bookmark-imports`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Client': 'extension',
      'Idempotency-Key': crypto.randomUUID(),
      Authorization: `Bearer ${settings.shiyeAccessToken}`,
    },
    body,
  })
  if (response.status === 401 && !retried) {
    const refreshed = await refreshToken(settings)
    if (refreshed) {
      return postBookmark({ ...settings, ...refreshed }, folder, bookmark, true)
    }
  }
  if (!response.ok) {
    throw new Error(`bookmark import failed: HTTP ${response.status}`)
  }
}

async function syncBookmark(bookmark) {
  if (!bookmark || !/^https?:\/\//i.test(bookmark.url || '')) {
    return
  }
  const settings = await getSettings()
  const folder = settings.autoSyncFolder
  if (!settings.autoSyncEnabled || !folder || !settings.shiyeAccessToken) {
    return
  }
  if (!(await isUnderFolder(bookmark.parentId, folder.folderId))) {
    return
  }
  if (pendingSync.has(bookmark.id)) {
    return
  }
  pendingSync.add(bookmark.id)
  try {
    await postBookmark(settings, folder, bookmark)
  } catch (error) {
    console.warn('[shiye] auto sync failed:', error.message)
  } finally {
    pendingSync.delete(bookmark.id)
  }
}

chrome.bookmarks.onCreated.addListener((_id, bookmark) => {
  void syncBookmark(bookmark).catch((error) => {
    console.warn('[shiye] auto sync failed:', error.message)
  })
})

function syncById(id) {
  void getBookmark(id)
    .then((bookmark) => syncBookmark(bookmark))
    .catch((error) => {
      console.warn('[shiye] auto sync failed:', error.message)
    })
}

chrome.bookmarks.onChanged.addListener(syncById)
chrome.bookmarks.onMoved.addListener(syncById)
