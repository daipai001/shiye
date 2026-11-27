#!/usr/bin/env node
/**
 * Article ingest load test.
 *
 * 1) concurrent unique CURRENT_PAGE ingests
 * 2) concurrent same-URL ingests to exercise the single-article lock
 *
 * Usage:
 *   BASE_URL=http://127.0.0.1:18080/api/v1 CONCURRENCY=5 TOTAL=20 \
 *     node scripts/load-test-import.mjs
 */
const base = process.env.BASE_URL || 'http://127.0.0.1:18080/api/v1'
const concurrency = Math.max(1, Number(process.env.CONCURRENCY || 5))
const total = Math.max(1, Number(process.env.TOTAL || 20))
const password = process.env.IMPORT_PASSWORD || 'ImportLoad123'
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
  return { status: response.status, payload, text }
}

function percentile(sorted, value) {
  if (sorted.length === 0) return 0
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((value / 100) * sorted.length) - 1))
  return sorted[index]
}

async function ingest(token, url) {
  const started = performance.now()
  const response = await jsonRequest('POST', '/articles/collect', {
    mode: 'CURRENT_PAGE',
    url,
    title: `Import Load ${url.slice(-12)}`,
    content: 'Import load test content. ' +
      'RAG chunking and embedding concurrency validation. '.repeat(30),
  }, token)
  return {
    status: response.status,
    code: response.payload?.code ?? null,
    latencyMs: performance.now() - started,
  }
}

const user = await jsonRequest('POST', '/auth/register', {
  email: `import-load-${stamp}@shiye.local`,
  password,
  displayName: 'Import Load',
})
if (user.status !== 200 || !user.payload?.data?.accessToken) {
  throw new Error(`register failed: HTTP ${user.status} ${user.text.slice(0, 200)}`)
}
const token = user.payload.data.accessToken

const uniqueLatencies = []
const uniqueStatuses = new Map()
let uniqueOk = 0
let uniqueFailed = 0
let next = 0

async function uniqueWorker() {
  while (true) {
    const index = next
    next += 1
    if (index >= total) return
    const result = await ingest(token, `https://import-load.example/${stamp}/${index}`)
    uniqueLatencies.push(result.latencyMs)
    uniqueStatuses.set(result.status, (uniqueStatuses.get(result.status) || 0) + 1)
    if (result.status === 200) {
      uniqueOk += 1
    } else {
      uniqueFailed += 1
    }
  }
}

const uniqueStartedAt = performance.now()
await Promise.all(Array.from({ length: concurrency }, () => uniqueWorker()))
const uniqueElapsedSeconds = (performance.now() - uniqueStartedAt) / 1000
uniqueLatencies.sort((left, right) => left - right)

const sameUrl = `https://import-load.example/${stamp}/same`
const sameResults = await Promise.all(
  Array.from({ length: Math.min(concurrency, 6) }, () => ingest(token, sameUrl)),
)
const sameStatuses = new Map()
for (const result of sameResults) {
  sameStatuses.set(result.status, (sameStatuses.get(result.status) || 0) + 1)
}
const sameServerErrors = sameResults.filter((result) => result.status >= 500).length
const sameLocks = sameResults.filter((result) => result.status === 409).length
const sameOk = sameResults.filter((result) => result.status === 200).length

console.log(JSON.stringify({
  base,
  total,
  concurrency,
  uniqueOk,
  uniqueFailed,
  uniqueStatuses: Object.fromEntries(uniqueStatuses),
  uniqueRps: uniqueElapsedSeconds > 0 ? uniqueOk / uniqueElapsedSeconds : 0,
  uniqueP50Ms: Math.round(percentile(uniqueLatencies, 50)),
  uniqueP95Ms: Math.round(percentile(uniqueLatencies, 95)),
  sameUrlRequests: sameResults.length,
  sameUrlOk: sameOk,
  sameUrlLocks: sameLocks,
  sameUrlStatuses: Object.fromEntries(sameStatuses),
  sameUrlServerErrors: sameServerErrors,
}, null, 2))

if (sameServerErrors > 0 || sameOk === 0) {
  process.exit(2)
}
