export interface ApiResult<T> {
  code: number
  message: string
  data: T
  requestId?: string
}

export interface UserSummary {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
  status: string
  createdAt: string
}

export interface PluginSettings {
  ballEnabled: boolean
  defaultMode: string
  defaultScope: string
  defaultScopeId?: string | null
  shortcut?: string | null
  siteBlacklist: string[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
  user: UserSummary
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface BookmarkImportSummary {
  id: string
  folderPath: string
  status: string
  total: number
  success: number
  failed: number
  skipped: number
  errorCode?: string | null
  createdAt: string
  finishedAt?: string | null
}

export interface ArticleStats {
  total: number
  ready: number
  processing: number
  failed: number
}

export interface ArticleSummary {
  id: string
  title: string
  domain?: string | null
  url?: string | null
  sourceType: string
  summary?: string | null
  status: string
  wordCount: number
  chunkCount: number
  language?: string | null
  tags?: Tag[]
  folders?: ArticleFolder[]
  createdAt: string
  updatedAt: string
}

export interface Tag {
  id: string
  name: string
  color?: string | null
  articleCount?: number
}

export interface ArticleDetail extends ArticleSummary {
  author?: string | null
  publishedAt?: string | null
  contentText?: string | null
  contentTruncated?: boolean
  fetchedAt?: string | null
}

export interface ArticleFolder {
  id: string
  path: string
}

export interface BookmarkFolder {
  id: string
  browser: string
  folderId: string
  folderPath: string
  isPrimary: boolean
  autoSync: boolean
  lastSyncedAt?: string | null
}

export interface ArticleUploadResponse {
  fileAssetId: string
  articleId: string
  status: string
  duplicate: boolean
}

export interface ArticleChunk {
  id: string
  chunkIndex: number
  content: string
  tokenCount: number
  charStart: number
  charEnd: number
  embeddingModel: string
  embeddingDim: number
  vectorId: string
}

export interface ChatCitation {
  rank: number
  articleId: string
  chunkId: string
  title: string
  quote: string
  score: number
  charStart: number
  charEnd: number
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  model?: string | null
  status: string
  promptTokens: number
  completionTokens: number
  firstTokenMs?: number | null
  latencyMs?: number | null
  citations: ChatCitation[]
  feedback?: 'UP' | 'DOWN' | null
  createdAt?: string
  streaming?: boolean
}

export interface Conversation {
  id: string
  title: string
  scopeType: string
  scopeId?: string | null
  model?: string | null
  lastMessage?: string | null
  createdAt: string
  updatedAt: string
}

export interface ConversationDetail extends Conversation {
  messageCount: number
}

export interface Quota {
  month: string
  tokenQuota: number
  tokensUsed: number
  tokenRemaining: number
  costQuota: number
  costUsed: number
  costRemaining: number
  resetAt: string
}

export interface UsageRecord {
  id: string
  requestId: string
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

export interface UsageBreakdown {
  label: string
  calls: number
  tokens: number
  cost: number
}

export interface UsageDaily {
  date: string
  calls: number
  tokens: number
  cost: number
  errors: number
}

export interface UsageSummary {
  from: string
  to: string
  days: number
  calls: number
  tokens: number
  cost: number
  errors: number
  scenes: UsageBreakdown[]
  sources: UsageBreakdown[]
  daily: UsageDaily[]
}
