export interface ApiResult<T> {
  code: number
  message: string
  data: T
  requestId?: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
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

export interface TrendPoint {
  date: string
  calls: number
  tokens: number
  cost: number
}

export interface SourceSplit {
  source: string
  calls: number
  tokens: number
  cost: number
}

export interface RecentFailure {
  type: string
  resourceId: string
  userName?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  createdAt: string
}

export interface Overview {
  users: number
  articles: number
  chunks: number
  todayCalls: number
  todayTokens: number
  todayCost: number
  errorRate: number
  p95LatencyMs: number
  trends: TrendPoint[]
  sourceSplit: SourceSplit[]
  recentFailures: RecentFailure[]
}

export interface AdminUser {
  id: string
  email: string
  displayName: string
  status: string
  role: string
  monthlyTokenQuota: number
  tokensUsed: number
  monthlyCostQuota: number
  costUsed: number
  articleCount: number
  lastLoginAt?: string | null
  createdAt: string
}

export interface AdminUsageGroup {
  key: string
  calls: number
  tokens: number
  cost: number
}

export interface AdminUsageSummary {
  calls: number
  tokens: number
  cost: number
  errorRate: number
  sourceSplit: AdminUsageGroup[]
  modelSplit: AdminUsageGroup[]
}

export interface AdminUsageRecord {
  id: string
  requestId: string
  userId: string
  userEmail?: string | null
  source: string
  scene: string
  model: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cost: number
  currency: string
  latencyMs?: number | null
  firstTokenMs?: number | null
  status: string
  errorCode?: string | null
  createdAt: string
}

export interface AdminUsageResponse {
  items: AdminUsageRecord[]
  total: number
  page: number
  pageSize: number
  summary: AdminUsageSummary
}

export interface AdminImport {
  id: string
  userId: string
  userEmail?: string | null
  folderPath: string
  status: string
  total: number
  success: number
  failed: number
  skipped: number
  errorCode?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt: string
}

export interface AdminImportItem {
  id: string
  userId: string
  userEmail?: string | null
  title?: string | null
  url: string
  folderPath?: string | null
  status: string
  articleId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
}

export interface AdminTask {
  id: string
  articleId?: string | null
  userId: string
  userEmail?: string | null
  title?: string | null
  url: string
  stage: string
  status: string
  errorCode?: string | null
  errorMessage?: string | null
  createdAt: string
  finishedAt?: string | null
}

export interface AdminModelConfig {
  chat: {
    provider: string
    baseUrl: string
    model: string
    apiKeyMasked?: string | null
    configured: boolean
    maxTokens: number
    temperature: number
    inputPrice: number
    outputPrice: number
    currency: string
  }
  embedding: {
    provider: string
    baseUrl: string
    model: string
    apiKeyMasked?: string | null
    configured: boolean
    dimension: number
    batchSize: number
    inputPrice: number
    currency: string
  }
  rerank: {
    enabled: boolean
    baseUrl: string
    model: string
    apiKeyMasked?: string | null
    configured: boolean
  }
  budget: {
    globalMonthlyTokens: number
    globalMonthlyCost: number
    defaultUserTokens: number
    defaultUserCost: number
  }
  source: string
}

export interface AdminModelUpdatePayload {
  chat: {
    baseUrl: string
    model: string
    apiKey: string
    maxTokens: number
    temperature: number
    inputPrice: number
    outputPrice: number
  }
  embedding: {
    baseUrl: string
    model: string
    apiKey: string
    dimension: number
    batchSize: number
  }
  rerank: {
    enabled: boolean
    baseUrl: string
    model: string
    apiKey: string
  }
}

export interface AdminSettings {
  registrationEnabled: boolean
  defaultMonthlyTokens: number
  defaultMonthlyCost: number
  globalMonthlyTokens: number
  globalMonthlyCost: number
  alertThreshold: number
  chatRateLimitPerMinute: number
  dailyTokenLimit: number
  ragChunkTargetSize: number
  ragChunkOverlap: number
  ragTopK: number
  ragHybridEnabled: boolean
  ragRerankEnabled: boolean
  ragCitationRequired: boolean
  registrationInvitationRequired: boolean
  registrationInvitationCode?: string | null
  source: string
}

export type AdminSettingsUpdatePayload = Omit<AdminSettings, 'source'>

export interface ModelTestResult {
  ok: boolean
  latencyMs: number
  model?: string | null
  message: string
}

export interface AdminAuditLog {
  id: string
  adminId: string
  adminEmail?: string | null
  action: string
  resourceType: string
  resourceId?: string | null
  detail?: unknown
  ip?: string | null
  userAgent?: string | null
  createdAt: string
}

export interface EvalCase {
  id: string
  userId: string
  userEmail?: string | null
  question: string
  expectedArticleIds: number[]
  expectedChunkIds: number[]
  goldAnswer?: string | null
  tags: string[]
  status: string
  createdAt: string
  updatedAt: string
}

export interface EvalResult {
  id: string
  caseId: string
  question?: string | null
  retrievedChunkIds: number[]
  recallAt5?: number | null
  citationAccuracy?: number | null
  answerCorrectness?: number | null
  latencyMs?: number | null
  cost?: number | null
  createdAt: string
}

export interface EvalRun {
  id: string
  userId: string
  userName?: string | null
  name: string
  model?: string | null
  embeddingModel?: string | null
  topK?: number | null
  rerank: boolean
  status: string
  metrics?: Record<string, unknown> | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt: string
  results?: EvalResult[]
}
