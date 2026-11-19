<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type {
  ApiResult,
  ArticleChunk,
  ArticleDetail,
  ArticleStats,
  ArticleSummary,
  ArticleUploadResponse,
  BookmarkFolder,
  BookmarkImportSummary,
  PageResult,
  Tag,
} from '../api/types'

const router = useRouter()
const pageSize = 10

const stats = ref<ArticleStats>({ total: 0, ready: 0, processing: 0, failed: 0 })
const articles = ref<ArticleSummary[]>([])
const total = ref(0)
const page = ref(1)
const q = ref('')
const statusFilter = ref('')
const tagFilter = ref('')
const folderFilter = ref('')
const tags = ref<Tag[]>([])
const folders = ref<BookmarkFolder[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)

const imports = ref<BookmarkImportSummary[]>([])

const drawer = ref(false)
const detail = ref<ArticleDetail | null>(null)
const chunks = ref<ArticleChunk[]>([])
const detailLoading = ref(false)
const activeTab = ref('content')

const statusText: Record<string, string> = {
  PENDING: '排队中',
  PARSING: '解析中',
  CHUNKING: '切片中',
  EMBEDDING: '向量化中',
  READY: '已索引',
  FAILED: '失败',
  RUNNING: '处理中',
  PAUSED: '已暂停',
  DONE: '已完成',
  CANCELLED: '已取消',
}

function statusLabel(status: string) {
  return statusText[status] || status
}

function badgeClass(status: string) {
  return `badge ${status.toLowerCase()}`
}

function formatTime(value?: string | null) {
  return formatUtcTime(value)
}

function tagStyle(tag: Tag) {
  if (!tag.color) {
    return {}
  }
  return {
    color: tag.color,
    borderColor: `${tag.color}55`,
    background: `${tag.color}14`,
  }
}

async function loadStats() {
  try {
    const { data } = await api.get<ApiResult<ArticleStats>>('/articles/stats')
    stats.value = data.data
  } catch {
    // 静默失败，列表仍可单独使用
  }
}

async function loadArticles() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<ArticleSummary>>>('/articles', {
      params: {
        page: page.value,
        pageSize,
        q: q.value.trim() || undefined,
        status: statusFilter.value || undefined,
        tagId: tagFilter.value || undefined,
        folderId: folderFilter.value || undefined,
      },
    })
    articles.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取资料列表失败')
  } finally {
    loading.value = false
  }
}

async function loadImports() {
  try {
    const { data } = await api.get<ApiResult<PageResult<BookmarkImportSummary>>>(
      '/bookmark-imports?page=1&pageSize=3',
    )
    imports.value = data.data.items
  } catch {
    imports.value = []
  }
}

async function loadTags() {
  try {
    const { data } = await api.get<ApiResult<{ items: Tag[] }>>('/tags')
    tags.value = data.data.items
  } catch {
    tags.value = []
  }
}

async function loadFolders() {
  try {
    const { data } = await api.get<ApiResult<{ items: BookmarkFolder[] }>>('/bookmark-folders')
    folders.value = data.data.items
  } catch {
    folders.value = []
  }
}

async function loadAll() {
  await Promise.all([loadStats(), loadArticles(), loadImports(), loadTags(), loadFolders()])
}

function applySearch() {
  page.value = 1
  loadArticles()
}

function chooseUpload() {
  uploadInput.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件不能超过 20MB')
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await api.post<ApiResult<ArticleUploadResponse>>(
      '/articles/upload',
      formData,
      { timeout: 180000 },
    )
    ElMessage.success(data.data.duplicate ? '这篇资料已经存在' : '已上传，正在解析、切片和向量化')
    await Promise.all([loadStats(), loadArticles()])
  } catch {
    ElMessage.error('上传失败，请检查文件类型或稍后再试')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function openArticle(article: ArticleSummary) {
  drawer.value = true
  detail.value = null
  chunks.value = []
  activeTab.value = 'content'
  detailLoading.value = true
  try {
    const [detailResponse, chunkResponse] = await Promise.all([
      api.get<ApiResult<ArticleDetail>>(`/articles/${article.id}`),
      api.get<ApiResult<PageResult<ArticleChunk>>>(`/articles/${article.id}/chunks`, {
        params: { page: 1, pageSize: 100 },
      }),
    ])
    detail.value = detailResponse.data.data
    chunks.value = chunkResponse.data.data.items
  } catch {
    ElMessage.error('读取资料详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function deleteArticle(article: ArticleSummary) {
  try {
    await ElMessageBox.confirm(
      `删除《${article.title}》？正文、切片和向量都会清理。`,
      '删除资料',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await api.delete(`/articles/${article.id}`)
    ElMessage.success('已删除')
    drawer.value = false
    await Promise.all([loadStats(), loadArticles()])
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>资料库</h1>
        <p>收藏的网页会在这里变成可检索的资料：抓正文、切片、向量化。</p>
      </div>
      <div class="page-actions">
        <input
          ref="uploadInput"
          type="file"
          accept=".md,.markdown,.txt,.pdf"
          class="file-input"
          @change="handleUpload"
        />
        <el-button :loading="uploading" @click="chooseUpload">上传资料</el-button>
        <el-button type="primary" @click="router.push('/plugin')">导入书签</el-button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card"><b>{{ stats.total }}</b><span>资料总数</span></div>
      <div class="stat-card"><b>{{ stats.ready }}</b><span>已索引</span></div>
      <div class="stat-card"><b>{{ stats.processing }}</b><span>处理中</span></div>
      <div class="stat-card"><b>{{ stats.failed }}</b><span>失败</span></div>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">ARTICLES</div>
          <h2>资料</h2>
        </div>
        <div class="filters">
          <el-input
            v-model="q"
            class="search"
            placeholder="搜索标题 / 摘要"
            clearable
            @keyup.enter="applySearch"
            @clear="applySearch"
          />
          <el-select v-model="statusFilter" class="status-select" placeholder="全部状态" clearable @change="applySearch">
            <el-option label="排队中" value="PENDING" />
            <el-option label="解析中" value="PARSING" />
            <el-option label="切片中" value="CHUNKING" />
            <el-option label="向量化中" value="EMBEDDING" />
            <el-option label="已索引" value="READY" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-select
            v-model="tagFilter"
            class="status-select"
            placeholder="全部标签"
            clearable
            filterable
            @change="applySearch"
          >
            <el-option
              v-for="tag in tags"
              :key="tag.id"
              :label="`${tag.name} (${tag.articleCount || 0})`"
              :value="tag.id"
            />
          </el-select>
          <el-select
            v-model="folderFilter"
            class="folder-select"
            placeholder="全部文件夹"
            clearable
            filterable
            @change="applySearch"
          >
            <el-option
              v-for="folder in folders"
              :key="folder.id"
              :label="folder.folderPath"
              :value="folder.id"
            />
          </el-select>
          <el-button text :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <div v-if="loading && articles.length === 0" class="empty">正在加载资料…</div>
      <div v-else-if="articles.length === 0" class="empty">
        还没有资料。打开「插件」页生成授权码，在插件设置里选择书签文件夹导入；后台会自动抓取、切片和向量化。
      </div>

      <div v-else class="article-list">
        <article v-for="item in articles" :key="item.id" class="article-row">
          <div class="article-body">
            <div class="article-title-line">
              <h3 @click="openArticle(item)">{{ item.title }}</h3>
              <span :class="badgeClass(item.status)">{{ statusLabel(item.status) }}</span>
            </div>
            <p class="summary">{{ item.summary || '暂无摘要' }}</p>
            <div v-if="item.tags?.length" class="tag-line">
              <span v-for="tag in item.tags" :key="tag.id" class="tag-chip" :style="tagStyle(tag)">
                {{ tag.name }}
              </span>
            </div>
            <div v-if="item.folders?.length" class="folder-line">
              <span v-for="folder in item.folders" :key="folder.id" class="folder-chip">
                {{ folder.path }}
              </span>
            </div>
            <div class="meta">
              <span>{{ item.domain || '未知来源' }}</span>
              <span>{{ item.wordCount }} 字</span>
              <span>{{ item.chunkCount }} 片</span>
              <span>{{ item.language || '-' }}</span>
              <span>更新于 {{ formatTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div class="article-actions">
            <el-button text @click="openArticle(item)">查看</el-button>
            <el-button text type="danger" @click="deleteArticle(item)">删除</el-button>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          v-model:current-page="page"
          background
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="total"
          @current-change="loadArticles"
        />
      </div>
    </section>

    <section class="panel imports-panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">BOOKMARK IMPORTS</div>
          <h2>最近导入</h2>
        </div>
        <el-button text :loading="loading" @click="loadImports">刷新</el-button>
      </div>
      <p v-if="imports.length === 0" class="empty compact">还没有导入任务。</p>
      <div v-else class="import-list">
        <div v-for="item in imports" :key="item.id" class="import-row">
          <div class="import-main">
            <b>#{{ item.id }}</b>
            <span class="path">{{ item.folderPath }}</span>
            <span :class="badgeClass(item.status)">{{ statusLabel(item.status) }}</span>
          </div>
          <div class="import-meta">
            <span>总数 {{ item.total }}</span>
            <span>成功 {{ item.success }}</span>
            <span>失败 {{ item.failed }}</span>
            <span>跳过 {{ item.skipped }}</span>
            <span>{{ formatTime(item.createdAt) }}</span>
          </div>
        </div>
      </div>
    </section>

    <el-drawer v-model="drawer" size="640px" :title="detail?.title || '资料详情'">
      <div v-if="detailLoading" class="empty">正在加载详情…</div>
      <div v-else-if="detail" class="detail">
        <div class="detail-meta">
          <span>{{ detail.domain || '未知来源' }}</span>
          <a v-if="detail.url" :href="detail.url" target="_blank" rel="noopener noreferrer">
            {{ detail.url }}
          </a>
        </div>
        <div class="detail-badges">
          <span :class="badgeClass(detail.status)">{{ statusLabel(detail.status) }}</span>
          <span>{{ detail.wordCount }} 字</span>
          <span>{{ detail.chunkCount }} 个切片</span>
          <span>{{ detail.language || '-' }}</span>
          <span v-if="detail.author">{{ detail.author }}</span>
        </div>
        <p class="detail-summary">{{ detail.summary || '暂无摘要' }}</p>
        <div v-if="detail.tags?.length" class="tag-line detail-tags">
          <span v-for="tag in detail.tags" :key="tag.id" class="tag-chip" :style="tagStyle(tag)">
            {{ tag.name }}
          </span>
        </div>
        <div v-if="detail.folders?.length" class="folder-line detail-tags">
          <span v-for="folder in detail.folders" :key="folder.id" class="folder-chip">
            {{ folder.path }}
          </span>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="正文" name="content">
            <pre class="content-text">{{ detail.contentText || '正文为空（抓取或解析失败）' }}</pre>
          </el-tab-pane>
          <el-tab-pane :label="`切片 ${chunks.length}`" name="chunks">
            <div v-if="chunks.length === 0" class="empty compact">暂无切片。</div>
            <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
              <div class="chunk-head">
                #{{ chunk.chunkIndex }} · 字符 {{ chunk.charStart }}–{{ chunk.charEnd }} ·
                {{ chunk.tokenCount }} tokens · {{ chunk.embeddingModel }}
              </div>
              <p>{{ chunk.content }}</p>
            </div>
          </el-tab-pane>
        </el-tabs>

        <div class="detail-actions">
          <el-button type="danger" plain @click="deleteArticle(detail)">删除这篇资料</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.file-input {
  display: none;
}
.stat-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 18px;
}
.stat-card b {
  display: block;
  font-size: 28px;
  letter-spacing: -0.03em;
}
.stat-card span {
  color: var(--muted);
  font-size: 12px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}
.panel-head h2 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.02em;
}
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search {
  width: 220px;
}
.status-select {
  width: 120px;
}
.folder-select {
  width: 160px;
}
.empty {
  margin: 0;
  border: 1px dashed #d7d4cc;
  border-radius: 9px;
  padding: 30px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.empty.compact {
  padding: 18px;
}
.article-list {
  display: grid;
  gap: 8px;
}
.article-row {
  display: flex;
  gap: 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fbfbf9;
  padding: 12px 14px;
}
.article-body {
  flex: 1;
  min-width: 0;
}
.article-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.article-title-line h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.article-title-line h3:hover {
  color: var(--primary);
}
.summary {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tag-line {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 7px;
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--muted);
  background: #faf9f6;
}
.detail-tags {
  margin: 4px 0 14px;
}
.folder-line {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 5px;
}
.folder-chip {
  display: inline-flex;
  align-items: center;
  border: 1px dashed #d7d4cc;
  border-radius: 6px;
  padding: 1px 7px;
  font-size: 9.5px;
  line-height: 1.6;
  color: var(--faint);
  background: #fbfbf9;
}
.meta,
.import-meta,
.detail-meta,
.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 6px;
  color: var(--faint);
  font-size: 10.5px;
}
.article-actions {
  display: flex;
  align-items: flex-start;
  gap: 2px;
  flex: none;
}
.badge {
  border: 1px solid var(--line);
  background: #f1f0ec;
  color: var(--muted);
  border-radius: 999px;
  padding: 2px 7px;
  font-size: 9.5px;
  font-weight: 700;
  white-space: nowrap;
}
.badge.ready {
  color: #16794f;
  background: #f4fbf8;
  border-color: #c9efe1;
}
.badge.failed {
  color: #b3372f;
  background: #fdf7f6;
  border-color: #eed4d1;
}
.badge.pending,
.badge.parsing,
.badge.chunking,
.badge.embedding,
.badge.running {
  color: var(--primary);
  background: #f7f7fe;
  border-color: #deddf8;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.imports-panel .panel-head {
  margin-bottom: 8px;
}
.import-list {
  display: grid;
  gap: 8px;
}
.import-row {
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fbfbf9;
  padding: 10px 12px;
}
.import-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.import-main b {
  font-size: 12px;
}
.import-main .path {
  color: var(--muted);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.import-main .badge {
  margin-left: auto;
}
.detail-meta {
  margin-top: 0;
  align-items: center;
}
.detail-meta a {
  color: var(--primary);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}
.detail-badges {
  margin-top: 8px;
}
.detail-summary {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
  margin: 12px 0;
}
.content-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Cascadia Mono', Consolas, 'Microsoft YaHei', monospace;
  font-size: 11.5px;
  line-height: 1.8;
  color: var(--ink);
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
  max-height: 52vh;
  overflow: auto;
}
.chunk-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fbfbf9;
}
.chunk-head {
  color: var(--faint);
  font-size: 10px;
  margin-bottom: 5px;
}
.chunk-card p {
  margin: 0;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}
.detail-actions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

@media (max-width: 960px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .panel-head {
    flex-direction: column;
  }
  .filters {
    width: 100%;
    flex-wrap: wrap;
  }
  .article-row {
    flex-direction: column;
  }
  .article-actions {
    justify-content: flex-end;
  }
}
</style>
