<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import type { ApiResult, PluginSettings, UserSummary } from '../api/types'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const savingPlugin = ref(false)

const profile = ref<UserSummary | null>(null)
const displayName = ref('')
const avatarUrl = ref('')

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

const plugin = ref<PluginSettings>({
  ballEnabled: true,
  defaultMode: 'LIBRARY',
  defaultScope: 'ALL',
  shortcut: '',
  siteBlacklist: [],
})
const blacklistText = ref('')

async function load() {
  loading.value = true
  try {
    const [meResponse, pluginResponse] = await Promise.all([
      api.get<ApiResult<UserSummary>>('/users/me'),
      api.get<ApiResult<PluginSettings>>('/users/me/plugin-settings'),
    ])
    profile.value = meResponse.data.data
    displayName.value = meResponse.data.data.displayName
    avatarUrl.value = meResponse.data.data.avatarUrl || ''
    plugin.value = pluginResponse.data.data
    blacklistText.value = (pluginResponse.data.data.siteBlacklist || []).join('\n')
    auth.updateUser(meResponse.data.data)
  } catch {
    ElMessage.error('读取账户设置失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!displayName.value.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  savingProfile.value = true
  try {
    const { data } = await api.patch<ApiResult<UserSummary>>('/users/me', {
      displayName: displayName.value.trim(),
      avatarUrl: avatarUrl.value.trim() || null,
    })
    profile.value = data.data
    auth.updateUser(data.data)
    ElMessage.success('资料已更新')
  } catch {
    ElMessage.error('保存资料失败')
  } finally {
    savingProfile.value = false
  }
}

async function changePassword() {
  if (newPassword.value.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await api.post('/users/me/password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    })
    ElMessage.success('密码已修改，请重新登录')
    auth.clear()
    router.push('/login')
  } catch {
    ElMessage.error('修改密码失败，请检查原密码')
  } finally {
    savingPassword.value = false
  }
}

async function savePlugin() {
  savingPlugin.value = true
  try {
    const { data } = await api.put<ApiResult<PluginSettings>>('/users/me/plugin-settings', {
      ballEnabled: plugin.value.ballEnabled,
      defaultMode: plugin.value.defaultMode,
      defaultScope: plugin.value.defaultScope,
      shortcut: plugin.value.shortcut || null,
      siteBlacklist: blacklistText.value
        .split(/\r?\n/)
        .map((item) => item.trim())
        .filter(Boolean),
    })
    plugin.value = data.data
    blacklistText.value = (data.data.siteBlacklist || []).join('\n')
    ElMessage.success('插件偏好已保存')
  } catch {
    ElMessage.error('保存插件偏好失败')
  } finally {
    savingPlugin.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page settings-page">
    <div class="page-head">
      <div>
        <h1>设置</h1>
        <p>账户资料、密码和插件偏好。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div v-if="loading && !profile" class="empty">正在读取设置…</div>
    <template v-else>
      <section class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">ACCOUNT</div>
            <h2>账户资料</h2>
          </div>
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </div>
        <div class="form-grid">
          <div class="field">
            <label>邮箱</label>
            <el-input :model-value="profile?.email" disabled />
          </div>
          <div class="field">
            <label>状态</label>
            <el-input :model-value="profile?.status" disabled />
          </div>
          <div class="field">
            <label>昵称</label>
            <el-input v-model="displayName" maxlength="64" />
          </div>
          <div class="field">
            <label>头像 URL</label>
            <el-input v-model="avatarUrl" maxlength="512" placeholder="可选" />
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">PASSWORD</div>
            <h2>修改密码</h2>
          </div>
          <el-button :loading="savingPassword" @click="changePassword">修改密码</el-button>
        </div>
        <div class="form-grid">
          <div class="field">
            <label>原密码</label>
            <el-input v-model="oldPassword" type="password" show-password />
          </div>
          <div class="field">
            <label>新密码</label>
            <el-input v-model="newPassword" type="password" show-password />
          </div>
          <div class="field">
            <label>确认新密码</label>
            <el-input v-model="confirmPassword" type="password" show-password />
          </div>
        </div>
        <p class="hint">修改密码后所有 refresh token 会失效，需要重新登录。</p>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">EXTENSION</div>
            <h2>插件偏好</h2>
          </div>
          <el-button type="primary" :loading="savingPlugin" @click="savePlugin">保存偏好</el-button>
        </div>
        <div class="form-grid">
          <div class="field">
            <label>悬浮球</label>
            <el-switch v-model="plugin.ballEnabled" active-text="显示" inactive-text="隐藏" />
          </div>
          <div class="field">
            <label>默认模式</label>
            <el-select v-model="plugin.defaultMode" style="width: 100%">
              <el-option label="资料库问答" value="LIBRARY" />
              <el-option label="当前页问答" value="CURRENT" />
            </el-select>
          </div>
          <div class="field">
            <label>默认范围</label>
            <el-select v-model="plugin.defaultScope" style="width: 100%">
              <el-option label="全部资料" value="ALL" />
              <el-option label="按文件夹" value="FOLDER" />
              <el-option label="按标签" value="TAG" />
            </el-select>
          </div>
          <div class="field">
            <label>快捷键</label>
            <el-input v-model="plugin.shortcut" maxlength="64" placeholder="例如 Ctrl+Shift+S" />
          </div>
          <div class="field wide">
            <label>不注入悬浮球的域名</label>
            <el-input
              v-model="blacklistText"
              type="textarea"
              :rows="4"
              placeholder="每行一个域名，例如 example.com"
            />
          </div>
        </div>
        <p class="hint">插件设置保存在服务端；扩展的悬浮球开关也会保存在浏览器本地。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 900px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 14px;
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
  font-size: 19px;
  letter-spacing: -0.02em;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
}
.field label {
  display: block;
  color: var(--muted);
  font-size: 10.5px;
  margin-bottom: 5px;
}
.field.wide {
  grid-column: 1 / -1;
}
.hint {
  color: var(--faint);
  font-size: 10px;
  line-height: 1.7;
  margin: 10px 0 0;
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
@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
