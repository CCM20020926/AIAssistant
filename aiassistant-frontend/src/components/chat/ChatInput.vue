<template>
  <div class="chat-input-area">
    <div class="input-wrapper">
      <el-input
        v-model="inputText"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        placeholder="输入消息..."
        resize="none"
        @keydown.enter.exact.prevent="handleSend"
      />
      <el-button
        v-if="!chatStore.streaming"
        type="primary"
        circle
        :icon="Promotion"
        :disabled="!inputText.trim()"
        @click="handleSend"
        class="send-btn"
      />
      <el-button
        v-else
        type="danger"
        circle
        :icon="VideoPause"
        @click="chatStore.stopStreaming()"
        class="send-btn"
      />
    </div>
    <div class="input-footer">
      <span class="hint">Enter 发送，Shift+Enter 换行</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Promotion, VideoPause } from '@element-plus/icons-vue'
import { useChatStore } from '@/store'

const chatStore = useChatStore()
const inputText = ref('')

function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.streaming) return

  chatStore.sendMessage(text)
  inputText.value = ''
}
</script>

<style scoped>
.chat-input-area {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e8e8e8;
}
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto;
}
.input-wrapper :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 10px 16px;
  font-size: 15px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  border: 1px solid #e0e0e0;
}
.input-wrapper :deep(.el-textarea__inner:focus) {
  border-color: #667eea;
}
.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}
.input-footer {
  max-width: 800px;
  margin: 6px auto 0;
}
.hint {
  font-size: 12px;
  color: #bbb;
}
</style>
