#!/usr/bin/env node
/**
 * Team-scope RAG chat load test.
 *
 * Creates two users and a team, shares one article, then runs concurrent
 * TEAM-scope chats. Use a mock/limited model endpoint for repeatable runs.
 *
 * Usage:
 *   BASE_URL=http://127.0.0.1:18080/api/v1 VUS=5 DURATION=15s \
 *     node scripts/load-test-team.mjs
 */
const base = process.env.BASE_URL || 'http://127.0.0.1:18080/api/v1'
const vus = Number(process.env.VUS || 5)
const durationSeconds = Number((process.env.DURATION || '15s').replace(/s$/, ''))
const password = process.env.TEAM_PASSWORD || 'TeamLoad123'
const question = process.env.QUESTION || 'Summarize the team knowledge retrieval validation.'
const stamp = `${Date.now()}-${Math.floor(Math.random() * 100000)}`

async function jsonRequest(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body == null ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let payload = null
  try {
    payload = JSON.parse(text)
  } catch {
    // keep null for non-JSON failures
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} -> HTTP ${response.status}: ${text.slice(0, 200)}`)
  }
  return payload.data
}

function percentile(sorted, value) {
  if (sorted.length === 0) return 0
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((value / 100) * sorted.length) - 1))
  return sorted[index]
}

async function chatOnce(token, teamId) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 60000)
  const started = performance.now()
  const result = { ok: false, status: 0, citations: 0, done: false, error: null }
  try {
    const response = await fetch(`${base}/chat/stream`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        scope: { type: 'TEAM', id: String(teamId) },
        question,
      }),
      signal: controller.signal,
    })
    result.status = response.status
    if (response.status !== 200) {
      result.error = `HTTP ${response.status}`
      return result
    }
    const text = await response.text()
    result.citations = (text.match(/event:citation/g) || []).length
    result.done = text.includes('event:done')
    result.ok = result.citations > 0
    if (text.includes('event:error')) {
      const match = text.match(/event:error\s+data:(\{[^\n]*\})/)
      result.error = match ? match[1].slice(0, 160) : 'SSE error'
    }
    return result
  } catch (error) {
    result.error = error.name === 'AbortError' ? 'timeout' : String(error.message || error)
    return result
  } finally {
    clearTimeout(timer)
    result.latencyMs = performance.now() - started
  }
}

const a = await jsonRequest('POST', '/auth/register', {
  email: `team-load-a-${stamp}@shiye.local`,
  password,
  displayName: 'Team Load A',
})
const b = await jsonRequest('POST', '/auth/register', {
  email: `team-load-b-${stamp}@shiye.local`,
  password,
  displayName: 'Team Load B',
})
const team = await jsonRequest('POST', '/teams', { name: `Load Team ${stamp}` }, a.accessToken)
const invite = await jsonRequest(
  'POST',
  `/teams/${team.id}/invites`,
  { role: 'MEMBER' },
  a.accessToken,
)
await jsonRequest('POST', '/teams/join', { code: invite.code }, b.accessToken)
const content = 'Team load test knowledge base. ' +
  'RAG retrieval and citation validation for concurrent team chat. '.repeat(40)
const article = await jsonRequest('POST', '/articles/collect', {
  mode: 'CURRENT_PAGE',
  url: `https://team-load.example/${stamp}`,
  title: 'Team Load Article',
  content,
}, a.accessToken)
await jsonRequest(
  'POST',
  `/articles/${article.articleId}/share`,
  { teamId: team.id },
  a.accessToken,
)

const latencies = []
const statuses = new Map()
let sent = 0
let ok = 0
let cited = 0
let errors = 0
const samples = []
const stopAt = Date.now() + durationSeconds * 1000

async function worker() {
  while (Date.now() < stopAt) {
    sent += 1
    const result = await chatOnce(b.accessToken, team.id)
    latencies.push(result.latencyMs)
    statuses.set(result.status, (statuses.get(result.status) || 0) + 1)
    if (result.ok) {
      ok += 1
    } else {
      errors += 1
      if (samples.length < 5) samples.push(result)
    }
    if (result.citations > 0) cited += 1
  }
}

await Promise.all(Array.from({ length: vus }, () => worker()))
latencies.sort((left, right) => left - right)
console.log(JSON.stringify({
  base,
  teamId: team.id,
  sent,
  ok,
  errors,
  cited,
  statuses: Object.fromEntries(statuses),
  rps: sent / durationSeconds,
  p50Ms: Math.round(percentile(latencies, 50)),
  p95Ms: Math.round(percentile(latencies, 95)),
  maxMs: Math.round(latencies.at(-1) || 0),
  errorSamples: samples,
}, null, 2))
