#!/usr/bin/env node
/**
 * RediSearch HNSW 规模测试（无第三方依赖，直接走 RESP2）。
 *
 * 会 FLUSHDB，必须显式确认：
 *   HNSW_TEST_FLUSH=yes REDIS_PORT=16380 COUNT=100000 \
 *     node scripts/hnsw-scale-test.mjs
 *
 * 输出：写入耗时、索引耗时、内存、查询 P50/P95、索引重建耗时。
 */
import net from 'node:net'
import { spawn } from 'node:child_process'
import { once } from 'node:events'

const host = process.env.REDIS_HOST || '127.0.0.1'
const port = Number(process.env.REDIS_PORT || 16380)
const count = Math.max(1000, Number(process.env.COUNT || 100000))
const dim = Math.max(64, Number(process.env.DIM || 1024))
const batchSize = Math.max(50, Number(process.env.BATCH || 500))
const queryCount = Math.max(10, Number(process.env.QUERIES || 100))
const indexName = process.env.INDEX_NAME || 'idx:hnsw_bench'
const keyPrefix = process.env.KEY_PREFIX || 'kb:hnswbench:'
const keep = process.env.HNSW_TEST_KEEP === 'yes'

if (process.env.HNSW_TEST_FLUSH !== 'yes') {
  console.error('Refusing to run without HNSW_TEST_FLUSH=yes (this script flushes the Redis DB)')
  process.exit(1)
}

const CRLF = Buffer.from('\r\n')

class RedisClient {
  constructor() {
    this.socket = net.createConnection({ host, port })
    this.buffer = Buffer.alloc(0)
    this.pending = []
    this.ready = new Promise((resolve, reject) => {
      this.socket.once('connect', resolve)
      this.socket.once('error', reject)
    })
    this.socket.on('data', (chunk) => {
      this.buffer = Buffer.concat([this.buffer, chunk])
      this.drain()
    })
    this.socket.on('error', (error) => {
      for (const item of this.pending) item.reject(error)
      this.pending = []
    })
  }

  drain() {
    while (this.pending.length > 0) {
      const parsed = this.parseAt(0)
      if (!parsed) return
      this.buffer = this.buffer.subarray(parsed.next)
      const item = this.pending.shift()
      if (parsed.value instanceof Error) item.reject(parsed.value)
      else item.resolve(parsed.value)
    }
  }

  parseAt(offset) {
    if (offset >= this.buffer.length) return null
    const type = String.fromCharCode(this.buffer[offset])
    const lineEnd = this.buffer.indexOf(CRLF, offset + 1)
    if (lineEnd < 0) return null
    const line = this.buffer.subarray(offset + 1, lineEnd).toString()
    if (type === '+') {
      return { value: line, next: lineEnd + 2 }
    }
    if (type === '-') {
      return { value: new Error(line), next: lineEnd + 2 }
    }
    if (type === ':') {
      return { value: Number(line), next: lineEnd + 2 }
    }
    if (type === '$') {
      const length = Number(line)
      if (length === -1) return { value: null, next: lineEnd + 2 }
      const dataStart = lineEnd + 2
      const dataEnd = dataStart + length
      if (this.buffer.length < dataEnd + 2) return null
      return { value: this.buffer.subarray(dataStart, dataEnd), next: dataEnd + 2 }
    }
    if (type === '*') {
      const length = Number(line)
      if (length === -1) return { value: null, next: lineEnd + 2 }
      let cursor = lineEnd + 2
      const values = []
      for (let i = 0; i < length; i += 1) {
        const child = this.parseAt(cursor)
        if (!child) return null
        values.push(child.value)
        cursor = child.next
      }
      return { value: values, next: cursor }
    }
    throw new Error(`unsupported RESP type: ${type}`)
  }

  command(...args) {
    const encoded = encode(args)
    return new Promise((resolve, reject) => {
      this.pending.push({ resolve, reject })
      this.socket.write(encoded)
    })
  }

  close() {
    this.socket.end()
  }
}

function encode(args) {
  const chunks = [Buffer.from(`*${args.length}\r\n`)]
  for (const arg of args) {
    const value = Buffer.isBuffer(arg) ? arg : Buffer.from(String(arg))
    chunks.push(Buffer.from(`$${value.length}\r\n`), value, CRLF)
  }
  return Buffer.concat(chunks)
}

function vector(seed) {
  const buffer = Buffer.alloc(dim * 4)
  let state = seed >>> 0
  for (let i = 0; i < dim; i += 1) {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0
    buffer.writeFloatLE((state / 0xffffffff) * 2 - 1, i * 4)
  }
  return buffer
}

function percentile(sorted, value) {
  if (sorted.length === 0) return 0
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((value / 100) * sorted.length) - 1))
  return sorted[index]
}

function flatMap(value) {
  if (!Array.isArray(value)) return new Map()
  const map = new Map()
  for (let i = 0; i + 1 < value.length; i += 2) {
    map.set(String(value[i]), value[i + 1])
  }
  return map
}

async function indexInfo(client) {
  const raw = await client.command('FT.INFO', indexName)
  return flatMap(raw)
}

async function waitIndexing(client, timeoutMs = 30 * 60 * 1000) {
  const started = Date.now()
  for (;;) {
    const info = await indexInfo(client)
    const indexing = Number(info.get('indexing') || 0)
    if (indexing === 0) return
    if (Date.now() - started > timeoutMs) {
      throw new Error('indexing timeout')
    }
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
}

async function memory(client) {
  const raw = await client.command('INFO', 'memory')
  const text = raw.toString('utf8')
  const used = /used_memory:(\d+)/.exec(text)
  const human = /used_memory_human:([^\r\n]+)/.exec(text)
  return { usedBytes: used ? Number(used[1]) : 0, human: human ? human[1].trim() : '' }
}

async function createIndex(client) {
  await client.command(
    'FT.CREATE', indexName,
    'ON', 'HASH',
    'PREFIX', 1, keyPrefix,
    'SCHEMA',
    'user_id', 'TAG',
    'team_id', 'TAG',
    'article_id', 'TAG',
    'chunk_id', 'NUMERIC',
    'embedding_model', 'TAG',
    'vector_bin', 'VECTOR', 'HNSW', 6,
    'TYPE', 'FLOAT32', 'DIM', dim, 'DISTANCE_METRIC', 'COSINE',
  )
}

/**
 * 用 redis-cli --pipe 写入，避免 Node 持有 10 万条命令/响应导致内存膨胀。
 */
async function insertViaPipe() {
  const cli = process.env.REDIS_CLI || 'redis-cli'
  const child = spawn(cli, ['-h', host, '-p', String(port), '--pipe'], {
    stdio: ['pipe', 'pipe', 'pipe'],
  })
  let stderr = ''
  const output = []
  child.stderr.on('data', (chunk) => { stderr += chunk.toString() })
  child.stdout.on('data', (chunk) => output.push(chunk))
  const exited = new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`redis-cli --pipe exited ${code}: ${stderr.slice(0, 300)}`))
    })
  })
  const write = async (buffer) => {
    if (!child.stdin.write(buffer)) {
      await once(child.stdin, 'drain')
    }
  }
  const pipeBatch = Math.max(20, Math.min(batchSize, 100))
  for (let start = 0; start < count; start += pipeBatch) {
    const end = Math.min(count, start + pipeBatch)
    const commands = []
    for (let i = start; i < end; i += 1) {
      commands.push(encode([
        'HSET', `${keyPrefix}${i}`,
        'user_id', String(i % 100),
        'team_id', String(i % 10),
        'article_id', String(Math.floor(i / 20)),
        'chunk_id', String(i + 1),
        'embedding_model', 'bench',
        'vector_bin', vector(i + 1),
      ]))
    }
    await write(Buffer.concat(commands))
    if ((start / pipeBatch) % 20 === 0) {
      process.stderr.write(`\rinserted ${end}/${count}`)
    }
  }
  child.stdin.end()
  await exited
  process.stderr.write('\n')
  const text = Buffer.concat(output).toString('utf8')
  const errors = /errors:\s*(\d+)/.exec(text)
  if (errors && Number(errors[1]) > 0) {
    throw new Error(`redis-cli --pipe reported errors: ${text.slice(0, 300)}`)
  }
}

const client = new RedisClient()
await client.ready

try {
  const version = (await client.command('INFO', 'server')).toString('utf8')
    .match(/redis_version:([^\r\n]+)/)?.[1]?.trim() || 'unknown'
  await client.command('FLUSHDB')
  await createIndex(client)

  const insertStarted = performance.now()
  await insertViaPipe()
  const insertMs = performance.now() - insertStarted

  const indexStarted = performance.now()
  await waitIndexing(client)
  const indexMs = performance.now() - indexStarted
  const afterIndex = await memory(client)
  const info = await indexInfo(client)

  const queryVector = vector(987654321)
  const latencies = []
  let emptyQueries = 0
  for (let i = 0; i < queryCount; i += 1) {
    const started = performance.now()
    const result = await client.command(
      'FT.SEARCH', indexName,
      `(@team_id:{${i % 10}} @embedding_model:{bench})=>[KNN 10 @vector_bin $vec AS score]`,
      'PARAMS', 2, 'vec', queryVector,
      'SORTBY', 'score',
      'RETURN', 2, 'chunk_id', 'score',
      'DIALECT', 2,
      'LIMIT', 0, 10,
    )
    latencies.push(performance.now() - started)
    if (Number(result?.[0] || 0) === 0) emptyQueries += 1
  }
  latencies.sort((left, right) => left - right)

  const rebuildStarted = performance.now()
  await client.command('FT.DROPINDEX', indexName)
  await createIndex(client)
  await waitIndexing(client)
  const rebuildMs = performance.now() - rebuildStarted
  const afterRebuild = await memory(client)

  console.log(JSON.stringify({
    host,
    port,
    redisVersion: version,
    count,
    dimension: dim,
    indexName,
    insertSeconds: Number((insertMs / 1000).toFixed(2)),
    indexSeconds: Number((indexMs / 1000).toFixed(2)),
    rebuildSeconds: Number((rebuildMs / 1000).toFixed(2)),
    queryCount,
    emptyQueries,
    queryP50Ms: Number(percentile(latencies, 50).toFixed(2)),
    queryP95Ms: Number(percentile(latencies, 95).toFixed(2)),
    queryMaxMs: Number((latencies.at(-1) || 0).toFixed(2)),
    numDocs: Number(info.get('num_docs') || 0),
    memoryAfterIndex: afterIndex,
    memoryAfterRebuild: afterRebuild,
  }, null, 2))
} finally {
  if (!keep) {
    try {
      await client.command('FLUSHDB')
    } catch {
      // ignore cleanup failure
    }
  }
  client.close()
}
