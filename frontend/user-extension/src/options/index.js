const DEFAULT_API = 'http://localhost:18080/api/v1'
const BATCH_SIZE = 50

const els = {
  backendState: document.getElementById('backendState'),
  authState: document.getElementById('authState'),
  authConnected: document.getElementById('authConnected'),
  authDisconnected: document.getElementById('authDisconnected'),
  userAvatar: document.getElementById('userAvatar'),
  userName: document.getElementById('userName'),
  userEmail: document.getElementById('userEmail'),
  authCode: document.getElementById('authCode'),
  authMessage: document.getElementById('authMessage'),
  connect: document.getElementById('connect'),
  disconnect: document.getElementById('disconnect'),
  loadBookmarks: document.getElementById('loadBookmarks'),
  bookmarkHint: document.getElementById('bookmarkHint'),
  folderPanel: document.getElementById('folderPanel'),
  folderList: document.getElementById('folderList'),
  selectedFolder: document.getElementById('selectedFolder'),
  selectedFolderMeta: document.getElementById('selectedFolderMeta'),
  recursive: document.getElementById('recursive'),
  autoSync: document.getElementById('autoSync'),
  startImport: document.getElementById('startImport'),
  importHint: document.getElementById('importHint'),
  importProgress: document.getElementById('importProgress'),
  importList: document.getElementById('importList'),
  refreshImports: document.getElementById('refreshImports'),
  ballEnabled: document.getElementById('ballEnabled'),
  apiBaseUrl: document.getElementById('apiBaseUrl'),
  openWeb: document.getElementById('openWeb'),
  version: document.getElementById('version'),
  saveApi: document.getElementById('saveApi'),
  saveResult: document.getElementById('saveResult'),
}

const state = {
  user: null,
  folders: [],
  selected: null,
  importing: false,
  resumableImportId: null,
  resumableFolderKey: null,
}

class ApiError extends Error {
  constructor(message, status, code) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function getSettings() {
  return chrome.storage.local.get({
    apiBaseUrl: DEFAULT_API,
    ballEnabled: true,
    autoSyncEnabled: false,
    autoSyncFolder: null,
    shiyeAccessToken: '',
    shiyeRefreshToken: '',
    shiyeUser: null,
  })
}

async function clearAuth() {
  await chrome.storage.local.remove(['shiyeAccessToken', 'shiyeRefreshToken', 'shiyeUser'])
}

async function requestNewToken() {
  const { apiBaseUrl, shiyeRefreshToken } = await getSettings()
  if (!shiyeRefreshToken) return false
  const response = await fetch(`${apiBaseUrl}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Client': 'extension' },
    body: JSON.stringify({ refreshToken: shiyeRefreshToken }),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || !payload || payload.code !== 0) return false
  await chrome.storage.local.set({
    shiyeAccessToken: payload.data.accessToken,
    shiyeRefreshToken: payload.data.refreshToken,
    shiyeUser: payload.data.user,
  })
  return true
}

async function apiRequest(path, options = {}, allowRefresh = true) {
  const { apiBaseUrl, shiyeAccessToken } = await getSettings()
  const headers = { 'Content-Type': 'application/json', 'X-Client': 'extension', ...(options.headers || {}) }
  if (shiyeAccessToken) {
    headers.Authorization = `Bearer ${shiyeAccessToken}`
  }
  const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers })
  if (response.status === 401 && allowRefresh && !path.startsWith('/auth/')) {
    const refreshed = await requestNewToken()
    if (refreshed) {
      return apiRequest(path, options, false)
    }
    await clearAuth()
    renderAuth(null)
    throw new ApiError('登录已失效，请重新生成授权码连接', 401, 40101)
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok || !payload || payload.code !== 0) {
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, response.status, payload?.code)
  }
  return payload.data
}

async function requestWithRetry(path, options, attempts = 2) {
  let lastError
  for (let attempt = 0; attempt <= attempts; attempt += 1) {
    try {
      return await apiRequest(path, options)
    } catch (error) {
      lastError = error
      if (error instanceof ApiError && error.status < 500) throw error
      if (attempt < attempts) await delay(600 * (attempt + 1))
    }
  }
  throw lastError
}

function isConnected() {
  return Boolean(state.user && state.user.email)
}

async function init() {
  const settings = await getSettings()
  els.ballEnabled.checked = settings.ballEnabled
  els.autoSync.checked = Boolean(settings.autoSyncEnabled)
  els.apiBaseUrl.value = settings.apiBaseUrl
  els.version.textContent = chrome.runtime.getManifest().version
  state.user = settings.shiyeUser
  renderAuth(settings.shiyeUser)
  await checkBackend()
  if (settings.shiyeAccessToken) {
    try {
      const me = await apiRequest('/users/me')
      state.user = me
      await chrome.storage.local.set({ shiyeUser: me })
      renderAuth(me)
    } catch {
      // 401 已清理本地令牌；其他错误保留缓存状态。
    }
  }
  await loadRecentImports()
}

function renderAuth(user) {
  state.user = user
  const connected = Boolean(user && user.email)
  els.authState.textContent = connected ? '已连接' : '未连接'
  els.authState.classList.toggle('ok', connected)
  els.authConnected.hidden = !connected
  els.authDisconnected.hidden = connected
  els.loadBookmarks.disabled = !connected
  els.startImport.disabled = !connected || !state.selected
  els.autoSync.disabled = !connected
  els.bookmarkHint.textContent = connected
    ? '点击「读取书签」选择要导入的文件夹。'
    : '连接账号后读取书签。'
  if (connected) {
    const name = user.displayName || user.email
    els.userName.textContent = name
    els.userEmail.textContent = user.email
    els.userAvatar.textContent = name.slice(0, 2).toUpperCase()
  }
}

async function checkBackend() {
  const { apiBaseUrl } = await getSettings()
  try {
    const response = await fetch(`${apiBaseUrl}/health`, { headers: { 'X-Client': 'extension' } })
    const payload = await response.json()
    if (response.ok && payload.code === 0) {
      els.backendState.textContent = '后端已连接'
      els.backendState.classList.add('ok')
    } else {
      els.backendState.textContent = '后端异常'
      els.backendState.classList.remove('ok')
    }
  } catch {
    els.backendState.textContent = '后端未连接'
    els.backendState.classList.remove('ok')
  }
}

async function connectAccount() {
  const code = els.authCode.value.trim().toUpperCase()
  if (!code) {
    showAuthError('请先粘贴授权码')
    return
  }
  els.connect.disabled = true
  els.connect.textContent = '连接中…'
  try {
    const data = await apiRequest(
      '/auth/extension-token',
      {
        method: 'POST',
        body: JSON.stringify({
          code,
          clientInfo: `Chrome Extension / ${navigator.userAgent}`,
        }),
      },
      false,
    )
    await chrome.storage.local.set({
      shiyeAccessToken: data.accessToken,
      shiyeRefreshToken: data.refreshToken,
      shiyeUser: data.user,
    })
    els.authCode.value = ''
    renderAuth(data.user)
    await loadRecentImports()
  } catch (error) {
    showAuthError(error.message || '连接失败')
  } finally {
    els.connect.disabled = false
    els.connect.textContent = '连接'
  }
}

function showAuthError(message) {
  els.authMessage.textContent = message
  els.authMessage.style.color = 'var(--danger)'
  setTimeout(() => {
    els.authMessage.style.color = ''
  }, 4000)
}

function showBookmarkError(message) {
  els.bookmarkHint.textContent = message
  els.bookmarkHint.style.color = 'var(--danger)'
  setTimeout(() => {
    els.bookmarkHint.style.color = ''
    els.bookmarkHint.textContent = '点击「读取书签」选择要导入的文件夹。'
  }, 4000)
}

async function disconnectAccount() {
  if (!window.confirm('断开后需要重新生成授权码才能导入书签，确定吗？')) return
  await clearAuth()
  state.user = null
  state.selected = null
  state.folders = []
  state.resumableImportId = null
  state.resumableFolderKey = null
  els.autoSync.checked = false
  await chrome.storage.local.set({ autoSyncEnabled: false, autoSyncFolder: null })
  renderAuth(null)
  els.folderPanel.hidden = true
  els.folderList.textContent = ''
  els.importList.textContent = ''
  els.importList.appendChild(hintNode('连接账号后显示。'))
}

function hintNode(text) {
  const p = document.createElement('p')
  p.className = 'hint'
  p.textContent = text
  return p
}

function getBookmarkTree() {
  return new Promise((resolve, reject) => {
    chrome.bookmarks.getTree((tree) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message))
        return
      }
      resolve(tree)
    })
  })
}

function countLinks(node) {
  let count = 0
  for (const child of node.children || []) {
    if (child.url) count += 1
    if (child.children) count += countLinks(child)
  }
  return count
}

function flattenFolders(tree) {
  const folders = []
  const walk = (nodes, depth, parentPath) => {
    for (const node of nodes) {
      if (node.url) continue
      const isRoot = !node.parentId
      const title = isRoot ? '全部书签' : node.title || '未命名文件夹'
      const path = isRoot ? title : parentPath ? `${parentPath}/${title}` : title
      folders.push({ id: node.id, title, path, depth, count: countLinks(node), node })
      if (node.children) {
        walk(node.children, isRoot ? 1 : depth + 1, isRoot ? '' : path)
      }
    }
  }
  walk(tree, 0, '')
  return folders
}

async function loadBookmarks() {
  if (!isConnected()) {
    showAuthError('请先连接账号')
    return
  }
  els.loadBookmarks.disabled = true
  els.loadBookmarks.textContent = '读取中…'
  try {
    const tree = await getBookmarkTree()
    state.folders = flattenFolders(tree)
    state.selected = state.folders.find((folder) => folder.count > 0 && folder.depth <= 1)
      || state.folders.find((folder) => folder.count > 0)
      || state.folders[0]
      || null
    els.folderPanel.hidden = false
    renderFolders()
    els.bookmarkHint.textContent = `共 ${state.folders.length} 个文件夹。`
    if (els.autoSync.checked) {
      await persistAutoSync()
    }
  } catch (error) {
    showBookmarkError(`读取书签失败：${error.message}`)
  } finally {
    els.loadBookmarks.disabled = false
    els.loadBookmarks.textContent = '读取书签'
  }
}

function renderFolders() {
  els.folderList.textContent = ''
  for (const folder of state.folders) {
    const row = document.createElement('button')
    row.type = 'button'
    row.className = 'folder-row'
    row.style.paddingLeft = `${12 + Math.min(folder.depth, 8) * 16}px`
    if (state.selected && state.selected.id === folder.id) {
      row.classList.add('active')
    }

    const icon = document.createElement('span')
    icon.className = 'folder-icon'
    icon.textContent = folder.id === '0' ? '⌂' : '▸'

    const name = document.createElement('span')
    name.textContent = folder.title

    const count = document.createElement('span')
    count.className = 'count'
    count.textContent = `${folder.count} 条`

    row.append(icon, name, count)
    row.addEventListener('click', () => {
      state.selected = folder
      renderFolders()
      updateSelectedFolder()
      if (els.autoSync.checked) {
        void persistAutoSync()
      }
    })
    els.folderList.appendChild(row)
  }
  updateSelectedFolder()
}

function updateSelectedFolder() {
  const folder = state.selected
  els.startImport.disabled = !isConnected() || !folder || state.importing
  if (!folder) {
    els.selectedFolder.textContent = '尚未选择文件夹'
    els.selectedFolderMeta.textContent = ''
    els.importHint.textContent = ''
    return
  }
  const links = collectLinks(folder, els.recursive.checked)
  els.selectedFolder.textContent = folder.path
  els.selectedFolderMeta.textContent = `${folder.count} 条书签 · 已选择`
  els.importHint.textContent = links.length
    ? `本次将导入 ${links.length} 条链接${els.recursive.checked ? '（含子文件夹）' : '（仅当前层）'}`
    : '当前范围没有可导入的 http/https 链接'
}

function normalizeForDedup(raw) {
  try {
    const url = new URL(raw)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    url.hash = ''
    const kept = []
    for (const [key, value] of url.searchParams.entries()) {
      const lower = key.toLowerCase()
      if (lower.startsWith('utm_')) continue
      if (['fbclid', 'gclid', 'yclid', 'mc_cid', 'mc_eid'].includes(lower)) continue
      kept.push([key, value])
    }
    kept.sort((a, b) => a[0].localeCompare(b[0]) || a[1].localeCompare(b[1]))
    url.search = ''
    for (const [key, value] of kept) {
      url.searchParams.append(key, value)
    }
    let path = url.pathname
    if (path.length > 1 && path.endsWith('/')) path = path.slice(0, -1)
    return `${url.protocol}//${url.host.toLowerCase()}${path}${url.search}`
  } catch {
    return null
  }
}

function collectLinks(folder, recursive) {
  const links = []
  const seen = new Set()
  const walk = (node, basePath) => {
    for (const child of node.children || []) {
      if (child.url) {
        const key = normalizeForDedup(child.url)
        if (!key || seen.has(key)) continue
        seen.add(key)
        links.push({
          title: child.title || child.url,
          url: child.url,
          parentPath: basePath,
        })
      } else if (recursive && child.children) {
        walk(child, `${basePath}/${child.title || '未命名文件夹'}`)
      }
    }
  }
  walk(folder.node, folder.path)
  return links
}

function detectBrowser() {
  return navigator.userAgent.includes('Edg/') ? 'EDGE' : 'CHROME'
}

async function persistAutoSync() {
  const enabled = els.autoSync.checked
  if (enabled && !state.selected) {
    els.autoSync.checked = false
    els.bookmarkHint.textContent = '先选择文件夹，再开启自动同步。'
    await chrome.storage.local.set({ autoSyncEnabled: false, autoSyncFolder: null })
    return
  }
  await chrome.storage.local.set({
    autoSyncEnabled: enabled,
    autoSyncFolder: enabled && state.selected
      ? {
          browser: detectBrowser(),
          folderId: state.selected.id,
          folderPath: state.selected.path,
          recursive: els.recursive.checked,
        }
      : null,
  })
}

function showProgress(message, isError = false) {
  els.importProgress.hidden = false
  els.importProgress.classList.toggle('error', isError)
  els.importProgress.textContent = message
}

async function startImport() {
  if (!isConnected()) {
    showAuthError('请先连接账号')
    return
  }
  const folder = state.selected
  if (!folder) {
    showBookmarkError('请先选择书签文件夹')
    return
  }
  const recursive = els.recursive.checked
  const links = collectLinks(folder, recursive)
  if (!links.length) {
    showProgress('当前范围没有可导入的 http/https 链接。', true)
    return
  }

  state.importing = true
  els.startImport.disabled = true
  els.startImport.textContent = '导入中…'
  const folderKey = `${detectBrowser()}:${folder.id}`
  let importId = state.resumableFolderKey === folderKey ? state.resumableImportId : null
  const totalBatches = Math.ceil(links.length / BATCH_SIZE)
  let lastTotal = 0

  try {
    for (let start = 0; start < links.length; start += BATCH_SIZE) {
      const batch = links.slice(start, start + BATCH_SIZE)
      const batchNo = Math.floor(start / BATCH_SIZE) + 1
      showProgress(`正在提交第 ${batchNo} / ${totalBatches} 批（${start + 1}-${start + batch.length} 条）…`)
      const payload = {
        folder: {
          browser: detectBrowser(),
          folderId: folder.id,
          folderPath: folder.path,
          recursive,
        },
        items: batch.map((item) => ({
          title: item.title,
          url: item.url,
          parentPath: item.parentPath,
        })),
      }
      if (importId) payload.importId = importId
      const data = await requestWithRetry('/bookmark-imports', {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify(payload),
      })
      importId = data.importId
      lastTotal = data.total
      state.resumableImportId = importId
      state.resumableFolderKey = folderKey
      showProgress(`已提交 ${Math.min(start + batch.length, links.length)} / ${links.length} 条；任务 #${importId} 当前共 ${lastTotal} 条`)
    }

    state.resumableImportId = null
    state.resumableFolderKey = null
    showProgress(
      `导入任务 #${importId} 已创建，共 ${lastTotal} 条。当前状态为排队中，后台抓取/切片/向量化将在下一阶段接入。`,
    )
    await loadRecentImports()
  } catch (error) {
    if (importId) {
      state.resumableImportId = importId
      state.resumableFolderKey = folderKey
      showProgress(
        `第 ${Math.floor(lastTotal / BATCH_SIZE) + 1} 批附近失败：${error.message}。任务 #${importId} 已保留，重新点击「开始导入」会继续追加。`,
        true,
      )
    } else {
      showProgress(`导入失败：${error.message}`, true)
    }
  } finally {
    state.importing = false
    els.startImport.textContent = '开始导入'
    updateSelectedFolder()
  }
}

function statusClass(status) {
  return String(status || '').toLowerCase().replace(/[^a-z]/g, '')
}

function badgeNode(status) {
  const badge = document.createElement('span')
  badge.className = `badge ${statusClass(status)}`
  badge.textContent = status
  return badge
}

function formatTime(value) {
  if (!value) return '-'
  const text = String(value)
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  const date = new Date(hasZone ? text : `${text}Z`)
  if (Number.isNaN(date.getTime())) {
    return text.replace('T', ' ').slice(0, 16)
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

async function loadRecentImports() {
  if (!isConnected()) {
    els.importList.textContent = ''
    els.importList.appendChild(hintNode('连接账号后显示。'))
    return
  }
  els.refreshImports.disabled = true
  try {
    const data = await apiRequest('/bookmark-imports?page=1&pageSize=5')
    els.importList.textContent = ''
    if (!data.items.length) {
      els.importList.appendChild(hintNode('还没有导入任务。读取书签并选择一个文件夹开始。'))
      return
    }
    for (const item of data.items) {
      els.importList.appendChild(renderImportRow(item))
    }
  } catch (error) {
    els.importList.textContent = ''
    els.importList.appendChild(hintNode(`读取导入任务失败：${error.message}`))
  } finally {
    els.refreshImports.disabled = false
  }
}

function renderImportRow(item) {
  const row = document.createElement('div')
  row.className = 'import-row'

  const main = document.createElement('div')
  main.className = 'import-main'

  const id = document.createElement('b')
  id.textContent = `#${item.id}`
  const path = document.createElement('span')
  path.className = 'path'
  path.textContent = item.folderPath
  path.title = item.folderPath
  main.append(id, path, badgeNode(item.status))

  const actions = document.createElement('span')
  actions.className = 'actions'

  const detailButton = document.createElement('button')
  detailButton.type = 'button'
  detailButton.className = 'ghost small'
  detailButton.textContent = '查看条目'
  actions.appendChild(detailButton)

  if (['PENDING', 'RUNNING', 'PAUSED'].includes(item.status)) {
    const cancelButton = document.createElement('button')
    cancelButton.type = 'button'
    cancelButton.className = 'danger small'
    cancelButton.textContent = '取消'
    cancelButton.addEventListener('click', () => cancelImport(item.id))
    actions.appendChild(cancelButton)
  }
  if (['DONE', 'FAILED'].includes(item.status) && item.failed > 0) {
    const retryButton = document.createElement('button')
    retryButton.type = 'button'
    retryButton.className = 'small'
    retryButton.textContent = '重试失败项'
    retryButton.addEventListener('click', () => retryImport(item.id))
    actions.appendChild(retryButton)
  }

  main.appendChild(actions)

  const meta = document.createElement('div')
  meta.className = 'import-meta'
  meta.textContent = `总数 ${item.total} · 成功 ${item.success} · 失败 ${item.failed} · 跳过 ${item.skipped} · 创建于 ${formatTime(item.createdAt)}`
  if (item.errorCode) {
    const err = document.createElement('span')
    err.textContent = `任务错误：${item.errorCode}`
    meta.appendChild(err)
  }

  const itemsBox = document.createElement('div')
  itemsBox.className = 'import-items'
  itemsBox.hidden = true

  detailButton.addEventListener('click', async () => {
    if (!itemsBox.hidden) {
      itemsBox.hidden = true
      detailButton.textContent = '查看条目'
      return
    }
    detailButton.textContent = '加载中…'
    try {
      const data = await apiRequest(`/bookmark-imports/${item.id}/items?page=1&pageSize=50`)
      itemsBox.textContent = ''
      if (!data.items.length) {
        itemsBox.appendChild(hintNode('暂无条目。'))
      } else {
        for (const entry of data.items) {
          itemsBox.appendChild(renderItemRow(entry))
        }
        if (data.total > data.items.length) {
          itemsBox.appendChild(hintNode(`仅显示前 ${data.items.length} / ${data.total} 条。`))
        }
      }
      itemsBox.hidden = false
      detailButton.textContent = '收起条目'
    } catch (error) {
      detailButton.textContent = '查看条目'
      itemsBox.textContent = ''
      itemsBox.hidden = false
      itemsBox.appendChild(hintNode(`加载条目失败：${error.message}`))
    }
  })

  row.append(main, meta, itemsBox)
  return row
}

function renderItemRow(entry) {
  const row = document.createElement('div')
  row.className = 'item-row'
  row.appendChild(badgeNode(entry.status))

  const link = document.createElement('a')
  link.className = 'title'
  if (/^https?:\/\//i.test(entry.url)) {
    link.href = entry.url
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
  }
  link.textContent = entry.title || entry.url
  link.title = entry.url
  row.appendChild(link)

  if (entry.errorMessage) {
    const error = document.createElement('span')
    error.className = 'err'
    error.textContent = `${entry.errorCode || 'ERROR'}：${entry.errorMessage}`
    row.appendChild(error)
  }
  return row
}

async function cancelImport(id) {
  if (!window.confirm(`取消导入任务 #${id}？未处理的条目会标记为跳过。`)) return
  try {
    await apiRequest(`/bookmark-imports/${id}/cancel`, { method: 'POST', body: '{}' })
    await loadRecentImports()
  } catch (error) {
    window.alert(`取消失败：${error.message}`)
  }
}

async function retryImport(id) {
  try {
    const data = await apiRequest(`/bookmark-imports/${id}/retry`, {
      method: 'POST',
      body: '{}',
    })
    window.alert(`已重新入队 ${data.retried} 条失败项。`)
    await loadRecentImports()
  } catch (error) {
    window.alert(`重试失败：${error.message}`)
  }
}

els.connect.addEventListener('click', connectAccount)
els.authCode.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') connectAccount()
})
els.disconnect.addEventListener('click', disconnectAccount)
els.loadBookmarks.addEventListener('click', loadBookmarks)
els.startImport.addEventListener('click', startImport)
els.recursive.addEventListener('change', () => {
  updateSelectedFolder()
  if (els.autoSync.checked) {
    void persistAutoSync()
  }
})
els.autoSync.addEventListener('change', () => {
  void persistAutoSync()
})
els.refreshImports.addEventListener('click', loadRecentImports)
els.ballEnabled.addEventListener('change', () => {
  chrome.storage.local.set({ ballEnabled: els.ballEnabled.checked })
})
els.saveApi.addEventListener('click', async () => {
  const value = els.apiBaseUrl.value.trim().replace(/\/+$/, '')
  if (!/^https?:\/\//.test(value)) {
    els.saveResult.textContent = '请输入 http/https 地址'
    return
  }
  let origin
  try {
    origin = new URL(value).origin + '/*'
  } catch {
    els.saveResult.textContent = '地址格式不正确'
    return
  }
  try {
    const granted = await chrome.permissions.request({ origins: [origin] })
    if (!granted) {
      els.saveResult.textContent = '未获得该域名的访问权限，无法保存'
      return
    }
  } catch {
    els.saveResult.textContent = '无法请求域名权限，请检查地址'
    return
  }
  await chrome.storage.local.set({ apiBaseUrl: value })
  els.saveResult.textContent = '已保存，正在检测后端…'
  await checkBackend()
  els.saveResult.textContent = '已保存'
})

els.openWeb.addEventListener('click', async () => {
  const { apiBaseUrl } = await getSettings()
  try {
    window.open(new URL(apiBaseUrl).origin, '_blank', 'noopener')
  } catch {
    window.open('http://localhost:15173', '_blank', 'noopener')
  }
})

init()
