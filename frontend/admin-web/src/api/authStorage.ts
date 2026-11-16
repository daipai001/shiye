import type { AdminSummary, AdminTokenResponse } from './types'

const ACCESS_KEY = 'shiye_admin_token'
const REFRESH_KEY = 'shiye_admin_refresh_token'
const ADMIN_KEY = 'shiye_admin_profile'

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_KEY) || ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_KEY) || ''
}

export function loadAdmin(): AdminSummary | null {
  const raw = localStorage.getItem(ADMIN_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AdminSummary
  } catch {
    return null
  }
}

export function saveTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function saveAdmin(admin: AdminSummary) {
  localStorage.setItem(ADMIN_KEY, JSON.stringify(admin))
}

export function saveSession(payload: AdminTokenResponse) {
  saveTokens(payload.accessToken, payload.refreshToken)
  saveAdmin(payload.admin)
}

export function clearSession() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(ADMIN_KEY)
}
