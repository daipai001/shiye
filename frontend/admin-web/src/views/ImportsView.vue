<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAdminAuthStore } from '../stores/auth'
import { formatUtcTime } from '../utils/time'
import type { AdminImport, AdminImportItem, ApiResult, PageResult } from '../api/types'

const auth = useAdminAuthStore()
const imports = ref<AdminImport[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const statusFilter = ref('')
const userId = ref('')
const loading = ref(false)

const drawer = ref(false)
const current = ref<AdminImport | null>(null)
const items = ref<AdminImportItem[]>([])
const itemsLoading = ref(false)
const itemStatus = ref('')

const statusText: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '处理中',
  PAUSED: '已暂停',
  DONE: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

function statusLabel(status: string) {
  return statusText[status] || status
}

function badgeClass(status: string) {
  return `badge ${status.toLowerCase()}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminImport>>>('/imports', {
      params: {
        page: page.value,
        pageSize,
        status: statusFilter.value || undefined,
        userId: userId.value.trim() || undefined,
      },
    })
    imports.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取导入任务失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
}

async function openDetail(item: AdminImport) {
  current.value = item
  drawer.value = true
  itemStatus.value = ''
  await loadItems()
}

async function loadItems() {
  if (!current.value) return
  itemsLoading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminImportItem>>>(
      `/imports/${current.value.id}/items`,
      { params: { page: 1, pageSize: 100, status: itemStatus.value || undefined } },
    )
    items.value = data.data.items
  } catch {
    ElMessage.error('读取导入条目失败')
  } finally {
    itemsLoading.value = false
  }
}

async function action(item: AdminImport, actionName: 'retry' | 'pause' | 'resume' | 'cancel') {
  if (actionName === 'cancel') {
    try {
      await ElMessageBox.confirm(`取消导入任务 #${item.id}？`, '取消任务', {
        confirmButtonText: '取消任务',
        cancelButtonText: '返回',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  try {
    await api.post(`/imports/${item.id}/${actionName}`, {})
    ElMessage.success('操作成功')
    await load()
    if (current.value?.id === item.id) {
      const { data } = await api.get<ApiResult<AdminImport>>(`/imports/${item.id}`)
      current.value = data.data
      await loadItems()
    }
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>书签导入</h1>
        <p>全站导入任务、逐条状态和失败重试。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="userId" placeholder="用户 ID" clearable class="short" @keyup.enter="applyFilter" @clear="applyFilter" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="short" @change="applyFilter">
        <el-option v-for="(label, key) in statusText" :key="key" :label="label" :value="key" />
      </el-select>
      <el-button type="primary" @click="applyFilter">筛选</el-button>
    </div>

    <div v-if="loading && imports.length === 0" class="empty">正在加载导入任务…</div>
    <div v-else-if="imports.length === 0" class="empty">没有导入任务。</div>
    <div v-else class="import-list">
      <div class="import-head">
        <span>任务</span>
        <span>用户</span>
        <span>文件夹</span>
        <span>状态</span>
        <span>进度</span>
        <span>创建时间</span>
        <span>操作</span>
      </div>
      <div v-for="item in imports" :key="item.id" class="import-row">
        <b>#{{ item.id }}</b>
        <span class="email">{{ item.userEmail || item.userId }}</span>
        <span class="path">{{ item.folderPath }}</span>
        <span><i :class="badgeClass(item.status)">{{ statusLabel(item.status) }}</i></span>
        <span class="counts">{{ item.success }}/{{ item.total }} 成功 · {{ item.failed }} 失败 · {{ item.skipped }} 跳过</span>
        <span class="time">{{ formatUtcTime(item.createdAt) }}</span>
        <div class="actions">
          <el-button text @click="openDetail(item)">详情</el-button>
          <el-button v-if="auth.canWrite && ['PENDING', 'RUNNING'].includes(item.status)" text @click="action(item, 'pause')">暂停</el-button>
          <el-button v-if="auth.canWrite && item.status === 'PAUSED'" text @click="action(item, 'resume')">继续</el-button>
          <el-button v-if="auth.canWrite && ['PENDING', 'RUNNING', 'PAUSED'].includes(item.status)" text type="danger" @click="action(item, 'cancel')">取消</el-button>
          <el-button v-if="auth.canWrite && ['DONE', 'FAILED'].includes(item.status) && item.failed > 0" text @click="action(item, 'retry')">重试</el-button>
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

    <el-drawer v-model="drawer" size="720px" :title="current ? `导入任务 #${current.id}` : '导入任务'">
      <div v-if="current" class="detail-head">
        <div>
          <b>{{ current.folderPath }}</b>
          <p>{{ current.userEmail }} · {{ formatUtcTime(current.createdAt) }}</p>
        </div>
        <i :class="badgeClass(current.status)">{{ statusLabel(current.status) }}</i>
      </div>
      <div v-if="current" class="detail-metrics">
        <span>总数 {{ current.total }}</span>
        <span>成功 {{ current.success }}</span>
        <span>失败 {{ current.failed }}</span>
        <span>跳过 {{ current.skipped }}</span>
      </div>

      <div class="toolbar">
        <el-select v-model="itemStatus" placeholder="全部条目状态" clearable class="short" @change="loadItems">
          <el-option v-for="value in ['PENDING', 'FETCHING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'READY', 'FAILED', 'SKIPPED']" :key="value" :label="value" :value="value" />
        </el-select>
        <el-button text :loading="itemsLoading" @click="loadItems">刷新条目</el-button>
      </div>

      <div v-if="itemsLoading && items.length === 0" class="empty compact">加载中…</div>
      <div v-else-if="items.length === 0" class="empty compact">没有条目。</div>
      <div v-else class="item-list">
        <div v-for="item in items" :key="item.id" class="item-row">
          <div class="item-main">
            <b>{{ item.title || item.url }}</b>
            <span class="url">{{ item.url }}</span>
          </div>
          <i :class="badgeClass(item.status)">{{ item.status }}</i>
          <span class="item-error">{{ item.errorCode || '' }} {{ item.errorMessage || '' }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
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
.empty.compact {
  padding: 18px;
}
.import-list {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.import-head,
.import-row {
  display: grid;
  grid-template-columns: 0.45fr 1.1fr 1.3fr 0.7fr 1.3fr 1fr 1.4fr;
  gap: 8px;
  align-items: center;
  padding: 10px 13px;
  font-size: 10px;
}
.import-head {
  background: #fbfbf9;
  color: var(--faint);
  border-bottom: 1px solid var(--line);
  font-weight: 600;
}
.import-row {
  border-bottom: 1px solid #f1f0ec;
  color: var(--muted);
}
.import-row:last-child {
  border-bottom: 0;
}
.import-row b {
  color: var(--ink);
  font-size: 11px;
}
.email,
.path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.counts {
  font-size: 9.5px;
  white-space: nowrap;
}
.time {
  color: var(--faint);
  font-size: 9.5px;
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
.badge.running,
.badge.pending,
.badge.paused {
  background: #f7f7fe;
  color: var(--primary);
  border-color: #deddf8;
}
.badge.done {
  background: #f4fbf8;
  color: #16794f;
  border-color: #c9efe1;
}
.badge.failed {
  background: #fdf7f6;
  color: #b3372f;
  border-color: #eed4d1;
}
.actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.detail-head b {
  font-size: 14px;
}
.detail-head p {
  margin: 4px 0 0;
  color: var(--faint);
  font-size: 10px;
}
.detail-metrics {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 12px 0;
  padding: 9px 11px;
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--muted);
  font-size: 10.5px;
}
.item-list {
  display: grid;
  gap: 6px;
}
.item-row {
  display: grid;
  grid-template-columns: 1fr 70px 1.4fr;
  gap: 8px;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 10px;
  background: #fbfbf9;
}
.item-main {
  min-width: 0;
}
.item-main b {
  display: block;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.url {
  display: block;
  color: var(--faint);
  font-size: 9px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-error {
  color: #b3372f;
  font-size: 9.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 1100px) {
  .import-head,
  .import-row {
    grid-template-columns: 0.5fr 1.2fr 1fr 0.8fr 1.2fr;
  }
  .import-head span:nth-child(5),
  .import-row .counts,
  .import-head span:nth-child(6),
  .import-row .time {
    display: none;
  }
}
</style>
