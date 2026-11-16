<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { apiErrorMessage } from '../api/errors'
import type { ApiResult } from '../api/types'

interface ExtensionCodeResponse {
  code: string
  expiresIn: number
}

const code = ref('')
const remaining = ref(0)
const loading = ref(false)
let timer: number | undefined

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startTimer(seconds: number) {
  stopTimer()
  remaining.value = seconds
  timer = window.setInterval(() => {
    remaining.value -= 1
    if (remaining.value <= 0) {
      remaining.value = 0
      stopTimer()
      code.value = ''
    }
  }, 1000)
}

async function generateCode() {
  loading.value = true
  try {
    const { data } = await api.post<ApiResult<ExtensionCodeResponse>>('/auth/extension-code')
    code.value = data.data.code
    startTimer(data.data.expiresIn)
    ElMessage.success('授权码已生成，5 分钟内有效')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '生成授权码失败'))
  } finally {
    loading.value = false
  }
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(code.value)
    ElMessage.success('已复制授权码')
  } catch {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

function formatRemaining(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>浏览器插件</h1>
        <p>把收藏夹接进拾页：安装插件 → 生成授权码 → 选择书签文件夹导入。</p>
      </div>
    </div>

    <div class="plugin-grid">
      <section class="card steps-card">
        <div class="eyebrow">CONNECT IN 3 STEPS</div>
        <h2>连接你的浏览器</h2>
        <ol class="steps">
          <li>
            <span class="step-no">01</span>
            <div>
              <b>安装扩展</b>
              <p>Chrome / Edge 打开扩展管理页，开启开发者模式，加载 `frontend/user-extension`。</p>
            </div>
          </li>
          <li>
            <span class="step-no">02</span>
            <div>
              <b>生成一次性授权码</b>
              <p>右侧点击生成，把 5 分钟有效的授权码粘贴到插件设置页。</p>
            </div>
          </li>
          <li>
            <span class="step-no">03</span>
            <div>
              <b>导入书签文件夹</b>
              <p>在插件设置页读取书签树，选择文件夹、勾选包含子文件夹，然后开始导入。</p>
            </div>
          </li>
        </ol>
        <div class="note">
          授权码只用于换取插件令牌，服务端只保存其 SHA-256 哈希；使用一次后立即失效。
        </div>
      </section>

      <section class="card code-card">
        <div class="eyebrow">ONE-TIME AUTH CODE</div>
        <h2>插件授权码</h2>
        <p class="muted">在插件设置页的「连接账号」中粘贴此码。</p>

        <div v-if="code" class="code-box">
          <code>{{ code }}</code>
          <button type="button" class="copy" @click="copyCode">复制</button>
        </div>
        <div v-else class="code-empty">尚未生成</div>

        <p v-if="code" class="countdown">剩余有效期 {{ formatRemaining(remaining) }}</p>
        <p v-else class="countdown">授权码 5 分钟内有效，且只能使用一次。</p>

        <button class="generate" type="button" :disabled="loading" @click="generateCode">
          {{ loading ? '生成中…' : code ? '重新生成' : '生成授权码' }}
        </button>
      </section>
    </div>

    <section class="card import-card">
      <div class="import-head">
        <div>
          <div class="eyebrow">BOOKMARK IMPORT</div>
          <h2>书签导入规则</h2>
        </div>
        <span class="pill">每次一个文件夹</span>
      </div>
      <div class="rule-grid">
        <div class="rule">
          <b>包含子文件夹</b>
          <span>勾选后，父文件夹下的所有分类会被一起导入。</span>
        </div>
        <div class="rule">
          <b>自动去重</b>
          <span>服务端规范化 URL，去掉锚点和 utm 等追踪参数后比对。</span>
        </div>
        <div class="rule">
          <b>分批追加</b>
          <span>超过 50 条时插件自动分批，仍归入同一个导入任务。</span>
        </div>
        <div class="rule">
          <b>失败可重试</b>
          <span>抓取失败的条目会保留原因，可在导入详情中重试。</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.plugin-grid {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 16px;
  margin-bottom: 16px;
}
.steps-card h2,
.code-card h2,
.import-card h2 {
  margin: 0 0 6px;
  font-size: 20px;
  letter-spacing: -0.02em;
}
.steps {
  list-style: none;
  margin: 18px 0;
  padding: 0;
  display: grid;
  gap: 12px;
}
.steps li {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fbfbf9;
}
.step-no {
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--line);
  color: var(--faint);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 800;
}
.steps b {
  font-size: 12.5px;
}
.steps p {
  margin: 3px 0 0;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.7;
}
.note {
  background: #fff;
  border: 1px dashed #d7d4cc;
  border-radius: 9px;
  padding: 10px 12px;
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.7;
}
.code-card {
  display: flex;
  flex-direction: column;
}
.code-card .muted {
  margin: 0 0 16px;
  font-size: 11.5px;
}
.code-box {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  background: #fbfbf9;
  border-radius: 10px;
  padding: 14px;
}
.code-box code {
  flex: 1;
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
  word-break: break-all;
}
.copy {
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 7px;
  padding: 5px 9px;
  font-size: 10.5px;
  color: var(--muted);
  cursor: pointer;
  font-family: inherit;
}
.copy:hover {
  color: var(--ink);
}
.code-empty {
  border: 1px dashed #d7d4cc;
  border-radius: 10px;
  padding: 24px;
  text-align: center;
  color: var(--faint);
  font-size: 11.5px;
}
.countdown {
  margin: 9px 0 16px;
  color: var(--faint);
  font-size: 10.5px;
}
.generate {
  margin-top: auto;
  width: 100%;
  height: 42px;
  border: 0;
  border-radius: 9px;
  background: var(--ink);
  color: #fff;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
}
.generate:hover:not(:disabled) {
  opacity: 0.88;
}
.generate:disabled {
  opacity: 0.55;
  cursor: default;
}
.import-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.pill {
  border: 1px solid var(--line);
  background: #fbfbf9;
  color: var(--muted);
  border-radius: 999px;
  padding: 4px 9px;
  font-size: 10px;
  white-space: nowrap;
}
.rule-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.rule {
  border-top: 1px solid var(--line);
  padding-top: 10px;
}
.rule b {
  display: block;
  font-size: 12px;
  margin-bottom: 3px;
}
.rule span {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

@media (max-width: 960px) {
  .plugin-grid,
  .rule-grid {
    grid-template-columns: 1fr;
  }
}
</style>
