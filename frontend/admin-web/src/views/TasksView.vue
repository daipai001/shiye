<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAdminAuthStore } from '../stores/auth'
import { formatUtcTime } from '../utils/time'
import type { AdminTask, ApiResult, PageResult } from '../api/types'

const auth = useAdminAuthStore()
const tasks = ref<AdminTask[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const statusFilter = ref('')
const userId = ref('')
const loading = ref(false)

const ingestTasks = ref<AdminTask[]>([])
const ingestTotal = ref(0)
const ingestPage = ref(1)
const ingestStatusFilter = ref('')
const ingestLoading = ref(false)

const statuses = ['PENDING', 'FETCHING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'READY', 'FAILED', 'SKIPPED']
const ingestStatuses = ['RUNNING', 'SUCCESS', 'FAILED', 'DEAD', 'PENDING']

const sourceLabels: Record<string, string> = {
  BOOKMARK: '书签',
  MANUAL: '手动',
  UPLOAD: '上传',
  REINDEX: '重建索引',
}

function badgeClass(status: string) {
  return `badge ${status.toLowerCase()}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminTask>>>('/tasks', {
      params: {
        page: page.value,
        pageSize,
        status: statusFilter.value || undefined,
        userId: userId.value.trim() || undefined,
      },
    })
    tasks.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取书签索引任务失败')
  } finally {
    loading.value = false
  }
}

async function loadIngest() {
  ingestLoading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminTask>>>('/ingest-tasks', {
      params: {
        page: ingestPage.value,
        pageSize,
        status: ingestStatusFilter.value || undefined,
        userId: userId.value.trim() || undefined,
      },
    })
    ingestTasks.value = data.data.items
    ingestTotal.value = data.data.total
  } catch {
    ElMessage.error('读取资料入库任务失败')
  } finally {
    ingestLoading.value = false
  }
}

function loadAll() {
  page.value = 1
  ingestPage.value = 1
  void Promise.all([load(), loadIngest()])
}

function applyFilter() {
  page.value = 1
  ingestPage.value = 1
  void Promise.all([load(), loadIngest()])
}

function applyIngestFilter() {
  ingestPage.value = 1
  loadIngest()
}

async function retry(task: AdminTask) {
  try {
    await api.post(`/tasks/${task.id}/retry`)
    ElMessage.success('已重新入队')
    await load()
  } catch {
    ElMessage.error('重试失败')
  }
}

onMounted(() => {
  void load()
  void loadIngest()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>索引任务</h1>
        <p>书签导入的抓取/解析/切片/向量化状态，以及资料入库任务。</p>
      </div>
      <el-button text :loading="loading || ingestLoading" @click="loadAll">刷新</el-button>
    </div>

    <div class="block-head">
      <div>
        <h2>书签导入任务</h2>
        <p>失败项可重新入队。</p>
      </div>
    </div>

    <div class="toolbar">
      <el-input v-model="userId" placeholder="用户 ID" clearable class="short" @keyup.enter="applyFilter" @clear="applyFilter" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="short" @change="applyFilter">
        <el-option v-for="value in statuses" :key="value" :label="value" :value="value" />
      </el-select>
      <el-button type="primary" @click="applyFilter">筛选</el-button>
    </div>

    <div v-if="loading && tasks.length === 0" class="empty">正在加载任务…</div>
    <div v-else-if="tasks.length === 0" class="empty">没有索引任务。</div>
    <div v-else class="task-list">
      <div class="task-head">
        <span>任务</span>
        <span>用户</span>
        <span>资料</span>
        <span>阶段 / 状态</span>
        <span>错误</span>
        <span>更新时间</span>
        <span>操作</span>
      </div>
      <div v-for="task in tasks" :key="task.id" class="task-row">
        <b>#{{ task.id }}</b>
        <span class="email">{{ task.userEmail || task.userId }}</span>
        <div class="task-article">
          <b>{{ task.title || task.url }}</b>
          <span>{{ task.articleId ? `article #${task.articleId}` : '尚未生成资料' }}</span>
        </div>
        <span><i :class="badgeClass(task.status)">{{ task.stage }}</i></span>
        <span class="error">{{ task.errorCode || '-' }}<em v-if="task.errorMessage">{{ task.errorMessage }}</em></span>
        <span class="time">{{ formatUtcTime(task.finishedAt || task.createdAt) }}</span>
        <div class="actions">
          <el-button v-if="auth.canWrite && task.status === 'FAILED'" text @click="retry(task)">重试</el-button>
          <span v-else class="muted">—</span>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pager">
      <el-pagination
        v-model:current-page="page"
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="total"
        @current-change="load"
      />
    </div>

    <div class="block-head ingest-head">
      <div>
        <h2>资料入库任务</h2>
        <p>上传、当前页、URL 抓取和重建索引的切片/向量化/索引状态。</p>
      </div>
      <el-button text :loading="ingestLoading" @click="loadIngest">刷新</el-button>
    </div>

    <div class="toolbar">
      <el-select
        v-model="ingestStatusFilter"
        placeholder="全部状态"
        clearable
        class="short"
        @change="applyIngestFilter"
      >
        <el-option v-for="value in ingestStatuses" :key="value" :label="value" :value="value" />
      </el-select>
      <el-button type="primary" @click="applyIngestFilter">筛选</el-button>
    </div>

    <div v-if="ingestLoading && ingestTasks.length === 0" class="empty">正在加载入库任务…</div>
    <div v-else-if="ingestTasks.length === 0" class="empty">没有资料入库任务。</div>
    <div v-else class="ingest-list">
      <div class="ingest-head-row">
        <span>任务</span>
        <span>用户</span>
        <span>资料</span>
        <span>来源 / 阶段</span>
        <span>状态</span>
        <span>错误</span>
        <span>更新时间</span>
      </div>
      <div v-for="task in ingestTasks" :key="task.id" class="ingest-row">
        <b>#{{ task.id }}</b>
        <span class="email">{{ task.userEmail || task.userId }}</span>
        <div class="task-article">
          <b>{{ task.title || task.url || '未生成资料' }}</b>
          <span>{{ task.articleId ? `article #${task.articleId}` : '—' }}</span>
        </div>
        <span class="ingest-stage">
          <b>{{ sourceLabels[task.sourceType || ''] || task.sourceType || '-' }}</b>
          <em>{{ task.stage }}</em>
        </span>
        <span><i :class="badgeClass(task.status)">{{ task.status }}</i></span>
        <span class="error">{{ task.errorCode || '-' }}<em v-if="task.errorMessage">{{ task.errorMessage }}</em></span>
        <span class="time">{{ formatUtcTime(task.finishedAt || task.createdAt) }}</span>
      </div>
    </div>

    <div v-if="ingestTotal > pageSize" class="pager">
      <el-pagination
        v-model:current-page="ingestPage"
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="ingestTotal"
        @current-change="loadIngest"
      />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.block-head h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.02em;
}
.block-head p {
  margin: 4px 0 0;
  color: var(--faint);
  font-size: 10px;
}
.ingest-head {
  margin-top: 24px;
}
.ingest-list {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.ingest-head-row,
.ingest-row {
  display: grid;
  grid-template-columns: 0.45fr 1.1fr 1.6fr 0.9fr 0.7fr 1.2fr 1fr;
  gap: 8px;
  align-items: center;
  padding: 10px 13px;
  font-size: 10px;
}
.ingest-head-row {
  background: #fbfbf9;
  color: var(--faint);
  border-bottom: 1px solid var(--line);
  font-weight: 600;
}
.ingest-row {
  border-bottom: 1px solid #f1f0ec;
  color: var(--muted);
}
.ingest-row:last-child {
  border-bottom: 0;
}
.ingest-row > b {
  color: var(--ink);
  font-size: 11px;
}
.ingest-stage b {
  display: block;
  color: var(--ink);
  font-size: 9.5px;
  font-weight: 600;
}
.ingest-stage em {
  display: block;
  color: var(--faint);
  font-size: 9px;
  font-style: normal;
}
.short {
  width: 140px;
}
.empty {
  border: 1px dashed #d7d4cc;
  border-radius: 9px;
  padding: 36px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  background: #fff;
}
.task-list {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.task-head,
.task-row {
  display: grid;
  grid-template-columns: 0.45fr 1.1fr 1.6fr 0.8fr 1.2fr 1fr 0.6fr;
  gap: 8px;
  align-items: center;
  padding: 10px 13px;
  font-size: 10px;
}
.task-head {
  background: #fbfbf9;
  color: var(--faint);
  border-bottom: 1px solid var(--line);
  font-weight: 600;
}
.task-row {
  border-bottom: 1px solid #f1f0ec;
  color: var(--muted);
}
.task-row:last-child {
  border-bottom: 0;
}
.task-row > b {
  color: var(--ink);
  font-size: 11px;
}
.email {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-article {
  min-width: 0;
}
.task-article b {
  display: block;
  color: var(--ink);
  font-size: 10.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-article span {
  display: block;
  color: var(--faint);
  font-size: 9px;
}
.badge {
  display: inline-block;
  border-radius: 999px;
  padding: 1px 7px;
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
  background: #f1f0ec;
  color: var(--muted);
  border: 1px solid var(--line);
}
.badge.pending,
.badge.fetching,
.badge.parsing,
.badge.chunking,
.badge.embedding {
  background: #f7f7fe;
  color: var(--primary);
  border-color: #deddf8;
}
.badge.ready {
  background: #f4fbf8;
  color: #16794f;
  border-color: #c9efe1;
}
.badge.success,
.badge.running {
  background: #f4fbf8;
  color: #16794f;
  border-color: #c9efe1;
}
.badge.dead {
  background: #fdf7f6;
  color: #b3372f;
  border-color: #eed4d1;
}
.badge.failed {
  background: #fdf7f6;
  color: #b3372f;
  border-color: #eed4d1;
}
.error {
  color: #b3372f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.error em {
  display: block;
  color: var(--faint);
  font-size: 9px;
  font-style: normal;
}
.time {
  color: var(--faint);
  font-size: 9.5px;
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.muted {
  color: var(--faint);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
@media (max-width: 1100px) {
  .task-head,
  .task-row {
    grid-template-columns: 0.5fr 1.1fr 1.4fr 0.8fr 0.6fr;
  }
  .task-head span:nth-child(5),
  .task-row .error,
  .task-head span:nth-child(6),
  .task-row .time {
    display: none;
  }
  .ingest-head-row,
  .ingest-row {
    grid-template-columns: 0.5fr 1fr 1.4fr 0.9fr 0.7fr 0.8fr;
  }
  .ingest-head-row span:nth-child(6),
  .ingest-row .error {
    display: none;
  }
}
</style>
