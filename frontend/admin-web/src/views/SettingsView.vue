<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import type {
  AdminSettings,
  AdminSettingsUpdatePayload,
  ApiResult,
} from '../api/types'

const config = ref<AdminSettings | null>(null)
const form = ref<AdminSettingsUpdatePayload>(defaults())
const loading = ref(false)
const saving = ref(false)

function defaults(): AdminSettingsUpdatePayload {
  return {
    registrationEnabled: true,
    defaultMonthlyTokens: 200000,
    defaultMonthlyCost: 5,
    globalMonthlyTokens: 2000000,
    globalMonthlyCost: 20,
    alertThreshold: 0.8,
    chatRateLimitPerMinute: 10,
    dailyTokenLimit: 0,
  }
}

function fillForm(settings: AdminSettings) {
  form.value = {
    registrationEnabled: settings.registrationEnabled,
    defaultMonthlyTokens: settings.defaultMonthlyTokens,
    defaultMonthlyCost: Number(settings.defaultMonthlyCost),
    globalMonthlyTokens: settings.globalMonthlyTokens,
    globalMonthlyCost: Number(settings.globalMonthlyCost),
    alertThreshold: settings.alertThreshold,
    chatRateLimitPerMinute: settings.chatRateLimitPerMinute,
    dailyTokenLimit: settings.dailyTokenLimit,
  }
}

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get<ApiResult<AdminSettings>>('/settings')
    config.value = data.data
    fillForm(data.data)
  } catch {
    ElMessage.error('读取系统设置失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  if (config.value) {
    fillForm(config.value)
  }
}

async function save() {
  saving.value = true
  try {
    const { data } = await api.put<ApiResult<AdminSettings>>('/settings', form.value)
    config.value = data.data
    fillForm(data.data)
    ElMessage.success('系统设置已保存，user-api 最多 5 秒生效')
  } catch {
    ElMessage.error('保存失败，请检查参数范围')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>系统设置</h1>
        <p>注册开关、默认配额、全局预算和限流。保存后 user-api 最多 5 秒生效。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </div>

    <div v-if="!config" class="empty">正在读取系统设置…</div>
    <section v-else class="panel">
      <div class="source-line">
        配置来源：{{ config.source === 'DATABASE' ? '数据库覆盖值' : '服务端环境变量' }}
        <span>· 全站预算 {{ formatCost(config.globalMonthlyCost) }}</span>
      </div>

      <el-form label-width="180px">
        <div class="section-title">注册</div>
        <div class="form-grid">
          <el-form-item label="开放新用户注册">
            <el-switch v-model="form.registrationEnabled" />
          </el-form-item>
        </div>
        <p class="hint">关闭后只影响新注册；已登录用户不受影响。注册仍受 IP 限流保护。</p>

        <div class="section-title">默认新用户配额</div>
        <div class="form-grid">
          <el-form-item label="月度 tokens">
            <el-input-number
              v-model="form.defaultMonthlyTokens"
              :min="0"
              :step="10000"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="月度成本（CNY）">
            <el-input-number
              v-model="form.defaultMonthlyCost"
              :min="0"
              :step="1"
              :precision="4"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <p class="hint">只影响之后注册的新用户；已有用户配额请在「用户与配额」调整。</p>

        <div class="section-title">全站月度预算</div>
        <div class="form-grid">
          <el-form-item label="全站月度 tokens">
            <el-input-number
              v-model="form.globalMonthlyTokens"
              :min="0"
              :step="100000"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="全站月度成本（CNY）">
            <el-input-number
              v-model="form.globalMonthlyCost"
              :min="0"
              :step="5"
              :precision="4"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="告警阈值">
            <el-input-number
              v-model="form.alertThreshold"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <p class="hint">全站预算超限后所有用户的 AI 调用都会被拒绝；告警阈值当前仅保存展示。</p>

        <div class="section-title">限流与每日上限</div>
        <div class="form-grid">
          <el-form-item label="问答限流（次/分钟）">
            <el-input-number
              v-model="form.chatRateLimitPerMinute"
              :min="1"
              :max="1000"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="每日 token 上限">
            <el-input-number
              v-model="form.dailyTokenLimit"
              :min="0"
              :step="10000"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <p class="hint">每日上限按 UTC 日期统计；填 0 表示不限制。</p>

        <div class="actions">
          <el-button @click="reset">恢复当前值</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 20px 22px;
}
.source-line {
  color: var(--faint);
  font-size: 10.5px;
  margin-bottom: 16px;
}
.section-title {
  font-weight: 800;
  font-size: 13px;
  margin: 18px 0 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.section-title:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
.hint {
  margin: -2px 0 4px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.7;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
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
@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
