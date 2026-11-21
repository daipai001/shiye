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
const invitationCode = ref('')
const loading = ref(false)
const forgotOpen = ref(false)
const forgotEmail = ref('')
const sendingReset = ref(false)

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

function openForgot() {
  forgotEmail.value = email.value.trim()
  forgotOpen.value = true
}

async function sendPasswordReset() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(forgotEmail.value.trim())) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
  sendingReset.value = true
  try {
    await api.post('/auth/password/forgot', { email: forgotEmail.value.trim() })
    ElMessage.success('如果该邮箱已注册，重置邮件会很快送达')
    forgotOpen.value = false
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '发送重置邮件失败'))
  } finally {
    sendingReset.value = false
  }
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
            invitationCode: invitationCode.value.trim() || undefined,
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
      <div class="auth-aurora" aria-hidden="true"></div>
      <div class="auth-grid" aria-hidden="true"></div>
      <div class="auth-orb orb-a" aria-hidden="true"></div>
      <div class="auth-orb orb-b" aria-hidden="true"></div>

      <header class="brand-top">
        <span class="brand-mark">拾</span>
        <span class="brand-name">拾页</span>
        <span class="brand-latin">ShiYe</span>
        <span class="brand-badge"><i></i>AI 知识库 · 浏览器插件</span>
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
          <li><i>03</i><span>回答带引用，token 和成本在管理后台一目了然</span></li>
        </ul>
      </div>

      <div class="brand-preview">
        <div class="preview-glow" aria-hidden="true"></div>
        <div class="preview-head">
          <span class="mini-logo">拾</span>
          <b>资料库问答</b>
          <span class="mini-pill">DeepSeek-V3</span>
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
      <div class="form-halo" aria-hidden="true"></div>
      <div class="auth-card">
        <div class="mobile-brand">
          <span class="brand-mark">拾</span>
          <div>
            <b>拾页</b>
            <span>把收藏夹变成会回答的知识库</span>
          </div>
        </div>

        <div class="card-eyebrow">{{ mode === 'login' ? 'WELCOME BACK' : 'CREATE YOUR LIBRARY' }}</div>
        <h2>{{ mode === 'login' ? '登录拾页' : '创建你的知识库' }}</h2>
        <p class="card-sub">
          {{ mode === 'login' ? '登录后继续管理你的资料库' : '注册后即可使用插件悬浮球和资料库问答' }}
        </p>

        <div class="auth-tabs" :class="{ register: mode === 'register' }">
          <span class="tab-indicator" aria-hidden="true"></span>
          <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">
            登录
          </button>
          <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">
            注册
          </button>
        </div>

        <form :key="mode" class="auth-form" novalidate @submit.prevent="submit">
          <label v-if="mode === 'register'" class="field">
            <span>昵称</span>
            <div class="input-wrap">
              <i aria-hidden="true">☺</i>
              <input v-model="displayName" type="text" maxlength="64" placeholder="怎么称呼你（可留空）" />
            </div>
          </label>

          <label class="field">
            <span>邮箱</span>
            <div class="input-wrap">
              <i aria-hidden="true">@</i>
              <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
            </div>
          </label>

          <label class="field">
            <span>密码</span>
            <div class="input-wrap">
              <i aria-hidden="true">⌁</i>
              <input
                v-model="password"
                type="password"
                :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                placeholder="至少 8 位"
              />
            </div>
          </label>

          <label v-if="mode === 'register'" class="field">
            <span>确认密码</span>
            <div class="input-wrap">
              <i aria-hidden="true">⌁</i>
              <input
                v-model="confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="再输入一次"
              />
            </div>
          </label>

          <label v-if="mode === 'register'" class="field">
            <span>邀请码</span>
            <div class="input-wrap">
              <i aria-hidden="true">#</i>
              <input v-model="invitationCode" type="text" maxlength="64" placeholder="如需邀请码请填写" />
            </div>
          </label>

          <button class="submit" type="submit" :disabled="loading">
            <span>{{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并进入' }}</span>
            <i aria-hidden="true">→</i>
          </button>
          <button v-if="mode === 'login'" class="forgot-link" type="button" @click="openForgot">
            忘记密码？
          </button>
        </form>

        <p v-if="mode === 'register'" class="note">
          注册即送 <b>20 万 tokens / 月</b> 试用额度。系统只有一个管理员，其他账号为普通用户。
        </p>
        <p v-else class="note">
          悬浮球需要登录后使用。未登录时点击悬浮球，会引导你生成一次性授权码。
        </p>

        <p class="terms">登录即表示同意服务条款和隐私政策 · 数据仅存于你的服务端</p>
      </div>
    </section>

    <el-dialog v-model="forgotOpen" title="找回密码" width="380px">
      <p class="forgot-hint">输入注册邮箱，我们会发送重置链接。未配置 SMTP 时不会实际发送邮件。</p>
      <el-input v-model="forgotEmail" type="email" placeholder="you@example.com" @keyup.enter="sendPasswordReset" />
      <template #footer>
        <el-button @click="forgotOpen = false">取消</el-button>
        <el-button type="primary" :loading="sendingReset" @click="sendPasswordReset">发送重置邮件</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  background: var(--bg);
  overflow: hidden;
}

/* ---------- left brand ---------- */
.auth-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  padding: 34px 48px 28px;
  border-right: 1px solid var(--line);
  background: #f5f4f0;
  overflow: hidden;
}
.auth-aurora {
  position: absolute;
  inset: -20% -10%;
  background:
    radial-gradient(38% 34% at 18% 18%, rgba(91, 91, 214, 0.16), transparent 72%),
    radial-gradient(34% 30% at 86% 20%, rgba(45, 148, 126, 0.11), transparent 70%),
    radial-gradient(42% 38% at 68% 78%, rgba(214, 158, 91, 0.13), transparent 72%);
  filter: blur(10px);
  animation: auroraDrift 16s ease-in-out infinite alternate;
  pointer-events: none;
}
.auth-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(28, 28, 26, 0.045) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(28, 28, 26, 0.045) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(ellipse at 24% 30%, black 10%, transparent 68%);
  animation: gridDrift 24s linear infinite;
  pointer-events: none;
}
.auth-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  pointer-events: none;
  opacity: 0.6;
}
.orb-a {
  width: 210px;
  height: 210px;
  right: 4%;
  top: 16%;
  background: radial-gradient(circle at 32% 30%, #fff, rgba(91, 91, 214, 0.2) 46%, transparent 70%);
  animation: floatOrb 9s ease-in-out infinite;
}
.orb-b {
  width: 140px;
  height: 140px;
  left: 6%;
  bottom: 12%;
  background: radial-gradient(circle at 40% 40%, #fff, rgba(45, 148, 126, 0.16) 50%, transparent 72%);
  animation: floatOrb 11s ease-in-out infinite reverse;
}
.brand-top,
.brand-main,
.brand-preview,
.brand-foot {
  position: relative;
  z-index: 1;
}
.brand-top {
  display: flex;
  align-items: center;
  gap: 9px;
  animation: fadeUp 0.7s both;
}
.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--ink);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.5px;
  box-shadow: 0 6px 16px rgba(28, 28, 26, 0.16);
}
.brand-name {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.4px;
}
.brand-latin {
  color: var(--faint);
  font-size: 10px;
  letter-spacing: 0.4px;
}
.brand-badge {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(8px);
  color: var(--muted);
  font-size: 9.5px;
  padding: 5px 9px;
  border-radius: 999px;
}
.brand-badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2d947e;
  box-shadow: 0 0 0 3px rgba(45, 148, 126, 0.12);
  animation: softPulse 2.6s ease-in-out infinite;
}
.brand-main {
  max-width: 570px;
  animation: fadeUp 0.75s 0.08s both;
}
.brand-main .eyebrow {
  color: var(--faint);
  margin-bottom: 12px;
}
.brand-main h1 {
  margin: 0;
  font-family: Georgia, 'Songti SC', 'Noto Serif SC', serif;
  font-weight: 600;
  font-size: 43px;
  line-height: 1.32;
  letter-spacing: -0.03em;
}
.brand-main h1 em {
  position: relative;
  font-style: normal;
  color: var(--primary);
}
.brand-main h1 em::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 2px;
  height: 7px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(91, 91, 214, 0.18), rgba(45, 148, 126, 0.12));
  z-index: -1;
  transform-origin: left;
  animation: underlineIn 0.8s 0.55s both;
}
.brand-desc {
  margin: 14px 0 0;
  max-width: 520px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.8;
}
.brand-points {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}
.brand-points li {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--muted);
  font-size: 11px;
  transition: transform 0.25s ease, color 0.25s ease;
  animation: fadeUp 0.6s both;
}
.brand-points li:nth-child(1) { animation-delay: 0.25s; }
.brand-points li:nth-child(2) { animation-delay: 0.33s; }
.brand-points li:nth-child(3) { animation-delay: 0.41s; }
.brand-points li:hover {
  transform: translateX(4px);
  color: var(--ink);
}
.brand-points i {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.8);
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
  width: 480px;
  max-width: 100%;
  padding: 14px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: 0 18px 45px rgba(28, 28, 26, 0.08);
  backdrop-filter: blur(10px);
  animation: previewIn 0.8s 0.22s both, previewFloat 7s 1s ease-in-out infinite;
}
.preview-glow {
  position: absolute;
  inset: 12% 18%;
  background: radial-gradient(circle, rgba(91, 91, 214, 0.12), transparent 70%);
  filter: blur(26px);
  z-index: -1;
  animation: softPulse 5s ease-in-out infinite;
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
  font-size: 9px;
  color: var(--muted);
  background: #f4f3ef;
  border: 1px solid var(--line);
  padding: 2px 6px;
  border-radius: 999px;
}
.mini-pill:nth-of-type(1) {
  margin-left: auto;
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
  animation: msgIn 0.55s 0.55s both;
}
.mini-msg.ai {
  background: #fbfbf9;
  border: 1px solid var(--line);
  color: var(--muted);
  animation: msgIn 0.55s 0.75s both;
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
  animation: fadeUp 0.5s 0.95s both;
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
  box-shadow: 0 10px 24px rgba(28, 28, 26, 0.18);
  animation: ballFloat 5s ease-in-out infinite;
}
.mini-ball::after {
  content: '';
  position: absolute;
  inset: -7px;
  border-radius: 50%;
  border: 1px solid rgba(91, 91, 214, 0.22);
  animation: pulseRing 3s ease-out infinite;
}
.brand-foot {
  color: var(--faint);
  font-size: 10px;
  animation: fadeUp 0.6s 0.5s both;
}

/* ---------- right form ---------- */
.auth-form-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 34px;
  background: var(--bg);
  overflow: hidden;
}
.form-halo {
  position: absolute;
  width: 460px;
  height: 460px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(91, 91, 214, 0.08), transparent 68%);
  filter: blur(10px);
  animation: softPulse 8s ease-in-out infinite;
  pointer-events: none;
}
.auth-card {
  position: relative;
  width: 408px;
  max-width: 100%;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 30px;
  box-shadow: 0 24px 60px rgba(28, 28, 26, 0.07);
  backdrop-filter: blur(10px);
  animation: cardIn 0.7s both;
}
.mobile-brand {
  display: none;
}
.card-eyebrow {
  color: var(--faint);
  letter-spacing: 1.8px;
  font-size: 9px;
  font-weight: 800;
  margin-bottom: 8px;
}
.auth-card h2 {
  margin: 0 0 4px;
  font-family: Georgia, 'Songti SC', 'Noto Serif SC', serif;
  font-size: 27px;
  letter-spacing: -0.025em;
}
.card-sub {
  margin: 0 0 18px;
  color: var(--muted);
  font-size: 11.5px;
}
.auth-tabs {
  position: relative;
  display: flex;
  gap: 4px;
  background: #f1f0ec;
  border-radius: 10px;
  padding: 3px;
  margin-bottom: 17px;
}
.tab-indicator {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc(50% - 5px);
  border-radius: 7px;
  background: #fff;
  border: 1px solid var(--line);
  box-shadow: 0 2px 8px rgba(28, 28, 26, 0.05);
  transition: transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}
.auth-tabs.register .tab-indicator {
  transform: translateX(calc(100% + 2px));
}
.auth-tabs button {
  position: relative;
  z-index: 1;
  flex: 1;
  border: 0;
  background: transparent;
  color: var(--muted);
  padding: 7px 0;
  border-radius: 7px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  transition: color 0.2s ease;
}
.auth-tabs button.active {
  color: var(--ink);
  font-weight: 700;
}
.auth-form {
  animation: formIn 0.34s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.field {
  display: block;
  margin-bottom: 12px;
}
.field > span {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 6px;
}
.input-wrap {
  display: flex;
  align-items: center;
  height: 42px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}
.input-wrap i {
  width: 38px;
  text-align: center;
  color: var(--faint);
  font-style: normal;
  font-size: 12px;
  transition: color 0.2s ease;
}
.input-wrap input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  padding: 0 11px 0 0;
  font-size: 12.5px;
  color: var(--ink);
  outline: none;
  font-family: inherit;
}
.input-wrap input::placeholder {
  color: var(--faint);
}
.input-wrap:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(91, 91, 214, 0.1);
  transform: translateY(-1px);
}
.input-wrap:focus-within i {
  color: var(--primary);
}
.forgot-link {
  display: block;
  margin: 12px auto 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
}
.forgot-link:hover {
  color: var(--ink);
}
.forgot-hint {
  color: var(--faint);
  font-size: 10px;
  line-height: 1.6;
  margin: 0 0 12px;
}
.submit {
  position: relative;
  width: 100%;
  height: 44px;
  margin-top: 5px;
  border: 0;
  border-radius: 9px;
  background: var(--ink);
  color: #fff;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}
.submit i {
  font-style: normal;
  transition: transform 0.2s ease;
}
.submit::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 30%, rgba(255, 255, 255, 0.16) 48%, transparent 66%);
  transform: translateX(-120%);
  transition: transform 0.55s ease;
}
.submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(28, 28, 26, 0.16);
}
.submit:hover:not(:disabled)::after {
  transform: translateX(120%);
}
.submit:hover:not(:disabled) i {
  transform: translateX(3px);
}
.submit:active:not(:disabled) {
  transform: scale(0.99);
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

@keyframes auroraDrift {
  0% { transform: translate3d(-2%, -1%, 0) scale(1); }
  100% { transform: translate3d(2%, 2%, 0) scale(1.05); }
}
@keyframes gridDrift {
  0% { background-position: 0 0, 0 0; }
  100% { background-position: 42px 42px, 42px 42px; }
}
@keyframes floatOrb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(10px, -14px) scale(1.04); }
}
@keyframes softPulse {
  0%, 100% { opacity: 0.55; transform: scale(1); }
  50% { opacity: 0.9; transform: scale(1.04); }
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes previewIn {
  from { opacity: 0; transform: translateY(16px) scale(0.985); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes previewFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
@keyframes ballFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
@keyframes pulseRing {
  0% { opacity: 0.55; transform: scale(0.82); }
  70%, 100% { opacity: 0; transform: scale(1.35); }
}
@keyframes msgIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes formIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes underlineIn {
  from { transform: scaleX(0); opacity: 0; }
  to { transform: scaleX(1); opacity: 1; }
}

@media (max-width: 1100px) {
  .auth-brand {
    padding: 30px 34px 24px;
  }
  .brand-main h1 {
    font-size: 36px;
  }
  .brand-preview {
    width: 420px;
  }
  .brand-badge {
    display: none;
  }
  .auth-form-wrap {
    padding: 26px;
  }
}

@media (max-width: 860px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }
  .auth-brand {
    display: none;
  }
  .auth-form-wrap {
    padding: 24px;
  }
  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 20px;
  }
  .mobile-brand div {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .mobile-brand b {
    font-size: 15px;
  }
  .mobile-brand span {
    color: var(--faint);
    font-size: 9.5px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-aurora,
  .auth-grid,
  .auth-orb,
  .brand-badge i,
  .brand-top,
  .brand-main,
  .brand-main h1 em::after,
  .brand-points li,
  .brand-preview,
  .preview-glow,
  .mini-msg,
  .mini-cites,
  .mini-ball,
  .mini-ball::after,
  .brand-foot,
  .auth-card,
  .auth-form,
  .form-halo {
    animation: none !important;
  }
  .tab-indicator,
  .input-wrap,
  .submit,
  .submit::after,
  .brand-points li {
    transition: none !important;
  }
}
</style>
