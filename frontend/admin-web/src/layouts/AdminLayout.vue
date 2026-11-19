<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { getRefreshToken } from '../api/authStorage'
import type { AdminSummary, ApiResult } from '../api/types'
import { useAdminAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAdminAuthStore()

onMounted(async () => {
  try {
    const { data } = await api.get<ApiResult<AdminSummary>>('/auth/me')
    auth.updateAdmin(data.data)
  } catch {
    // 401 已由 client 统一处理；其他错误保留本地缓存资料，不打断页面。
  }
})

async function logout() {
  try {
    await api.post('/auth/logout', { refreshToken: getRefreshToken() })
  } catch {
    // 本地令牌照常清理，后端不可达时也不阻塞退出。
  } finally {
    auth.clear()
    ElMessage.success('已退出后台')
    router.push('/login')
  }
}
</script>

<template>
  <div class="layout admin">
    <aside class="sidebar">
      <div class="brand">
        拾页
        <span>运营后台 · ADMIN</span>
      </div>
      <nav class="nav">
        <router-link to="/overview">总览</router-link>
        <router-link to="/usage">用量与成本</router-link>
        <router-link to="/users">用户与配额</router-link>
        <router-link to="/imports">书签导入</router-link>
        <router-link to="/tasks">索引任务</router-link>
        <router-link to="/models">模型配置</router-link>
        <router-link to="/settings">系统设置</router-link>
        <router-link to="/eval">RAG 评测</router-link>
        <router-link to="/audit">审计日志</router-link>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <strong>拾页 · 运营后台</strong>
        <span class="spacer" />
        <span class="badge">{{ auth.admin?.role || 'ADMIN' }}</span>
        <span class="muted">{{ auth.admin?.name || auth.admin?.email || '管理员' }}</span>
        <el-button text @click="logout">退出</el-button>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>
