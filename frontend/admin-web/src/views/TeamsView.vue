<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAdminAuthStore } from '../stores/auth'
import { formatUtcTime } from '../utils/time'
import type { AdminTeam, ApiResult, PageResult } from '../api/types'

const auth = useAdminAuthStore()
const teams = ref<AdminTeam[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const q = ref('')
const statusFilter = ref('')
const loading = ref(false)

const quotaDialog = ref(false)
const quotaTeam = ref<AdminTeam | null>(null)
const tokenQuota = ref(0)
const costQuota = ref(0)

const quotaTitle = computed(() => (quotaTeam.value ? `调整 ${quotaTeam.value.name} 的配额` : '调整团队配额'))

function tokenPercent(team: AdminTeam) {
  if (!team.monthlyTokenQuota) return 0
  return Math.min(100, Math.round((team.tokensUsed / team.monthlyTokenQuota) * 100))
}

function costPercent(team: AdminTeam) {
  if (!team.monthlyCostQuota) return 0
  return Math.min(100, Math.round((team.costUsed / team.monthlyCostQuota) * 100))
}

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminTeam>>>('/teams', {
      params: {
        page: page.value,
        pageSize,
        q: q.value.trim() || undefined,
        status: statusFilter.value || undefined,
      },
    })
    teams.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取团队列表失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
}

function openQuota(team: AdminTeam) {
  quotaTeam.value = team
  tokenQuota.value = team.monthlyTokenQuota
  costQuota.value = team.monthlyCostQuota
  quotaDialog.value = true
}

async function saveQuota() {
  if (!quotaTeam.value) return
  if (tokenQuota.value < 0) {
    ElMessage.warning('Token 配额不能为负数')
    return
  }
  try {
    await api.put(`/teams/${quotaTeam.value.id}/quota`, {
      monthlyTokenQuota: tokenQuota.value,
      monthlyCostQuota: costQuota.value,
    })
    ElMessage.success('团队配额已更新')
    quotaDialog.value = false
    await load()
  } catch {
    ElMessage.error('更新团队配额失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>团队与配额</h1>
        <p>查看团队规模、本月用量和配额；运营可以调整团队配额。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model="q"
        placeholder="搜索团队名 / 所有者邮箱"
        clearable
        class="search"
        @keyup.enter="applyFilter"
        @clear="applyFilter"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="select" @change="applyFilter">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="applyFilter">搜索</el-button>
    </div>

    <div v-if="loading && teams.length === 0" class="empty">正在加载团队…</div>
    <div v-else-if="teams.length === 0" class="empty">还没有团队。</div>
    <div v-else class="team-list">
      <div class="team-head">
        <span>团队</span>
        <span>所有者</span>
        <span>成员</span>
        <span>本月 tokens</span>
        <span>本月成本</span>
        <span>创建时间</span>
        <span>操作</span>
      </div>
      <div v-for="team in teams" :key="team.id" class="team-row">
        <div class="team-cell">
          <b>{{ team.name }}</b>
          <span>#{{ team.id }} · {{ team.status === 'ACTIVE' ? '启用' : '禁用' }}</span>
        </div>
        <span class="owner">{{ team.ownerEmail || team.ownerUserId }}</span>
        <span>{{ team.memberCount }}</span>
        <div class="usage-cell">
          <span>{{ team.tokensUsed.toLocaleString() }} / {{ team.monthlyTokenQuota.toLocaleString() }}</span>
          <div class="bar"><i :style="{ width: `${tokenPercent(team)}%` }" /></div>
        </div>
        <div class="usage-cell">
          <span>{{ formatCost(team.costUsed) }} / {{ formatCost(team.monthlyCostQuota) }}</span>
          <div class="bar"><i :style="{ width: `${costPercent(team)}%` }" /></div>
        </div>
        <span class="time">{{ formatUtcTime(team.createdAt) }}</span>
        <div class="actions">
          <el-button v-if="auth.canWrite" text @click="openQuota(team)">配额</el-button>
          <span v-else class="muted">只读</span>
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

    <el-dialog v-model="quotaDialog" :title="quotaTitle" width="420px">
      <div class="dialog-field">
        <span>月度 tokens 配额</span>
        <el-input-number v-model="tokenQuota" :min="0" :step="100000" style="width: 100%" />
      </div>
      <div class="dialog-field">
        <span>月度成本配额（¥）</span>
        <el-input-number v-model="costQuota" :min="0" :step="1" :precision="4" style="width: 100%" />
      </div>
      <p class="dialog-hint">团队问答同时受个人配额和团队配额限制；修改只影响后续检查。</p>
      <template #footer>
        <el-button @click="quotaDialog = false">取消</el-button>
        <el-button type="primary" @click="saveQuota">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.search {
  width: 260px;
}
.select {
  width: 130px;
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
.team-list {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.team-head,
.team-row {
  display: grid;
  grid-template-columns: 1.5fr 1.3fr 0.5fr 1.2fr 1.1fr 1fr 0.7fr;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  font-size: 10.5px;
}
.team-head {
  background: #fbfbf9;
  color: var(--faint);
  border-bottom: 1px solid var(--line);
  font-weight: 600;
}
.team-row {
  border-bottom: 1px solid #f1f0ec;
  color: var(--muted);
}
.team-row:last-child {
  border-bottom: 0;
}
.team-cell {
  min-width: 0;
}
.team-cell b {
  display: block;
  color: var(--ink);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-cell span {
  display: block;
  color: var(--faint);
  font-size: 9.5px;
}
.owner {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-cell span {
  display: block;
  white-space: nowrap;
  font-size: 9.5px;
}
.bar {
  height: 4px;
  background: #ecebe6;
  border-radius: 999px;
  overflow: hidden;
  margin-top: 4px;
}
.bar i {
  display: block;
  height: 100%;
  background: #1c1c1a;
  border-radius: 999px;
}
.time {
  color: var(--faint);
  font-size: 9.5px;
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.dialog-field {
  margin-bottom: 14px;
}
.dialog-field span {
  display: block;
  font-size: 11px;
  margin-bottom: 6px;
  color: var(--muted);
}
.dialog-hint {
  color: var(--faint);
  font-size: 10px;
  margin: 4px 0 0;
}
@media (max-width: 1100px) {
  .team-head,
  .team-row {
    grid-template-columns: 1.6fr 1.2fr 0.5fr 1.2fr 0.9fr;
  }
  .team-head span:nth-child(6),
  .team-row .time,
  .team-head span:nth-child(5),
  .team-row .usage-cell:nth-child(5) {
    display: none;
  }
}
</style>
