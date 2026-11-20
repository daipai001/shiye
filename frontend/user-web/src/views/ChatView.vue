<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { streamChat } from '../api/chat'
import type {
  ApiResult,
  ArticleSummary,
  BookmarkFolder,
  ChatCitation,
  ChatMessage,
  Conversation,
  PageResult,
  Quota,
  Tag,
} from '../api/types'

const conversations = ref<Conversation[]>([])
const currentId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const streaming = ref(false)
const quota = ref<Quota | null>(null)
const messagesBox = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null
const activeRequestId = ref<string | null>(null)
const scopeType = ref<'ALL' | 'ARTICLE' | 'FOLDER' | 'TAG' | 'GENERAL'>('ALL')
const scopeId = ref('')
const folders = ref<BookmarkFolder[]>([])
const tags = ref<Tag[]>([])
const articles = ref<ArticleSummary[]>([])
const articleLoading = ref(false)

const currentTitle = computed(() => {
  const current = conversations.value.find((item) => item.id === currentId.value)
  return current?.title || '新对话'
})

const tokenPercent = computed(() => {
  if (!quota.value || quota.value.tokenQuota <= 0) return 0
  return Math.min(100, Math.round((quota.value.tokensUsed / quota.value.tokenQuota) * 100))
})

const costPercent = computed(() => {
  if (!quota.value || quota.value.costQuota <= 0) return 0
  return Math.min(100, Math.round((quota.value.costUsed / quota.value.costQuota) * 100))
})

function formatCost(value?: number | null) {
  return `¥${Number(value || 0).toFixed(4)}`
}

async function loadQuota() {
  try {
    const { data } = await api.get<ApiResult<Quota>>('/usage/quota')
    quota.value = data.data
  } catch {
    quota.value = null
  }
}

async function loadConversations() {
  loadingConversations.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<Conversation>>>(
      '/conversations?page=1&pageSize=30',
    )
    conversations.value = data.data.items
  } catch {
    conversations.value = []
  } finally {
    loadingConversations.value = false
  }
}

async function loadScopes() {
  try {
    const [folderResponse, tagResponse] = await Promise.all([
      api.get<ApiResult<{ items: BookmarkFolder[] }>>('/bookmark-folders'),
      api.get<ApiResult<{ items: Tag[] }>>('/tags'),
    ])
    folders.value = folderResponse.data.data.items
    tags.value = tagResponse.data.data.items
  } catch {
    folders.value = []
    tags.value = []
  }
  await searchArticles('')
}

async function searchArticles(query: string) {
  articleLoading.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<ArticleSummary>>>('/articles', {
      params: {
        page: 1,
        pageSize: 50,
        status: 'READY',
        q: query.trim() || undefined,
      },
    })
    articles.value = data.data.items
  } catch {
    articles.value = []
  } finally {
    articleLoading.value = false
  }
}

async function ensureArticleOption(articleId: string) {
  if (!articleId || articles.value.some((article) => article.id === articleId)) {
    return
  }
  try {
    const { data } = await api.get<ApiResult<ArticleSummary>>(`/articles/${articleId}`)
    articles.value = [data.data, ...articles.value]
  } catch {
    // 资料被删除时保留空选择，由用户重新选择
  }
}

async function selectConversation(conversation: Conversation) {
  if (streaming.value) return
  currentId.value = conversation.id
  const type = conversation.scopeType as 'ALL' | 'ARTICLE' | 'FOLDER' | 'TAG' | 'GENERAL'
  scopeType.value = ['ALL', 'ARTICLE', 'FOLDER', 'TAG', 'GENERAL'].includes(type) ? type : 'ALL'
  scopeId.value = conversation.scopeId || ''
  if (scopeType.value === 'ARTICLE') {
    await ensureArticleOption(scopeId.value)
  }
  loadingMessages.value = true
  try {
    const { data } = await api.get<ApiResult<PageResult<ChatMessage>>>(
      `/conversations/${conversation.id}/messages`,
      { params: { page: 1, pageSize: 100, order: 'asc' } },
    )
    messages.value = data.data.items
    await scrollToBottom()
  } catch {
    ElMessage.error('读取会话消息失败')
  } finally {
    loadingMessages.value = false
  }
}

function onScopeChange() {
  scopeId.value = ''
  currentId.value = null
  messages.value = []
}

function newConversation() {
  if (streaming.value) {
    abortController?.abort()
  }
  currentId.value = null
  messages.value = []
  draft.value = ''
}

async function deleteConversation(conversation: Conversation) {
  try {
    await ElMessageBox.confirm(`删除会话「${conversation.title}」？`, '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await api.delete(`/conversations/${conversation.id}`)
    if (currentId.value === conversation.id) {
      newConversation()
    }
    await loadConversations()
  } catch {
    ElMessage.error('删除会话失败')
  }
}

async function scrollToBottom() {
  await nextTick()
  const box = messagesBox.value
  if (box) {
    box.scrollTop = box.scrollHeight
  }
}

function applySuggestion(text: string) {
  draft.value = text
  send()
}

async function send() {
  const question = draft.value.trim()
  if (!question || streaming.value) return
  if (question.length > 4000) {
    ElMessage.warning('问题最多 4000 字')
    return
  }
  if (scopeType.value !== 'ALL' && scopeType.value !== 'GENERAL' && !scopeId.value) {
    ElMessage.warning('请选择问答范围')
    return
  }

  draft.value = ''
  const userMessage: ChatMessage = {
    id: `local-user-${Date.now()}`,
    role: 'USER',
    content: question,
    status: 'SUCCESS',
    promptTokens: 0,
    completionTokens: 0,
    citations: [],
  }
  messages.value.push(userMessage, {
    id: `local-assistant-${Date.now()}`,
    role: 'ASSISTANT',
    content: '',
    status: 'SUCCESS',
    promptTokens: 0,
    completionTokens: 0,
    citations: [],
    streaming: true,
  })
  const assistant = messages.value[messages.value.length - 1]
  streaming.value = true
  abortController = new AbortController()
  await scrollToBottom()

  await streamChat(
    {
      conversationId: currentId.value,
      scope: {
        type: scopeType.value,
        id: scopeType.value === 'ALL' || scopeType.value === 'GENERAL' ? null : scopeId.value,
      },
      question,
    },
    {
      onMeta: (meta) => {
        activeRequestId.value = meta.requestId
        currentId.value = meta.conversationId
        assistant.id = meta.messageId
        void scrollToBottom()
      },
      onDelta: (text) => {
        assistant.content += text
        void scrollToBottom()
      },
      onCitation: (citation: ChatCitation) => {
        assistant.citations.push(citation)
        void scrollToBottom()
      },
      onUsage: (usage) => {
        assistant.promptTokens = usage.promptTokens
        assistant.completionTokens = usage.completionTokens
        if (quota.value) {
          quota.value.tokensUsed += usage.totalTokens
          quota.value.tokenRemaining = Math.max(
            0,
            quota.value.tokenQuota - quota.value.tokensUsed,
          )
          quota.value.costUsed += usage.cost
          quota.value.costRemaining = Math.max(0, quota.value.costQuota - quota.value.costUsed)
        }
      },
      onDone: () => {
        assistant.streaming = false
      },
      onError: (error) => {
        assistant.streaming = false
        assistant.status = 'FAILED'
        if (!assistant.content) {
          assistant.content = error.message || '回答失败，请稍后重试'
        } else {
          assistant.content += `\n\n[回答中断：${error.message}]`
        }
        ElMessage.error(error.message || '回答失败')
      },
    },
    abortController.signal,
  )

  assistant.streaming = false
  streaming.value = false
  activeRequestId.value = null
  abortController = null
  await Promise.all([loadConversations(), loadQuota()])
  await scrollToBottom()
}

async function stopStreaming() {
  const requestId = activeRequestId.value
  if (requestId) {
    try {
      await api.post(`/chat/${requestId}/cancel`)
    } catch {
      // 本地中断仍然生效
    }
  }
  abortController?.abort()
  activeRequestId.value = null
  streaming.value = false
}

async function submitFeedback(message: ChatMessage, rating: 'UP' | 'DOWN') {
  if (!message.id || message.id.startsWith('local-') || message.streaming) {
    return
  }
  try {
    await api.post(`/messages/${message.id}/feedback`, { rating })
    message.feedback = rating
    ElMessage.success('感谢反馈')
  } catch {
    ElMessage.error('提交反馈失败')
  }
}

onMounted(async () => {
  await Promise.all([loadConversations(), loadQuota(), loadScopes()])
})

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<template>
  <div class="page chat-page">
    <div class="page-head">
      <div>
        <h1>问答</h1>
        <p>基于你收藏的资料回答，并附带原文引用。</p>
      </div>
      <el-button type="primary" @click="newConversation">新对话</el-button>
    </div>

    <div class="chat-shell">
      <aside class="conversation-side">
        <div class="side-label">会话</div>
        <div v-if="loadingConversations" class="side-empty">加载中…</div>
        <div v-else-if="conversations.length === 0" class="side-empty">
          还没有会话，直接在右侧提问即可。
        </div>
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          type="button"
          class="conversation-item"
          :class="{ active: conversation.id === currentId }"
          @click="selectConversation(conversation)"
        >
          <span class="conv-title">{{ conversation.title }}</span>
          <span class="conv-last">{{ conversation.lastMessage || '暂无消息' }}</span>
          <span class="conv-delete" title="删除会话" @click.stop="deleteConversation(conversation)">
            ×
          </span>
        </button>
      </aside>

      <section class="chat-main">
        <header class="chat-top">
          <div class="chat-title">
            <strong>{{ currentTitle }}</strong>
            <span>全部资料 · DeepSeek</span>
          </div>
          <div v-if="quota" class="quota">
            <div class="quota-line">
              <span>Tokens</span>
              <b>{{ quota.tokensUsed.toLocaleString() }} / {{ quota.tokenQuota.toLocaleString() }}</b>
            </div>
            <div class="quota-bar"><i :style="{ width: `${tokenPercent}%` }" /></div>
            <div class="quota-line">
              <span>成本</span>
              <b>{{ formatCost(quota.costUsed) }} / {{ formatCost(quota.costQuota) }}</b>
            </div>
            <div class="quota-bar"><i :style="{ width: `${costPercent}%` }" /></div>
          </div>
        </header>

        <div class="scope-bar">
          <span class="scope-label">问答范围</span>
          <el-select
            v-model="scopeType"
            size="small"
            style="width: 120px"
            :disabled="streaming"
            @change="onScopeChange"
          >
            <el-option label="全部资料" value="ALL" />
            <el-option label="单篇资料" value="ARTICLE" />
            <el-option label="按文件夹" value="FOLDER" />
            <el-option label="按标签" value="TAG" />
            <el-option label="通用问答" value="GENERAL" />
          </el-select>
          <el-select
            v-if="scopeType === 'ARTICLE'"
            v-model="scopeId"
            size="small"
            filterable
            remote
            :remote-method="searchArticles"
            :loading="articleLoading"
            placeholder="搜索资料标题"
            style="width: 260px"
            :disabled="streaming"
          >
            <el-option
              v-for="article in articles"
              :key="article.id"
              :label="article.title"
              :value="article.id"
            />
          </el-select>
          <el-select
            v-if="scopeType === 'FOLDER'"
            v-model="scopeId"
            size="small"
            filterable
            placeholder="选择文件夹"
            style="width: 220px"
            :disabled="streaming"
          >
            <el-option
              v-for="folder in folders"
              :key="folder.id"
              :label="folder.folderPath"
              :value="folder.id"
            />
          </el-select>
          <el-select
            v-if="scopeType === 'TAG'"
            v-model="scopeId"
            size="small"
            filterable
            placeholder="选择标签"
            style="width: 180px"
            :disabled="streaming"
          >
            <el-option
              v-for="tag in tags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            />
          </el-select>
        </div>

        <div ref="messagesBox" class="messages">
          <div v-if="loadingMessages" class="chat-empty">正在加载消息…</div>
          <div v-else-if="messages.length === 0" class="chat-empty">
            <div class="empty-title">问点什么？</div>
            <p>回答只基于你资料库里的内容，并尽量给出引用。没有命中时会明确告诉你。</p>
            <div class="suggestions">
              <button type="button" @click="applySuggestion('我收藏的资料里，RAG 切片一般建议多大？')">
                RAG 切片一般建议多大？
              </button>
              <button type="button" @click="applySuggestion('帮我总结一下收藏里关于中文长文档切片的内容。')">
                总结中文长文档的切片建议
              </button>
              <button type="button" @click="applySuggestion('资料库里有哪些关于向量检索的内容？')">
                资料库里有哪些向量检索内容？
              </button>
            </div>
          </div>

          <div
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="message.role.toLowerCase()"
          >
            <div class="message-label">{{ message.role === 'USER' ? '你' : '拾页' }}</div>
            <div class="bubble">
              <div v-if="message.content" class="message-content">{{ message.content }}</div>
              <div v-else-if="message.streaming" class="message-content muted">正在思考…</div>
              <span v-if="message.streaming" class="cursor">▍</span>

              <div
                v-if="message.citations.length && !message.content.includes('没有找到相关内容')"
                class="citations"
              >
                <div v-for="citation in message.citations" :key="`${message.id}-${citation.rank}`" class="citation-card">
                  <div class="citation-head">
                    <span class="citation-rank">[{{ citation.rank }}]</span>
                    <span class="citation-title">{{ citation.title || '未命名资料' }}</span>
                    <span class="citation-score">{{ citation.score.toFixed(3) }}</span>
                  </div>
                  <p class="citation-quote">{{ citation.quote }}</p>
                  <div class="citation-foot">
                    字符 {{ citation.charStart }}–{{ citation.charEnd }}
                  </div>
                </div>
              </div>

              <div
                v-if="message.role === 'ASSISTANT' && !message.streaming && message.content && !message.id.startsWith('local-')"
                class="feedback"
              >
                <button
                  type="button"
                  :class="{ active: message.feedback === 'UP' }"
                  title="有帮助"
                  @click="submitFeedback(message, 'UP')"
                >
                  👍
                </button>
                <button
                  type="button"
                  :class="{ active: message.feedback === 'DOWN' }"
                  title="没帮助"
                  @click="submitFeedback(message, 'DOWN')"
                >
                  👎
                </button>
              </div>
            </div>
          </div>
        </div>

        <footer class="composer">
          <textarea
            v-model="draft"
            class="composer-input"
            rows="2"
            maxlength="4000"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            :disabled="streaming"
            @keydown.enter.exact.prevent="send"
          />
          <button
            v-if="streaming"
            type="button"
            class="stop-button"
            @click="stopStreaming"
          >
            停止
          </button>
          <button type="button" class="send-button" :disabled="streaming || !draft.trim()" @click="send">
            {{ streaming ? '回答中…' : '发送' }}
          </button>
          <p class="composer-hint">回答仅基于你的资料库，不保证覆盖所有内容；引用可回资料库查看原文。</p>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  max-width: 1180px;
}
.chat-shell {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 14px;
  height: calc(100vh - 190px);
  min-height: 560px;
}
.conversation-side,
.chat-main {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}
.conversation-side {
  padding: 12px 10px;
  overflow-y: auto;
}
.side-label {
  font-size: 9px;
  letter-spacing: 1.5px;
  font-weight: 800;
  color: var(--faint);
  padding: 2px 6px 8px;
}
.side-empty {
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.7;
  padding: 8px 6px;
}
.conversation-item {
  position: relative;
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 8px;
  padding: 8px 28px 8px 9px;
  margin-bottom: 2px;
  cursor: pointer;
  font-family: inherit;
}
.conversation-item:hover {
  background: #f7f6f2;
}
.conversation-item.active {
  background: #f1f0ec;
  border-color: var(--line);
}
.conv-title {
  display: block;
  font-size: 11.5px;
  color: var(--ink);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-last {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-delete {
  position: absolute;
  right: 6px;
  top: 7px;
  color: var(--faint);
  font-size: 13px;
  opacity: 0;
}
.conversation-item:hover .conv-delete {
  opacity: 1;
}
.chat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.scope-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--line);
  background: #fff;
}
.scope-label {
  color: var(--faint);
  font-size: 9.5px;
  letter-spacing: 0.5px;
}
.chat-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  background: #fbfbf9;
}
.chat-title strong {
  display: block;
  font-size: 13px;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-title span {
  display: block;
  color: var(--faint);
  font-size: 10px;
  margin-top: 2px;
}
.quota {
  width: 220px;
  flex: none;
}
.quota-line {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--faint);
  font-size: 9.5px;
  margin-bottom: 3px;
}
.quota-line b {
  color: var(--muted);
  font-weight: 600;
}
.quota-bar {
  height: 4px;
  background: #ecebe6;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 6px;
}
.quota-bar i {
  display: block;
  height: 100%;
  background: var(--ink);
  border-radius: 999px;
  transition: width 0.2s;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 18px 10px;
  background: #fff;
}
.chat-empty {
  max-width: 520px;
  margin: 12vh auto 0;
  text-align: center;
  color: var(--muted);
}
.empty-title {
  font-family: Georgia, 'Songti SC', serif;
  font-size: 24px;
  color: var(--ink);
  margin-bottom: 6px;
}
.chat-empty p {
  font-size: 11.5px;
  line-height: 1.8;
  margin: 0;
}
.suggestions {
  display: grid;
  gap: 7px;
  margin-top: 18px;
}
.suggestions button {
  border: 1px solid var(--line);
  background: #fbfbf9;
  color: var(--muted);
  border-radius: 8px;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  font-size: 11px;
}
.suggestions button:hover {
  border-color: #c9c6bf;
  color: var(--ink);
}
.message-row {
  max-width: 860px;
  margin: 0 auto 16px;
}
.message-label {
  color: var(--faint);
  font-size: 9.5px;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.message-row.user .message-label {
  text-align: right;
}
.bubble {
  display: inline-block;
  max-width: 100%;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fbfbf9;
  border: 1px solid var(--line);
}
.message-row.user .bubble {
  float: right;
  background: #f1f0ec;
  border-color: #e5e2dc;
}
.message-row.user::after {
  content: '';
  display: table;
  clear: both;
}
.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink);
  text-align: left;
}
.message-content.muted {
  color: var(--faint);
}
.cursor {
  color: var(--faint);
  font-size: 12px;
  animation: blink 0.9s steps(2, start) infinite;
}
@keyframes blink {
  to { visibility: hidden; }
}
.citations {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid var(--line);
}
.citation-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 7px;
  padding: 7px 9px;
}
.citation-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.citation-rank {
  color: var(--primary);
  font-size: 9.5px;
  font-weight: 700;
}
.citation-title {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--ink);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.citation-score {
  color: var(--faint);
  font-size: 9px;
}
.citation-quote {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 10.5px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.citation-foot {
  margin-top: 3px;
  color: var(--faint);
  font-size: 9px;
}
.feedback {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}
.feedback button {
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 6px;
  padding: 1px 7px;
  font-size: 11px;
  cursor: pointer;
  opacity: 0.55;
}
.feedback button.active {
  border-color: var(--primary);
  background: #f7f7fe;
  opacity: 1;
}
.stop-button {
  float: right;
  margin: 7px 6px 0 0;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: var(--ink);
  padding: 7px 14px;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}
.composer {
  border-top: 1px solid var(--line);
  padding: 10px 14px 8px;
  background: #fbfbf9;
}
.composer-input {
  width: 100%;
  resize: none;
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 9px 11px;
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--ink);
  background: #fff;
  outline: none;
}
.composer-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(91, 91, 214, 0.08);
}
.send-button {
  float: right;
  margin-top: 7px;
  border: 0;
  border-radius: 7px;
  background: var(--ink);
  color: #fff;
  padding: 7px 18px;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}
.send-button:disabled {
  opacity: 0.45;
  cursor: default;
}
.composer-hint {
  clear: both;
  margin: 6px 0 0;
  color: var(--faint);
  font-size: 9.5px;
}
@media (max-width: 900px) {
  .chat-shell {
    grid-template-columns: 1fr;
    height: auto;
  }
  .conversation-side {
    max-height: 160px;
  }
  .chat-main {
    min-height: 560px;
  }
  .quota {
    display: none;
  }
}
</style>
