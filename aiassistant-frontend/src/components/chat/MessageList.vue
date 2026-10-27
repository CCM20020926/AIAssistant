<template>
  <div class="message-list" ref="listRef">
    <MessageItem v-for="msg in chatStore.messages" :key="msg.id" :message="msg" />

    <div v-if="chatStore.streaming" class="message-item assistant">
      <div class="msg-avatar">AI</div>
      <div class="msg-body">
        <ThinkingBlock v-if="chatStore.thinkingContent" :content="chatStore.thinkingContent" />
        <div class="msg-content streaming" v-html="renderedStreaming"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useChatStore } from '@/store'
import { renderMarkdown } from '@/utils/markdown'
import MessageItem from './MessageItem.vue'
import ThinkingBlock from './ThinkingBlock.vue'

const chatStore = useChatStore()
const listRef = ref(null)

const renderedStreaming = computed(() => {
  return renderMarkdown(chatStore.streamingContent)
})

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

watch(() => chatStore.messages.length, scrollToBottom)
watch(() => chatStore.streamingContent, scrollToBottom)
watch(() => chatStore.thinkingContent, scrollToBottom)
</script>

<style scoped>
.message-list {
  height: 100%;
  overflow-y: auto;
  padding: 0 20px;
  max-width: 800px;
  margin: 0 auto;
}
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.message-item.assistant .msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.msg-body {
  flex: 1;
  min-width: 0;
}
.msg-content {
  line-height: 1.7;
  font-size: 15px;
  color: #333;
  word-break: break-word;
}
.msg-content.streaming {
  border-right: 2px solid #667eea;
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% { border-color: transparent; }
}
</style>
