<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { AdminAuditLog, ApiResult, PageResult } from '../api/types'

const pageSize = 20
const items = ref<AdminAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const action = ref('')
const loading = ref(false)
const exporting = ref(false)
const selected = ref<AdminAuditLog | null>(null)
const detailVisible = ref(false)

function openDetail(item: AdminAuditLog) {
  selected.value = item
  detailVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminAuditLog>>>('/audit-logs', {
      params: {
        page: page.value,
        pageSize,
        action: action.value.trim() || undefined,
      },
    })
    items.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取审计日志失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
}

async function exportCsv() {
  exporting.value = true
  try {
    const { data } = await api.get<Blob>('/audit-logs/export', {
      params: { action: action.value.trim() || undefined },
      responseType: 'blob',
    })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = 'shiye-audit-logs.csv'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出审计日志失败')
  } finally {
    exporting.value = false
  }
}

function detailText(detail: unknown) {
  if (detail == null) return '无'
  try {
    return JSON.stringify(detail, null, 2)
  } catch {
    return String(detail)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>审计日志</h1>
        <p>管理员对模型、系统设置、用户、导入和任务的操作记录。</p>
      </div>
      <div class="head-actions">
        <el-input
          v-model="action"
          class="search"
          placeholder="按 action 筛选，例如 MODEL_UPDATE"
          clearable
          @keyup.enter="applyFilter"
          @clear="applyFilter"
        />
        <el-button :loading="exporting" @click="exportCsv">导出 CSV</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loading && items.length === 0" class="empty">正在加载审计日志…</div>
      <div v-else-if="items.length === 0" class="empty">暂无审计记录。</div>
      <div v-else class="log-list">
        <div class="log-head">
          <span>时间</span>
          <span>管理员</span>
          <span>动作</span>
          <span>资源</span>
          <span>IP</span>
          <span>详情</span>
        </div>
        <div v-for="item in items" :key="item.id" class="log-row">
          <span class="muted">{{ formatUtcTime(item.createdAt) }}</span>
          <span>{{ item.adminEmail || item.adminId }}</span>
          <span><b>{{ item.action }}</b></span>
          <span>{{ item.resourceType }}<em v-if="item.resourceId"> #{{ item.resourceId }}</em></span>
          <span class="muted">{{ item.ip || '-' }}</span>
          <span>
            <el-button text size="small" @click="openDetail(item)">查看</el-button>
          </span>
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
    </section>

    <el-dialog v-model="detailVisible" title="审计详情" width="620px">
      <template v-if="selected">
        <div class="detail-row"><span>动作</span><b>{{ selected.action }}</b></div>
        <div class="detail-row"><span>管理员</span><b>{{ selected.adminEmail || selected.adminId }}</b></div>
        <div class="detail-row"><span>资源</span><b>{{ selected.resourceType }} {{ selected.resourceId || '' }}</b></div>
        <div class="detail-row"><span>IP</span><b>{{ selected.ip || '-' }}</b></div>
        <div class="detail-row"><span>User-Agent</span><b>{{ selected.userAgent || '-' }}</b></div>
        <pre class="detail-json">{{ detailText(selected.detail) }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search {
  width: 280px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
}
.empty {
  border: 1px dashed #d7d4cc;
  border-radius: 9px;
  padding: 32px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}
.log-list {
  border: 1px solid var(--line);
  border-radius: 9px;
  overflow: hidden;
}
.log-head,
.log-row {
  display: grid;
  grid-template-columns: 1.2fr 1.1fr 1fr 1fr 0.8fr 0.5fr;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: 10.5px;
}
.log-head {
  background: #fbfbf9;
  color: var(--faint);
  font-weight: 600;
  border-bottom: 1px solid var(--line);
}
.log-row {
  color: var(--muted);
  border-bottom: 1px solid #f1f0ec;
}
.log-row:last-child {
  border-bottom: 0;
}
.log-row b {
  color: var(--ink);
  font-weight: 600;
}
.log-row em {
  color: var(--faint);
  font-style: normal;
  margin-left: 3px;
}
.muted {
  color: var(--faint);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 6px 0;
  border-top: 1px solid #f1f0ec;
  font-size: 11px;
  color: var(--muted);
}
.detail-row b {
  color: var(--ink);
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}
.detail-json {
  margin: 12px 0 0;
  padding: 10px;
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 10.5px;
  line-height: 1.6;
  max-height: 340px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 1000px) {
  .log-head,
  .log-row {
    grid-template-columns: 1.2fr 1fr 1fr 0.6fr;
  }
  .log-head span:nth-child(5),
  .log-row span:nth-child(5) {
    display: none;
  }
}
</style>
