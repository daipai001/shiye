<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { formatUtcTime } from '../utils/time'
import type { AdminUser, ApiResult, PageResult } from '../api/types'

const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const q = ref('')
const statusFilter = ref('')
const sortFilter = ref('')
const loading = ref(false)

const quotaDialog = ref(false)
const quotaUser = ref<AdminUser | null>(null)
const tokenQuota = ref(0)
const costQuota = ref(0)

const passwordDialog = ref(false)
const passwordUser = ref<AdminUser | null>(null)
const newPassword = ref('')
const confirmPassword = ref('')
const resettingPassword = ref(false)

const quotaTitle = computed(() => (quotaUser.value ? `调整 ${quotaUser.value.email} 的配额` : '调整配额'))
const passwordTitle = computed(() => (passwordUser.value ? `重置 ${passwordUser.value.email} 的密码` : '重置用户密码'))

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

function tokenPercent(user: AdminUser) {
  if (!user.monthlyTokenQuota) return 0
  return Math.min(100, Math.round((user.tokensUsed / user.monthlyTokenQuota) * 100))
}

function costPercent(user: AdminUser) {
  if (!user.monthlyCostQuota) return 0
  return Math.min(100, Math.round((user.costUsed / user.monthlyCostQuota) * 100))
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<AdminUser>>>('/users', {
      params: {
        page: page.value,
        pageSize,
        q: q.value.trim() || undefined,
        status: statusFilter.value || undefined,
        sort: sortFilter.value || undefined,
      },
    })
    users.value = data.data.items
    total.value = data.data.total
  } catch {
    ElMessage.error('读取用户列表失败')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 1
  load()
}

function openQuota(user: AdminUser) {
  quotaUser.value = user
  tokenQuota.value = user.monthlyTokenQuota
  costQuota.value = user.monthlyCostQuota
  quotaDialog.value = true
}

async function saveQuota() {
  if (!quotaUser.value) return
  if (tokenQuota.value < 1000) {
    ElMessage.warning('Token 配额至少为 1000')
    return
  }
  try {
    await api.put(`/users/${quotaUser.value.id}/quota`, {
      monthlyTokenQuota: tokenQuota.value,
      monthlyCostQuota: costQuota.value,
    })
    ElMessage.success('配额已更新')
    quotaDialog.value = false
    await load()
  } catch {
    ElMessage.error('更新配额失败')
  }
}

function openPassword(user: AdminUser) {
  passwordUser.value = user
  newPassword.value = ''
  confirmPassword.value = ''
  passwordDialog.value = true
}

async function savePassword() {
  if (!passwordUser.value) return
  if (newPassword.value.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (!/[A-Za-z]/.test(newPassword.value) || !/\d/.test(newPassword.value)) {
    ElMessage.warning('新密码需同时包含字母和数字')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  resettingPassword.value = true
  try {
    await api.put(`/users/${passwordUser.value.id}/password`, {
      newPassword: newPassword.value,
    })
    ElMessage.success('密码已重置，用户需要重新登录')
    passwordDialog.value = false
    await load()
  } catch {
    ElMessage.error('重置密码失败')
  } finally {
    resettingPassword.value = false
  }
}

async function toggleStatus(user: AdminUser) {
  const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (next === 'DISABLED') {
    try {
      await ElMessageBox.confirm(
        `禁用 ${user.email}？该用户现有 refresh token 会被撤销。`,
        '禁用用户',
        { confirmButtonText: '禁用', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    await api.put(`/users/${user.id}/status`, { status: next })
    ElMessage.success(next === 'ACTIVE' ? '已启用' : '已禁用')
    await load()
  } catch {
    ElMessage.error('更新用户状态失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>用户与配额</h1>
        <p>查看用户用量、调整月度配额、禁用/启用账号。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索邮箱 / 昵称" clearable class="search" @keyup.enter="applyFilter" @clear="applyFilter" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="select" @change="applyFilter">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-select v-model="sortFilter" placeholder="默认排序" clearable class="select" @change="applyFilter">
        <el-option label="用量最高" value="usage" />
      </el-select>
      <el-button type="primary" @click="applyFilter">搜索</el-button>
    </div>

    <div v-if="loading && users.length === 0" class="empty">正在加载用户…</div>
    <div v-else-if="users.length === 0" class="empty">没有匹配的用户。</div>
    <div v-else class="user-list">
      <div class="user-head">
        <span>用户</span>
        <span>状态</span>
        <span>资料</span>
        <span>本月 tokens</span>
        <span>本月成本</span>
        <span>最后登录</span>
        <span>操作</span>
      </div>
      <div v-for="user in users" :key="user.id" class="user-row">
        <div class="user-cell">
          <b>{{ user.displayName || user.email }}</b>
          <span>{{ user.email }}</span>
        </div>
        <span class="status" :class="user.status.toLowerCase()">{{ user.status === 'ACTIVE' ? '正常' : '禁用' }}</span>
        <span>{{ user.articleCount }}</span>
        <div class="usage-cell">
          <span>{{ user.tokensUsed.toLocaleString() }} / {{ user.monthlyTokenQuota.toLocaleString() }}</span>
          <div class="bar"><i :style="{ width: `${tokenPercent(user)}%` }" /></div>
        </div>
        <div class="usage-cell">
          <span>{{ formatCost(user.costUsed) }} / {{ formatCost(user.monthlyCostQuota) }}</span>
          <div class="bar"><i :style="{ width: `${costPercent(user)}%` }" /></div>
        </div>
        <span class="time">{{ formatUtcTime(user.lastLoginAt) }}</span>
        <div class="actions">
          <el-button text @click="openPassword(user)">重置密码</el-button>
          <el-button text @click="openQuota(user)">配额</el-button>
          <el-button text :type="user.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(user)">
            {{ user.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
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
        <el-input-number v-model="tokenQuota" :min="1000" :step="10000" style="width: 100%" />
      </div>
      <div class="dialog-field">
        <span>月度成本配额（¥）</span>
        <el-input-number v-model="costQuota" :min="0" :step="1" :precision="4" style="width: 100%" />
      </div>
      <p class="dialog-hint">修改只影响后续配额检查，不会重置已使用量。</p>
      <template #footer>
        <el-button @click="quotaDialog = false">取消</el-button>
        <el-button type="primary" @click="saveQuota">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialog" :title="passwordTitle" width="420px">
      <div class="dialog-field">
        <span>新密码</span>
        <el-input v-model="newPassword" type="password" show-password maxlength="72" />
      </div>
      <div class="dialog-field">
        <span>确认新密码</span>
        <el-input v-model="confirmPassword" type="password" show-password maxlength="72" />
      </div>
      <p class="dialog-hint">至少 8 位，需同时包含字母和数字；重置后该用户所有 refresh token 会失效。</p>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="resettingPassword" @click="savePassword">重置密码</el-button>
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
  width: 240px;
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
.user-list {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.user-head,
.user-row {
  display: grid;
  grid-template-columns: 1.7fr 0.6fr 0.5fr 1.2fr 1.1fr 1fr 1.4fr;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  font-size: 10.5px;
}
.user-head {
  background: #fbfbf9;
  color: var(--faint);
  border-bottom: 1px solid var(--line);
  font-weight: 600;
}
.user-row {
  border-bottom: 1px solid #f1f0ec;
  color: var(--muted);
}
.user-row:last-child {
  border-bottom: 0;
}
.user-cell {
  min-width: 0;
}
.user-cell b {
  display: block;
  color: var(--ink);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-cell span {
  display: block;
  color: var(--faint);
  font-size: 9.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status {
  display: inline-block;
  width: fit-content;
  border-radius: 999px;
  padding: 1px 7px;
  font-size: 9px;
  font-weight: 700;
  background: #f1f0ec;
  color: var(--muted);
}
.status.active {
  background: #f4fbf8;
  color: #16794f;
}
.status.disabled {
  background: #fdf7f6;
  color: #b3372f;
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
  .user-head,
  .user-row {
    grid-template-columns: 1.8fr 0.7fr 0.6fr 1.2fr 0.9fr;
  }
  .user-head span:nth-child(5),
  .user-row .usage-cell:nth-child(5),
  .user-head span:nth-child(6),
  .user-row .time {
    display: none;
  }
}
</style>
