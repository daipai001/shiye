import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  saveAdmin,
  saveTokens,
} from './authStorage'
import type { AdminTokenResponse, ApiResult } from './types'

const baseURL = import.meta.env.VITE_ADMIN_API_BASE_URL || 'http://localhost:18081/admin/api/v1'

const api = axios.create({
  baseURL,
  timeout: 20000,
})

const authPaths = ['/auth/login', '/auth/refresh']

function isAuthPath(url?: string) {
  return authPaths.some((path) => url?.includes(path))
}

let refreshPromise: Promise<string> | null = null

async function requestNewAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('NO_REFRESH_TOKEN')
  }
  const { data } = await axios.post<ApiResult<AdminTokenResponse>>(
    `${baseURL}/auth/refresh`,
    { refreshToken },
    { timeout: 20000, headers: { 'X-Client': 'admin' } },
  )
  saveTokens(data.data.accessToken, data.data.refreshToken)
  saveAdmin(data.data.admin)
  return data.data.accessToken
}

api.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Client'] = 'admin'
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status

    if (status !== 401 || !config || config._retry || isAuthPath(config.url)) {
      return Promise.reject(error)
    }

    config._retry = true
    try {
      if (!refreshPromise) {
        refreshPromise = requestNewAccessToken().finally(() => {
          refreshPromise = null
        })
      }
      const accessToken = await refreshPromise
      config.headers.Authorization = `Bearer ${accessToken}`
      return api(config)
    } catch {
      clearSession()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
  },
)

export default api
