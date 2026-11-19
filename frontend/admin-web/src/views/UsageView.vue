<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { AdminUsageResponse, ApiResult } from '../api/types'

const data = ref<AdminUsageResponse | null>(null)
const page = ref(1)
const pageSize = 20
const source = ref('')
const scene = ref('')
const status = ref('')
const userId = ref('')
const loading = ref(false)

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

function formatPercent(value?: number | null) {
  return `${Number(value || 0) * 100 < 1 ? (Number(value || 0) * 100).toFixed(2) : (Number(value || 0) * 100).toFixed(1)}%`
}

function params() {
  return {
    page: page.value,
    pageSize,
    source: source.value || undefined,
    scene: scene.value || undefined,
    status: status.value || undefined,
    userId: userId.value.trim() || undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const response = await api.get<ApiResult<AdminUsageResponse>>('/usage', {
      params: params(),
    })
    data.value = response.data.data
  } catch {
    ElMessage.error('读取用量失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
}

async function exportCsv() {
  try {
    const response = await api.get('/usage/export', {
      params: params(),
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'shiye-usage.csv'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>用量与成本</h1>
        <p>全站 AI 调用、token、成本和错误率；支持筛选和 CSV 导出。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" plain @click="exportCsv">导出 CSV</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card"><b>{{ (data?.summary.calls || 0).toLocaleString() }}</b><span>调用次数</span></div>
      <div class="summary-card"><b>{{ (data?.summary.tokens || 0).toLocaleString() }}</b><span>总 tokens</span></div>
      <div class="summary-card"><b>{{ formatCost(data?.summary.cost) }}</b><span>总成本</span></div>
      <div class="summary-card"><b>{{ formatPercent(data?.summary.errorRate) }}</b><span>错误率</span></div>
    </div>

    <div class="toolbar">
      <el-input v-model="userId" placeholder="用户 ID" clearable class="short" @keyup.enter="applyFilter" @clear="applyFilter" />
      <el-select v-model="source" placeholder="全部来源" clearable class="short" @change="applyFilter">
        <el-option label="网页" value="WEB" />
        <el-option label="插件" value="EXTENSION" />
      </el-select>
      <el-select v-model="scene" placeholder="全部场景" clearable class="short" @change="applyFilter">
        <el-option label="问答" value="CHAT" />
        <el-option label="向量化" value="EMBEDDING" />
        <el-option label="摘要" value="SUMMARY" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" clearable class="short" @change="applyFilter">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="中断" value="ABORTED" />
      </el-select>
      <el-button type="primary" @click="applyFilter">筛选</el-button>
    </div>

    <div class="split-grid">
      <section class="panel">
        <div class="eyebrow">SOURCE SPLIT</div>
        <h2>来源</h2>
        <div v-if="!data?.summary.sourceSplit?.length" class="empty compact">暂无数据</div>
        <div v-for="item in data?.summary.sourceSplit || []" :key="item.key" class="split-row">
          <span>{{ item.key === 'EXTENSION' ? '插件' : '网页' }}</span>
          <b>{{ item.tokens.toLocaleString() }} tokens</b>
          <em>{{ formatCost(item.cost) }}</em>
        </div>
      </section>
      <section class="panel">
        <div class="eyebrow">MODEL SPLIT</div>
        <h2>模型</h2>
        <div v-if="!data?.summary.modelSplit?.length" class="empty compact">暂无数据</div>
        <div v-for="item in data?.summary.modelSplit || []" :key="item.key" class="split-row">
          <span>{{ item.key }}</span>
          <b>{{ item.tokens.toLocaleString() }} tokens</b>
          <em>{{ formatCost(item.cost) }}</em>
        </div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">AI USAGE</div>
          <h2>调用明细</h2>
        </div>
        <span class="hint" v-if="data">共 {{ data.total }} 条</span>
      </div>

      <div v-if="!data?.items?.length" class="empty">没有匹配的调用记录。</div>
      <div v-else class="usage-list">
        <div class="usage-head">
          <span>时间</span>
          <span>用户</span>
          <span>来源 / 场景</span>
          <span>模型</span>
          <span>Tokens</span>
          <span>成本</span>
          <span>延迟</span>
          <span>状态</span>
        </div>
        <div v-for="item in data.items" :key="item.id" class="usage-row">
          <span class="time">{{ formatUtcTime(item.createdAt) }}</span>
          <span class="email">{{ item.userEmail || item.userId }}</span>
          <span>{{ item.source === 'EXTENSION' ? '插件' : '网页' }} / {{ item.scene === 'CHAT' ? '问答' : item.scene }}</span>
          <span class="model">{{ item.model }}</span>
          <span>{{ item.totalTokens.toLocaleString() }}</span>
          <span>{{ formatCost(item.cost) }}</span>
          <span>{{ item.latencyMs ? `${item.latencyMs} ms` : '-' }}</span>
          <span><i class="status" :class="item.status.toLowerCase()">{{ item.status }}</i></span>
        </div>
      </div>

      <div v-if="data && data.total > pageSize" class="pager">
        <el-pagination
          v-model:current-page="page"
          background
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="data.total"
          @current-change="load"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.summary-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 15px 17px;
}
.summary-card b {
  display: block;
  font-size: 25px;
  letter-spacing: -0.03em;
}
.summary-card span {
  color: var(--muted);
  font-size: 11px;
}
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.short {
  width: 130px;
}
.split-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 18px;
}
.panel h2 {
  margin: 0 0 10px;
  font-size: 17px;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  color: var(--faint);
  font-size: 10px;
}
.split-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-top: 1px solid #f1f0ec;
  font-size: 10.5px;
  color: var(--muted);
}
.split-row b {
  margin-left: auto;
  color: var(--ink);
  font-size: 10px;
}
.split-row em {
  color: var(--faint);
  font-style: normal;
  width: 80px;
  text-align: right;
}
.usage-list {
  border: 1px solid var(--line);
  border-radius: 9px;
  overflow: hidden;
}
.usage-head,
.usage-row {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 1fr 1fr 0.7fr 0.7fr 0.7fr 0.6fr;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: 10px;
}
.usage-head {
  background: #fbfbf9;
  color: var(--faint);
  font-weight: 600;
  border-bottom: 1px solid var(--line);
}
.usage-row {
  color: var(--muted);
  border-bottom: 1px solid #f1f0ec;
}
.usage-row:last-child {
  border-bottom: 0;
}
.usage-row .time {
  color: var(--faint);
  font-size: 9.5px;
}
.usage-row .email {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-row .model {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status {
  display: inline-block;
  border-radius: 999px;
  padding: 1px 6px;
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
  background: #f1f0ec;
  color: var(--muted);
}
.status.success {
  background: #f4fbf8;
  color: #16794f;
}
.status.failed {
  background: #fdf7f6;
  color: #b3372f;
}
.empty {
  border: 1px dashed #d7d4cc;
  border-radius: 8px;
  padding: 26px;
  text-align: center;
  color: var(--muted);
  font-size: 11.5px;
}
.empty.compact {
  padding: 14px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
@media (max-width: 1100px) {
  .summary-grid,
  .split-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .usage-head,
  .usage-row {
    grid-template-columns: 1.2fr 1.2fr 1fr 0.7fr 0.7fr 0.6fr;
  }
  .usage-head span:nth-child(3),
  .usage-row span:nth-child(3),
  .usage-head span:nth-child(7),
  .usage-row span:nth-child(7) {
    display: none;
  }
}
</style>
