import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  createKnowledgeBase as apiCreate,
  getKnowledgeBaseList as apiList,
  deleteKnowledgeBase as apiDelete,
  uploadDocument as apiUpload,
  getDocumentList as apiDocList,
  deleteDocument as apiDocDelete,
  getDocumentStatus as apiDocStatus
} from '@/api/knowledge'
import { getConversationsByKnowledgeBase } from '@/api/conversation'
import { createAuthedEventSource } from '@/utils/sse'

const NOTIFICATION_URL = '/api/document/notifications'

export const useKnowledgeStore = defineStore('knowledge', {
  state: () => ({
    list: [],
    currentId: null,
    documents: [],
    conversations: [],
    loading: false,
    pollingTimer: null,
    eventSource: null
  }),

  actions: {
    async fetchList() {
      this.loading = true
      try {
        const res = await apiList()
        this.list = res.data || []
      } finally {
        this.loading = false
      }
    },

    async create(name, description) {
      const res = await apiCreate(name, description)
      await this.fetchList()
      return res.data
    },

    async remove(id) {
      await apiDelete(id)
      this.list = this.list.filter(k => k.id !== id)
      if (this.currentId === id) {
        this.currentId = null
        this.documents = []
        this.conversations = []
        this.stopPolling()
      }
    },

    setCurrent(id) {
      this.currentId = id
    },

    async fetchDocuments(knowledgeBaseId) {
      const res = await apiDocList(knowledgeBaseId)
      this.documents = res.data || []
    },

    async fetchConversations(knowledgeBaseId) {
      const res = await getConversationsByKnowledgeBase(knowledgeBaseId)
      this.conversations = res.data || []
    },

    async upload(file, knowledgeBaseId) {
      const res = await apiUpload(file, knowledgeBaseId)
      await this.fetchDocuments(knowledgeBaseId)
      this.startPolling(knowledgeBaseId)
      return res.data
    },

    async removeDocument(id) {
      await apiDocDelete(id)
      this.documents = this.documents.filter(d => d.id !== id)
    },

    startPolling(knowledgeBaseId) {
      this.stopPolling()
      this.pollingTimer = setInterval(async () => {
        await this.fetchDocuments(knowledgeBaseId)
        const allDone = this.documents.every(
          d => d.parseStatus === 2 || d.parseStatus === 3
        )
        if (allDone) this.stopPolling()
      }, 3000)
    },

    stopPolling() {
      if (this.pollingTimer) {
        clearInterval(this.pollingTimer)
        this.pollingTimer = null
      }
    },

    // Fix #10: resume polling after a page refresh when documents are still parsing
    resumePollingIfNeeded(knowledgeBaseId) {
      const inProgress = this.documents.some(
        d => d.parseStatus === 0 || d.parseStatus === 1
      )
      if (inProgress) {
        this.startPolling(knowledgeBaseId)
      }
    },

    // Fix #6: subscribe to gateway SSE parse notifications
    connectNotifications() {
      if (this.eventSource) return
      this.eventSource = createAuthedEventSource(NOTIFICATION_URL, {
        onMessage: ({ event, data }) => {
          if (event !== 'document-parse-result' || !data) return
          this.applyParseResult(data)
        },
        onError: () => {
          // Connection dropped; drop the handle so it can be re-established later.
          this.eventSource = null
        }
      })
    },

    disconnectNotifications() {
      if (this.eventSource) {
        this.eventSource.close()
        this.eventSource = null
      }
    },

    applyParseResult(data) {
      const success = data.status === 'SUCCESS'
      const doc = this.documents.find(d => d.id === data.documentId)
      if (doc) {
        doc.parseStatus = success ? 2 : 3
        doc.parseError = data.errorMessage || null
        if (data.chunkCount != null) doc.chunkCount = data.chunkCount
      }
      if (success) {
        ElMessage.success(`文档解析完成（${data.chunkCount || 0} 块）`)
        if (this.currentId) this.fetchList()
      } else {
        ElMessage.error(`文档解析失败：${data.errorMessage || '未知错误'}`)
      }
      const allDone = this.documents.every(
        d => d.parseStatus === 2 || d.parseStatus === 3
      )
      if (allDone) this.stopPolling()
    }
  }
})
