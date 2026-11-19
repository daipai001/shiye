<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { ApiResult, Overview } from '../api/types'

const overview = ref<Overview | null>(null)
const loading = ref(false)

const maxCalls = computed(() => {
  const values = overview.value?.trends.map((point) => point.calls) || []
  return Math.max(1, ...values)
})

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

function errorPercent(value: number) {
  return `${(value * 100).toFixed(2)}%`
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<Overview>>('/overview')
    overview.value = data.data
  } catch {
    ElMessage.error('读取总览失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>总览</h1>
        <p>用户、资料、AI 调用和失败任务的实时概览。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="metric-grid">
      <div class="metric"><b>{{ overview?.users ?? '-' }}</b><span>注册用户</span></div>
      <div class="metric"><b>{{ overview?.articles ?? '-' }}</b><span>资料总数</span></div>
      <div class="metric"><b>{{ overview?.chunks ?? '-' }}</b><span>切片总数</span></div>
      <div class="metric"><b>{{ overview?.todayCalls ?? '-' }}</b><span>今日调用</span></div>
      <div class="metric"><b>{{ (overview?.todayTokens ?? 0).toLocaleString() }}</b><span>今日 tokens</span></div>
      <div class="metric"><b>{{ formatCost(overview?.todayCost) }}</b><span>今日成本</span></div>
      <div class="metric"><b>{{ errorPercent(overview?.errorRate ?? 0) }}</b><span>今日错误率</span></div>
      <div class="metric"><b>{{ overview?.p95LatencyMs ?? 0 }} ms</b><span>今日 P95 延迟</span></div>
    </div>

    <div class="overview-grid">
      <section class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">LAST 7 DAYS</div>
            <h2>调用趋势</h2>
          </div>
          <span class="hint">按调用次数</span>
        </div>
        <div class="trend">
          <div v-for="point in overview?.trends || []" :key="point.date" class="trend-col">
            <div class="trend-value">{{ point.calls }}</div>
            <div class="trend-bar-wrap">
              <div class="trend-bar" :style="{ height: `${Math.max(4, (point.calls / maxCalls) * 100)}%` }" />
            </div>
            <div class="trend-date">{{ point.date.slice(5) }}</div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">SOURCE SPLIT · TODAY</div>
            <h2>来源拆分</h2>
          </div>
        </div>
        <div v-if="!overview?.sourceSplit?.length" class="empty compact">今天还没有 AI 调用。</div>
        <div v-else class="source-list">
          <div v-for="source in overview.sourceSplit" :key="source.source" class="source-row">
            <div>
              <b>{{ source.source === 'EXTENSION' ? '插件' : '网页' }}</b>
              <span>{{ source.calls }} 次 · {{ source.tokens.toLocaleString() }} tokens</span>
            </div>
            <strong>{{ formatCost(source.cost) }}</strong>
          </div>
        </div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">FAILURES</div>
          <h2>最近失败</h2>
        </div>
        <span class="hint">导入 / 索引 / AI</span>
      </div>
      <div v-if="!overview?.recentFailures?.length" class="empty">没有失败记录。</div>
      <div v-else class="failure-list">
        <div v-for="failure in overview.recentFailures" :key="`${failure.type}-${failure.resourceId}-${failure.createdAt}`" class="failure-row">
          <span class="type">{{ failure.type }}</span>
          <span class="user">{{ failure.userName || '-' }}</span>
          <span class="error">{{ failure.errorCode || 'FAILED' }}<em v-if="failure.errorMessage">{{ failure.errorMessage }}</em></span>
          <span class="time">{{ formatUtcTime(failure.createdAt) }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.metric {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 15px 17px;
}
.metric b {
  display: block;
  font-size: 25px;
  letter-spacing: -0.03em;
}
.metric span {
  color: var(--muted);
  font-size: 11px;
}
.overview-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-head h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.02em;
}
.hint {
  color: var(--faint);
  font-size: 10px;
}
.trend {
  height: 180px;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding-top: 10px;
}
.trend-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
}
.trend-value {
  color: var(--faint);
  font-size: 9.5px;
  margin-bottom: 4px;
}
.trend-bar-wrap {
  width: 100%;
  flex: 1;
  display: flex;
  align-items: flex-end;
  background: #fbfbf9;
  border-radius: 6px 6px 0 0;
  overflow: hidden;
}
.trend-bar {
  width: 100%;
  background: #1c1c1a;
  border-radius: 6px 6px 0 0;
  min-height: 3px;
}
.trend-date {
  color: var(--faint);
  font-size: 9px;
  margin-top: 5px;
}
.source-list {
  display: grid;
  gap: 10px;
}
.source-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
}
.source-row b {
  display: block;
  font-size: 12px;
}
.source-row span {
  display: block;
  color: var(--faint);
  font-size: 10px;
  margin-top: 2px;
}
.source-row strong {
  font-size: 12px;
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
  padding: 18px;
}
.failure-list {
  display: grid;
}
.failure-row {
  display: grid;
  grid-template-columns: 70px 1fr 2fr 130px;
  gap: 10px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f1f0ec;
  font-size: 11px;
  color: var(--muted);
}
.failure-row:last-child {
  border-bottom: 0;
}
.type {
  display: inline-block;
  width: fit-content;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 1px 6px;
  font-size: 9px;
  color: var(--faint);
}
.user {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.error {
  color: var(--danger);
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
@media (max-width: 1000px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .failure-row {
    grid-template-columns: 60px 1fr;
  }
  .error,
  .time {
    display: none;
  }
}
</style>
