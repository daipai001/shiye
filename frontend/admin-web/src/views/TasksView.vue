<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { AdminTask, ApiResult, PageResult } from '../api/types'

const tasks = ref<AdminTask[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const statusFilter = ref('')
const userId = ref('')
const loading = ref(false)

const statuses = ['PENDING', 'FETCHING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'READY', 'FAILED', 'SKIPPED']

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
    ElMessage.error('读取索引任务失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>索引任务</h1>
        <p>逐条查看书签导入的抓取/解析/切片/向量化状态；失败项可重试。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
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
          <el-button v-if="task.status === 'FAILED'" text @click="retry(task)">重试</el-button>
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
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
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
}
</style>
