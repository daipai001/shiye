<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { apiErrorMessage } from '../api/errors'
import type { ApiResult, TokenResponse } from '../api/types'
import { useAuthStore } from '../stores/auth'

type Mode = 'login' | 'register'

const router = useRouter()
const auth = useAuthStore()

const mode = ref<Mode>('login')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const displayName = ref('')
const loading = ref(false)

function switchMode(next: Mode) {
  mode.value = next
  confirmPassword.value = ''
}

function validate(): string | null {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    return '请输入有效的邮箱地址'
  }
  if (password.value.length < 8) {
    return '密码至少 8 位'
  }
  if (password.value.length > 72) {
    return '密码最多 72 位'
  }
  if (mode.value === 'register' && (!/[A-Za-z]/.test(password.value) || !/\d/.test(password.value))) {
    return '密码需同时包含字母和数字'
  }
  if (mode.value === 'register') {
    if (confirmPassword.value !== password.value) {
      return '两次输入的密码不一致'
    }
    if (displayName.value.trim().length > 64) {
      return '昵称最多 64 个字符'
    }
  }
  return null
}

async function submit() {
  const invalid = validate()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }

  loading.value = true
  try {
    const endpoint = mode.value === 'login' ? '/auth/login' : '/auth/register'
    const payload =
      mode.value === 'login'
        ? { email: email.value.trim(), password: password.value }
        : {
            email: email.value.trim(),
            password: password.value,
            displayName: displayName.value.trim() || undefined,
          }
    const { data } = await api.post<ApiResult<TokenResponse>>(endpoint, payload)
    auth.setSession(data.data)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功，已为你创建知识库')
    router.push('/library')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, mode.value === 'login' ? '登录失败' : '注册失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <section class="auth-brand">
      <header class="brand-top">
        <span class="brand-name">拾页</span>
        <span class="brand-latin">ShiYe</span>
        <span class="brand-badge">AI 知识库 · 浏览器插件</span>
      </header>

      <div class="brand-main">
        <div class="eyebrow">YOUR BOOKMARKS, NOW ANSWERABLE</div>
        <h1>把收藏夹，<br />变成<em>会回答</em>的知识库。</h1>
        <p class="brand-desc">
          导入浏览器书签，拾页会自动抓正文、切片、建索引。任意网页点悬浮球，就能基于你的收藏提问，回答带原文引用。
        </p>
        <ul class="brand-points">
          <li><i>01</i><span>导入书签文件夹，自动抓正文、切片、建索引</span></li>
          <li><i>02</i><span>任意网页右下角悬浮球，随时问资料库或当前页面</span></li>
          <li><i>03</i><span>回答带引用，token 和成本在后台一目了然</span></li>
        </ul>
      </div>

      <div class="brand-preview">
        <div class="preview-head">
          <span class="mini-logo">拾</span>
          <b>资料库问答</b>
          <span class="mini-pill">引用开启</span>
        </div>
        <div class="mini-msg user">我收藏的文章里，chunk size 一般建议多大？</div>
        <div class="mini-msg ai">
          你收藏的资料里主要有三种倾向：<br />
          1. 按语义段落切，单块 500~800 字，重叠 10%~15%。<span class="mini-cite">[1]</span><br />
          2. 先按标题层级切，再在段内二次切分。<span class="mini-cite">[2]</span>
        </div>
        <div class="mini-cites">
          <span>[1] RAG 切片策略 · 0.87</span>
          <span>[2] 中文长文档 · 0.83</span>
        </div>
        <div class="mini-ball">拾</div>
      </div>

      <footer class="brand-foot">拾页 · 收藏不是终点，能问才是。</footer>
    </section>

    <section class="auth-form-wrap">
      <div class="auth-card">
        <div class="card-eyebrow">{{ mode === 'login' ? 'WELCOME BACK' : 'CREATE YOUR LIBRARY' }}</div>
        <h2>{{ mode === 'login' ? '登录拾页' : '创建你的知识库' }}</h2>
        <p class="card-sub">
          {{ mode === 'login' ? '登录后继续管理你的资料库' : '注册后即可使用资料库问答和插件悬浮球' }}
        </p>

        <div class="auth-tabs">
          <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">
            登录
          </button>
          <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">
            注册
          </button>
        </div>

        <form @submit.prevent="submit">
          <label v-if="mode === 'register'" class="field">
            <span>昵称</span>
            <input v-model="displayName" type="text" maxlength="64" placeholder="怎么称呼你（可留空）" />
          </label>

          <label class="field">
            <span>邮箱</span>
            <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              placeholder="至少 8 位"
            />
          </label>

          <label v-if="mode === 'register'" class="field">
            <span>确认密码</span>
            <input
              v-model="confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再输入一次"
            />
          </label>

          <button class="submit" type="submit" :disabled="loading">
            {{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并进入' }}
          </button>
        </form>

        <p v-if="mode === 'register'" class="note">
          注册即送 <b>20 万 tokens / 月</b> 试用额度。系统只有一个管理员，其他账号为普通用户。
        </p>
        <p v-else class="note">悬浮球需要登录后使用。未登录时点击悬浮球，会引导你生成一次性授权码。</p>

        <p class="terms">登录即表示同意服务条款和隐私政策 · 数据仅存于你的服务端</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  background: var(--bg);
}
.auth-brand {
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
  max-width: 560px;
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
  max-width: 510px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.8;
}
.brand-points {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}
.brand-points li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 11px;
}
.brand-points i {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--faint);
  display: flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
  font-size: 9.5px;
  flex: none;
}
.brand-preview {
  position: relative;
  width: 470px;
  max-width: 100%;
  padding: 14px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.preview-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 10px;
}
.mini-logo {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--ink);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 800;
}
.preview-head b {
  font-size: 11.5px;
}
.mini-pill {
  margin-left: auto;
  font-size: 9px;
  color: var(--muted);
  background: #f4f3ef;
  border: 1px solid var(--line);
  padding: 2px 6px;
  border-radius: 999px;
}
.mini-msg {
  font-size: 10.5px;
  line-height: 1.65;
  padding: 8px 10px;
  border-radius: 9px;
  margin-bottom: 7px;
}
.mini-msg.user {
  background: #f1f0ec;
  color: var(--ink);
  margin-left: 42px;
}
.mini-msg.ai {
  background: #fbfbf9;
  border: 1px solid var(--line);
  color: var(--muted);
}
.mini-cite {
  color: var(--primary);
  font-weight: 700;
  font-size: 9px;
}
.mini-cites {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.mini-cites span {
  font-size: 9px;
  color: var(--muted);
  background: #f4f3ef;
  border: 1px solid var(--line);
  padding: 3px 6px;
  border-radius: 6px;
}
.mini-ball {
  position: absolute;
  right: 14px;
  bottom: -18px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--ink);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 14px;
  border: 1px solid rgba(255, 255, 255, 0.22);
}
.brand-foot {
  color: var(--faint);
  font-size: 10px;
}
.auth-form-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 34px;
  background: var(--bg);
}
.auth-card {
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
.auth-card h2 {
  margin: 0 0 4px;
  font-family: Georgia, 'Songti SC', 'Noto Serif SC', serif;
  font-size: 26px;
  letter-spacing: -0.02em;
}
.card-sub {
  margin: 0 0 18px;
  color: var(--muted);
  font-size: 11.5px;
}
.auth-tabs {
  display: flex;
  gap: 4px;
  background: #f1f0ec;
  border-radius: 9px;
  padding: 3px;
  margin-bottom: 16px;
}
.auth-tabs button {
  flex: 1;
  border: 1px solid transparent;
  background: transparent;
  color: var(--muted);
  padding: 7px 0;
  border-radius: 7px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}
.auth-tabs button.active {
  background: #fff;
  border-color: var(--line);
  color: var(--ink);
  font-weight: 700;
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
  .auth-shell {
    grid-template-columns: 1fr;
  }
  .auth-brand {
    display: none;
  }
  .auth-form-wrap {
    padding: 24px;
  }
}
</style>
