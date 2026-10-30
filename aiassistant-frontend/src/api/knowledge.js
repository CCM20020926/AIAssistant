import request from './request'

export function createKnowledgeBase(name, description) {
  return request.post('/api/knowledge-base', { name, description })
}

export function getKnowledgeBaseList() {
  return request.get('/api/knowledge-base/list')
}

export function deleteKnowledgeBase(id) {
  return request.delete(`/api/knowledge-base/${id}`)
}

export function uploadDocument(file, knowledgeBaseId) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/document/upload?knowledgeBaseId=${knowledgeBaseId}`, formData)
}

export function getDocumentList(knowledgeBaseId) {
  return request.get('/api/document/list', { params: { knowledgeBaseId } })
}

export function deleteDocument(id) {
  return request.delete(`/api/document/${id}`)
}

export function getDocumentStatus(documentId) {
  return request.get(`/api/document/status/${documentId}`)
}
