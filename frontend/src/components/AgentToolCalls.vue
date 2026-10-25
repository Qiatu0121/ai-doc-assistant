<script setup>
import { ref } from 'vue'

defineProps({
  calls: {
    type: Array,
    default: () => [],
  },
})

const TOOL_LABELS = {
  searchKnowledgeBase: '知识库检索',
  analyzeErrorLog: '错误日志分析',
}

const openCalls = ref([])

function toggleCall(index) {
  openCalls.value = openCalls.value.includes(index)
    ? openCalls.value.filter((item) => item !== index)
    : [...openCalls.value, index]
}

function isOpen(index) {
  return openCalls.value.includes(index)
}

function toolLabel(toolName) {
  return TOOL_LABELS[toolName] || toolName || '未知工具'
}

function formatJson(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}
</script>

<template>
  <div v-if="calls.length" class="tool-calls">
    <div class="tool-calls-heading">
      <span>
        <el-icon><Connection /></el-icon>
        工具调用记录
      </span>
      <strong>{{ calls.length }} 次</strong>
    </div>

    <div class="tool-list">
      <article
        v-for="call in calls"
        :key="`${call.callIndex}-${call.toolName}`"
        class="tool-item"
        :class="{ failed: !call.success }"
      >
        <button
          type="button"
          class="tool-summary"
          :aria-expanded="isOpen(call.callIndex)"
          @click="toggleCall(call.callIndex)"
        >
          <el-icon class="tool-status" :size="15">
            <CircleCheckFilled v-if="call.success" />
            <CircleCloseFilled v-else />
          </el-icon>
          <span class="tool-name">
            <strong>{{ toolLabel(call.toolName) }}</strong>
            <code>{{ call.toolName }}</code>
          </span>
          <span class="tool-duration">{{ call.durationMs }} ms</span>
          <el-icon class="tool-chevron" :class="{ open: isOpen(call.callIndex) }" :size="14">
            <ArrowDown />
          </el-icon>
        </button>

        <div v-if="isOpen(call.callIndex)" class="tool-detail">
          <div class="tool-field">
            <span>参数</span>
            <pre>{{ formatJson(call.arguments) }}</pre>
          </div>
          <div v-if="call.error" class="tool-field error-field">
            <span>错误</span>
            <pre>{{ call.error }}</pre>
          </div>
          <div v-else class="tool-field">
            <span>结果摘要</span>
            <pre>{{ call.resultPreview || '—' }}</pre>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.tool-calls {
  margin-top: 14px;
  padding-top: 13px;
  border-top: 1px solid #eff2f7;
}

.tool-calls-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.tool-calls-heading > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  font-weight: 600;
  color: #7b869b;
  letter-spacing: 0.03em;
}

.tool-calls-heading > strong {
  padding: 2px 7px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  font-weight: 500;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #edf1f6;
  border-radius: 999px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-item {
  overflow: hidden;
  background: #fbfcfe;
  border: 1px solid #edf1f7;
  border-radius: 10px;
}

.tool-item.failed {
  background: #fffafa;
  border-color: #fee2e2;
}

.tool-summary {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 11px;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.tool-status {
  flex: 0 0 auto;
  color: #10b981;
}

.failed .tool-status {
  color: #ef4444;
}

.tool-name {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: baseline;
  gap: 7px;
}

.tool-name strong {
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 600;
  color: #334155;
}

.tool-name code {
  overflow: hidden;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-duration {
  flex: 0 0 auto;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  color: #8b95a8;
}

.tool-chevron {
  flex: 0 0 auto;
  color: #94a3b8;
  transition: transform 0.2s ease;
}

.tool-chevron.open {
  transform: rotate(180deg);
}

.tool-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 11px 11px;
}

.tool-field {
  overflow: hidden;
  background: #fff;
  border: 1px solid #eff2f7;
  border-radius: 8px;
}

.tool-field > span {
  display: block;
  padding: 5px 9px;
  font-size: 9px;
  color: #94a3b8;
  background: #f8fafc;
  border-bottom: 1px solid #f1f4f8;
}

.tool-field pre {
  max-height: 160px;
  margin: 0;
  padding: 9px;
  overflow: auto;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
  line-height: 1.65;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
}

.error-field {
  border-color: #fee2e2;
}

.error-field > span {
  color: #dc2626;
  background: #fef2f2;
  border-bottom-color: #fee2e2;
}

.error-field pre {
  color: #b91c1c;
}

.tool-summary:focus-visible {
  outline: 2px solid #a5b4fc;
  outline-offset: -2px;
}
</style>
