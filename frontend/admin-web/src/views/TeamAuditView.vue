<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { ApiResult, PageResult } from '../api/types'
import type { AdminTeamAuditLog } from '../api/teamAuditTypes'

const pageSize = 20
const items = ref<AdminTeamAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const action = ref('')
const teamId = ref('')
const loading = ref(false)
const selected = ref<AdminTeamAuditLog | null>(null)
const detailVisible = ref(false)

function openDetail(item: AdminTeamAuditLog) {
  selected.value = item
  detailVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const numericTeamId = Number(teamId.value.trim())
    const { data } = await api.get<ApiResult<PageResult<AdminTeamAuditLog>>>('/team-audits', {
      params: {
        page: page.value,
        pageSize,
        action: action.value.trim() || undefined,
        teamId: Number.isFinite(numericTeamId) && numericTeamId > 0
          ? numericTeamId
          : undefined,
      },
    })
    items.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取团队审计失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
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
        <h1>团队审计</h1>
        <p>团队邀请、加入、移除、角色变更、转让、解散和资料共享记录。</p>
      </div>
      <div class="head-actions">
        <el-input
          v-model="teamId"
          class="search narrow"
          placeholder="团队 ID"
          clearable
          @keyup.enter="applyFilter"
          @clear="applyFilter"
        />
        <el-input
          v-model="action"
          class="search"
          placeholder="按 action 筛选，例如 MEMBER_REMOVE"
          clearable
          @keyup.enter="applyFilter"
          @clear="applyFilter"
        />
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loading && items.length === 0" class="empty">正在加载团队审计…</div>
      <div v-else-if="items.length === 0" class="empty">暂无团队审计记录。</div>
      <div v-else class="log-list">
        <div class="log-head">
          <span>时间</span>
          <span>团队</span>
          <span>操作人</span>
          <span>动作</span>
          <span>对象</span>
          <span>IP</span>
          <span>详情</span>
        </div>
        <div v-for="item in items" :key="item.id" class="log-row">
          <span class="muted">{{ formatUtcTime(item.createdAt) }}</span>
          <span>{{ item.teamName || item.teamId }}</span>
          <span>{{ item.operatorEmail || item.operatorId }}</span>
          <span><b>{{ item.action }}</b></span>
          <span>
            {{ item.targetType || '-' }}
            <em v-if="item.targetId"> #{{ item.targetId }}</em>
          </span>
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

    <el-dialog v-model="detailVisible" title="团队审计详情" width="620px">
      <template v-if="selected">
        <div class="detail-row"><span>动作</span><b>{{ selected.action }}</b></div>
        <div class="detail-row"><span>团队</span><b>{{ selected.teamName || selected.teamId }}</b></div>
        <div class="detail-row"><span>操作人</span><b>{{ selected.operatorEmail || selected.operatorId }}</b></div>
        <div class="detail-row"><span>对象</span><b>{{ selected.targetType }} {{ selected.targetId || '' }}</b></div>
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
.search.narrow {
  width: 120px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.empty {
  padding: 40px;
  text-align: center;
  color: var(--muted);
}
.log-list {
  display: flex;
  flex-direction: column;
}
.log-head,
.log-row {
  display: grid;
  grid-template-columns: 180px 1.1fr 1.2fr 1.2fr 1.1fr 120px 70px;
  gap: 12px;
  align-items: center;
  padding: 10px 16px;
}
.log-head {
  font-size: 12px;
  color: var(--muted);
  border-bottom: 1px solid var(--line);
}
.log-row {
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.log-row:last-child {
  border-bottom: none;
}
.log-row em {
  color: var(--muted);
  font-style: normal;
}
.muted {
  color: var(--muted);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
}
.detail-row {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  font-size: 13px;
}
.detail-row span {
  width: 84px;
  color: var(--muted);
}
.detail-json {
  margin-top: 12px;
  max-height: 320px;
  overflow: auto;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
