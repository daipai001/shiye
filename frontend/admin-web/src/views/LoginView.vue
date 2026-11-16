<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { apiErrorMessage } from '../api/errors'
import type { AdminTokenResponse, ApiResult } from '../api/types'
import { useAdminAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAdminAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    ElMessage.warning('请输入管理员邮箱')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }

  loading.value = true
  try {
    const { data } = await api.post<ApiResult<AdminTokenResponse>>('/auth/login', {
      email: email.value.trim(),
      password: password.value,
    })
    auth.setSession(data.data)
    ElMessage.success('管理员登录成功')
    router.push('/overview')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '管理员登录失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <section class="login-brand">
      <header class="brand-top">
        <span class="brand-name">拾页</span>
        <span class="brand-latin">ShiYe</span>
        <span class="brand-badge">运营后台 · ADMIN</span>
      </header>

      <div class="brand-main">
        <div class="eyebrow">OPERATIONS &amp; COST CONTROL</div>
        <h1>看清每一笔<br /><em>token</em> 的去向。</h1>
        <p class="brand-desc">
          独立管理员账号与独立令牌。用户配额、索引任务、模型预算和全站成本，都在这里统一管理。
        </p>
        <ul class="brand-points">
          <li><i>01</i><span>用户与配额：月度 token / 成本上限，单独调整</span></li>
          <li><i>02</i><span>用量与成本：按用户、模型、日期核对账单</span></li>
          <li><i>03</i><span>模型与预算：全局开关、限流与熔断</span></li>
        </ul>
      </div>

      <footer class="brand-foot">
        <span class="dot" />
        <span>仅限管理员访问 · 令牌与用户端完全隔离</span>
      </footer>
    </section>

    <section class="login-form-wrap">
      <div class="login-card">
        <div class="card-eyebrow">ADMIN SIGN IN</div>
        <h2>运营后台</h2>
        <p class="card-sub">使用独立管理员账号登录，不要使用普通用户账号。</p>

        <form @submit.prevent="submit">
          <label class="field">
            <span>管理员邮箱</span>
            <input
              v-model="email"
              type="email"
              autocomplete="username"
              placeholder="admin@shiye.local"
            />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="管理员密码"
            />
          </label>

          <button class="submit" type="submit" :disabled="loading">
            {{ loading ? '验证中…' : '进入后台' }}
          </button>
        </form>

        <p class="note">
          管理端 API 位于 <b>18081</b>，与用户端 <b>18080</b> 分离部署。管理员令牌不能访问用户端接口，反之亦然。
        </p>

        <p class="terms">首次登录后请立即修改初始密码 · 管理操作会写入审计日志</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.02fr 0.98fr;
  background: var(--bg);
}
.login-brand {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  padding: 34px 48px 26px;
  border-right: 1px solid var(--line);
  background: var(--bg);
}
.brand-top {
  display: flex;
  align-items: center;
  gap: 9px;
}
.brand-name {
  font-size: 23px;
  font-weight: 800;
  letter-spacing: 0.5px;
}
.brand-latin {
  color: var(--faint);
  font-size: 10.5px;
}
.brand-badge {
  margin-left: auto;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--muted);
  font-size: 9.5px;
  padding: 5px 9px;
  border-radius: 999px;
}
.brand-main {
  max-width: 540px;
}
.brand-main .eyebrow {
  color: var(--faint);
  margin-bottom: 12px;
}
.brand-main h1 {
  margin: 0;
  font-family: Georgia, 'Songti SC', 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 42px;
  line-height: 1.35;
  letter-spacing: -0.02em;
}
.brand-main h1 em {
  font-style: normal;
  color: var(--primary);
}
.brand-desc {
  margin: 14px 0 0;
  max-width: 500px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.8;
}
.brand-points {
  display: grid;
  gap: 12px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
  max-width: 480px;
}
.brand-points li {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--muted);
  font-size: 11.5px;
}
.brand-points i {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: #f1f0ec;
  color: var(--faint);
  display: flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
  font-size: 9.5px;
  flex: none;
}
.brand-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--faint);
  font-size: 10px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--faint);
}
.login-form-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 34px;
  background: var(--bg);
}
.login-card {
  width: 400px;
  max-width: 100%;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 28px;
}
.card-eyebrow {
  color: var(--faint);
  letter-spacing: 1.5px;
  font-size: 9px;
  font-weight: 800;
  margin-bottom: 8px;
}
.login-card h2 {
  margin: 0 0 4px;
  font-family: Georgia, 'Songti SC', 'Noto Serif SC', serif;
  font-size: 26px;
  letter-spacing: -0.02em;
}
.card-sub {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.7;
}
.field {
  display: block;
  margin-bottom: 12px;
}
.field span {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 6px;
}
.field input {
  width: 100%;
  height: 40px;
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 0 11px;
  font-size: 12.5px;
  color: var(--ink);
  background: #fff;
  outline: none;
  font-family: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.field input::placeholder {
  color: var(--faint);
}
.field input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(91, 91, 214, 0.1);
}
.submit {
  width: 100%;
  height: 42px;
  margin-top: 4px;
  border: 0;
  border-radius: 9px;
  background: var(--ink);
  color: #fff;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.15s;
}
.submit:hover:not(:disabled) {
  opacity: 0.88;
}
.submit:disabled {
  opacity: 0.55;
  cursor: default;
}
.note {
  margin: 14px 0 0;
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 9px 11px;
  color: var(--muted);
  font-size: 10.5px;
  line-height: 1.7;
}
.note b {
  color: var(--ink);
}
.terms {
  margin: 12px 0 0;
  text-align: center;
  color: var(--faint);
  font-size: 9.5px;
}

@media (max-width: 960px) {
  .admin-login {
    grid-template-columns: 1fr;
  }
  .login-brand {
    display: none;
  }
  .login-form-wrap {
    padding: 24px;
  }
}
</style>
