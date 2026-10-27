<template>
  <div class="message-item" :class="message.role">
    <div v-if="message.role === 'assistant'" class="msg-avatar">AI</div>
    <div class="msg-body">
      <ThinkingBlock v-if="message.thinking" :content="message.thinking" />
      <div class="msg-content" :class="{ error: message.isError }" v-html="rendered"></div>
      <div class="msg-time">{{ formatTime(message.createdAt) }}</div>
    </div>
    <div v-if="message.role === 'user'" class="msg-avatar user-avatar">我</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import ThinkingBlock from './ThinkingBlock.vue'

const props = defineProps({
  message: { type: Object, required: true }
})

const rendered = computed(() => {
  if (props.message.role === 'user') {
    return props.message.content.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
  return renderMarkdown(props.message.content)
})

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.message-item.user {
  flex-direction: row-reverse;
}
.msg-avatar {
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
.user-avatar {
  background: linear-gradient(135deg, #43e97b, #38f9d7);
  color: #1a1a2e;
}
.msg-body {
  max-width: 70%;
  min-width: 0;
}
.message-item.user .msg-body {
  text-align: right;
}
.msg-content {
  display: inline-block;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.7;
  font-size: 15px;
  text-align: left;
  word-break: break-word;
}
.message-item.user .msg-content {
  background: #667eea;
  color: #fff;
  border-top-right-radius: 4px;
}
.message-item.assistant .msg-content {
  background: #fff;
  color: #333;
  border-top-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.msg-content.error {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #cf1322;
}
.msg-content :deep(pre) {
  background: #f6f8fa;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  margin: 8px 0;
}
.msg-content :deep(code) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
}
.msg-time {
  font-size: 12px;
  color: #bbb;
  margin-top: 4px;
}
</style>
