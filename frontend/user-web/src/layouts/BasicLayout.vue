<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { getRefreshToken } from '../api/authStorage'
import type { ApiResult, UserSummary } from '../api/types'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  try {
    const { data } = await api.get<ApiResult<UserSummary>>('/users/me')
    auth.updateUser(data.data)
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
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        拾页
        <span>AI 资料库</span>
      </div>
      <nav class="nav">
        <router-link to="/library">资料库</router-link>
        <router-link to="/chat">问答</router-link>
        <router-link to="/usage">用量</router-link>
        <router-link to="/settings">设置</router-link>
        <router-link to="/plugin">插件</router-link>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <strong>拾页</strong>
        <span class="spacer" />
        <span class="muted">{{ auth.user?.displayName || auth.user?.email || '已登录用户' }}</span>
        <el-button text @click="logout">退出</el-button>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>
