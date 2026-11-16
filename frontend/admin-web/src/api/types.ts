export interface ApiResult<T> {
  code: number
  message: string
  data: T
  requestId?: string
}

export interface AdminSummary {
  id: string
  email: string
  name: string
  role: string
}

export interface AdminTokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
  admin: AdminSummary
}
