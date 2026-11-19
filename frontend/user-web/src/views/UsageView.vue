<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { ApiResult, PageResult, Quota, UsageRecord, UsageSummary } from '../api/types'

const pageSize = 20
const quota = ref<Quota | null>(null)
const summary = ref<UsageSummary | null>(null)
const records = ref<UsageRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const maxDailyTokens = computed(() => {
  const daily = summary.value?.daily ?? []
  return Math.max(1, ...daily.map((day) => day.tokens))
})

const tokenPercent = computed(() => {
  if (!quota.value || quota.value.tokenQuota <= 0) return 0
  return Math.min(100, Math.round((quota.value.tokensUsed / quota.value.tokenQuota) * 100))
})

const costPercent = computed(() => {
  if (!quota.value || quota.value.costQuota <= 0) return 0
  return Math.min(100, Math.round((quota.value.costUsed / quota.value.costQuota) * 100))
})

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

function barHeight(tokens: number) {
  if (!tokens) {
    return '0%'
  }
  return `${Math.max(4, Math.round((tokens / maxDailyTokens.value) * 100))}%`
}

function sceneLabel(scene: string) {
  if (scene === 'CHAT') return '问答'
  if (scene === 'EMBEDDING') return '向量化'
  if (scene === 'SUMMARY') return '摘要'
  if (scene === 'RERANK') return '重排'
  return scene
}

function sourceLabel(source: string) {
  if (source === 'WEB') return 'Web'
  if (source === 'EXTENSION') return '插件'
  if (source === 'ADMIN') return '管理后台'
  if (source === 'API') return 'API'
  return source
}

async function loadQuota() {
  try {
    const { data } = await api.get<ApiResult<Quota>>('/usage/quota')
    quota.value = data.data
  } catch {
    ElMessage.error('读取配额失败')
  }
}

async function loadSummary() {
  try {
    const { data } = await api.get<ApiResult<UsageSummary>>('/usage/summary', {
      params: { days: 7 },
    })
    summary.value = data.data
  } catch {
    ElMessage.error('读取用量趋势失败')
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<UsageRecord>>>('/usage/records', {
      params: { page: page.value, pageSize },
    })
    records.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取用量明细失败')
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadQuota(), loadSummary(), loadRecords()])
}

async function exportCsv() {
  try {
    const { data } = await api.get<Blob>('/usage/export', { responseType: 'blob' })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = 'shiye-usage.csv'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出用量失败')
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>用量</h1>
        <p>本月配额和每次 AI 调用的 token、成本、耗时。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="exportCsv">导出 CSV</el-button>
        <el-button text :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="usage-cards">
      <div class="usage-card">
        <span>本月 tokens</span>
        <b>{{ (quota?.tokensUsed || 0).toLocaleString() }}</b>
        <small>配额 {{ (quota?.tokenQuota || 0).toLocaleString() }}</small>
        <div class="bar"><i :style="{ width: `${tokenPercent}%` }" /></div>
        <em>{{ tokenPercent }}%</em>
      </div>
      <div class="usage-card">
        <span>本月成本</span>
        <b>{{ formatCost(quota?.costUsed) }}</b>
        <small>配额 {{ formatCost(quota?.costQuota) }}</small>
        <div class="bar"><i :style="{ width: `${costPercent}%` }" /></div>
        <em>{{ costPercent }}%</em>
      </div>
      <div class="usage-card">
        <span>调用次数</span>
        <b>{{ total }}</b>
        <small>问答 / 向量化等 AI 调用</small>
      </div>
      <div class="usage-card">
        <span>重置时间</span>
        <b class="small-value">{{ formatUtcTime(quota?.resetAt) }}</b>
        <small>{{ quota?.month || '-' }} 月度配额</small>
      </div>
    </div>

    <section class="usage-panel trend-panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">LAST {{ summary?.days || 7 }} DAYS</div>
          <h2>用量趋势</h2>
        </div>
        <span class="panel-note">UTC 日期 · 失败 {{ summary?.errors || 0 }} 次</span>
      </div>

      <div class="trend-totals">
        <span><b>{{ (summary?.calls || 0).toLocaleString() }}</b> 调用</span>
        <span><b>{{ (summary?.tokens || 0).toLocaleString() }}</b> tokens</span>
        <span><b>{{ formatCost(summary?.cost) }}</b> 成本</span>
      </div>

      <div v-if="summary" class="bar-chart">
        <div
          v-for="day in summary.daily"
          :key="day.date"
          class="bar-col"
          :title="`${day.date} · ${day.calls} 次 · ${day.tokens} tokens`"
        >
          <div class="bar-track">
            <i :style="{ height: barHeight(day.tokens) }" />
          </div>
          <span>{{ day.date.slice(5) }}</span>
        </div>
      </div>

      <div v-if="summary" class="breakdowns">
        <div>
          <h3>按场景</h3>
          <p v-for="item in summary.scenes" :key="item.label">
            <span>{{ sceneLabel(item.label) }} · {{ item.calls }} 次</span>
            <b>{{ item.tokens.toLocaleString() }} tokens · {{ formatCost(item.cost) }}</b>
          </p>
          <p v-if="summary.scenes.length === 0" class="muted">暂无调用</p>
        </div>
        <div>
          <h3>按来源</h3>
          <p v-for="item in summary.sources" :key="item.label">
            <span>{{ sourceLabel(item.label) }} · {{ item.calls }} 次</span>
            <b>{{ item.tokens.toLocaleString() }} tokens · {{ formatCost(item.cost) }}</b>
          </p>
          <p v-if="summary.sources.length === 0" class="muted">暂无调用</p>
        </div>
      </div>
    </section>

    <section class="usage-panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">AI USAGE</div>
          <h2>调用明细</h2>
        </div>
        <span class="panel-note">单价快照写入每条记录，后续改价不影响历史</span>
      </div>

      <div v-if="loading && records.length === 0" class="empty">正在加载用量…</div>
      <div v-else-if="records.length === 0" class="empty">还没有 AI 调用。去问答页问一个问题试试。</div>

      <div v-else class="usage-list">
        <div class="usage-head">
          <span>时间</span>
          <span>场景</span>
          <span>模型</span>
          <span>Tokens</span>
          <span>成本</span>
          <span>耗时</span>
          <span>状态</span>
        </div>
        <div v-for="record in records" :key="record.id" class="usage-row">
          <span class="time">{{ formatUtcTime(record.createdAt) }}</span>
          <span>{{ sceneLabel(record.scene) }}</span>
          <span class="model">{{ record.model }}</span>
          <span>{{ record.totalTokens.toLocaleString() }}</span>
          <span>{{ formatCost(record.cost) }}</span>
          <span>{{ record.latencyMs ? `${record.latencyMs} ms` : '-' }}</span>
          <span>
            <span class="status" :class="record.status.toLowerCase()">{{ record.status }}</span>
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
          @current-change="loadRecords"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.usage-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.usage-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px 18px;
}
.usage-card span {
  display: block;
  color: var(--muted);
  font-size: 11px;
}
.usage-card b {
  display: block;
  font-size: 26px;
  letter-spacing: -0.03em;
  margin: 4px 0 2px;
}
.usage-card b.small-value {
  font-size: 16px;
  margin-top: 10px;
}
.usage-card small {
  color: var(--faint);
  font-size: 10px;
}
.usage-card em {
  display: block;
  color: var(--faint);
  font-size: 9.5px;
  font-style: normal;
  margin-top: 4px;
  text-align: right;
}
.bar {
  height: 5px;
  background: #ecebe6;
  border-radius: 999px;
  overflow: hidden;
  margin-top: 9px;
}
.bar i {
  display: block;
  height: 100%;
  background: var(--ink);
  border-radius: 999px;
}
.trend-panel {
  margin-bottom: 16px;
}
.trend-totals {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  margin: 2px 0 12px;
  color: var(--muted);
  font-size: 10.5px;
}
.trend-totals b {
  color: var(--ink);
  font-size: 18px;
  letter-spacing: -0.02em;
  margin-right: 3px;
}
.bar-chart {
  display: flex;
  align-items: stretch;
  gap: 8px;
  height: 128px;
  padding: 10px 12px 7px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fbfbf9;
}
.bar-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.bar-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.bar-track i {
  display: block;
  width: 58%;
  max-width: 34px;
  background: var(--ink);
  border-radius: 4px 4px 2px 2px;
}
.bar-col span {
  color: var(--faint);
  font-size: 9px;
  white-space: nowrap;
}
.breakdowns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-top: 12px;
}
.breakdowns h3 {
  margin: 0 0 5px;
  color: var(--muted);
  font-size: 10.5px;
  font-weight: 700;
}
.breakdowns p {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 0;
  padding: 4px 0;
  border-top: 1px solid #f1f0ec;
  color: var(--muted);
  font-size: 10px;
}
.breakdowns p b {
  color: var(--ink);
  font-weight: 600;
  white-space: nowrap;
}
.muted {
  color: var(--faint);
  font-size: 10.5px;
}
.usage-panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
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
.panel-note {
  color: var(--faint);
  font-size: 10px;
}
.empty {
  border: 1px dashed #d7d4cc;
  border-radius: 9px;
  padding: 28px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}
.usage-list {
  border: 1px solid var(--line);
  border-radius: 9px;
  overflow: hidden;
}
.usage-head,
.usage-row {
  display: grid;
  grid-template-columns: 1.35fr 0.8fr 1.1fr 0.7fr 0.7fr 0.7fr 0.6fr;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: 10.5px;
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
  font-weight: 700;
  background: #f1f0ec;
  color: var(--muted);
}
.status.success {
  color: #16794f;
  background: #f4fbf8;
}
.status.failed {
  color: #b3372f;
  background: #fdf7f6;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
@media (max-width: 960px) {
  .usage-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .breakdowns {
    grid-template-columns: 1fr;
  }
  .bar-chart {
    gap: 4px;
    padding-left: 6px;
    padding-right: 6px;
  }
  .usage-head,
  .usage-row {
    grid-template-columns: 1.3fr 0.8fr 1fr 0.7fr 0.7fr;
  }
  .usage-head span:nth-child(6),
  .usage-head span:nth-child(7),
  .usage-row span:nth-child(6),
  .usage-row span:nth-child(7) {
    display: none;
  }
}
</style>
