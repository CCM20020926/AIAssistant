<template>
  <div class="chat-header">
    <div class="header-title">
      {{ currentTitle }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useConversationStore } from '@/store'

const convStore = useConversationStore()

const currentTitle = computed(() => {
  if (!convStore.currentId) return '新对话'
  const conv = convStore.list.find(c => c.id === convStore.currentId)
  return conv?.title || '对话'
})
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  min-height: 56px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}
</style>
