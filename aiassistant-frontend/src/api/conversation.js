import request from './request'

export function getConversationList(lastId, size = 20) {
  return request.get('/api/conversation/list', {
    params: { lastId, size }
  })
}

export function deleteConversation(id) {
  return request.delete(`/api/conversation/${id}`)
}

export function renameConversation(id, title) {
  return request.put(`/api/conversation/${id}/title`, { title })
}

export function getMessages(conversationId, lastMsgId, size = 50) {
  return request.get(`/api/conversation/${conversationId}/messages`, {
    params: { lastMsgId, size }
  })
}
