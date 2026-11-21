<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { apiErrorMessage } from '../api/errors'

const route = useRoute()
const router = useRouter()
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const done = ref(false)

async function submit() {
  if (password.value.length < 8
    || !/[A-Za-z]/.test(password.value)
    || !/\d/.test(password.value)) {
    ElMessage.warning('密码至少 8 位，且需同时包含字母和数字')
    return
  }
  if (password.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    ElMessage.error('链接缺少重置码')
    return
  }
  loading.value = true
  try {
    await api.post('/auth/password/reset', {
      token,
      newPassword: password.value,
    })
    done.value = true
    ElMessage.success('密码已重置，请重新登录')
    router.push('/login')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '重置密码失败，链接可能已过期'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-card">
      <div class="eyebrow">PASSWORD RESET</div>
      <h1>重置密码</h1>
      <p class="hint">设置一个新密码，重置后所有旧会话会失效。</p>
      <label class="field">
        <span>新密码</span>
        <el-input v-model="password" type="password" show-password maxlength="72" />
      </label>
      <label class="field">
        <span>确认新密码</span>
        <el-input v-model="confirmPassword" type="password" show-password maxlength="72" />
      </label>
      <el-button type="primary" :loading="loading" :disabled="done" @click="submit">
        重置密码
      </el-button>
      <router-link class="link" to="/login">返回登录</router-link>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f7f7f5;
}
.auth-card {
  width: min(420px, 100%);
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  padding: 32px;
}
.eyebrow {
  color: var(--faint);
  font-size: 10px;
  letter-spacing: 0.12em;
}
h1 {
  margin: 6px 0 8px;
  font-size: 26px;
  letter-spacing: -0.03em;
}
.hint {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  margin-bottom: 18px;
}
.field {
  display: block;
  margin-bottom: 14px;
}
.field span {
  display: block;
  margin-bottom: 6px;
  color: var(--muted);
  font-size: 11px;
}
.link {
  display: inline-block;
  margin-top: 14px;
  color: var(--ink);
  font-size: 12px;
}
</style>
