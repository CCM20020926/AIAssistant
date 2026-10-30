<template>
  <div class="chat-input-area">
    <div v-if="options.knowledgeMode" class="knowledge-actions">
      <el-button size="small" @click="createDialog.open()">
        <el-icon><Plus /></el-icon> 新建知识库
      </el-button>
      <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pdf,.docx,.md,.txt">
        <el-button size="small">
          <el-icon><Upload /></el-icon> 上传文档
        </el-button>
      </el-upload>
    </div>
    <div class="input-switches">
      <el-switch v-model="options.deepThink" active-text="深度思考" size="small" />
      <el-switch v-model="options.webSearch" active-text="联网搜索" size="small" />
      <el-switch v-model="options.pptMode" active-text="PPT" size="small" :disabled="true" />
      <el-switch v-model="options.knowledgeMode" active-text="知识库" size="small" />
    </div>
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
    <KnowledgeBasePanel v-if="options.knowledgeMode" />
    <CreateKnowledgeBaseDialog ref="createDialog" @created="onKnowledgeBaseCreated" />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onUnmounted } from 'vue'
import { Promotion, VideoPause, Plus, Upload } from '@element-plus/icons-vue'
import { useChatStore, useKnowledgeStore } from '@/store'
import { ElMessage } from 'element-plus'
import CreateKnowledgeBaseDialog from './CreateKnowledgeBaseDialog.vue'
import KnowledgeBasePanel from './KnowledgeBasePanel.vue'

const chatStore = useChatStore()
const knowledgeStore = useKnowledgeStore()
const inputText = ref('')
const createDialog = ref(null)
const options = reactive({
  deepThink: false,
  webSearch: false,
  pptMode: false,
  knowledgeMode: false
})

watch(() => options.knowledgeMode, (on) => {
  if (on) {
    knowledgeStore.fetchList()
    knowledgeStore.connectNotifications()
  } else {
    knowledgeStore.stopPolling()
    knowledgeStore.disconnectNotifications()
  }
})

onUnmounted(() => {
  knowledgeStore.stopPolling()
  knowledgeStore.disconnectNotifications()
})

function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.streaming) return

  if (options.knowledgeMode && !knowledgeStore.currentId) {
    ElMessage.warning('请先选择一个知识库')
    return
  }

  chatStore.sendMessage(text, { ...options, knowledgeBaseId: knowledgeStore.currentId })
  inputText.value = ''
}

async function handleUpload(file) {
  if (!knowledgeStore.currentId) {
    ElMessage.warning('请先选择一个知识库')
    return false
  }
  try {
    await knowledgeStore.upload(file, knowledgeStore.currentId)
    ElMessage.success('上传成功，解析中...')
  } catch (e) {
    ElMessage.error('上传失败')
  }
  return false
}

function onKnowledgeBaseCreated() {
  knowledgeStore.fetchList()
}
</script>

<style scoped>
.chat-input-area {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e8e8e8;
}
.knowledge-actions {
  display: flex;
  gap: 8px;
  max-width: 800px;
  margin: 0 auto 10px;
}
.input-switches {
  display: flex;
  gap: 16px;
  max-width: 800px;
  margin: 0 auto 10px;
  align-items: center;
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
