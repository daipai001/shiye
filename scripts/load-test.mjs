#!/usr/bin/env node
/**
 * Minimal dependency-free load test for authenticated read endpoints.
 * Usage:
 *   BASE_URL=https://localhost/api/v1 EMAIL=... PASSWORD=... VUS=20 DURATION=20s node scripts/load-test.mjs
 * Self-signed local HTTPS: NODE_TLS_REJECT_UNAUTHORIZED=0
 */
const base = process.env.BASE_URL || 'http://127.0.0.1:18080/api/v1'
const email = process.env.EMAIL
const password = process.env.PASSWORD
const vus = Number(process.env.VUS || 20)
const durationSeconds = Number((process.env.DURATION || '20s').replace(/s$/, ''))
const paths = (process.env.PATHS || '/articles?page=1&pageSize=20,/articles/stats,/teams')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean)

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

const token = await login()
const latencies = []
let ok = 0
let failed = 0
let sent = 0
const stopAt = Date.now() + durationSeconds * 1000

async function worker() {
  while (Date.now() < stopAt) {
    for (const path of paths) {
      const started = performance.now()
      sent += 1
      try {
        const response = await fetch(`${base}${path}`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        if (response.ok) {
          ok += 1
        } else {
          failed += 1
        }
      } catch {
        failed += 1
      } finally {
        latencies.push(performance.now() - started)
      }
    }
  }
}

await Promise.all(Array.from({ length: vus }, () => worker()))
latencies.sort((a, b) => a - b)
const elapsed = durationSeconds
const rps = sent / elapsed
console.log(JSON.stringify({
  base,
  vus,
  durationSeconds,
  paths,
  sent,
  ok,
  failed,
  errorRate: sent === 0 ? 0 : failed / sent,
  rps: Number(rps.toFixed(2)),
  p50Ms: Number(percentile(latencies, 50).toFixed(1)),
  p95Ms: Number(percentile(latencies, 95).toFixed(1)),
  p99Ms: Number(percentile(latencies, 99).toFixed(1)),
  maxMs: Number((latencies[latencies.length - 1] || 0).toFixed(1)),
}, null, 2))
