import http from './http'

export function fetchDocuments() {
  return http.get('/documents')
}

export function uploadDocument(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/documents', formData, {
    timeout: 180_000,
    onUploadProgress,
  })
}

export function fetchDocumentChunks(id) {
  return http.get(`/documents/${id}/chunks`)
}

export function deleteDocument(id) {
  return http.delete(`/documents/${id}`)
}
