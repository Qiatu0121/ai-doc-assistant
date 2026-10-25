<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteDocument,
  fetchDocumentChunks,
  fetchDocuments,
  uploadDocument,
} from '@/api/documents'
import {
  ALLOWED_FILE_EXTENSIONS,
  MAX_FILE_SIZE,
  copyText,
  formatDateTime,
  formatFileSize,
  getFileExtension,
  getStatusMeta,
} from '@/utils/format'

const documents = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')

const uploadRef = ref()
const uploading = ref(false)
const uploadProgress = ref(0)

const drawerVisible = ref(false)
const activeDocument = ref(null)
const chunks = ref([])
const chunksLoading = ref(false)
const deletingId = ref(null)

const stats = computed(() => {
  const ready = documents.value.filter((document) => document.status === 'READY').length
  const failed = documents.value.filter((document) => document.status === 'FAILED').length
  const chunkTotal = documents.value.reduce(
    (total, document) => total + (Number(document.chunkCount) || 0),
    0,
  )

  return [
    {
      label: '文档总数',
      value: documents.value.length,
      hint: '知识库中的全部文档',
      icon: 'Files',
      tone: 'violet',
    },
    {
      label: '已就绪',
      value: ready,
      hint: '可被 Agent 检索',
      icon: 'CircleCheck',
      tone: 'green',
    },
    {
      label: '处理失败',
      value: failed,
      hint: '查看详情定位原因',
      icon: 'Warning',
      tone: 'red',
    },
    {
      label: '知识分块',
      value: chunkTotal.toLocaleString('zh-CN'),
      hint: '向量检索的最小单元',
      icon: 'Grid',
      tone: 'blue',
    },
  ]
})

const filteredDocuments = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return documents.value.filter((document) => {
    const matchesKeyword =
      !query ||
      document.name?.toLowerCase().includes(query) ||
      document.fileName?.toLowerCase().includes(query)
    const matchesStatus = !statusFilter.value || document.status === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = (await fetchDocuments()) || []
  } catch (error) {
    ElMessage.error(error.message || '加载文档列表失败')
  } finally {
    loading.value = false
  }
}

function validateFile(file) {
  const extension = getFileExtension(file.name)
  if (!ALLOWED_FILE_EXTENSIONS.includes(extension)) {
    return '仅支持 PDF、Markdown、TXT、DOCX 文件'
  }
  if (file.size > MAX_FILE_SIZE) {
    return '文件不能超过 10MB'
  }
  return ''
}

async function handleFileChange(uploadFile) {
  const file = uploadFile.raw
  if (!file || uploading.value) return

  const validationError = validateFile(file)
  if (validationError) {
    ElMessage.error(validationError)
    uploadRef.value?.clearFiles()
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  try {
    const created = await uploadDocument(file, (event) => {
      if (!event.total) return
      // 留出后端解析和向量化的时间，进度条最多显示到 95%
      uploadProgress.value = Math.min(95, Math.round((event.loaded / event.total) * 100))
    })
    uploadProgress.value = 100
    ElMessage.success(`「${created.name}」已索引 ${created.chunkCount} 个分块`)
  } catch (error) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    await loadDocuments()
    uploading.value = false
    uploadRef.value?.clearFiles()
    window.setTimeout(() => {
      uploadProgress.value = 0
    }, 600)
  }
}

async function openDetail(document) {
  activeDocument.value = document
  chunks.value = []
  drawerVisible.value = true
  chunksLoading.value = true
  try {
    const result = (await fetchDocumentChunks(document.id)) || []
    if (activeDocument.value?.id === document.id) {
      chunks.value = result
    }
  } catch (error) {
    ElMessage.error(error.message || '加载文档分块失败')
  } finally {
    chunksLoading.value = false
  }
}

async function handleDelete(document) {
  try {
    await ElMessageBox.confirm(
      `删除后文档、分块和向量数据都将无法恢复。确定删除「${document.name}」吗？`,
      '删除文档',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  deletingId.value = document.id
  try {
    await deleteDocument(document.id)
    if (activeDocument.value?.id === document.id) {
      drawerVisible.value = false
    }
    ElMessage.success('文档已删除')
    await loadDocuments()
  } catch (error) {
    ElMessage.error(error.message || '删除文档失败')
  } finally {
    deletingId.value = null
  }
}

async function copyChunk(chunk) {
  try {
    await copyText(chunk.content)
    ElMessage.success('分块内容已复制')
  } catch (error) {
    ElMessage.error(error.message || '复制失败')
  }
}

function chunkNumber(index) {
  return String((Number(index) || 0) + 1).padStart(2, '0')
}

onMounted(loadDocuments)
</script>

<template>
  <div class="documents-page">
    <section class="stats-grid" aria-label="文档概览">
      <article
        v-for="stat in stats"
        :key="stat.label"
        class="stat-card"
        :class="`tone-${stat.tone}`"
      >
        <span class="stat-icon">
          <el-icon :size="20"><component :is="stat.icon" /></el-icon>
        </span>
        <div class="stat-copy">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.hint }}</small>
        </div>
      </article>
    </section>

    <section class="panel upload-panel">
      <div class="panel-heading">
        <div>
          <div class="title-row">
            <span class="section-icon"><el-icon><UploadFilled /></el-icon></span>
            <h2>上传并索引文档</h2>
          </div>
          <p>上传后同步完成解析、分块与向量化，可直接用于 Agent 问答。</p>
        </div>
        <span class="format-pill">PDF · MD · TXT · DOCX</span>
      </div>

      <el-upload
        ref="uploadRef"
        class="uploader"
        drag
        accept=".pdf,.md,.markdown,.txt,.docx"
        :auto-upload="false"
        :show-file-list="false"
        :disabled="uploading"
        :on-change="handleFileChange"
      >
        <div class="upload-area">
          <span class="upload-mark" :class="{ busy: uploading }">
            <el-icon :size="24" :class="{ 'is-loading': uploading }">
              <component :is="uploading ? 'Loading' : 'Plus'" />
            </el-icon>
          </span>
          <div class="upload-copy">
            <strong>{{ uploading ? '正在处理文档…' : '拖拽文件到这里，或点击选择' }}</strong>
            <span>单个文件不超过 10 MB，上传后请等待索引完成</span>
          </div>
          <span class="upload-action">{{ uploading ? '处理中' : '选择文件' }}</span>
        </div>
      </el-upload>

      <div v-if="uploading" class="upload-progress">
        <el-progress
          :percentage="uploadProgress"
          :stroke-width="6"
          :show-text="false"
          :status="uploadProgress === 100 ? 'success' : ''"
        />
        <span>{{ uploadProgress >= 100 ? '正在写入向量库…' : `传输进度 ${uploadProgress}%` }}</span>
      </div>
    </section>

    <section class="panel list-panel">
      <div class="list-heading">
        <div>
          <div class="title-row">
            <span class="section-icon"><el-icon><Collection /></el-icon></span>
            <h2>文档列表</h2>
          </div>
          <p>按上传时间倒序排列，失败记录会保留以便排查。</p>
        </div>

        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="search-input"
            clearable
            placeholder="搜索文档名或文件名"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="statusFilter" class="status-select" placeholder="全部状态">
            <el-option label="全部状态" value="" />
            <el-option label="已就绪" value="READY" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="处理失败" value="FAILED" />
          </el-select>
          <el-button class="refresh-button" :loading="loading" @click="loadDocuments">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <div class="result-line">
        <span>共 {{ filteredDocuments.length }} 篇文档</span>
        <span v-if="keyword || statusFilter" class="filter-tip">已应用筛选</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredDocuments"
        row-key="id"
        class="document-table"
        element-loading-text="正在加载文档…"
      >
        <el-table-column label="文档" min-width="260">
          <template #default="{ row }">
            <div class="document-cell">
              <span class="file-badge" :class="`type-${row.fileType}`">
                {{ (row.fileType || 'file').toUpperCase() }}
              </span>
              <div class="document-copy">
                <strong :title="row.name">{{ row.name }}</strong>
                <span :title="row.fileName">{{ row.fileName }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="大小" width="110">
          <template #default="{ row }">
            <span class="muted">{{ formatFileSize(row.fileSize) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="125">
          <template #default="{ row }">
            <el-tooltip
              :disabled="row.status !== 'FAILED' || !row.errorMessage"
              :content="row.errorMessage || ''"
              placement="top"
              :show-after="250"
            >
              <span class="status-pill" :class="`status-${getStatusMeta(row.status).key}`">
                <i></i>
                {{ getStatusMeta(row.status).label }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="分块" width="90" align="center">
          <template #default="{ row }">
            <span class="chunk-count">{{ row.chunkCount ?? 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">
            <span class="muted">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="140" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看详情</el-button>
            <el-button
              link
              type="danger"
              :loading="deletingId === row.id"
              @click.stop="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            <span><el-icon :size="28"><DocumentAdd /></el-icon></span>
            <strong>还没有匹配的文档</strong>
            <p>上传第一篇文档，或调整搜索与筛选条件。</p>
          </div>
        </template>
      </el-table>
    </section>

    <el-drawer
      v-model="drawerVisible"
      class="chunk-drawer"
      size="min(760px, 94vw)"
      :with-header="false"
      destroy-on-close
    >
      <div v-if="activeDocument" class="drawer-content">
        <header class="drawer-header">
          <div class="drawer-title">
            <span class="file-badge large" :class="`type-${activeDocument.fileType}`">
              {{ (activeDocument.fileType || 'file').toUpperCase() }}
            </span>
            <div>
              <span class="drawer-kicker">DOCUMENT DETAILS</span>
              <h3>{{ activeDocument.name }}</h3>
              <p>{{ activeDocument.fileName }}</p>
            </div>
          </div>
          <el-button circle text aria-label="关闭详情" @click="drawerVisible = false">
            <el-icon :size="19"><Close /></el-icon>
          </el-button>
        </header>

        <div class="drawer-meta">
          <div>
            <span>状态</span>
            <strong class="status-pill" :class="`status-${getStatusMeta(activeDocument.status).key}`">
              <i></i>
              {{ getStatusMeta(activeDocument.status).label }}
            </strong>
          </div>
          <div>
            <span>文件大小</span>
            <strong>{{ formatFileSize(activeDocument.fileSize) }}</strong>
          </div>
          <div>
            <span>分块数量</span>
            <strong>{{ activeDocument.chunkCount ?? 0 }}</strong>
          </div>
          <div>
            <span>上传时间</span>
            <strong>{{ formatDateTime(activeDocument.createTime) }}</strong>
          </div>
        </div>

        <el-alert
          v-if="activeDocument.status === 'FAILED'"
          class="failure-alert"
          type="error"
          title="文档处理失败"
          :description="activeDocument.errorMessage || '后端未返回详细错误信息'"
          :closable="false"
          show-icon
        />

        <div class="chunk-heading">
          <div>
            <h4>文本分块</h4>
            <p>按索引顺序展示，Agent 检索时会匹配这些内容。</p>
          </div>
          <el-tag effect="plain" round>{{ chunks.length }} 块</el-tag>
        </div>

        <div v-loading="chunksLoading" class="chunk-list">
          <article v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
            <header>
              <span>CHUNK {{ chunkNumber(chunk.chunkIndex) }}</span>
              <el-button link type="primary" @click="copyChunk(chunk)">
                <el-icon><CopyDocument /></el-icon>
                复制
              </el-button>
            </header>
            <pre>{{ chunk.content }}</pre>
          </article>

          <div v-if="!chunksLoading && !chunks.length" class="chunk-empty">
            <span><el-icon :size="26"><Files /></el-icon></span>
            <strong>暂无分块内容</strong>
            <p>处理失败的文档不会保留分块数据。</p>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.documents-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  --tone: #4f46e5;
  --soft: #eef2ff;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 19px 20px;
  background: #fff;
  border: 1px solid #e8ecf4;
  border-radius: 17px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.035);
}

.tone-green {
  --tone: #059669;
  --soft: #ecfdf5;
}

.tone-red {
  --tone: #dc2626;
  --soft: #fef2f2;
}

.tone-blue {
  --tone: #0284c7;
  --soft: #f0f9ff;
}

.stat-icon {
  display: grid;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  color: var(--tone);
  background: var(--soft);
  border-radius: 12px;
  place-items: center;
}

.stat-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.stat-copy > span {
  font-size: 12px;
  color: #64748b;
}

.stat-copy strong {
  margin-top: 2px;
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.04em;
  line-height: 1.2;
}

.stat-copy small {
  margin-top: 4px;
  overflow: hidden;
  font-size: 11px;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel {
  background: #fff;
  border: 1px solid #e8ecf4;
  border-radius: 19px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.035);
}

.upload-panel {
  padding: 22px 24px 24px;
}

.panel-heading,
.list-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 9px;
}

.section-icon {
  display: grid;
  width: 28px;
  height: 28px;
  color: #4f46e5;
  background: #eef2ff;
  border-radius: 9px;
  place-items: center;
}

.panel-heading h2,
.list-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #111827;
  letter-spacing: -0.015em;
}

.panel-heading p,
.list-heading p {
  margin: 7px 0 0 37px;
  font-size: 12px;
  color: #8a94a8;
}

.format-pill {
  flex: 0 0 auto;
  padding: 7px 11px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #edf0f6;
  border-radius: 999px;
}

.uploader {
  display: block;
  margin-top: 18px;
}

.uploader :deep(.el-upload) {
  width: 100%;
}

.uploader :deep(.el-upload-dragger) {
  width: 100%;
  padding: 0;
  background: #fbfcff;
  border: 1px dashed #cfd7e6;
  border-radius: 14px;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.uploader :deep(.el-upload-dragger:hover) {
  background: #f8f8ff;
  border-color: #7c74eb;
}

.uploader :deep(.el-upload.is-disabled .el-upload-dragger) {
  cursor: wait;
}

.upload-area {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 19px 21px;
  text-align: left;
}

.upload-mark {
  display: grid;
  flex: 0 0 auto;
  width: 45px;
  height: 45px;
  color: #4f46e5;
  background: #eef2ff;
  border: 1px solid #e3e3fb;
  border-radius: 13px;
  place-items: center;
}

.upload-mark.busy {
  color: #7c3aed;
  background: #f5f3ff;
}

.upload-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 5px;
}

.upload-copy strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-copy span {
  font-size: 11px;
  color: #94a3b8;
}

.upload-action {
  flex: 0 0 auto;
  padding: 7px 13px;
  font-size: 12px;
  font-weight: 550;
  color: #4f46e5;
  background: #fff;
  border: 1px solid #dfe1fb;
  border-radius: 9px;
}

.upload-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.upload-progress :deep(.el-progress) {
  flex: 1;
}

.upload-progress > span {
  flex: 0 0 auto;
  min-width: 118px;
  font-size: 11px;
  color: #8b95a9;
  text-align: right;
}

.list-panel {
  padding: 22px 24px 14px;
}

.list-heading {
  flex-wrap: wrap;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 9px;
}

.search-input {
  width: 220px;
}

.status-select {
  width: 120px;
}

.search-input :deep(.el-input__wrapper),
.status-select :deep(.el-select__wrapper) {
  background: #f8fafc;
  box-shadow: 0 0 0 1px #edf0f5 inset;
}

.search-input :deep(.el-input__wrapper.is-focus),
.status-select :deep(.el-select__wrapper.is-focused) {
  background: #fff;
}

.refresh-button {
  color: #475569;
  border-color: #e2e8f0;
}

.result-line {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 19px 0 9px;
  font-size: 11px;
  color: #94a3b8;
}

.filter-tip {
  padding: 3px 7px;
  color: #4f46e5;
  background: #f2f1ff;
  border-radius: 999px;
}

.document-table {
  --el-table-border-color: #f0f3f8;
  --el-table-header-bg-color: #f8fafc;
  --el-table-row-hover-bg-color: #fafbff;
  width: 100%;
}

.document-table :deep(.el-table__header th.el-table__cell) {
  height: 40px;
  padding: 0;
  font-size: 11px;
  font-weight: 600;
  color: #7b869b;
  background: #f8fafc;
  border-top: 1px solid #f0f3f8;
  border-bottom: 1px solid #f0f3f8;
}

.document-table :deep(.el-table__body td.el-table__cell) {
  padding: 12px 0;
  border-bottom-color: #f1f4f8;
}

.document-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.document-table :deep(.el-table-fixed-column--right) {
  background: #fff;
}

.document-table :deep(.el-table__row:hover .el-table-fixed-column--right) {
  background: #fafbff;
}

.document-cell {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding-right: 12px;
}

.file-badge {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  font-weight: 700;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e8edf4;
  border-radius: 10px;
  letter-spacing: 0.02em;
  place-items: center;
}

.file-badge.type-pdf {
  color: #dc2626;
  background: #fef2f2;
  border-color: #fee2e2;
}

.file-badge.type-docx,
.file-badge.type-doc {
  color: #2563eb;
  background: #eff6ff;
  border-color: #dbeafe;
}

.file-badge.type-md,
.file-badge.type-markdown {
  color: #4f46e5;
  background: #eef2ff;
  border-color: #e0e7ff;
}

.file-badge.large {
  width: 48px;
  height: 48px;
  font-size: 10px;
  border-radius: 12px;
}

.document-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.document-copy strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-copy span {
  overflow: hidden;
  font-size: 10px;
  color: #9aa4b5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  color: #7b869b;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.3;
  border-radius: 999px;
}

.status-pill i {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
}

.status-ready {
  color: #047857;
  background: #ecfdf5;
}

.status-processing {
  color: #b45309;
  background: #fffbeb;
}

.status-processing i {
  animation: pulse-dot 1.2s ease-in-out infinite;
}

.status-failed {
  color: #dc2626;
  background: #fef2f2;
}

.status-unknown {
  color: #64748b;
  background: #f1f5f9;
}

@keyframes pulse-dot {
  50% {
    opacity: 0.35;
  }
}

.chunk-count {
  display: inline-grid;
  min-width: 28px;
  height: 24px;
  padding: 0 8px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #eff2f7;
  border-radius: 7px;
  place-items: center;
}

.table-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 36px 0 32px;
}

.table-empty > span {
  display: grid;
  width: 54px;
  height: 54px;
  margin-bottom: 12px;
  color: #94a3b8;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 15px;
  place-items: center;
}

.table-empty strong {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.table-empty p {
  margin: 5px 0 0;
  font-size: 11px;
  color: #9aa4b5;
}

.drawer-content {
  padding: 4px 3px 12px;
}

.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f3f8;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 0;
}

.drawer-title > div {
  min-width: 0;
}

.drawer-kicker {
  display: block;
  margin-bottom: 3px;
  font-size: 9px;
  font-weight: 700;
  color: #9aa4b5;
  letter-spacing: 0.14em;
}

.drawer-title h3 {
  margin: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: 650;
  color: #111827;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-title p {
  margin: 4px 0 0;
  overflow: hidden;
  font-size: 11px;
  color: #94a3b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 18px 0;
}

.drawer-meta > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #f0f3f8;
  border-radius: 11px;
}

.drawer-meta > div > span {
  font-size: 10px;
  color: #94a3b8;
}

.drawer-meta strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 550;
  color: #475569;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-meta .status-pill {
  align-self: flex-start;
  padding: 2px 7px;
  font-size: 10px;
}

.failure-alert {
  margin-bottom: 18px;
  border-radius: 11px;
}

.failure-alert :deep(.el-alert__description) {
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 11px;
}

.chunk-heading h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
  color: #1e293b;
}

.chunk-heading p {
  margin: 4px 0 0;
  font-size: 10px;
  color: #94a3b8;
}

.chunk-list {
  display: flex;
  min-height: 100px;
  flex-direction: column;
  gap: 9px;
}

.chunk-card {
  overflow: hidden;
  background: #fbfcfe;
  border: 1px solid #edf1f7;
  border-radius: 12px;
}

.chunk-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 13px;
  background: #f8fafc;
  border-bottom: 1px solid #eff2f7;
}

.chunk-card header > span {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  font-weight: 600;
  color: #8b95a8;
  letter-spacing: 0.1em;
}

.chunk-card header :deep(.el-button) {
  height: auto;
  padding: 0;
  font-size: 10px;
}

.chunk-card pre {
  max-height: 280px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  font-family: 'SFMono-Regular', Consolas, 'Microsoft YaHei', monospace;
  font-size: 12px;
  line-height: 1.7;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 34px 0;
  color: #94a3b8;
}

.chunk-empty > span {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 10px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 14px;
  place-items: center;
}

.chunk-empty strong {
  font-size: 12px;
  color: #64748b;
}

.chunk-empty p {
  margin: 4px 0 0;
  font-size: 10px;
}

@media (max-width: 1180px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .list-heading {
    align-items: stretch;
  }

  .toolbar {
    width: 100%;
    margin-top: 15px;
  }

  .search-input {
    flex: 1;
    width: auto;
  }
}

@media (max-width: 700px) {
  .upload-panel,
  .list-panel {
    padding: 18px 16px;
  }

  .panel-heading {
    flex-direction: column;
    gap: 12px;
  }

  .format-pill {
    margin-left: 37px;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .search-input {
    flex: 1 1 100%;
  }

  .status-select {
    flex: 1;
    width: auto;
  }

  .upload-area {
    padding: 16px;
  }

  .upload-action {
    display: none;
  }

  .upload-progress {
    align-items: stretch;
    flex-direction: column;
    gap: 7px;
  }

  .upload-progress > span {
    text-align: left;
  }

  .drawer-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .stat-card {
    padding: 15px 14px;
  }

  .stat-icon {
    width: 36px;
    height: 36px;
  }

  .stat-copy strong {
    font-size: 22px;
  }

  .stat-copy small {
    display: none;
  }

  .panel-heading p,
  .list-heading p {
    margin-left: 0;
  }

  .section-icon {
    display: none;
  }

  .format-pill {
    margin-left: 0;
  }

  .drawer-title h3 {
    font-size: 16px;
  }

  .drawer-meta {
    grid-template-columns: 1fr;
  }
}
</style>
