import { defineStore } from 'pinia'
import { chatStream } from '@/utils/sse'
import { useConversationStore } from './conversation'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    streaming: false,
    streamingContent: '',
    thinkingContent: '',
    abortController: null
  }),

  actions: {
    loadMessages(conversationId) {
      // messages are loaded from backend when switching conversations
      // for now, clear local state
      this.messages = []
      this.streamingContent = ''
      this.thinkingContent = ''
      this.streaming = false
    },

    async sendMessage(content, options = {}) {
      if (this.streaming || !content.trim()) return

      const convStore = useConversationStore()

      this.streaming = true
      this.streamingContent = ''
      this.thinkingContent = ''

      this.messages.push({
        id: Date.now(),
        role: 'user',
        content: content.trim(),
        createdAt: new Date().toISOString()
      })

      const params = {
        conversationId: convStore.currentId,
        content: content.trim(),
        deepThink: options.deepThink || false,
        webSearch: options.webSearch || false,
        pptMode: options.pptMode || false,
        knowledgeMode: options.knowledgeMode || false,
        knowledgeBaseId: options.knowledgeBaseId || null
      }

      this.abortController = chatStream(params, {
        onThinking: (data) => {
          this.thinkingContent += (data.content || '')
        },
        onAnswer: (data) => {
          this.streamingContent += (data.content || '')
        },
        onDone: (data) => {
          if (this.streamingContent) {
            this.messages.push({
              id: Date.now() + 1,
              role: 'assistant',
              content: this.streamingContent,
              thinking: this.thinkingContent || undefined,
              createdAt: new Date().toISOString()
            })
          }

          if (data.conversationId && !convStore.currentId) {
            convStore.currentId = data.conversationId
            convStore.prependConversation({
              id: data.conversationId,
              title: content.trim().substring(0, 30),
              mode: 'chat',
              updatedAt: new Date().toISOString()
            })
          } else if (data.conversationId) {
            convStore.updateTitle(data.conversationId, content.trim().substring(0, 30))
          }

          this.streaming = false
          this.streamingContent = ''
          this.thinkingContent = ''
          this.abortController = null
        },
        onError: (data) => {
          this.messages.push({
            id: Date.now() + 1,
            role: 'assistant',
            content: data.content || 'AI 服务暂时不可用，请稍后重试',
            isError: true,
            createdAt: new Date().toISOString()
          })
          this.streaming = false
          this.streamingContent = ''
          this.thinkingContent = ''
          this.abortController = null
        }
      })
    },

    stopStreaming() {
      this.abortController?.abort()
      if (this.streamingContent) {
        this.messages.push({
          id: Date.now() + 1,
          role: 'assistant',
          content: this.streamingContent + '\n\n*[已停止生成]*',
          createdAt: new Date().toISOString()
        })
      }
      this.streaming = false
      this.streamingContent = ''
      this.thinkingContent = ''
      this.abortController = null
    }
  }
})
