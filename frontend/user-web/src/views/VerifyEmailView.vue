<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'
import { apiErrorMessage } from '../api/errors'

const route = useRoute()
const status = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('')

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    status.value = 'error'
    message.value = '链接缺少验证码'
    return
  }
  try {
    await api.post('/auth/email/verify', { token })
    status.value = 'success'
    message.value = '邮箱已验证，可以继续使用拾页。'
  } catch (error) {
    status.value = 'error'
    message.value = apiErrorMessage(error, '验证链接无效或已过期')
  }
})
</script>

<template>
  <div class="auth-page">
    <section class="auth-card">
      <div class="eyebrow">EMAIL VERIFY</div>
      <h1>邮箱验证</h1>
      <p class="hint">{{ status === 'loading' ? '正在验证…' : message }}</p>
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
  margin: 6px 0 12px;
  font-size: 26px;
  letter-spacing: -0.03em;
}
.hint {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.link {
  display: inline-block;
  margin-top: 18px;
  color: var(--ink);
  font-size: 12px;
}
</style>
