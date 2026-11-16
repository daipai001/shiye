import axios from 'axios'
import type { ApiResult } from './types'

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as ApiResult<unknown> | undefined
    if (payload?.message) return payload.message
    if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
    if (!error.response) return '无法连接后端服务，请确认管理端 API 已启动'
  }
  return fallback
}
