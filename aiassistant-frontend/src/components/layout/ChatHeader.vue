<template>
  <div class="chat-header">
    <div class="header-title">
      {{ currentTitle }}
    </div>
    <div class="header-switches">
      <el-switch v-model="options.deepThink" active-text="深度思考" size="small" />
      <el-switch v-model="options.webSearch" active-text="联网搜索" size="small" />
      <el-switch v-model="options.pptMode" active-text="PPT" size="small" :disabled="true" />
      <el-switch v-model="options.knowledgeMode" active-text="知识库" size="small" :disabled="true" />
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useConversationStore } from '@/store'

const convStore = useConversationStore()

const options = reactive({
  deepThink: false,
  webSearch: false,
  pptMode: false,
  knowledgeMode: false
})

const currentTitle = computed(() => {
  if (!convStore.currentId) return '新对话'
  const conv = convStore.list.find(c => c.id === convStore.currentId)
  return conv?.title || '对话'
})

defineExpose({ options })
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
.header-switches {
  display: flex;
  gap: 16px;
  align-items: center;
}
</style>
