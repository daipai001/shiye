const ballEnabled = document.getElementById('ballEnabled')
const openOptions = document.getElementById('openOptions')
const checkBackend = document.getElementById('checkBackend')

chrome.storage.local.get({ ballEnabled: true, apiBaseUrl: 'http://localhost:18080/api/v1' }, (settings) => {
  ballEnabled.checked = settings.ballEnabled
})

ballEnabled.addEventListener('change', () => {
  chrome.storage.local.set({ ballEnabled: ballEnabled.checked })
})

openOptions.addEventListener('click', () => {
  chrome.runtime.openOptionsPage()
})

checkBackend.addEventListener('click', async () => {
  const { apiBaseUrl } = await chrome.storage.local.get({ apiBaseUrl: 'http://localhost:18080/api/v1' })
  try {
    const response = await fetch(`${apiBaseUrl}/health`)
    const data = await response.json()
    checkBackend.textContent = data?.code === 0 ? '后端已连接' : '后端异常'
  } catch {
    checkBackend.textContent = '后端未连接'
  }
})
