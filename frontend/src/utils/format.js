export const MAX_FILE_SIZE = 10 * 1024 * 1024
export const ALLOWED_FILE_EXTENSIONS = ['pdf', 'md', 'markdown', 'txt', 'docx']

export function formatFileSize(bytes) {
  if (!Number.isFinite(Number(bytes)) || Number(bytes) <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = Number(bytes)
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  const precision = unitIndex === 0 || size >= 100 ? 0 : 1
  return `${size.toFixed(precision)} ${units[unitIndex]}`
}

export function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

export function getStatusMeta(status) {
  const meta = {
    READY: { label: '已就绪', key: 'ready' },
    PROCESSING: { label: '处理中', key: 'processing' },
    FAILED: { label: '处理失败', key: 'failed' },
  }
  return meta[status] || { label: status || '未知', key: 'unknown' }
}

export function getFileExtension(fileName = '') {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase()
}

export async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }

  // 非 HTTPS 环境下的剪贴板降级方案
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) throw new Error('复制失败，请手动选择文本')
}
