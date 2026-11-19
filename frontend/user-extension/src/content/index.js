const HOST_ID = 'shiye-floating-host'
const DEFAULTS = {
  ballEnabled: true,
  apiBaseUrl: 'http://localhost:18080/api/v1',
  shiyeAccessToken: '',
  shiyeRefreshToken: '',
  shiyeUser: null,
}

let conversationId = null
let streaming = false

function getSettings() {
  return chrome.storage.local.get(DEFAULTS)
}

function extractPageContent() {
  try {
    const clone = document.cloneNode(true)
    clone
      .querySelectorAll('script,style,noscript,iframe,svg,canvas,nav,header,footer,aside,form')
      .forEach((node) => node.remove())
    const root = clone.querySelector('article,main,[role=main]') || clone.body
    const text = (root && root.innerText) || document.body.innerText || ''
    return text
      .replace(/\u00A0/g, ' ')
      .replace(/\r\n/g, '\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
      .slice(0, 500000)
  } catch {
    return ''
  }
}

function mount() {
  if (document.getElementById(HOST_ID)) return

  const host = document.createElement('div')
  host.id = HOST_ID
  host.style.cssText = 'position:fixed;right:24px;bottom:24px;z-index:2147483647;'
  const shadow = host.attachShadow({ mode: 'open' })
  shadow.innerHTML = `
    <style>
      * { box-sizing: border-box; }
      .ball {
        width: 52px; height: 52px; border-radius: 50%;
        border: 0; background: #1c1c1a; color: #fff;
        font: 700 16px/1 "Segoe UI", "Microsoft YaHei", sans-serif;
        cursor: pointer; box-shadow: 0 12px 30px rgba(28,28,26,.22);
      }
      .panel {
        position: absolute; right: 0; bottom: 64px; width: 380px;
        max-height: 580px; display: flex; flex-direction: column;
        background: #fff; border: 1px solid #e6e4df; border-radius: 14px;
        box-shadow: 0 18px 48px rgba(28,28,26,.16);
        font: 13px/1.6 "Segoe UI", "Microsoft YaHei", sans-serif;
        color: #1c1c1a; overflow: hidden;
      }
      .head {
        display: flex; align-items: center; gap: 8px;
        padding: 11px 13px; border-bottom: 1px solid #e6e4df;
        background: #fbfbf9; flex: none;
      }
      .brand { font-weight: 800; font-size: 14px; }
      .brand span { color: #9a9a94; font-size: 9px; font-weight: 400; margin-left: 4px; }
      .head .email { margin-left: auto; color: #9a9a94; font-size: 9.5px; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .icon { border: 0; background: transparent; color: #9a9a94; cursor: pointer; font-size: 14px; padding: 2px 4px; }
      .auth-view { padding: 18px 14px; }
      .auth-view p { color: #6f6f6a; font-size: 11.5px; line-height: 1.7; margin: 0 0 12px; }
      .main-view { display: flex; flex-direction: column; min-height: 0; }
      .tabs { display: flex; gap: 4px; padding: 8px 10px 0; background: #fbfbf9; flex: none; }
      .tabs button {
        flex: 1; border: 1px solid transparent; background: transparent; color: #9a9a94;
        border-radius: 7px; padding: 6px 0; font: inherit; font-size: 11px; cursor: pointer;
      }
      .tabs button.active { background: #fff; border-color: #e6e4df; color: #1c1c1a; font-weight: 600; }
      .pane { padding: 10px 12px 12px; display: flex; flex-direction: column; min-height: 0; }
      .mode-switch { display: flex; gap: 5px; margin-bottom: 7px; flex: none; }
      .mode-switch button {
        border: 1px solid #e6e4df; background: #fff; color: #9a9a94;
        border-radius: 999px; padding: 2px 9px; font: inherit; font-size: 10px;
        line-height: 1.6; cursor: pointer;
      }
      .mode-switch button.active { background: #1c1c1a; border-color: #1c1c1a; color: #fff; }
      .messages { flex: 1; overflow-y: auto; max-height: 320px; min-height: 80px; padding: 2px 0; }
      .msg { margin-bottom: 9px; }
      .msg .role { color: #9a9a94; font-size: 9px; margin-bottom: 3px; }
      .msg.user .bubble { background: #f1f0ec; margin-left: 30px; }
      .msg.assistant .bubble { background: #fbfbf9; border: 1px solid #e6e4df; }
      .bubble { border-radius: 9px; padding: 7px 9px; font-size: 11.5px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
      .citations { display: grid; gap: 5px; margin-top: 6px; }
      .cite { border: 1px solid #e6e4df; border-radius: 6px; padding: 5px 7px; background: #fff; }
      .cite b { display: block; color: #5b5bd6; font-size: 9px; font-weight: 700; }
      .cite span { display: block; color: #6f6f6a; font-size: 10px; line-height: 1.55; margin-top: 2px; }
      .composer { display: flex; gap: 7px; align-items: flex-end; margin-top: 8px; flex: none; }
      .composer textarea {
        flex: 1; resize: none; height: 58px; border: 1px solid #e6e4df; border-radius: 8px;
        padding: 7px 9px; font: inherit; font-size: 11.5px; line-height: 1.55; outline: none;
        color: #1c1c1a; background: #fff;
      }
      .composer textarea:focus { border-color: #5b5bd6; box-shadow: 0 0 0 2px rgba(91,91,214,.08); }
      .btn { border: 1px solid #e6e4df; background: #fff; color: #1c1c1a; border-radius: 7px; padding: 7px 11px; font: inherit; font-size: 11px; cursor: pointer; }
      .btn.primary { background: #1c1c1a; border-color: #1c1c1a; color: #fff; }
      .btn:disabled { opacity: .5; cursor: default; }
      .hint { color: #9a9a94; font-size: 9px; margin: 7px 0 0; line-height: 1.5; }
      .save-pane p { font-size: 11px; line-height: 1.65; color: #6f6f6a; margin: 0 0 7px; }
      .save-title { color: #1c1c1a !important; font-weight: 600; }
      .save-meta { color: #9a9a94 !important; font-size: 9.5px !important; }
      .save-result { margin-top: 9px !important; }
      .save-result.ok { color: #16794f !important; }
      .save-result.error { color: #b3372f !important; }
      .hidden { display: none !important; }
    </style>
    <button class="ball" title="打开拾页">拾</button>
    <div class="panel hidden">
      <div class="head">
        <div class="brand">拾页 <span>ShiYe</span></div>
        <span class="email"></span>
        <button class="icon close" title="关闭">✕</button>
      </div>
      <div class="auth-view hidden">
        <p>先连接账号，才能使用资料库问答和收藏当前页。连接只需要一次授权码。</p>
        <button class="btn primary" data-action="open-options">打开设置连接</button>
      </div>
      <div class="main-view hidden">
        <div class="tabs">
          <button data-tab="ask" class="active">资料库问答</button>
          <button data-tab="save">收藏此页</button>
        </div>
        <div class="pane ask-pane">
          <div class="mode-switch">
            <button type="button" data-ask-mode="library" class="active">资料库</button>
            <button type="button" data-ask-mode="page">当前页</button>
          </div>
          <div class="messages"></div>
          <form class="composer">
            <textarea placeholder="问我收藏的资料…" maxlength="4000"></textarea>
            <button type="submit" class="btn primary">发送</button>
          </form>
          <p class="hint ask-hint">回答基于你的资料库，并附原文引用。</p>
        </div>
        <div class="pane save-pane hidden">
          <p class="save-title"></p>
          <p class="save-meta"></p>
          <button class="btn primary save-btn" type="button">收藏并索引当前页</button>
          <p class="save-result"></p>
        </div>
      </div>
    </div>
  `

  const ball = shadow.querySelector('.ball')
  const panel = shadow.querySelector('.panel')
  const close = shadow.querySelector('.close')
  const authView = shadow.querySelector('.auth-view')
  const mainView = shadow.querySelector('.main-view')
  const emailEl = shadow.querySelector('.email')
  const tabs = [...shadow.querySelectorAll('[data-tab]')]
  const askPane = shadow.querySelector('.ask-pane')
  const savePane = shadow.querySelector('.save-pane')
  const messagesEl = shadow.querySelector('.messages')
  const composer = shadow.querySelector('.composer')
  const textarea = shadow.querySelector('textarea')
  const sendButton = shadow.querySelector('.composer .btn')
  const modeButtons = [...shadow.querySelectorAll('[data-ask-mode]')]
  const askHint = shadow.querySelector('.ask-hint')
  const saveTitle = shadow.querySelector('.save-title')
  const saveMeta = shadow.querySelector('.save-meta')
  const saveButton = shadow.querySelector('.save-btn')
  const saveResult = shadow.querySelector('.save-result')
  const openOptions = shadow.querySelector('[data-action="open-options"]')

  const auth = { apiBaseUrl: DEFAULTS.apiBaseUrl, token: '', refresh: '', user: null }
  let askMode = 'library'
  let serverSettings = null

  function renderAuth() {
    const connected = Boolean(auth.user && auth.user.email && auth.token)
    emailEl.textContent = connected ? auth.user.email : ''
    authView.classList.toggle('hidden', connected)
    mainView.classList.toggle('hidden', !connected)
  }

  async function syncAuth() {
    const settings = await getSettings()
    auth.apiBaseUrl = settings.apiBaseUrl || DEFAULTS.apiBaseUrl
    auth.token = settings.shiyeAccessToken || ''
    auth.refresh = settings.shiyeRefreshToken || ''
    auth.user = settings.shiyeUser || null
    renderAuth()
  }

  async function refreshToken() {
    if (!auth.refresh) return false
    try {
      const response = await fetch(`${auth.apiBaseUrl}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Client': 'extension' },
        body: JSON.stringify({ refreshToken: auth.refresh }),
      })
      if (!response.ok) return false
      const payload = await response.json()
      if (!payload || payload.code !== 0) return false
      auth.token = payload.data.accessToken
      auth.refresh = payload.data.refreshToken
      auth.user = payload.data.user
      await chrome.storage.local.set({
        shiyeAccessToken: auth.token,
        shiyeRefreshToken: auth.refresh,
        shiyeUser: auth.user,
      })
      renderAuth()
      return true
    } catch {
      return false
    }
  }

  async function apiFetch(path, options = {}) {
    const request = () =>
      fetch(`${auth.apiBaseUrl}${path}`, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          'X-Client': 'extension',
          ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
          ...(options.headers || {}),
        },
      })
    let response = await request()
    if (response.status === 401 && auth.refresh) {
      const refreshed = await refreshToken()
      if (refreshed) response = await request()
    }
    return response
  }

  function addMessage(role, text) {
    const wrap = document.createElement('div')
    wrap.className = `msg ${role}`
    const roleEl = document.createElement('div')
    roleEl.className = 'role'
    roleEl.textContent = role === 'user' ? '你' : '拾页'
    const bubble = document.createElement('div')
    bubble.className = 'bubble'
    bubble.textContent = text
    wrap.append(roleEl, bubble)
    messagesEl.appendChild(wrap)
    messagesEl.scrollTop = messagesEl.scrollHeight
    return { wrap, bubble }
  }

  function renderCitations(bubble, citations) {
    if (!citations.length) return
    const box = document.createElement('div')
    box.className = 'citations'
    for (const citation of citations) {
      const card = document.createElement('div')
      card.className = 'cite'
      const head = document.createElement('b')
      head.textContent = `[${citation.rank ?? citation.index}] ${citation.title || '未命名资料'}`
      const quote = document.createElement('span')
      quote.textContent = (citation.quote || '').slice(0, 180)
      card.append(head, quote)
      box.appendChild(card)
    }
    bubble.parentElement.appendChild(box)
  }

  function parseSseBlock(block) {
    let event = 'message'
    const dataLines = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
    if (!dataLines.length) return null
    try {
      return { event, data: JSON.parse(dataLines.join('\n')) }
    } catch {
      return null
    }
  }

  async function ask(question) {
    if (streaming) return
    const pageMode = askMode === 'page'
    const pageContent = pageMode ? extractPageContent().slice(0, 150000) : ''
    if (pageMode && pageContent.length < 50) {
      addMessage('assistant', '当前页面没有提取到有效正文，无法提问。')
      return
    }
    streaming = true
    textarea.disabled = true
    sendButton.disabled = true
    sendButton.textContent = '回答中…'
    addMessage('user', question)
    const assistant = addMessage('assistant', '正在思考…')
    const citations = []
    let answer = ''
    let failed = false

    try {
      const response = await apiFetch(pageMode ? '/chat/page-stream' : '/chat/stream', {
        method: 'POST',
        headers: { Accept: 'text/event-stream' },
        body: JSON.stringify(
          pageMode
            ? {
                url: location.href,
                title: document.title || location.hostname,
                content: pageContent,
                question,
              }
            : { conversationId, scope: libraryScope(), question },
        ),
      })
      if (!response.ok || !response.body) {
        const payload = await response.json().catch(() => null)
        throw new Error(payload?.message || `请求失败（${response.status}）`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const parsed = parseSseBlock(buffer.slice(0, boundary))
          buffer = buffer.slice(boundary + 2)
          boundary = buffer.indexOf('\n\n')
          if (!parsed) continue
          if (parsed.event === 'meta') {
            if (!pageMode && parsed.data.conversationId) {
              conversationId = parsed.data.conversationId
            }
          } else if (parsed.event === 'delta') {
            answer += parsed.data.text
            assistant.bubble.textContent = answer
            messagesEl.scrollTop = messagesEl.scrollHeight
          } else if (parsed.event === 'citation') {
            citations.push(parsed.data)
          } else if (parsed.event === 'error') {
            failed = true
            assistant.bubble.textContent = parsed.data.message || '回答失败'
          }
        }
      }

      if (!failed) {
        assistant.bubble.textContent = answer || '没有收到回答，请稍后重试。'
        renderCitations(assistant.bubble, citations)
      }
    } catch (error) {
      assistant.bubble.textContent = error.message || '回答失败，请稍后重试。'
    } finally {
      streaming = false
      textarea.disabled = false
      sendButton.disabled = false
      sendButton.textContent = '发送'
      messagesEl.scrollTop = messagesEl.scrollHeight
    }
  }

  async function savePage() {
    const content = extractPageContent()
    if (!content || content.length < 50) {
      saveResult.textContent = '当前页没有提取到有效正文。'
      saveResult.className = 'save-result error'
      return
    }
    saveButton.disabled = true
    saveButton.textContent = '收藏中…'
    saveResult.textContent = ''
    saveResult.className = 'save-result'
    try {
      const response = await apiFetch('/articles/collect', {
        method: 'POST',
        body: JSON.stringify({
          mode: 'CURRENT_PAGE',
          url: location.href,
          title: document.title || location.hostname,
          content,
        }),
      })
      const payload = await response.json().catch(() => null)
      if (!response.ok || !payload || payload.code !== 0) {
        throw new Error(payload?.message || `收藏失败（${response.status}）`)
      }
      saveResult.textContent = payload.data.duplicate
        ? `已经收藏过这篇资料（#${payload.data.articleId}）。`
        : `已收藏并索引（#${payload.data.articleId}），可以去网页端资料库查看。`
      saveResult.className = 'save-result ok'
    } catch (error) {
      saveResult.textContent = error.message || '收藏失败，请稍后重试。'
      saveResult.className = 'save-result error'
    } finally {
      saveButton.disabled = false
      saveButton.textContent = '收藏并索引当前页'
    }
  }

  function switchTab(tab) {
    for (const button of tabs) {
      button.classList.toggle('active', button.dataset.tab === tab)
    }
    askPane.classList.toggle('hidden', tab !== 'ask')
    savePane.classList.toggle('hidden', tab !== 'save')
    if (tab === 'save') {
      saveTitle.textContent = document.title || location.hostname
      saveMeta.textContent = `${location.href} · 约 ${extractPageContent().length.toLocaleString()} 字`
    }
  }

  function setAskMode(mode) {
    askMode = mode === 'page' ? 'page' : 'library'
    for (const button of modeButtons) {
      button.classList.toggle('active', button.dataset.askMode === askMode)
    }
    textarea.placeholder = askMode === 'page' ? '问当前页面的内容…' : '问我收藏的资料…'
    askHint.textContent = askMode === 'page'
      ? '回答只基于当前页面，不会写入资料库。'
      : '回答基于你的资料库，并附原文引用。'
  }

  function isBlacklisted(settings) {
    if (!settings || !Array.isArray(settings.siteBlacklist)) return false
    const host = location.hostname.toLowerCase()
    return settings.siteBlacklist.some((entry) => {
      const value = String(entry || '').toLowerCase().trim()
      return value && (host === value || host.endsWith(`.${value}`))
    })
  }

  function applyServerSettings() {
    if (!serverSettings) return
    if (!serverSettings.ballEnabled || isBlacklisted(serverSettings)) {
      host.remove()
      return
    }
    setAskMode(serverSettings.defaultMode === 'CURRENT' ? 'page' : 'library')
  }

  function libraryScope() {
    const type = serverSettings?.defaultScope
    const id = serverSettings?.defaultScopeId
    if ((type === 'FOLDER' || type === 'TAG') && id) {
      return { type, id: String(id) }
    }
    return { type: 'ALL', id: null }
  }

  async function refreshServerSettings() {
    if (!auth.token) {
      serverSettings = null
      return
    }
    try {
      const response = await apiFetch('/users/me/plugin-settings')
      if (!response.ok) return
      const payload = await response.json()
      if (payload && payload.code === 0 && payload.data) {
        serverSettings = payload.data
        applyServerSettings()
      }
    } catch {
      // 服务端偏好不可用时使用本地设置
    }
  }

  function matchesShortcut(event, shortcut) {
    if (!shortcut) return false
    const parts = String(shortcut).toLowerCase().split('+').map((part) => part.trim())
    const key = parts.pop()
    if (!key || event.key.toLowerCase() !== key) return false
    if (parts.includes('ctrl') !== event.ctrlKey) return false
    if (parts.includes('shift') !== event.shiftKey) return false
    if (parts.includes('alt') !== event.altKey) return false
    if (parts.includes('meta') !== event.metaKey) return false
    return true
  }

  ball.addEventListener('click', async () => {
    panel.classList.toggle('hidden')
    if (!panel.classList.contains('hidden')) {
      await syncAuth()
      await refreshServerSettings()
      switchTab('ask')
    }
  })
  close.addEventListener('click', () => {
    panel.classList.add('hidden')
  })
  openOptions.addEventListener('click', () => {
    chrome.runtime.sendMessage({ type: 'OPEN_OPTIONS' })
  })
  for (const button of tabs) {
    button.addEventListener('click', () => switchTab(button.dataset.tab))
  }
  for (const button of modeButtons) {
    button.addEventListener('click', () => setAskMode(button.dataset.askMode))
  }
  setAskMode('library')
  composer.addEventListener('submit', async (event) => {
    event.preventDefault()
    const question = textarea.value.trim()
    if (!question) return
    textarea.value = ''
    await ask(question)
  })
  textarea.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      composer.requestSubmit()
    }
  })
  saveButton.addEventListener('click', savePage)

  document.addEventListener('keydown', (event) => {
    if (matchesShortcut(event, serverSettings?.shortcut)) {
      event.preventDefault()
      ball.click()
    }
  })

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && (changes.shiyeAccessToken || changes.shiyeRefreshToken || changes.shiyeUser || changes.apiBaseUrl)) {
      void syncAuth().then(refreshServerSettings)
    }
  })

  document.documentElement.appendChild(host)
  void syncAuth().then(refreshServerSettings)
}

async function syncVisibility() {
  const { ballEnabled } = await chrome.storage.local.get({ ballEnabled: true })
  const existing = document.getElementById(HOST_ID)
  if (ballEnabled) {
    mount()
  } else if (existing) {
    existing.remove()
  }
}

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && changes.ballEnabled) {
    syncVisibility()
  }
})

syncVisibility()
