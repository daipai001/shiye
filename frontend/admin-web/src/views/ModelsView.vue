<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import type { AdminModelConfig, ApiResult, ModelTestResult } from '../api/types'

const config = ref<AdminModelConfig | null>(null)
const loading = ref(false)
const testing = ref<'' | 'CHAT' | 'EMBEDDING'>('')
const testResult = ref<{ type: string; result: ModelTestResult } | null>(null)

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(6)}`
}

function keyState(configured: boolean, masked?: string | null) {
  return configured ? masked || '已配置' : '未配置'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<AdminModelConfig>>('/models')
    config.value = data.data
  } catch {
    ElMessage.error('读取模型配置失败')
  } finally {
    loading.value = false
  }
}

async function test(type: 'CHAT' | 'EMBEDDING') {
  testing.value = type
  testResult.value = null
  try {
    const { data } = await api.post<ApiResult<ModelTestResult>>('/models/test', { type })
    testResult.value = { type, result: data.data }
    if (data.data.ok) {
      ElMessage.success(data.data.message)
    } else {
      ElMessage.warning(data.data.message)
    }
  } catch {
    ElMessage.error('测试请求失败')
  } finally {
    testing.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>模型配置</h1>
        <p>查看当前 Chat / Embedding 配置并测试连接。敏感 key 只显示掩码。</p>
      </div>
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div v-if="!config" class="empty">正在读取模型配置…</div>
    <template v-else>
      <div class="model-grid">
        <section class="panel">
          <div class="panel-head">
            <div>
              <div class="eyebrow">CHAT</div>
              <h2>{{ config.chat.provider }} · {{ config.chat.model }}</h2>
            </div>
            <span class="pill" :class="{ ok: config.chat.configured }">{{ config.chat.configured ? '已配置' : '未配置' }}</span>
          </div>
          <div class="kv"><span>Base URL</span><b>{{ config.chat.baseUrl }}</b></div>
          <div class="kv"><span>模型</span><b>{{ config.chat.model }}</b></div>
          <div class="kv"><span>API Key</span><b>{{ keyState(config.chat.configured, config.chat.apiKeyMasked) }}</b></div>
          <div class="kv"><span>max tokens</span><b>{{ config.chat.maxTokens }}</b></div>
          <div class="kv"><span>temperature</span><b>{{ config.chat.temperature }}</b></div>
          <div class="kv"><span>单价 / 1M</span><b>输入 {{ formatCost(config.chat.inputPrice) }} · 输出 {{ formatCost(config.chat.outputPrice) }}</b></div>
          <el-button class="test-btn" :loading="testing === 'CHAT'" @click="test('CHAT')">测试 Chat 连接</el-button>
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <div class="eyebrow">EMBEDDING</div>
              <h2>{{ config.embedding.provider }} · {{ config.embedding.model }}</h2>
            </div>
            <span class="pill" :class="{ ok: config.embedding.configured }">{{ config.embedding.configured ? '已配置' : '未配置' }}</span>
          </div>
          <div class="kv"><span>Base URL</span><b>{{ config.embedding.baseUrl }}</b></div>
          <div class="kv"><span>模型</span><b>{{ config.embedding.model }}</b></div>
          <div class="kv"><span>API Key</span><b>{{ keyState(config.embedding.configured, config.embedding.apiKeyMasked) }}</b></div>
          <div class="kv"><span>维度</span><b>{{ config.embedding.dimension }}</b></div>
          <div class="kv"><span>batch size</span><b>{{ config.embedding.batchSize }}</b></div>
          <div class="kv"><span>单价 / 1M</span><b>{{ formatCost(config.embedding.inputPrice) }}</b></div>
          <el-button class="test-btn" :loading="testing === 'EMBEDDING'" @click="test('EMBEDDING')">测试 Embedding 连接</el-button>
        </section>
      </div>

      <section class="panel budget-panel">
        <div class="eyebrow">BUDGET</div>
        <h2>默认预算</h2>
        <div class="budget-grid">
          <div><span>全站月 tokens</span><b>{{ config.budget.globalMonthlyTokens.toLocaleString() }}</b></div>
          <div><span>全站月成本</span><b>{{ formatCost(config.budget.globalMonthlyCost) }}</b></div>
          <div><span>新用户月 tokens</span><b>{{ config.budget.defaultUserTokens.toLocaleString() }}</b></div>
          <div><span>新用户月成本</span><b>{{ formatCost(config.budget.defaultUserCost) }}</b></div>
        </div>
      </section>

      <section v-if="testResult" class="panel result-panel">
        <div class="eyebrow">TEST RESULT</div>
        <h2>{{ testResult.type }} · {{ testResult.result.ok ? '连接成功' : '连接失败' }}</h2>
        <p>{{ testResult.result.message }}</p>
        <p class="muted">耗时 {{ testResult.result.latencyMs }} ms · 模型 {{ testResult.result.model || '-' }}</p>
      </section>

      <p class="config-note">
        当前版本模型 key / base URL / 价格通过服务端环境变量或 <code>application-local.yml</code> 管理，修改后重启 user-api 生效；
        管理端在线编辑和写库将在下一阶段接入。
      </p>
    </template>
  </div>
</template>

<style scoped>
.model-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.panel-head h2 {
  margin: 0;
  font-size: 17px;
  letter-spacing: -0.02em;
}
.pill {
  border: 1px solid #eed4d1;
  background: #fdf7f6;
  color: #b3372f;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 9px;
  font-weight: 700;
}
.pill.ok {
  border-color: #c9efe1;
  background: #f4fbf8;
  color: #16794f;
}
.kv {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 7px 0;
  border-top: 1px solid #f1f0ec;
  font-size: 10.5px;
  color: var(--muted);
}
.kv b {
  color: var(--ink);
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}
.test-btn {
  margin-top: 12px;
}
.budget-panel {
  margin-bottom: 16px;
}
.budget-panel h2 {
  margin: 0 0 12px;
  font-size: 18px;
}
.budget-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.budget-grid div {
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
}
.budget-grid span {
  display: block;
  color: var(--faint);
  font-size: 9.5px;
}
.budget-grid b {
  display: block;
  margin-top: 4px;
  font-size: 15px;
}
.result-panel {
  margin-bottom: 16px;
}
.result-panel h2 {
  margin: 0 0 6px;
  font-size: 17px;
}
.result-panel p {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
}
.result-panel .muted {
  color: var(--faint);
  margin-top: 4px;
}
.config-note {
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.8;
  background: #fbfbf9;
  border: 1px dashed #d7d4cc;
  border-radius: 8px;
  padding: 10px 12px;
}
.config-note code {
  font-family: 'Cascadia Mono', Consolas, monospace;
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
@media (max-width: 1000px) {
  .model-grid,
  .budget-grid {
    grid-template-columns: 1fr;
  }
}
</style>
