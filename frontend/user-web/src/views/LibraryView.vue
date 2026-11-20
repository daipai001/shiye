<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useTeamStore } from '../stores/teams'
import { formatUtcTime } from '../utils/time'
import type {
  ApiResult,
  ArticleChunk,
  ArticleDetail,
  ArticleFolder,
  ArticleStats,
  ArticleSummary,
  ArticleUploadResponse,
  BookmarkFolder,
  BookmarkImportSummary,
  PageResult,
  Tag,
} from '../api/types'

const router = useRouter()
const teamStore = useTeamStore()
const pageSize = 10

const stats = ref<ArticleStats>({ total: 0, ready: 0, processing: 0, failed: 0 })
const articles = ref<ArticleSummary[]>([])
const total = ref(0)
const page = ref(1)
const q = ref('')
const statusFilter = ref('')
const tagFilter = ref('')
const folderFilter = ref('')
const teamFilter = ref('')
const tags = ref<Tag[]>([])
const folders = ref<BookmarkFolder[]>([])
const teamTags = ref<Tag[]>([])
const teamFolders = ref<ArticleFolder[]>([])
const teamFolderToAdd = ref('')
const loading = ref(false)
const uploading = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)
const folderUploading = ref(false)
const folderUploadInput = ref<HTMLInputElement | null>(null)

const imports = ref<BookmarkImportSummary[]>([])

const drawer = ref(false)
const detail = ref<ArticleDetail | null>(null)
const chunks = ref<ArticleChunk[]>([])
const detailLoading = ref(false)
const activeTab = ref('content')
const newTag = ref('')
const tagDialog = ref(false)
const tagDrafts = ref<Array<{ id: string; name: string; color: string; count: number }>>([])
const savingTagIds = ref<string[]>([])

const shareDialog = ref(false)
const shareArticle = ref<ArticleSummary | null>(null)
const shareTeamId = ref('')
const sharing = ref(false)

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
    const { data } = await api.get<ApiResult<ArticleStats>>('/articles/stats', {
      params: { teamId: teamFilter.value || undefined },
    })
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
        teamId: teamFilter.value || undefined,
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
  await Promise.all([
    teamStore.load(),
    loadStats(),
    loadArticles(),
    loadImports(),
    loadTags(),
    loadFolders(),
  ])
}

function applySearch() {
  page.value = 1
  loadArticles()
}

async function loadTeamTaxonomy() {
  if (!teamFilter.value) {
    teamTags.value = []
    teamFolders.value = []
    return
  }
  try {
    const [tagResponse, folderResponse] = await Promise.all([
      api.get<ApiResult<Tag[]>>(`/teams/${teamFilter.value}/tags`),
      api.get<ApiResult<ArticleFolder[]>>(`/teams/${teamFilter.value}/folders`),
    ])
    teamTags.value = tagResponse.data.data
    teamFolders.value = folderResponse.data.data
  } catch {
    teamTags.value = []
    teamFolders.value = []
  }
}

async function onTeamChange() {
  page.value = 1
  tagFilter.value = ''
  folderFilter.value = ''
  teamFolderToAdd.value = ''
  await loadTeamTaxonomy()
  await Promise.all([loadStats(), loadArticles()])
}

function folderOptionLabel(folder: BookmarkFolder | ArticleFolder) {
  return 'folderPath' in folder ? folder.folderPath : folder.path
}

function currentTeamRole() {
  return teamStore.teams.find((team) => team.id === teamFilter.value)?.myRole
}

function canDeleteArticle() {
  if (!teamFilter.value) return true
  return currentTeamRole() === 'OWNER' || currentTeamRole() === 'ADMIN'
}

function openShare(article: ArticleSummary) {
  shareArticle.value = article
  shareTeamId.value = teamStore.teams[0]?.id || ''
  shareDialog.value = true
}

async function submitShare() {
  if (!shareArticle.value || !shareTeamId.value) {
    ElMessage.warning('请选择团队')
    return
  }
  sharing.value = true
  try {
    await api.post(`/articles/${shareArticle.value.id}/share`, {
      teamId: shareTeamId.value,
    })
    ElMessage.success('已共享到团队')
    shareDialog.value = false
    await Promise.all([loadStats(), loadArticles()])
    if (drawer.value && detail.value) {
      await reloadDetail(detail.value.id)
    }
  } catch {
    ElMessage.error('共享失败，资料可能已经共享到其他团队')
  } finally {
    sharing.value = false
  }
}

async function unshareArticle(article: ArticleSummary) {
  try {
    await ElMessageBox.confirm(
      `取消《${article.title}》在团队中的共享？团队成员将不能再检索这篇资料。`,
      '取消共享',
      { confirmButtonText: '取消共享', cancelButtonText: '返回', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await api.delete(`/articles/${article.id}/share`)
    ElMessage.success('已取消共享')
    await Promise.all([loadStats(), loadArticles()])
    if (drawer.value && detail.value) {
      await reloadDetail(detail.value.id)
    }
  } catch {
    ElMessage.error('取消共享失败')
  }
}

function chooseUpload() {
  uploadInput.value?.click()
}

function chooseFolderUpload() {
  folderUploadInput.value?.click()
}

async function handleFolderUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
    .filter((file) => /\.(md|markdown|txt|pdf)$/i.test(file.name))
    .slice(0, 100)
  if (files.length === 0) {
    ElMessage.warning('文件夹里没有可导入的 Markdown、TXT 或 PDF')
    input.value = ''
    return
  }
  folderUploading.value = true
  let success = 0
  let duplicate = 0
  let failed = 0
  try {
    for (const file of files) {
      if (file.size > 20 * 1024 * 1024) {
        failed += 1
        continue
      }
      const formData = new FormData()
      formData.append('file', file)
      try {
        const { data } = await api.post<ApiResult<ArticleUploadResponse>>(
          '/articles/upload',
          formData,
          { timeout: 180000 },
        )
        if (data.data.duplicate) {
          duplicate += 1
        } else {
          success += 1
        }
      } catch {
        failed += 1
      }
    }
    ElMessage.success(`文件夹导入完成：新增 ${success}，重复 ${duplicate}，失败 ${failed}`)
    await Promise.all([loadStats(), loadArticles()])
  } finally {
    folderUploading.value = false
    input.value = ''
  }
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

async function reloadDetail(articleId: string) {
  const { data } = await api.get<ApiResult<ArticleDetail>>(`/articles/${articleId}`)
  detail.value = data.data
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

async function addTag() {
  if (!detail.value) {
    return
  }
  const name = newTag.value.trim()
  if (!name) {
    return
  }
  try {
    if (detail.value.teamId) {
      await api.post(`/articles/${detail.value.id}/team-tags`, { name })
      await Promise.all([openArticle(detail.value), loadTeamTaxonomy(), loadArticles()])
    } else {
      await api.post(`/articles/${detail.value.id}/tags`, { name })
      await Promise.all([openArticle(detail.value), loadTags(), loadArticles()])
    }
    newTag.value = ''
  } catch {
    ElMessage.error('添加标签失败')
  }
}

async function removeTag(tag: Tag) {
  if (!detail.value) {
    return
  }
  try {
    if (detail.value.teamId) {
      await api.delete(`/articles/${detail.value.id}/team-tags/${tag.id}`)
      await Promise.all([openArticle(detail.value), loadTeamTaxonomy(), loadArticles()])
    } else {
      await api.delete(`/articles/${detail.value.id}/tags/${tag.id}`)
      await Promise.all([openArticle(detail.value), loadTags(), loadArticles()])
    }
  } catch {
    ElMessage.error('移除标签失败')
  }
}

async function addTeamFolder() {
  if (!detail.value || !teamFolderToAdd.value) {
    ElMessage.warning('请选择团队文件夹')
    return
  }
  try {
    await api.post(`/articles/${detail.value.id}/team-folders`, {
      folderId: teamFolderToAdd.value,
    })
    teamFolderToAdd.value = ''
    await Promise.all([openArticle(detail.value), loadTeamTaxonomy(), loadArticles()])
  } catch {
    ElMessage.error('加入团队文件夹失败')
  }
}

async function removeTeamFolder(folder: ArticleFolder) {
  if (!detail.value) {
    return
  }
  try {
    await api.delete(`/articles/${detail.value.id}/team-folders/${folder.id}`)
    await Promise.all([openArticle(detail.value), loadTeamTaxonomy(), loadArticles()])
  } catch {
    ElMessage.error('移出团队文件夹失败')
  }
}

function onFolderClick(folder: ArticleFolder) {
  if (detail.value?.teamId) {
    void removeTeamFolder(folder)
  }
}

function openTagManager() {
  tagDrafts.value = tags.value.map((tag) => ({
    id: tag.id,
    name: tag.name,
    color: tag.color || '#6b7280',
    count: tag.articleCount || 0,
  }))
  tagDialog.value = true
}

function isSavingTag(tagId: string) {
  return savingTagIds.value.includes(tagId)
}

async function saveTag(tag: { id: string; name: string; color: string }) {
  const name = tag.name.trim()
  if (!name) {
    ElMessage.warning('标签名不能为空')
    return
  }
  savingTagIds.value = [...savingTagIds.value, tag.id]
  try {
    await api.patch(`/tags/${tag.id}`, { name, color: tag.color })
    tag.name = name
    ElMessage.success('标签已更新')
    await Promise.all([loadTags(), loadArticles()])
    if (detail.value) {
      await openArticle(detail.value)
    }
  } catch {
    ElMessage.error('更新标签失败')
  } finally {
    savingTagIds.value = savingTagIds.value.filter((id) => id !== tag.id)
  }
}

async function deleteTag(tag: { id: string; name: string; count: number }) {
  try {
    await ElMessageBox.confirm(
      `删除标签「${tag.name}」？${tag.count > 0 ? `它会从 ${tag.count} 篇资料上移除。` : ''}`,
      '删除标签',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  savingTagIds.value = [...savingTagIds.value, tag.id]
  try {
    await api.delete(`/tags/${tag.id}`)
    if (tagFilter.value === tag.id) {
      tagFilter.value = ''
    }
    tagDrafts.value = tagDrafts.value.filter((item) => item.id !== tag.id)
    ElMessage.success('标签已删除')
    await Promise.all([loadTags(), loadArticles()])
    if (detail.value) {
      await openArticle(detail.value)
    }
  } catch {
    ElMessage.error('删除标签失败')
  } finally {
    savingTagIds.value = savingTagIds.value.filter((id) => id !== tag.id)
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
        <template v-if="!teamFilter">
          <el-button text @click="openTagManager">标签管理</el-button>
          <input
            ref="uploadInput"
            type="file"
            accept=".md,.markdown,.txt,.pdf"
            class="file-input"
            @change="handleUpload"
          />
          <input
            ref="folderUploadInput"
            type="file"
            accept=".md,.markdown,.txt,.pdf"
            class="file-input"
            webkitdirectory
            multiple
            @change="handleFolderUpload"
          />
          <el-button :loading="uploading" @click="chooseUpload">上传资料</el-button>
          <el-button :loading="folderUploading" @click="chooseFolderUpload">导入文件夹</el-button>
          <el-button type="primary" @click="router.push('/plugin')">导入书签</el-button>
        </template>
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
          <el-select
            v-model="teamFilter"
            class="folder-select"
            placeholder="个人资料"
            @change="onTeamChange"
          >
            <el-option label="个人资料" value="" />
            <el-option
              v-for="team in teamStore.teams"
              :key="team.id"
              :label="`团队：${team.name}`"
              :value="team.id"
            />
          </el-select>
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
            :placeholder="teamFilter ? '全部团队标签' : '全部标签'"
            clearable
            filterable
            @change="applySearch"
          >
            <el-option
              v-for="tag in (teamFilter ? teamTags : tags)"
              :key="tag.id"
              :label="`${tag.name} (${tag.articleCount || 0})`"
              :value="tag.id"
            />
          </el-select>
          <el-select
            v-model="folderFilter"
            class="folder-select"
            :placeholder="teamFilter ? '全部团队文件夹' : '全部文件夹'"
            clearable
            filterable
            @change="applySearch"
          >
            <el-option
              v-for="folder in (teamFilter ? teamFolders : folders)"
              :key="folder.id"
              :label="folderOptionLabel(folder)"
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
            <span v-if="item.teamId && !teamFilter" class="shared-chip">已共享</span>
            <el-button text @click="openArticle(item)">查看</el-button>
            <el-button v-if="!teamFilter && !item.teamId" text @click="openShare(item)">共享</el-button>
            <el-button v-if="!teamFilter && item.teamId" text @click="unshareArticle(item)">取消共享</el-button>
            <el-button v-if="canDeleteArticle()" text type="danger" @click="deleteArticle(item)">删除</el-button>
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
          <span
            v-for="tag in detail.tags"
            :key="tag.id"
            class="tag-chip removable"
            :style="tagStyle(tag)"
            @click="removeTag(tag)"
          >
            {{ tag.name }} <b>×</b>
          </span>
        </div>
        <div class="tag-editor">
          <el-input
            v-model="newTag"
            size="small"
            maxlength="64"
            placeholder="添加标签，例如 RAG"
            @keyup.enter="addTag"
          />
          <el-button size="small" @click="addTag">添加</el-button>
        </div>
        <div v-if="detail.folders?.length" class="folder-line detail-tags">
          <span
            v-for="folder in detail.folders"
            :key="folder.id"
            class="folder-chip"
            :class="{ removable: detail.teamId }"
            @click="onFolderClick(folder)"
          >
            {{ folder.path }}<b v-if="detail.teamId"> ×</b>
          </span>
        </div>
        <div v-if="detail.teamId" class="team-folder-editor">
          <el-select v-model="teamFolderToAdd" size="small" placeholder="加入团队文件夹" clearable>
            <el-option
              v-for="folder in teamFolders"
              :key="folder.id"
              :label="folder.path"
              :value="folder.id"
            />
          </el-select>
          <el-button size="small" @click="addTeamFolder">加入</el-button>
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
          <el-button v-if="canDeleteArticle()" type="danger" plain @click="deleteArticle(detail)">
            删除这篇资料
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="shareDialog" title="共享到团队" width="440px">
      <div class="dialog-field">
        <span>选择团队</span>
        <el-select v-model="shareTeamId" placeholder="选择团队" style="width: 100%">
          <el-option
            v-for="team in teamStore.teams"
            :key="team.id"
            :label="team.name"
            :value="team.id"
          />
        </el-select>
      </div>
      <p class="dialog-hint">共享后团队成员可以在团队资料库和团队问答中检索这篇资料；你仍然保留个人副本。</p>
      <template #footer>
        <el-button @click="shareDialog = false">取消</el-button>
        <el-button type="primary" :loading="sharing" @click="submitShare">共享</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagDialog" title="标签管理" width="580px">
      <p class="tag-manager-hint">重命名、改色或删除标签；删除会同时移除资料上的标签关联。</p>
      <div v-if="tagDrafts.length === 0" class="empty compact">暂无标签。</div>
      <div v-else class="tag-manager-list">
        <div v-for="tag in tagDrafts" :key="tag.id" class="tag-manager-row">
          <el-color-picker v-model="tag.color" size="small" />
          <el-input v-model="tag.name" size="small" maxlength="64" />
          <span class="tag-count">{{ tag.count }} 篇</span>
          <el-button text :loading="isSavingTag(tag.id)" @click="saveTag(tag)">保存</el-button>
          <el-button text type="danger" :loading="isSavingTag(tag.id)" @click="deleteTag(tag)">删除</el-button>
        </div>
      </div>
    </el-dialog>
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
.tag-manager-hint {
  color: var(--faint);
  font-size: 10px;
  margin: 0 0 12px;
}
.tag-manager-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tag-manager-row {
  display: grid;
  grid-template-columns: 32px 1fr 56px auto auto;
  align-items: center;
  gap: 8px;
}
.tag-count {
  color: var(--faint);
  font-size: 10px;
  white-space: nowrap;
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
  flex-wrap: wrap;
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
.tag-chip.removable {
  cursor: pointer;
}
.tag-chip.removable b {
  margin-left: 4px;
  font-weight: 700;
  opacity: 0.55;
}
.tag-editor {
  display: flex;
  gap: 6px;
  margin: 0 0 14px;
  max-width: 360px;
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
.folder-chip.removable {
  cursor: pointer;
}
.folder-chip.removable b {
  margin-left: 4px;
  font-weight: 700;
  opacity: 0.55;
}
.team-folder-editor {
  display: flex;
  gap: 6px;
  margin: -6px 0 14px;
  max-width: 360px;
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
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 2px;
  flex: none;
}
.shared-chip {
  align-self: center;
  border-radius: 999px;
  background: #f4f7fb;
  color: #2f5d8a;
  padding: 1px 7px;
  font-size: 8.5px;
}
.dialog-field {
  margin-bottom: 14px;
}
.dialog-field > span {
  display: block;
  font-size: 11px;
  margin-bottom: 6px;
  color: var(--muted);
}
.dialog-hint {
  color: var(--faint);
  font-size: 10px;
  margin: 4px 0 0;
  line-height: 1.6;
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
