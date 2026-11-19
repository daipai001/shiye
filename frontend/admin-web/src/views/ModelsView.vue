<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import type {
  AdminModelConfig,
  AdminModelUpdatePayload,
  ApiResult,
  ModelTestResult,
} from '../api/types'

const config = ref<AdminModelConfig | null>(null)
const loading = ref(false)
const testing = ref<'' | 'CHAT' | 'EMBEDDING' | 'RERANK'>('')
const testResult = ref<{ type: string; result: ModelTestResult } | null>(null)
const editVisible = ref(false)
const saving = ref(false)
const edit = ref<AdminModelUpdatePayload>(emptyPayload())

function emptyPayload(): AdminModelUpdatePayload {
  return {
    chat: {
      baseUrl: '',
      model: '',
      apiKey: '',
      maxTokens: 1600,
      temperature: 0.25,
      inputPrice: 2,
      outputPrice: 8,
    },
    embedding: {
      baseUrl: '',
      model: '',
      apiKey: '',
      dimension: 1024,
      batchSize: 16,
    },
    rerank: {
      enabled: false,
      baseUrl: '',
      model: '',
      apiKey: '',
    },
  }
}

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

function openEdit() {
  if (!config.value) {
    return
  }
  edit.value = {
    chat: {
      baseUrl: config.value.chat.baseUrl,
      model: config.value.chat.model,
      apiKey: '',
      maxTokens: config.value.chat.maxTokens,
      temperature: config.value.chat.temperature,
      inputPrice: config.value.chat.inputPrice,
      outputPrice: config.value.chat.outputPrice,
    },
    embedding: {
      baseUrl: config.value.embedding.baseUrl,
      model: config.value.embedding.model,
      apiKey: '',
      dimension: config.value.embedding.dimension,
      batchSize: config.value.embedding.batchSize,
    },
    rerank: {
      enabled: config.value.rerank.enabled,
      baseUrl: config.value.rerank.baseUrl,
      model: config.value.rerank.model,
      apiKey: '',
    },
  }
  editVisible.value = true
}

async function save() {
  saving.value = true
  try {
    const { data } = await api.put<ApiResult<AdminModelConfig>>('/models', edit.value)
    config.value = data.data
    editVisible.value = false
    ElMessage.success('模型配置已保存，user-api 最多 5 秒生效')
  } catch {
    ElMessage.error('保存失败，请检查 Base URL、模型名和参数范围')
  } finally {
    saving.value = false
  }
}

async function test(type: 'CHAT' | 'EMBEDDING' | 'RERANK') {
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
        <p>在线编辑 Chat / Embedding / Rerank 参数。敏感 key 只显示掩码，留空保存表示保持原值。</p>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="openEdit">编辑配置</el-button>
        <el-button text :loading="loading" @click="load">刷新</el-button>
      </div>
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

      <section class="panel rerank-panel">
        <div class="panel-head">
          <div>
            <div class="eyebrow">RERANK</div>
            <h2>{{ config.rerank.model }}</h2>
          </div>
          <span class="pill" :class="{ ok: config.rerank.configured }">{{ config.rerank.configured ? '已启用' : '未启用' }}</span>
        </div>
        <div class="rerank-grid">
          <div class="kv"><span>开关</span><b>{{ config.rerank.enabled ? '开启' : '关闭' }}</b></div>
          <div class="kv"><span>Base URL</span><b>{{ config.rerank.baseUrl }}</b></div>
          <div class="kv"><span>模型</span><b>{{ config.rerank.model }}</b></div>
          <div class="kv"><span>API Key</span><b>{{ keyState(config.rerank.configured, config.rerank.apiKeyMasked) }}</b></div>
        </div>
        <el-button class="test-btn" :loading="testing === 'RERANK'" @click="test('RERANK')">测试 Rerank 连接</el-button>
      </section>

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
        配置来源：{{ config.source === 'DATABASE' ? '数据库覆盖值' : '服务端环境变量' }}，数据库优先。
        保存后 user-api 最多 5 秒生效；Embedding provider 由启动配置决定，切换 dev/http 需要重启。
        修改 Embedding 模型或维度后，已有切片需要重新索引。默认配额和全站预算可在「系统设置」中修改。
      </p>
    </template>

    <el-dialog v-model="editVisible" title="编辑模型配置" width="760px" class="model-edit-dialog">
      <el-form label-width="130px">
        <div class="edit-scroll">
        <div class="edit-section">Chat</div>
        <div class="edit-grid">
          <el-form-item label="Base URL">
            <el-input v-model="edit.chat.baseUrl" placeholder="https://api.deepseek.com/v1" />
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="edit.chat.model" placeholder="deepseek-chat" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="edit.chat.apiKey" placeholder="留空保持现有 key" show-password />
          </el-form-item>
          <el-form-item label="max tokens">
            <el-input-number v-model="edit.chat.maxTokens" :min="64" :max="8192" :step="128" style="width: 100%" />
          </el-form-item>
          <el-form-item label="temperature">
            <el-input-number v-model="edit.chat.temperature" :min="0" :max="2" :step="0.05" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="输入单价 / 1M">
            <el-input-number v-model="edit.chat.inputPrice" :min="0" :step="0.1" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="输出单价 / 1M">
            <el-input-number v-model="edit.chat.outputPrice" :min="0" :step="0.1" :precision="4" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="edit-section">Embedding</div>
        <div class="edit-grid">
          <el-form-item label="Provider">
            <el-input :model-value="config?.embedding.provider || '-'" disabled />
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="edit.embedding.baseUrl" placeholder="https://api.siliconflow.cn/v1" />
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="edit.embedding.model" placeholder="BAAI/bge-m3" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="edit.embedding.apiKey" placeholder="留空保持现有 key" show-password />
          </el-form-item>
          <el-form-item label="维度">
            <el-input-number v-model="edit.embedding.dimension" :min="8" :max="4096" :step="128" style="width: 100%" />
          </el-form-item>
          <el-form-item label="batch size">
            <el-input-number v-model="edit.embedding.batchSize" :min="1" :max="64" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="edit-section">Rerank</div>
        <div class="edit-grid">
          <el-form-item label="启用">
            <el-switch v-model="edit.rerank.enabled" />
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="edit.rerank.baseUrl" placeholder="https://api.siliconflow.cn/v1" />
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="edit.rerank.model" placeholder="BAAI/bge-reranker-v2-m3" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="edit.rerank.apiKey" placeholder="留空保持现有 key" show-password />
          </el-form-item>
        </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
.rerank-panel {
  margin-bottom: 16px;
}
.rerank-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 22px;
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
.edit-scroll {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 8px;
}
.edit-section {
  font-weight: 800;
  font-size: 13px;
  margin: 4px 0 10px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
.edit-section:first-child {
  border-top: 0;
  padding-top: 0;
}
.edit-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
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
  .budget-grid,
  .rerank-grid,
  .edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
