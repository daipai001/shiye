#!/usr/bin/env node
/**
 * AI/RAG chat load test against a running ShiYe API.
 * Requires the mock model from deploy/docker-compose.loadtest.yml (or another
 * OpenAI-compatible endpoint) so it does not spend external API budget.
 *
 * Usage:
 *   BASE_URL=https://localhost/api/v1 EMAIL=... PASSWORD=... VUS=10 DURATION=30s \
 *     node scripts/load-test-chat.mjs
 * Local self-signed HTTPS: NODE_TLS_REJECT_UNAUTHORIZED=0
 */
const base = process.env.BASE_URL || 'http://127.0.0.1:18080/api/v1'
const email = process.env.EMAIL
const password = process.env.PASSWORD
const vus = Number(process.env.VUS || 10)
const durationSeconds = Number((process.env.DURATION || '30s').replace(/s$/, ''))
const question = process.env.QUESTION || '请根据资料总结拾页的检索和引用流程。'
const scopeType = process.env.SCOPE_TYPE || 'ALL'

if (!email || !password) {
  console.error('EMAIL and PASSWORD are required')
  process.exit(1)
}

async function login() {
  const response = await fetch(`${base}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!response.ok) {
    throw new Error(`login failed: HTTP ${response.status}`)
  }
  const body = await response.json()
  if (!body?.data?.accessToken) {
    throw new Error('login response has no access token')
  }
  return body.data.accessToken
}

function percentile(sorted, value) {
  if (sorted.length === 0) return 0
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((value / 100) * sorted.length) - 1))
  return sorted[index]
}

async function chatOnce(token) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 60000)
  const started = performance.now()
  const result = { ok: false, error: null, status: 0, promptTokens: 0, completionTokens: 0, events: 0 }
  try {
    const response = await fetch(`${base}/chat/stream`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ scope: { type: scopeType }, question }),
      signal: controller.signal,
    })
    result.status = response.status
    if (response.status !== 200) {
      const text = await response.text()
      result.error = `HTTP ${response.status}: ${text.slice(0, 160)}`
      return result
    }
    if (!response.body) {
      result.error = 'no response body'
      return result
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let eventName = ''
    let sawDone = false
    let sawError = null

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let index
      while ((index = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, index).replace(/\r$/, '')
        buffer = buffer.slice(index + 1)
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
          continue
        }
        if (!line.startsWith('data:')) continue
        const data = line.slice(5).trim()
        if (!data) continue
        let payload = null
        try {
          payload = JSON.parse(data)
        } catch {
          continue
        }
        result.events += 1
        if (eventName === 'usage') {
          result.promptTokens = Number(payload.promptTokens || payload.prompt_tokens || 0)
          result.completionTokens = Number(payload.completionTokens || payload.completion_tokens || 0)
        } else if (eventName === 'done') {
          sawDone = true
        } else if (eventName === 'error') {
          sawError = payload.message || payload.code || 'stream error'
        }
      }
    }

    if (sawError) {
      result.error = sawError
      return result
    }
    if (!sawDone) {
      result.error = 'stream ended without done event'
      return result
    }
    result.ok = true
    return result
  } catch (error) {
    result.error = error?.name === 'AbortError' ? 'timeout' : String(error?.message || error)
    return result
  } finally {
    clearTimeout(timer)
    result.latencyMs = performance.now() - started
  }
}

const token = await login()
const latencies = []
const errors = new Map()
let ok = 0
let failed = 0
let sent = 0
let promptTokens = 0
let completionTokens = 0
const stopAt = Date.now() + durationSeconds * 1000

async function worker() {
  while (Date.now() < stopAt) {
    sent += 1
    const result = await chatOnce(token)
    latencies.push(result.latencyMs)
    if (result.ok) {
      ok += 1
      promptTokens += result.promptTokens
      completionTokens += result.completionTokens
    } else {
      failed += 1
      const key = result.error || 'unknown'
      errors.set(key, (errors.get(key) || 0) + 1)
    }
  }
}

await Promise.all(Array.from({ length: vus }, () => worker()))
latencies.sort((a, b) => a - b)
const elapsed = durationSeconds
console.log(JSON.stringify({
  base,
  vus,
  durationSeconds,
  scopeType,
  sent,
  ok,
  failed,
  errorRate: sent === 0 ? 0 : Number((failed / sent).toFixed(4)),
  rps: Number((sent / elapsed).toFixed(2)),
  p50Ms: Number(percentile(latencies, 50).toFixed(1)),
  p95Ms: Number(percentile(latencies, 95).toFixed(1)),
  p99Ms: Number(percentile(latencies, 99).toFixed(1)),
  maxMs: Number((latencies[latencies.length - 1] || 0).toFixed(1)),
  promptTokens,
  completionTokens,
  totalTokens: promptTokens + completionTokens,
  tokensPerSecond: Number(((promptTokens + completionTokens) / elapsed).toFixed(2)),
  errors: Object.fromEntries(errors),
}, null, 2))
