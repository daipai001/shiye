import { apiBaseUrl } from './client'
import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  saveTokens,
  saveUser,
} from './authStorage'
import type { ApiResult, ChatCitation, TokenResponse } from './types'

export interface ChatStreamPayload {
  conversationId: string | null
  scope: { type: string; id?: string | null }
  question: string
  model?: string | null
}

export interface ChatStreamCallbacks {
  onMeta?: (data: { requestId: string; conversationId: string; messageId: string }) => void
  onDelta?: (text: string) => void
  onCitation?: (citation: ChatCitation) => void
  onUsage?: (usage: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
    cost: number
    currency: string
    estimated: boolean
  }) => void
  onDone?: (finishReason: string) => void
  onError?: (error: { code: string; message: string; retryable: boolean }) => void
}

async function refreshToken(): Promise<boolean> {
  const refresh = getRefreshToken()
  if (!refresh) return false
  try {
    const response = await fetch(`${apiBaseUrl}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Client': 'web' },
      body: JSON.stringify({ refreshToken: refresh }),
    })
    if (!response.ok) return false
    const payload = (await response.json()) as ApiResult<TokenResponse>
    if (payload.code !== 0) return false
    saveTokens(payload.data.accessToken, payload.data.refreshToken)
    saveUser(payload.data.user)
    return true
  } catch {
    return false
  }
}

async function postStream(payload: ChatStreamPayload, signal?: AbortSignal) {
  const request = () =>
    fetch(`${apiBaseUrl}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'X-Client': 'web',
        Authorization: `Bearer ${getAccessToken()}`,
      },
      body: JSON.stringify(payload),
      signal,
    })

  let response = await request()
  if (response.status === 401) {
    const refreshed = await refreshToken()
    if (refreshed) {
      response = await request()
    } else {
      clearSession()
    }
  }
  return response
}

export async function streamChat(
  payload: ChatStreamPayload,
  callbacks: ChatStreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await postStream(payload, signal)
  } catch (error) {
    if ((error as Error).name === 'AbortError') return
    callbacks.onError?.({ code: 'NETWORK_ERROR', message: '无法连接后端服务', retryable: true })
    return
  }

  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    let code = `HTTP_${response.status}`
    try {
      const body = (await response.json()) as { code?: number; message?: string }
      if (body.message) message = body.message
      if (body.code) code = String(body.code)
    } catch {
      // ignore non-json error
    }
    callbacks.onError?.({ code, message, retryable: response.status >= 500 })
    return
  }
  if (!response.body) {
    callbacks.onError?.({ code: 'EMPTY_STREAM', message: '服务端没有返回流', retryable: true })
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  let sawDone = false
  let sawError = false
  const dispatch = (block: string) => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
    if (dataLines.length === 0) return
    const raw = dataLines.join('\n')
    let data: unknown
    try {
      data = JSON.parse(raw)
    } catch {
      return
    }
    if (event === 'meta') {
      callbacks.onMeta?.(data as { requestId: string; conversationId: string; messageId: string })
    } else if (event === 'delta') {
      callbacks.onDelta?.((data as { text: string }).text)
    } else if (event === 'citation') {
      const raw = data as Omit<ChatCitation, 'rank'> & { index: number }
      const { index, ...citation } = raw
      callbacks.onCitation?.({ ...citation, rank: index })
    } else if (event === 'usage') {
      callbacks.onUsage?.(data as {
        promptTokens: number
        completionTokens: number
        totalTokens: number
        cost: number
        currency: string
        estimated: boolean
      })
    } else if (event === 'done') {
      sawDone = true
      callbacks.onDone?.((data as { finishReason: string }).finishReason)
    } else if (event === 'error') {
      sawError = true
      callbacks.onError?.(data as { code: string; message: string; retryable: boolean })
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        dispatch(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
    }
    if (buffer.trim()) {
      dispatch(buffer)
    }
    if (!sawDone && !sawError) {
      callbacks.onError?.({ code: 'STREAM_BROKEN', message: '回答流意外结束', retryable: true })
    }
  } catch (error) {
    if ((error as Error).name === 'AbortError') return
    callbacks.onError?.({ code: 'STREAM_BROKEN', message: '回答流中断', retryable: true })
  }
}
