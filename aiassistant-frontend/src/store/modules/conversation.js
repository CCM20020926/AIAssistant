import { defineStore } from 'pinia'
import { getConversationList, deleteConversation as apiDelete } from '@/api/conversation'

export const useConversationStore = defineStore('conversation', {
  state: () => ({
    list: [],
    currentId: null,
    loading: false,
    hasMore: true
  }),

  actions: {
    async fetchList(reset = false) {
      if (this.loading) return
      this.loading = true

      try {
        const lastId = reset ? null : (this.list.length > 0 ? this.list[this.list.length - 1].id : null)
        const res = await getConversationList(lastId)

        if (reset) {
          this.list = res.data
        } else {
          this.list.push(...res.data)
        }

        this.hasMore = res.data.length >= 20
      } finally {
        this.loading = false
      }
    },

    setCurrent(id) {
      this.currentId = id
    },

    prependConversation(conv) {
      this.list.unshift(conv)
      this.currentId = conv.id
    },

    async removeConversation(id) {
      await apiDelete(id)
      this.list = this.list.filter(c => c.id !== id)
      if (this.currentId === id) {
        this.currentId = this.list.length > 0 ? this.list[0].id : null
      }
    },

    updateTitle(id, title) {
      const conv = this.list.find(c => c.id === id)
      if (conv) conv.title = title
    }
  }
})
