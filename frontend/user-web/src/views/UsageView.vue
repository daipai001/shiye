<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { ApiResult, PageResult, Quota, UsageRecord } from '../api/types'

const pageSize = 20
const quota = ref<Quota | null>(null)
const records = ref<UsageRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

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

function sceneLabel(scene: string) {
  if (scene === 'CHAT') return '问答'
  if (scene === 'EMBEDDING') return '向量化'
  if (scene === 'SUMMARY') return '摘要'
  return scene
}

async function loadQuota() {
  try {
    const { data } = await api.get<ApiResult<Quota>>('/usage/quota')
    quota.value = data.data
  } catch {
    ElMessage.error('读取配额失败')
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
  await Promise.all([loadQuota(), loadRecords()])
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
      <el-button text :loading="loading" @click="loadAll">刷新</el-button>
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
