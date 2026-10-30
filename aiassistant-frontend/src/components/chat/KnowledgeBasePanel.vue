<template>
  <div class="knowledge-panel">
    <div class="panel-header">
      <span class="panel-title">知识库</span>
    </div>

    <div class="panel-body">
      <div v-if="knowledgeStore.list.length === 0" class="empty-tip">
        暂无知识库，点击上方按钮创建
      </div>

      <div v-for="kb in knowledgeStore.list" :key="kb.id"
           class="kb-item" :class="{ active: kb.id === knowledgeStore.currentId }"
           @click="selectKnowledgeBase(kb)">
        <div class="kb-title">
          <el-icon><Collection /></el-icon>
          <span>{{ kb.name }}</span>
        </div>
        <div class="kb-meta">{{ kb.docCount || 0 }} 个文档</div>
      </div>
    </div>

    <div class="document-list" v-if="knowledgeStore.currentId && knowledgeStore.documents.length > 0">
      <div class="doc-list-title">关联文档</div>
      <div v-for="doc in knowledgeStore.documents" :key="doc.id" class="doc-item">
        <div class="doc-info">
          <el-icon><Document /></el-icon>
          <span class="doc-name">{{ doc.name }}</span>
          <span class="doc-status" :class="statusClass(doc.parseStatus)">
            {{ statusText(doc.parseStatus) }}
          </span>
        </div>
        <span class="doc-chunks">{{ doc.chunkCount || 0 }} 块</span>
      </div>
    </div>

    <div class="conversation-list" v-if="knowledgeStore.currentId && knowledgeStore.conversations.length > 0">
      <div class="doc-list-title">关联对话</div>
      <div v-for="conv in knowledgeStore.conversations" :key="conv.id" class="doc-item">
        <div class="doc-info">
          <el-icon><ChatDotRound /></el-icon>
          <span class="doc-name">{{ conv.title }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Collection, Document, ChatDotRound } from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/store'

const knowledgeStore = useKnowledgeStore()

async function selectKnowledgeBase(kb) {
  knowledgeStore.setCurrent(kb.id)
  await knowledgeStore.fetchDocuments(kb.id)
  knowledgeStore.fetchConversations(kb.id)
  knowledgeStore.resumePollingIfNeeded(kb.id)
}

function statusClass(status) {
  const map = { 0: 'pending', 1: 'parsing', 2: 'success', 3: 'failed' }
  return map[status] || 'pending'
}

function statusText(status) {
  const map = { 0: '待解析', 1: '解析中', 2: '已完成', 3: '失败' }
  return map[status] || '未知'
}
</script>

<style scoped>
.knowledge-panel {
  border-top: 1px solid #e8e8e8;
  background: #fff;
  padding: 12px 20px;
  max-height: 300px;
  overflow-y: auto;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-right: auto;
}
.panel-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.empty-tip {
  color: #999;
  font-size: 13px;
  padding: 8px 0;
}
.kb-item {
  display: flex;
  flex-direction: column;
  padding: 8px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  cursor: pointer;
  min-width: 150px;
  transition: all 0.2s;
}
.kb-item:hover {
  border-color: #667eea;
  background: #f5f7ff;
}
.kb-item.active {
  border-color: #667eea;
  background: #eef0ff;
}
.kb-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
}
.kb-meta {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}
.document-list {
  margin-top: 12px;
  border-top: 1px solid #f0f0f0;
  padding-top: 8px;
}
.doc-list-title {
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
}
.doc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 12px;
}
.doc-info {
  display: flex;
  align-items: center;
  gap: 4px;
}
.doc-name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.doc-status.pending { color: #999; background: #f5f5f5; }
.doc-status.parsing { color: #e6a23c; background: #fdf6ec; }
.doc-status.success { color: #67c23a; background: #f0f9eb; }
.doc-status.failed { color: #f56c6c; background: #fef0f0; }
.doc-chunks {
  color: #999;
  font-size: 11px;
}
.conversation-list {
  margin-top: 12px;
  border-top: 1px solid #f0f0f0;
  padding-top: 8px;
}
</style>
