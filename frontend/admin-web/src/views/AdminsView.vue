<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAdminAuthStore } from '../stores/auth'
import { formatUtcTime } from '../utils/time'
import type { AdminAccount, AdminRole, ApiResult } from '../api/types'

const auth = useAdminAuthStore()
const admins = ref<AdminAccount[]>([])
const loading = ref(false)
const q = ref('')

const createDialog = ref(false)
const createForm = reactive({
  email: '',
  name: '',
  role: 'OPERATOR' as AdminRole,
  password: '',
})
const creating = ref(false)

const editDialog = ref(false)
const editTarget = ref<AdminAccount | null>(null)
const editForm = reactive({
  name: '',
  role: 'OPERATOR' as AdminRole,
})
const savingEdit = ref(false)

const passwordDialog = ref(false)
const passwordTarget = ref<AdminAccount | null>(null)
const newPassword = ref('')
const confirmPassword = ref('')
const resettingPassword = ref(false)

const roleOptions: Array<{ value: AdminRole; label: string; hint: string }> = [
  { value: 'SUPER_ADMIN', label: '超级管理员', hint: '全部权限，包括管理员、模型和系统设置' },
  { value: 'OPERATOR', label: '运营', hint: '用户、导入、任务、评测等业务读写' },
  { value: 'VIEWER', label: '只读', hint: '只能查看，不能执行写操作' },
]

const filtered = computed(() => {
  const keyword = q.value.trim().toLowerCase()
  if (!keyword) return admins.value
  return admins.value.filter((admin) =>
    admin.email.toLowerCase().includes(keyword)
    || admin.name.toLowerCase().includes(keyword),
  )
})

function roleLabel(role: AdminRole) {
  return roleOptions.find((item) => item.value === role)?.label || role
}

function isSelf(admin: AdminAccount) {
  return String(auth.admin?.id) === String(admin.id)
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<AdminAccount[]>>('/admins')
    admins.value = data.data
  } catch {
    ElMessage.error('读取管理员列表失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.email = ''
  createForm.name = ''
  createForm.role = 'OPERATOR'
  createForm.password = ''
  createDialog.value = true
}

async function submitCreate() {
  if (!createForm.email.trim()) {
    ElMessage.warning('请填写邮箱')
    return
  }
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写显示名')
    return
  }
  if (createForm.password.length < 8
    || !/[A-Za-z]/.test(createForm.password)
    || !/\d/.test(createForm.password)) {
    ElMessage.warning('密码至少 8 位，且需同时包含字母和数字')
    return
  }
  creating.value = true
  try {
    await api.post('/admins', {
      email: createForm.email.trim(),
      name: createForm.name.trim(),
      role: createForm.role,
      password: createForm.password,
    })
    ElMessage.success('管理员已创建')
    createDialog.value = false
    await load()
  } catch {
    ElMessage.error('创建管理员失败，邮箱可能已存在')
  } finally {
    creating.value = false
  }
}

function openEdit(admin: AdminAccount) {
  editTarget.value = admin
  editForm.name = admin.name
  editForm.role = admin.role
  editDialog.value = true
}

async function submitEdit() {
  if (!editTarget.value) return
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写显示名')
    return
  }
  savingEdit.value = true
  try {
    await api.put(`/admins/${editTarget.value.id}`, {
      name: editForm.name.trim(),
      role: editForm.role,
    })
    ElMessage.success('管理员信息已更新')
    editDialog.value = false
    await load()
  } catch {
    ElMessage.error('更新管理员失败，请确认没有移除最后一名超级管理员')
  } finally {
    savingEdit.value = false
  }
}

function openPassword(admin: AdminAccount) {
  passwordTarget.value = admin
  newPassword.value = ''
  confirmPassword.value = ''
  passwordDialog.value = true
}

async function submitPassword() {
  if (!passwordTarget.value) return
  if (newPassword.value.length < 8
    || !/[A-Za-z]/.test(newPassword.value)
    || !/\d/.test(newPassword.value)) {
    ElMessage.warning('新密码至少 8 位，且需同时包含字母和数字')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  resettingPassword.value = true
  try {
    await api.put(`/admins/${passwordTarget.value.id}/password`, {
      newPassword: newPassword.value,
    })
    ElMessage.success('密码已重置，该管理员需要重新登录')
    passwordDialog.value = false
    await load()
  } catch {
    ElMessage.error('重置密码失败')
  } finally {
    resettingPassword.value = false
  }
}

async function toggleStatus(admin: AdminAccount) {
  const next = admin.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (next === 'DISABLED') {
    try {
      await ElMessageBox.confirm(
        `禁用 ${admin.email}？该管理员的 refresh token 会被撤销，当前 access token 也会在下次请求失效。`,
        '禁用管理员',
        { confirmButtonText: '禁用', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    await api.put(`/admins/${admin.id}/status`, { status: next })
    ElMessage.success(next === 'ACTIVE' ? '已启用' : '已禁用')
    await load()
  } catch {
    ElMessage.error('更新管理员状态失败，请确认没有禁用最后一名超级管理员')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>管理员</h1>
        <p>管理后台账号与角色。超级管理员拥有全部权限，运营负责日常业务，只读账号不能执行写操作。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建管理员</el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="q"
        placeholder="搜索邮箱 / 显示名"
        clearable
        class="search"
      />
      <span class="toolbar-hint">共 {{ filtered.length }} 个账号</span>
    </div>

    <div v-if="loading && admins.length === 0" class="empty">正在加载管理员…</div>
    <div v-else-if="filtered.length === 0" class="empty">没有匹配的管理员。</div>
    <div v-else class="admin-list">
      <div class="admin-head">
        <span>管理员</span>
        <span>角色</span>
        <span>状态</span>
        <span>最后登录</span>
        <span>创建时间</span>
        <span>操作</span>
      </div>
      <div v-for="admin in filtered" :key="admin.id" class="admin-row">
        <div class="admin-cell">
          <b>{{ admin.name }}<em v-if="isSelf(admin)">你</em></b>
          <span>{{ admin.email }}</span>
        </div>
        <div class="role-cell">
          <span class="role" :class="admin.role.toLowerCase()">{{ roleLabel(admin.role) }}</span>
        </div>
        <span class="status" :class="admin.status.toLowerCase()">
          {{ admin.status === 'ACTIVE' ? '启用' : '禁用' }}
        </span>
        <span class="time">{{ formatUtcTime(admin.lastLoginAt) }}</span>
        <span class="time">{{ formatUtcTime(admin.createdAt) }}</span>
        <div class="actions">
          <el-button text @click="openEdit(admin)">编辑</el-button>
          <el-button text @click="openPassword(admin)">重置密码</el-button>
          <el-button
            text
            :type="admin.status === 'ACTIVE' ? 'danger' : 'success'"
            :disabled="isSelf(admin)"
            @click="toggleStatus(admin)"
          >
            {{ admin.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="createDialog" title="新建管理员" width="480px">
      <div class="dialog-field">
        <span>邮箱</span>
        <el-input v-model="createForm.email" placeholder="admin@example.com" maxlength="190" />
      </div>
      <div class="dialog-field">
        <span>显示名</span>
        <el-input v-model="createForm.name" placeholder="运营同学" maxlength="64" />
      </div>
      <div class="dialog-field">
        <span>角色</span>
        <el-select v-model="createForm.role" style="width: 100%">
          <el-option
            v-for="option in roleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          >
            <b>{{ option.label }}</b>
            <span class="option-hint">{{ option.hint }}</span>
          </el-option>
        </el-select>
      </div>
      <div class="dialog-field">
        <span>初始密码</span>
        <el-input v-model="createForm.password" type="password" show-password maxlength="72" />
      </div>
      <p class="dialog-hint">至少 8 位，需同时包含字母和数字；创建后请让管理员首次登录尽快修改密码。</p>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog" title="编辑管理员" width="460px">
      <div class="dialog-field">
        <span>显示名</span>
        <el-input v-model="editForm.name" maxlength="64" />
      </div>
      <div class="dialog-field">
        <span>角色</span>
        <el-select
          v-model="editForm.role"
          style="width: 100%"
          :disabled="editTarget ? isSelf(editTarget) : false"
        >
          <el-option
            v-for="option in roleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <p v-if="editTarget && isSelf(editTarget)" class="dialog-hint">不能修改自己的角色。</p>
      <p v-else class="dialog-hint">至少需要保留一名启用中的超级管理员。</p>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialog"
      :title="passwordTarget ? `重置 ${passwordTarget.email} 的密码` : '重置密码'"
      width="440px"
    >
      <div class="dialog-field">
        <span>新密码</span>
        <el-input v-model="newPassword" type="password" show-password maxlength="72" />
      </div>
      <div class="dialog-field">
        <span>确认新密码</span>
        <el-input v-model="confirmPassword" type="password" show-password maxlength="72" />
      </div>
      <p class="dialog-hint">重置后该管理员的所有 refresh token 会失效，需要重新登录。</p>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="resettingPassword" @click="submitPassword">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.search {
  width: 260px;
}
.toolbar-hint {
  color: var(--faint);
  font-size: 10px;
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
.admin-list {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.admin-head,
.admin-row {
  display: grid;
  grid-template-columns: 1.8fr 0.9fr 0.55fr 1fr 1fr 1.6fr;
  gap: 10px;
  align-items: center;
  padding: 11px 14px;
  font-size: 10.5px;
}
.admin-head {
  background: #fbfbf9;
  color: var(--faint);
  border-bottom: 1px solid var(--line);
  font-weight: 600;
}
.admin-row {
  border-bottom: 1px solid #f1f0ec;
  color: var(--muted);
}
.admin-row:last-child {
  border-bottom: 0;
}
.admin-cell {
  min-width: 0;
}
.admin-cell b {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ink);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-cell b em {
  border-radius: 999px;
  background: #f1f0ec;
  color: var(--muted);
  font-size: 8.5px;
  font-style: normal;
  padding: 1px 6px;
}
.admin-cell span {
  display: block;
  color: var(--faint);
  font-size: 9.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.role {
  display: inline-block;
  width: fit-content;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 9px;
  font-weight: 700;
  background: #f1f0ec;
  color: var(--muted);
}
.role.super_admin {
  background: #1c1c1a;
  color: #fff;
}
.role.operator {
  background: #f4f7fb;
  color: #2f5d8a;
}
.role.viewer {
  background: #f7f6f2;
  color: #7a7468;
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
.time {
  color: var(--faint);
  font-size: 9.5px;
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.option-hint {
  display: block;
  color: var(--faint);
  font-size: 9px;
  line-height: 1.3;
}
.dialog-field {
  margin-bottom: 14px;
}
.dialog-field > span {
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
  .admin-head,
  .admin-row {
    grid-template-columns: 1.8fr 0.9fr 0.6fr 1fr 1.5fr;
  }
  .admin-head span:nth-child(5),
  .admin-row .time:nth-child(5) {
    display: none;
  }
}
</style>
