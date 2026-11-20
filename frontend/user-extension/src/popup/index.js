const ballEnabled = document.getElementById('ballEnabled')
const openOptions = document.getElementById('openOptions')
const openWeb = document.getElementById('openWeb')
const checkBackend = document.getElementById('checkBackend')
const userLine = document.getElementById('userLine')
const version = document.getElementById('version')

version.textContent = `v${chrome.runtime.getManifest().version}`

chrome.storage.local.get(
  { ballEnabled: true, apiBaseUrl: 'http://localhost:18080/api/v1', shiyeUser: null },
  (settings) => {
    ballEnabled.checked = settings.ballEnabled
    userLine.textContent = settings.shiyeUser?.email
      ? `已连接：${settings.shiyeUser.email}`
      : '未连接账号'
  },
)

ballEnabled.addEventListener('change', () => {
  chrome.storage.local.set({ ballEnabled: ballEnabled.checked })
})

openOptions.addEventListener('click', () => {
  chrome.runtime.openOptionsPage()
})

openWeb.addEventListener('click', async () => {
  const { apiBaseUrl } = await chrome.storage.local.get({
    apiBaseUrl: 'http://localhost:18080/api/v1',
  })
  try {
    window.open(new URL(apiBaseUrl).origin, '_blank', 'noopener')
  } catch {
    window.open('http://localhost:15173', '_blank', 'noopener')
  }
})

checkBackend.addEventListener('click', async () => {
  const { apiBaseUrl } = await chrome.storage.local.get({
    apiBaseUrl: 'http://localhost:18080/api/v1',
  })
  checkBackend.textContent = '检查中…'
  try {
    const response = await fetch(`${apiBaseUrl}/health`)
    const data = await response.json()
    checkBackend.textContent = data?.code === 0 ? '后端已连接' : '后端异常'
  } catch {
    checkBackend.textContent = '后端未连接'
  }
})
