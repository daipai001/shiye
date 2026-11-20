<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAdminAuthStore } from '../stores/auth'
import { formatUtcTime } from '../utils/time'
import type { ApiResult, EvalCase, EvalRun, PageResult } from '../api/types'

const auth = useAdminAuthStore()
const loading = ref(false)
const cases = ref<EvalCase[]>([])
const runs = ref<EvalRun[]>([])
const caseDialog = ref(false)
const runDialog = ref(false)
const detailVisible = ref(false)
const detail = ref<EvalRun | null>(null)
const saving = ref(false)

const caseForm = ref({
  userId: 1,
  question: '',
  expectedChunkIds: '',
  expectedArticleIds: '',
  goldAnswer: '',
})

const runForm = ref({
  userId: 1,
  name: '',
  topK: 5,
})

async function load() {
  loading.value = true
  try {
    const [caseResponse, runResponse] = await Promise.all([
      api.get<ApiResult<PageResult<EvalCase>>>('/eval/cases?page=1&pageSize=50'),
      api.get<ApiResult<PageResult<EvalRun>>>('/eval/runs?page=1&pageSize=50'),
    ])
    cases.value = caseResponse.data.data.items
    runs.value = runResponse.data.data.items
  } catch {
    ElMessage.error('读取评测数据失败')
  } finally {
    loading.value = false
  }
}

function parseIds(value: string): number[] {
  return value
    .split(/[,，\s]+/)
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0)
}

async function createCase() {
  if (!caseForm.value.question.trim()) {
    ElMessage.warning('请填写问题')
    return
  }
  saving.value = true
  try {
    await api.post('/eval/cases', {
      userId: caseForm.value.userId,
      question: caseForm.value.question.trim(),
      expectedChunkIds: parseIds(caseForm.value.expectedChunkIds),
      expectedArticleIds: parseIds(caseForm.value.expectedArticleIds),
      goldAnswer: caseForm.value.goldAnswer.trim() || null,
      tags: [],
      status: 'ACTIVE',
    })
    caseDialog.value = false
    caseForm.value.question = ''
    caseForm.value.expectedChunkIds = ''
    caseForm.value.expectedArticleIds = ''
    caseForm.value.goldAnswer = ''
    ElMessage.success('评测用例已创建')
    await load()
  } catch {
    ElMessage.error('创建评测用例失败')
  } finally {
    saving.value = false
  }
}

async function createRun() {
  if (!runForm.value.name.trim()) {
    ElMessage.warning('请填写运行名称')
    return
  }
  saving.value = true
  try {
    await api.post('/eval/runs', {
      userId: runForm.value.userId,
      name: runForm.value.name.trim(),
      topK: runForm.value.topK,
      rerank: true,
    })
    runDialog.value = false
    runForm.value.name = ''
    ElMessage.success('评测运行已创建，user-api 会异步执行')
    await load()
  } catch {
    ElMessage.error('创建评测运行失败')
  } finally {
    saving.value = false
  }
}

async function openRun(run: EvalRun) {
  try {
    const { data } = await api.get<ApiResult<EvalRun>>(`/eval/runs/${run.id}`)
    detail.value = data.data
    detailVisible.value = true
  } catch {
    ElMessage.error('读取评测结果失败')
  }
}

function metricText(run: EvalRun) {
  if (!run.metrics) return '-'
  try {
    return JSON.stringify(run.metrics)
  } catch {
    return '-'
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>RAG 评测</h1>
        <p>维护真实问题用例，异步计算 recall@5 和检索延迟。</p>
      </div>
      <div class="head-actions">
        <template v-if="auth.canWrite">
          <el-button @click="caseDialog = true">新建用例</el-button>
          <el-button type="primary" @click="runDialog = true">新建运行</el-button>
        </template>
        <el-button text :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">CASES</div>
          <h2>评测用例</h2>
        </div>
        <span class="note">共 {{ cases.length }} 条</span>
      </div>
      <el-table :data="cases" size="small" empty-text="暂无用例">
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="question" label="问题" min-width="260" show-overflow-tooltip />
        <el-table-column label="期望切片" width="100">
          <template #default="{ row }">{{ row.expectedChunkIds.length }}</template>
        </el-table-column>
        <el-table-column label="期望资料" width="100">
          <template #default="{ row }">{{ row.expectedArticleIds.length }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatUtcTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="eyebrow">RUNS</div>
          <h2>评测运行</h2>
        </div>
        <span class="note">异步执行，刷新查看结果</span>
      </div>
      <el-table :data="runs" size="small" empty-text="暂无运行">
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="topK" label="topK" width="80" />
        <el-table-column label="召回率" width="110">
          <template #default="{ row }">
            {{ row.metrics?.avgRecallAt5 != null ? Number(row.metrics.avgRecallAt5).toFixed(3) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="指标" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ metricText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button text size="small" @click="openRun(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="caseDialog" title="新建评测用例" width="620px">
      <el-form label-width="120px">
        <el-form-item label="用户 ID">
          <el-input-number v-model="caseForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="问题">
          <el-input v-model="caseForm.question" type="textarea" :rows="3" maxlength="4000" />
        </el-form-item>
        <el-form-item label="期望切片 ID">
          <el-input v-model="caseForm.expectedChunkIds" placeholder="逗号分隔，可留空" />
        </el-form-item>
        <el-form-item label="期望资料 ID">
          <el-input v-model="caseForm.expectedArticleIds" placeholder="逗号分隔，可留空" />
        </el-form-item>
        <el-form-item label="标准答案">
          <el-input v-model="caseForm.goldAnswer" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createCase">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runDialog" title="新建评测运行" width="480px">
      <el-form label-width="100px">
        <el-form-item label="用户 ID">
          <el-input-number v-model="runForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="runForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="topK">
          <el-input-number v-model="runForm.topK" :min="1" :max="20" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createRun">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="评测结果" width="860px">
      <template v-if="detail">
        <div class="detail-meta">
          <span>#{{ detail.id }} {{ detail.name }}</span>
          <span>{{ detail.status }}</span>
          <span>用户 {{ detail.userId }}</span>
          <span>topK {{ detail.topK }}</span>
        </div>
        <pre class="metrics">{{ JSON.stringify(detail.metrics || {}, null, 2) }}</pre>
        <el-table :data="detail.results || []" size="small" empty-text="暂无结果">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="eval-detail">
                <div>
                  <b>模型回答</b>
                  <pre>{{ row.answerText || '未生成回答' }}</pre>
                </div>
                <div>
                  <b>判定说明</b>
                  <pre>{{ row.judgeReason || '-' }}</pre>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="caseId" label="用例" width="80" />
          <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
          <el-table-column label="recall@5" width="100">
            <template #default="{ row }">
              {{ row.recallAt5 != null ? Number(row.recallAt5).toFixed(3) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="引用准确率" width="110">
            <template #default="{ row }">
              {{ row.citationAccuracy != null ? Number(row.citationAccuracy).toFixed(3) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="答案正确率" width="110">
            <template #default="{ row }">
              {{ row.answerCorrectness != null ? Number(row.answerCorrectness).toFixed(3) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.latencyMs != null ? `${row.latencyMs} ms` : '-' }}</template>
          </el-table-column>
          <el-table-column label="成本" width="110">
            <template #default="{ row }">
              {{ row.cost != null ? `¥${Number(row.cost).toFixed(6)}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="召回切片" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.retrievedChunkIds || []).join(', ') }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
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
  padding: 18px 20px;
  margin-bottom: 16px;
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
  font-size: 18px;
  letter-spacing: -0.02em;
}
.note {
  color: var(--faint);
  font-size: 10px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  color: var(--muted);
  font-size: 11px;
  margin-bottom: 10px;
}
.metrics {
  margin: 0 0 12px;
  padding: 10px;
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 10.5px;
  line-height: 1.6;
  max-height: 180px;
  overflow: auto;
}
.eval-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  padding: 8px 12px;
}
.eval-detail b {
  color: var(--ink);
  font-size: 10.5px;
}
.eval-detail pre {
  margin: 6px 0 0;
  padding: 8px;
  background: #fbfbf9;
  border: 1px solid var(--line);
  border-radius: 7px;
  font-size: 10px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow: auto;
}
@media (max-width: 900px) {
  .eval-detail {
    grid-template-columns: 1fr;
  }
}
</style>
