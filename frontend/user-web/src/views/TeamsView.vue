<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useTeamStore } from '../stores/teams'
import { formatUtcTime } from '../utils/time'
import type {
  ApiResult,
  Team,
  TeamDetail,
  TeamMember,
} from '../api/types'

const auth = useAuthStore()
const teamStore = useTeamStore()

const selectedId = ref('')
const detail = ref<TeamDetail | null>(null)
const detailLoading = ref(false)

const createDialog = ref(false)
const createName = ref('')
const creating = ref(false)

const editDialog = ref(false)
const editForm = reactive({
  name: '',
  monthlyTokenQuota: 0,
  monthlyCostQuota: 0,
})
const savingEdit = ref(false)

const memberDialog = ref(false)
const memberEmail = ref('')
const memberRole = ref<'ADMIN' | 'MEMBER'>('MEMBER')
const addingMember = ref(false)

const selected = computed(() => teamStore.teams.find((team) => team.id === selectedId.value) || null)
const canManage = computed(() => selected.value?.myRole === 'OWNER' || selected.value?.myRole === 'ADMIN')
const isOwner = computed(() => selected.value?.myRole === 'OWNER')

function roleLabel(role: string) {
  if (role === 'OWNER') return '所有者'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}

function roleClass(role: string) {
  return `role-${role.toLowerCase()}`
}

async function loadTeams(preferId?: string) {
  await teamStore.load()
  const target = preferId
    ? teamStore.teams.find((team) => team.id === preferId)
    : teamStore.teams.find((team) => team.id === selectedId.value) || teamStore.teams[0]
  if (target) {
    selectedId.value = target.id
    await loadDetail(target.id)
  } else {
    selectedId.value = ''
    detail.value = null
  }
}

async function loadDetail(teamId: string) {
  detailLoading.value = true
  try {
    const { data } = await api.get<ApiResult<TeamDetail>>(`/teams/${teamId}`)
    detail.value = data.data
  } catch {
    detail.value = null
    ElMessage.error('读取团队详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function selectTeam(team: Team) {
  selectedId.value = team.id
  await loadDetail(team.id)
}

async function submitCreate() {
  if (!createName.value.trim()) {
    ElMessage.warning('请填写团队名称')
    return
  }
  creating.value = true
  try {
    const { data } = await api.post<ApiResult<TeamDetail>>('/teams', {
      name: createName.value.trim(),
    })
    ElMessage.success('团队已创建')
    createDialog.value = false
    createName.value = ''
    await loadTeams(data.data.id)
  } catch {
    ElMessage.error('创建团队失败')
  } finally {
    creating.value = false
  }
}

function openEdit() {
  if (!selected.value) return
  editForm.name = selected.value.name
  editForm.monthlyTokenQuota = selected.value.monthlyTokenQuota
  editForm.monthlyCostQuota = Number(selected.value.monthlyCostQuota)
  editDialog.value = true
}

async function submitEdit() {
  if (!selected.value) return
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写团队名称')
    return
  }
  savingEdit.value = true
  try {
    await api.put(`/teams/${selected.value.id}`, {
      name: editForm.name.trim(),
      monthlyTokenQuota: editForm.monthlyTokenQuota,
      monthlyCostQuota: editForm.monthlyCostQuota,
    })
    ElMessage.success('团队信息已更新')
    editDialog.value = false
    await loadTeams(selected.value.id)
  } catch {
    ElMessage.error('更新团队失败')
  } finally {
    savingEdit.value = false
  }
}

function openMemberDialog() {
  memberEmail.value = ''
  memberRole.value = 'MEMBER'
  memberDialog.value = true
}

async function submitMember() {
  if (!selected.value) return
  if (!memberEmail.value.trim()) {
    ElMessage.warning('请填写成员邮箱')
    return
  }
  addingMember.value = true
  try {
    await api.post(`/teams/${selected.value.id}/members`, {
      email: memberEmail.value.trim(),
      role: memberRole.value,
    })
    ElMessage.success('成员已添加')
    memberDialog.value = false
    await loadTeams(selected.value.id)
  } catch {
    ElMessage.error('添加成员失败，请确认邮箱已注册且不在团队中')
  } finally {
    addingMember.value = false
  }
}

async function changeRole(member: TeamMember, role: 'ADMIN' | 'MEMBER') {
  if (!selected.value) return
  try {
    await api.put(`/teams/${selected.value.id}/members/${member.userId}`, { role })
    ElMessage.success('角色已更新')
    await loadTeams(selected.value.id)
  } catch {
    ElMessage.error('更新角色失败')
  }
}

async function removeMember(member: TeamMember) {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(
      `将 ${member.email} 移出团队？移出后该成员将不能继续访问团队资料。`,
      '移除成员',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await api.delete(`/teams/${selected.value.id}/members/${member.userId}`)
    ElMessage.success('成员已移除')
    await loadTeams(selected.value.id)
  } catch {
    ElMessage.error('移除成员失败')
  }
}

async function leaveTeam() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(
      `退出「${selected.value.name}」？退出后需要重新被邀请才能访问团队资料。`,
      '退出团队',
      { confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await api.post(`/teams/${selected.value.id}/leave`)
    ElMessage.success('已退出团队')
    selectedId.value = ''
    await loadTeams()
  } catch {
    ElMessage.error('退出团队失败')
  }
}

onMounted(() => loadTeams())
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>团队</h1>
        <p>把资料共享给团队成员，一起检索和问答。团队配额独立于个人配额。</p>
      </div>
      <div class="page-actions">
        <el-button text :loading="teamStore.loading" @click="loadTeams()">刷新</el-button>
        <el-button type="primary" @click="createDialog = true">新建团队</el-button>
      </div>
    </div>

    <div v-if="teamStore.teams.length === 0 && !teamStore.loading" class="empty">
      还没有团队。新建一个团队，把需要共享的资料放进去。
    </div>

    <div v-else class="team-layout">
      <aside class="team-list">
        <button
          v-for="team in teamStore.teams"
          :key="team.id"
          type="button"
          class="team-card"
          :class="{ active: team.id === selectedId }"
          @click="selectTeam(team)"
        >
          <span class="team-card-head">
            <b>{{ team.name }}</b>
            <i :class="roleClass(team.myRole)">{{ roleLabel(team.myRole) }}</i>
          </span>
          <span class="team-card-meta">{{ team.memberCount }} 人 · {{ team.monthlyTokenQuota.toLocaleString() }} tokens/月</span>
        </button>
      </aside>

      <section v-if="detail" class="panel team-detail">
        <div class="detail-head">
          <div>
            <div class="eyebrow">TEAM</div>
            <h2>{{ detail.name }}</h2>
            <p>ID {{ detail.id }} · 创建于 {{ formatUtcTime(detail.createdAt) }}</p>
          </div>
          <div class="detail-actions">
            <el-button v-if="isOwner" text @click="openEdit">编辑团队</el-button>
            <el-button v-if="detail.myRole !== 'OWNER'" text type="danger" @click="leaveTeam">退出团队</el-button>
          </div>
        </div>

        <div class="quota-grid">
          <div class="quota-card">
            <span>成员</span>
            <b>{{ detail.memberCount }}</b>
          </div>
          <div class="quota-card">
            <span>月 token 配额</span>
            <b>{{ detail.monthlyTokenQuota.toLocaleString() }}</b>
          </div>
          <div class="quota-card">
            <span>月成本配额</span>
            <b>¥{{ Number(detail.monthlyCostQuota).toFixed(2) }}</b>
          </div>
        </div>

        <div class="member-head">
          <div>
            <div class="eyebrow">MEMBERS</div>
            <h3>成员</h3>
          </div>
          <el-button v-if="canManage" size="small" type="primary" @click="openMemberDialog">
            添加成员
          </el-button>
        </div>

        <div class="member-list">
          <div v-for="member in detail.members" :key="member.userId" class="member-row">
            <div class="member-main">
              <b>
                {{ member.displayName || member.email }}
                <em v-if="member.userId === auth.user?.id">你</em>
              </b>
              <span>{{ member.email }}</span>
            </div>
            <span :class="roleClass(member.role)">{{ roleLabel(member.role) }}</span>
            <span class="member-joined">{{ formatUtcTime(member.joinedAt) }}</span>
            <div class="member-actions">
              <template v-if="canManage && member.role !== 'OWNER' && member.userId !== auth.user?.id">
                <el-select
                  v-if="isOwner"
                  :model-value="member.role"
                  size="small"
                  class="role-select"
                  @change="(value: 'ADMIN' | 'MEMBER') => changeRole(member, value)"
                >
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="成员" value="MEMBER" />
                </el-select>
                <el-button text type="danger" @click="removeMember(member)">移除</el-button>
              </template>
            </div>
          </div>
        </div>
      </section>

      <section v-else class="panel team-detail empty-detail">
        <p>{{ detailLoading ? '正在加载团队…' : '选择左侧团队查看成员。' }}</p>
      </section>
    </div>

    <el-dialog v-model="createDialog" title="新建团队" width="440px">
      <div class="dialog-field">
        <span>团队名称</span>
        <el-input v-model="createName" maxlength="64" placeholder="例如：内容研究组" />
      </div>
      <p class="dialog-hint">创建后你将成为团队所有者，可邀请成员并设置团队配额。</p>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog" title="编辑团队" width="460px">
      <div class="dialog-field">
        <span>团队名称</span>
        <el-input v-model="editForm.name" maxlength="64" />
      </div>
      <div class="dialog-field">
        <span>月 token 配额</span>
        <el-input-number v-model="editForm.monthlyTokenQuota" :min="0" :step="100000" style="width: 100%" />
      </div>
      <div class="dialog-field">
        <span>月成本配额（¥）</span>
        <el-input-number v-model="editForm.monthlyCostQuota" :min="0" :step="1" :precision="2" style="width: 100%" />
      </div>
      <p class="dialog-hint">团队问答会同时受个人配额和团队配额限制。</p>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDialog" title="添加成员" width="440px">
      <div class="dialog-field">
        <span>成员邮箱</span>
        <el-input v-model="memberEmail" maxlength="190" placeholder="member@example.com" />
      </div>
      <div class="dialog-field">
        <span>角色</span>
        <el-select v-model="memberRole" style="width: 100%">
          <el-option label="成员" value="MEMBER" />
          <el-option v-if="isOwner" label="管理员" value="ADMIN" />
        </el-select>
      </div>
      <p class="dialog-hint">对方必须先注册拾页；管理员可以添加/移除成员，所有者可以管理管理员和团队配额。</p>
      <template #footer>
        <el-button @click="memberDialog = false">取消</el-button>
        <el-button type="primary" :loading="addingMember" @click="submitMember">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.team-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.team-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.team-card {
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  padding: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.team-card:hover {
  border-color: #c9c6bd;
}
.team-card.active {
  background: #fbfbf9;
  border-color: #1c1c1a;
}
.team-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.team-card-head b {
  font-size: 12px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-card-meta {
  display: block;
  margin-top: 4px;
  color: var(--faint);
  font-size: 9.5px;
}
.team-detail {
  padding: 18px;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.detail-head h2 {
  margin: 2px 0 4px;
  letter-spacing: -0.02em;
}
.detail-head p {
  margin: 0;
  color: var(--faint);
  font-size: 10px;
}
.detail-actions {
  display: flex;
  gap: 4px;
}
.quota-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0 20px;
}
.quota-card {
  border: 1px solid #f0eee8;
  border-radius: 9px;
  background: #fbfbf9;
  padding: 10px 12px;
}
.quota-card span {
  display: block;
  color: var(--faint);
  font-size: 9px;
}
.quota-card b {
  display: block;
  margin-top: 3px;
  color: var(--ink);
  font-size: 15px;
}
.member-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 8px;
}
.member-head h3 {
  margin: 2px 0 0;
  font-size: 16px;
}
.member-list {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}
.member-row {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 0.7fr 0.9fr 1fr;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f0ec;
  font-size: 10px;
}
.member-row:last-child {
  border-bottom: 0;
}
.member-main b {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ink);
  font-size: 11px;
}
.member-main b em {
  border-radius: 999px;
  background: #f1f0ec;
  color: var(--muted);
  font-size: 8.5px;
  font-style: normal;
  padding: 1px 6px;
}
.member-main span {
  display: block;
  color: var(--faint);
  font-size: 9.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-joined {
  color: var(--faint);
  font-size: 9.5px;
}
.member-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
}
.role-select {
  width: 92px;
}
.role-owner,
.role-admin,
.role-member {
  display: inline-block;
  width: fit-content;
  border-radius: 999px;
  padding: 1px 7px;
  font-size: 8.5px;
  font-style: normal;
  font-weight: 700;
}
.role-owner {
  background: #1c1c1a;
  color: #fff;
}
.role-admin {
  background: #f4f7fb;
  color: #2f5d8a;
}
.role-member {
  background: #f1f0ec;
  color: var(--muted);
}
.empty-detail {
  min-height: 220px;
  display: grid;
  place-items: center;
  color: var(--faint);
  font-size: 11px;
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
  line-height: 1.6;
}
@media (max-width: 900px) {
  .team-layout {
    grid-template-columns: 1fr;
  }
  .team-list {
    flex-direction: row;
    overflow-x: auto;
  }
  .team-card {
    min-width: 200px;
  }
  .quota-grid {
    grid-template-columns: 1fr;
  }
  .member-row {
    grid-template-columns: minmax(0, 1.4fr) 0.8fr 0.9fr;
  }
  .member-joined {
    display: none;
  }
}
</style>
