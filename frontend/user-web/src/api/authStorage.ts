import type { TokenResponse, UserSummary } from './types'

const ACCESS_KEY = 'shiye_user_token'
const REFRESH_KEY = 'shiye_user_refresh_token'
const USER_KEY = 'shiye_user_profile'

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_KEY) || ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_KEY) || ''
}

export function loadUser(): UserSummary | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserSummary
  } catch {
    return null
  }
}

export function saveTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function saveUser(user: UserSummary) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function saveSession(payload: TokenResponse) {
  saveTokens(payload.accessToken, payload.refreshToken)
  saveUser(payload.user)
}

export function clearSession() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}
