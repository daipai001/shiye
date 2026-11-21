#!/usr/bin/env node
/**
 * OpenAI-compatible mock model server for local load tests.
 *
 * Endpoints:
 *   POST /v1/chat/completions  (stream and non-stream)
 *   POST /v1/embeddings
 *   POST /v1/rerank
 *   GET  /health
 *
 * It never calls external services and returns deterministic data.
 * Usage:
 *   node scripts/mock-ai.mjs 19091
 */
import http from 'node:http'

const port = Number(process.argv[2] || 19091)

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
  })
  res.end(body)
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = ''
    req.on('data', (chunk) => { raw += chunk })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch (error) {
        reject(error)
      }
    })
    req.on('error', reject)
  })
}

function estimateTokens(text) {
  if (!text) return 0
  return Math.max(1, Math.ceil(String(text).length / 4))
}

function promptTokens(messages) {
  if (!Array.isArray(messages)) return 1
  return messages.reduce((total, message) => total + estimateTokens(message?.content), 0)
}

function answerFor(messages) {
  const question = Array.isArray(messages)
    ? [...messages].reverse().find((message) => message?.role === 'user')?.content || ''
    : ''
  const topic = String(question).replace(/\s+/g, ' ').slice(0, 40)
  return `根据资料 [1]，${topic || '这个问题'}的模拟回答如下：这是本地 mock 模型返回的流式内容，用于验证检索、引用、SSE 和并发负载。`
}

function hashVector(text, dimension = 1024) {
  const vector = new Array(dimension)
  let seed = 2166136261
  for (let i = 0; i < text.length; i += 1) {
    seed ^= text.charCodeAt(i)
    seed = Math.imul(seed, 16777619) >>> 0
  }
  for (let i = 0; i < dimension; i += 1) {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0
    vector[i] = (seed / 0xffffffff) * 2 - 1
  }
  const norm = Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0)) || 1
  return vector.map((value) => value / norm)
}

function chunkText(text) {
  const size = Math.max(8, Math.ceil(text.length / 4))
  const chunks = []
  for (let i = 0; i < text.length; i += size) {
    chunks.push(text.slice(i, i + size))
  }
  return chunks
}

async function handleChat(req, res, body) {
  const messages = body.messages || []
  const answer = answerFor(messages)
  const prompt = promptTokens(messages)
  const completion = estimateTokens(answer)
  const usage = {
    prompt_tokens: prompt,
    completion_tokens: completion,
    total_tokens: prompt + completion,
  }

  if (!body.stream) {
    sendJson(res, 200, {
      id: `mock-${Date.now()}`,
      object: 'chat.completion',
      model: body.model || 'mock-chat',
      choices: [{
        index: 0,
        message: { role: 'assistant', content: answer },
        finish_reason: 'stop',
      }],
      usage,
    })
    return
  }

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  })
  res.write(`data: ${JSON.stringify({ choices: [{ delta: { role: 'assistant' } }] })}\n\n`)
  for (const piece of chunkText(answer)) {
    res.write(`data: ${JSON.stringify({ choices: [{ delta: { content: piece } }] })}\n\n`)
    await new Promise((resolve) => setTimeout(resolve, 5))
  }
  res.write(`data: ${JSON.stringify({
    choices: [{ delta: {}, finish_reason: 'stop' }],
    usage,
  })}\n\n`)
  res.write('data: [DONE]\n\n')
  res.end()
}

function handleEmbeddings(res, body) {
  const inputs = Array.isArray(body.input) ? body.input : [body.input]
  sendJson(res, 200, {
    object: 'list',
    model: body.model || 'mock-embedding',
    data: inputs.map((text, index) => ({
      object: 'embedding',
      index,
      embedding: hashVector(String(text)),
    })),
    usage: {
      prompt_tokens: inputs.reduce((total, text) => total + estimateTokens(text), 0),
      total_tokens: inputs.reduce((total, text) => total + estimateTokens(text), 0),
    },
  })
}

function handleRerank(res, body) {
  const query = String(body.query || '')
  const documents = Array.isArray(body.documents) ? body.documents : []
  const queryTerms = new Set(query.split(/\s+/).filter(Boolean))
  const results = documents.map((document, index) => {
    const text = String(document)
    let overlap = 0
    for (const term of queryTerms) {
      if (term && text.includes(term)) overlap += 1
    }
    const score = queryTerms.size === 0 ? 0 : overlap / queryTerms.size
    return { index, relevance_score: Number(score.toFixed(4)) }
  })
  results.sort((a, b) => b.relevance_score - a.relevance_score)
  sendJson(res, 200, {
    model: body.model || 'mock-rerank',
    results: results.slice(0, Number(body.top_n || results.length)),
    meta: {
      tokens: {
        input_tokens: estimateTokens(query)
          + documents.reduce((total, document) => total + estimateTokens(document), 0),
      },
    },
  })
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      sendJson(res, 200, { status: 'UP', service: 'shiye-mock-ai' })
      return
    }
    if (req.method === 'POST' && req.url?.endsWith('/chat/completions')) {
      await handleChat(req, res, await readBody(req))
      return
    }
    if (req.method === 'POST' && req.url?.endsWith('/embeddings')) {
      handleEmbeddings(res, await readBody(req))
      return
    }
    if (req.method === 'POST' && req.url?.endsWith('/rerank')) {
      handleRerank(res, await readBody(req))
      return
    }
    sendJson(res, 404, { error: 'not found' })
  } catch (error) {
    sendJson(res, 500, { error: String(error?.message || error) })
  }
})

server.listen(port, '0.0.0.0', () => {
  console.log(`mock ai listening on :${port}`)
})
