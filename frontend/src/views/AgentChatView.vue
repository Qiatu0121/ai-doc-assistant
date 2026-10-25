<script setup>
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentToolCalls from '@/components/AgentToolCalls.vue'
import { chatWithAgent } from '@/api/agent'
import { copyText } from '@/utils/format'

const messages = ref([])
const input = ref('')
const sending = ref(false)
const composerFocused = ref(false)
const messageListRef = ref()
const composerRef = ref()

let messageSeq = 0

const suggestions = [
  {
    icon: 'Search',
    tone: 'violet',
    title: '检索已上传文档',
    desc: 'Spring AI 如何把文档写入向量库？',
    prompt: 'Spring AI 如何把文档写入向量库？',
  },
  {
    icon: 'Warning',
    tone: 'amber',
    title: '分析错误日志',
    desc: '定位异常堆栈与 Caused by 链',
    prompt:
      '请分析这段日志：2026-10-25 10:00:00 ERROR java.lang.NullPointerException at com.example.Service.run(Service.java:10) Caused by: java.net.ConnectException: Connection refused',
  },
  {
    icon: 'ChatDotRound',
    tone: 'blue',
    title: '了解 Agent 能力',
    desc: '看看当前可用的工具与适用场景',
    prompt: '你好，介绍一下你可以使用的工具和适用场景。',
  },
]

function createMessage(role, content, extra = {}) {
  messageSeq += 1
  return {
    id: `msg-${Date.now()}-${messageSeq}`,
    role,
    content,
    ...extra,
  }
}

async function scrollToBottom() {
  await nextTick()
  const container = messageListRef.value
  if (container) {
    container.scrollTop = container.scrollHeight
  }
}

async function sendMessage() {
  if (sending.value) return
  const payload = input.value.trim()
  if (!payload) {
    ElMessage.warning('请输入要询问的内容')
    composerRef.value?.focus()
    return
  }

  messages.value.push(createMessage('user', payload))
  input.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    const data = await chatWithAgent(payload)
    const answer = data?.answer?.trim() || 'Agent 未返回文本内容。'
    messages.value.push(createMessage('assistant', answer, { data }))
  } catch (error) {
    messages.value.push(
      createMessage('assistant', error.message || '请求失败，请稍后重试。', { error: true }),
    )
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function useSuggestion(suggestion) {
  input.value = suggestion.prompt
  composerRef.value?.focus()
}

function clearConversation() {
  if (sending.value) return
  messages.value = []
  input.value = ''
  composerRef.value?.focus()
}

async function copyAnswer(message) {
  try {
    await copyText(message.content)
    ElMessage.success('回答已复制')
  } catch (error) {
    ElMessage.error(error.message || '复制失败')
  }
}

function shortRequestId(requestId) {
  if (!requestId) return ''
  return requestId.length > 12 ? `${requestId.slice(0, 12)}…` : requestId
}
</script>

<template>
  <div class="agent-page">
    <section class="agent-hero">
      <div class="hero-copy">
        <span class="hero-kicker"><i></i>TOOL-USING AGENT</span>
        <h2>让模型自己决定检索什么、分析什么</h2>
        <p>工具选择与参数由模型完成，后端负责校验、日志与调用次数上限。</p>
      </div>

      <div class="hero-tools">
        <div class="hero-tool">
          <span><el-icon><Search /></el-icon></span>
          <div>
            <strong>searchKnowledgeBase</strong>
            <small>检索已上传文档</small>
          </div>
        </div>
        <div class="hero-tool">
          <span><el-icon><Document /></el-icon></span>
          <div>
            <strong>analyzeErrorLog</strong>
            <small>分析异常堆栈</small>
          </div>
        </div>
      </div>
    </section>

    <section class="chat-panel">
      <header class="chat-head">
        <div class="assistant-identity">
          <span class="assistant-avatar">AI</span>
          <div>
            <strong>AI 文档助手</strong>
            <span><i class="online-dot"></i>Agent 在线 · 单轮会话</span>
          </div>
        </div>
        <div class="chat-actions">
          <span class="session-pill">
            <el-icon><Timer /></el-icon>
            请求独立
          </span>
          <el-button text :disabled="!messages.length || sending" @click="clearConversation">
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
        </div>
      </header>

      <div ref="messageListRef" class="message-list">
        <div v-if="!messages.length" class="welcome">
          <div class="welcome-orbit">
            <span class="orbit-ring"></span>
            <span class="welcome-mark"><el-icon :size="25"><ChatLineRound /></el-icon></span>
          </div>
          <span class="welcome-kicker">READY WHEN YOU ARE</span>
          <h3>今天想查点什么？</h3>
          <p>Agent 会自行判断是否需要调用知识库检索或错误日志分析工具。</p>

          <div class="suggestion-grid">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion.title"
              type="button"
              class="suggestion-card"
              :class="`tone-${suggestion.tone}`"
              @click="useSuggestion(suggestion)"
            >
              <span class="suggestion-icon">
                <el-icon><component :is="suggestion.icon" /></el-icon>
              </span>
              <span class="suggestion-copy">
                <strong>{{ suggestion.title }}</strong>
                <small>{{ suggestion.desc }}</small>
              </span>
              <el-icon class="suggestion-arrow"><ArrowRight /></el-icon>
            </button>
          </div>

          <div class="privacy-note">
            <el-icon><InfoFilled /></el-icon>
            <span>
              V1 不持久化会话；日志内容会发送至已配置的模型服务，请先脱敏密钥与个人信息。
            </span>
          </div>
        </div>

        <template v-for="message in messages" :key="message.id">
          <div v-if="message.role === 'user'" class="message-row user-row">
            <div class="message-stack">
              <div class="user-bubble">{{ message.content }}</div>
            </div>
            <span class="user-avatar">你</span>
          </div>

          <div v-else class="message-row assistant-row">
            <span class="bot-avatar">AI</span>
            <div class="message-stack">
              <div class="assistant-bubble" :class="{ 'is-error': message.error }">
                <div class="answer-text">{{ message.content }}</div>

                <template v-if="message.data">
                  <div class="response-meta">
                    <span v-if="message.data.requestId" :title="message.data.requestId">
                      ID {{ shortRequestId(message.data.requestId) }}
                    </span>
                    <span>
                      <el-icon><Tools /></el-icon>
                      {{ message.data.toolCallCount || 0 }} /
                      {{ message.data.maxToolCalls || 0 }} 次工具
                    </span>
                    <button type="button" @click="copyAnswer(message)">
                      <el-icon><CopyDocument /></el-icon>
                      复制回答
                    </button>
                  </div>

                  <el-alert
                    v-if="message.data.limitExceeded"
                    class="limit-alert"
                    type="warning"
                    title="已达到工具调用上限"
                    description="后端已中止本轮 Agent 循环，请缩小问题范围后重试。"
                    :closable="false"
                    show-icon
                  />

                  <AgentToolCalls :calls="message.data.toolCalls || []" />
                </template>

                <div v-if="message.error" class="error-hint">
                  请确认后端服务、模型配置与网络连接后重试。
                </div>
              </div>
            </div>
          </div>
        </template>

        <div v-if="sending" class="message-row assistant-row">
          <span class="bot-avatar">AI</span>
          <div class="message-stack">
            <div class="assistant-bubble typing-bubble">
              <span class="typing-dots"><i></i><i></i><i></i></span>
              <em>Agent 正在思考与调用工具…</em>
            </div>
          </div>
        </div>
      </div>

      <footer class="composer-area">
        <div class="composer-box" :class="{ focused: composerFocused }">
          <el-input
            ref="composerRef"
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="4000"
            resize="none"
            :disabled="sending"
            placeholder="询问文档内容，或粘贴一段错误日志…"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            @keydown.enter.ctrl.prevent="sendMessage"
            @keydown.enter.meta.prevent="sendMessage"
          />

          <div class="composer-footer">
            <div class="composer-hints">
              <span class="shortcut"><kbd>Ctrl</kbd> + <kbd>Enter</kbd></span>
              <span class="char-count" :class="{ near: input.length > 3800 }">
                {{ input.length }} / 4000
              </span>
            </div>
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!input.trim() || sending"
              @click="sendMessage"
            >
              发送
              <el-icon class="el-icon--right"><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
        <p class="composer-note">
          工具由模型自主选择；单次请求最多执行后端配置的工具调用次数。
        </p>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.agent-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.agent-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  overflow: hidden;
  padding: 22px 24px;
  color: #fff;
  background:
    radial-gradient(circle at 84% -20%, rgba(129, 140, 248, 0.4), transparent 38%),
    linear-gradient(115deg, #111a2f 0%, #0f172a 52%, #1e1b4b 100%);
  border-radius: 19px;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.16);
}

.agent-hero::after {
  position: absolute;
  top: -70px;
  right: 18%;
  width: 160px;
  height: 160px;
  border: 1px solid rgba(165, 180, 252, 0.18);
  border-radius: 50%;
  content: '';
}

.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 560px;
}

.hero-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 9px;
  font-weight: 700;
  color: #a5b4fc;
  letter-spacing: 0.16em;
}

.hero-kicker i {
  width: 5px;
  height: 5px;
  background: #818cf8;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(129, 140, 248, 0.14);
}

.hero-copy h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  color: #f8fafc;
  letter-spacing: -0.025em;
}

.hero-copy p {
  margin: 7px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: #94a3b8;
}

.hero-tools {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 0 0 auto;
  gap: 9px;
}

.hero-tool {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 180px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.055);
  border: 1px solid rgba(148, 163, 184, 0.17);
  border-radius: 12px;
  backdrop-filter: blur(4px);
}

.hero-tool > span {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  color: #a5b4fc;
  background: rgba(99, 102, 241, 0.16);
  border-radius: 8px;
  place-items: center;
}

.hero-tool div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hero-tool strong {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  font-weight: 500;
  color: #e2e8f0;
}

.hero-tool small {
  font-size: 9px;
  color: #7f8ea8;
}

.chat-panel {
  display: flex;
  height: clamp(580px, calc(100vh - 330px), 860px);
  min-height: 580px;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8ecf4;
  border-radius: 19px;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.045);
}

.chat-head {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 15px 20px;
  background: rgba(248, 250, 252, 0.82);
  border-bottom: 1px solid #eef2f7;
}

.assistant-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.assistant-avatar,
.bot-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 11px;
  box-shadow: 0 7px 16px rgba(99, 102, 241, 0.22);
  place-items: center;
}

.assistant-identity > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.assistant-identity strong {
  font-size: 13px;
  font-weight: 650;
  color: #1e293b;
}

.assistant-identity span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  color: #94a3b8;
}

.online-dot {
  width: 5px;
  height: 5px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.11);
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}

.session-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 9px;
  font-size: 9px;
  color: #64748b;
  background: #fff;
  border: 1px solid #e8edf4;
  border-radius: 999px;
}

.chat-actions :deep(.el-button) {
  font-size: 11px;
  color: #7b869b;
}

.message-list {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  overflow-y: auto;
  background:
    radial-gradient(circle at 10% 0%, rgba(99, 102, 241, 0.035), transparent 30%),
    linear-gradient(180deg, #fcfdff 0%, #fff 100%);
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  flex-direction: column;
  max-width: 760px;
  margin: auto;
  padding: 14px 0 8px;
  text-align: center;
}

.welcome-orbit {
  position: relative;
  display: grid;
  width: 74px;
  height: 74px;
  margin-bottom: 15px;
  place-items: center;
}

.orbit-ring {
  position: absolute;
  inset: 0;
  background: conic-gradient(from 40deg, transparent, rgba(99, 102, 241, 0.34), transparent 58%);
  border-radius: 50%;
  animation: orbit 7s linear infinite;
}

.orbit-ring::after {
  position: absolute;
  inset: 4px;
  background: #fcfdff;
  border-radius: 50%;
  content: '';
}

.welcome-mark {
  position: relative;
  z-index: 1;
  display: grid;
  width: 56px;
  height: 56px;
  color: #4f46e5;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 17px;
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.12);
  place-items: center;
}

@keyframes orbit {
  to {
    transform: rotate(360deg);
  }
}

.welcome-kicker {
  font-size: 8px;
  font-weight: 700;
  color: #a5b1c5;
  letter-spacing: 0.18em;
}

.welcome h3 {
  margin: 8px 0 0;
  font-size: 21px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.03em;
}

.welcome > p {
  margin: 7px 0 0;
  font-size: 11px;
  color: #94a3b8;
}

.suggestion-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
  margin-top: 22px;
}

.suggestion-card {
  --tone: #4f46e5;
  --soft: #eef2ff;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px;
  text-align: left;
  background: #fff;
  border: 1px solid #e9edf5;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.suggestion-card.tone-amber {
  --tone: #b45309;
  --soft: #fffbeb;
}

.suggestion-card.tone-blue {
  --tone: #0369a1;
  --soft: #f0f9ff;
}

.suggestion-card:hover {
  border-color: #cfd6f8;
  box-shadow: 0 9px 22px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}

.suggestion-icon {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  color: var(--tone);
  background: var(--soft);
  border-radius: 8px;
  place-items: center;
}

.suggestion-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.suggestion-copy strong {
  font-size: 11px;
  font-weight: 600;
  color: #334155;
}

.suggestion-copy small {
  overflow: hidden;
  font-size: 9px;
  color: #9aa4b5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggestion-arrow {
  flex: 0 0 auto;
  color: #cbd5e1;
  font-size: 12px;
}

.privacy-note {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 6px;
  max-width: 620px;
  margin-top: 18px;
  font-size: 9px;
  line-height: 1.6;
  color: #9aa4b5;
}

.privacy-note .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.user-row {
  justify-content: flex-end;
}

.message-stack {
  display: flex;
  min-width: 0;
  max-width: min(86%, 800px);
  flex-direction: column;
}

.user-bubble {
  padding: 11px 15px;
  font-size: 13px;
  line-height: 1.65;
  color: #fff;
  white-space: pre-wrap;
  word-break: break-word;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  border-radius: 15px 15px 4px;
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.14);
}

.user-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  background: #eef2f7;
  border: 1px solid #e5eaf2;
  border-radius: 9px;
  place-items: center;
}

.bot-avatar {
  width: 30px;
  height: 30px;
  font-size: 9px;
  border-radius: 9px;
  box-shadow: 0 5px 12px rgba(99, 102, 241, 0.18);
}

.assistant-bubble {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e9edf5;
  border-radius: 4px 15px 15px;
  box-shadow: 0 7px 22px rgba(15, 23, 42, 0.045);
}

.assistant-bubble.is-error {
  background: #fffafa;
  border-color: #fecaca;
}

.answer-text {
  font-size: 13px;
  line-height: 1.75;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.is-error .answer-text {
  color: #b91c1c;
}

.response-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f1f4f8;
}

.response-meta > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  color: #9aa4b5;
}

.response-meta button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 0;
  font-size: 9px;
  color: #7b869b;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.response-meta button:hover {
  color: #4f46e5;
}

.limit-alert {
  margin-top: 10px;
  border-radius: 9px;
}

.limit-alert :deep(.el-alert__title) {
  font-size: 11px;
}

.limit-alert :deep(.el-alert__description) {
  margin-top: 3px;
  font-size: 10px;
}

.error-hint {
  margin-top: 7px;
  font-size: 10px;
  color: #ef4444;
}

.typing-bubble {
  display: flex;
  align-items: center;
  gap: 9px;
}

.typing-bubble em {
  font-size: 10px;
  font-style: normal;
  color: #94a3b8;
}

.typing-dots {
  display: flex;
  gap: 3px;
}

.typing-dots i {
  width: 5px;
  height: 5px;
  background: #818cf8;
  border-radius: 50%;
  animation: typing 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.16s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes typing {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.composer-area {
  flex: 0 0 auto;
  padding: 14px 18px 12px;
  background: #fff;
  border-top: 1px solid #eef2f7;
}

.composer-box {
  padding: 5px 5px 4px;
  background: #fbfcfe;
  border: 1px solid #e3e9f2;
  border-radius: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.composer-box.focused {
  background: #fff;
  border-color: #b9b4f4;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.07);
}

.composer-box :deep(.el-textarea__inner) {
  padding: 10px 11px 7px;
  font-size: 13px;
  line-height: 1.65;
  color: #1e293b;
  background: transparent;
  border: 0;
  border-radius: 10px;
  box-shadow: none;
}

.composer-box :deep(.el-textarea__inner::placeholder) {
  color: #a5afc0;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 7px 2px 11px;
}

.composer-hints {
  display: flex;
  align-items: center;
  gap: 10px;
}

.shortcut {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 9px;
  color: #adb6c6;
}

.shortcut kbd {
  padding: 1px 4px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 8px;
  color: #7b869b;
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 4px;
}

.char-count {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  color: #b4bdcc;
}

.char-count.near {
  color: #dc2626;
}

.composer-footer :deep(.el-button) {
  padding: 8px 15px;
  font-size: 11px;
}

.composer-note {
  margin: 7px 2px 0;
  font-size: 9px;
  color: #adb6c6;
  text-align: center;
}

@media (max-width: 1120px) {
  .agent-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .hero-tools {
    width: 100%;
  }

  .hero-tool {
    flex: 1;
  }

  .suggestion-grid {
    grid-template-columns: 1fr;
  }

  .suggestion-copy small {
    white-space: normal;
  }
}

@media (max-width: 700px) {
  .agent-hero {
    padding: 18px;
  }

  .hero-copy h2 {
    font-size: 17px;
  }

  .hero-tools {
    flex-direction: column;
  }

  .hero-tool {
    min-width: 0;
  }

  .chat-panel {
    height: 640px;
    min-height: 0;
  }

  .chat-head {
    padding: 13px 14px;
  }

  .session-pill {
    display: none;
  }

  .message-list {
    gap: 16px;
    padding: 16px 13px;
  }

  .message-stack {
    max-width: calc(100% - 40px);
  }

  .welcome {
    padding: 0;
  }

  .welcome h3 {
    font-size: 19px;
  }

  .composer-area {
    padding: 11px 12px 9px;
  }

  .shortcut {
    display: none;
  }

  .composer-note {
    text-align: left;
  }
}
</style>
