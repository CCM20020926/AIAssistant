<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <el-button type="primary" @click="handleNewChat" :icon="Plus">
        新建对话
      </el-button>
    </div>

    <div class="sidebar-list" @scroll="handleScroll" ref="listRef">
      <div v-for="conv in convStore.list" :key="conv.id"
        class="sidebar-item" :class="{ active: conv.id === convStore.currentId }"
        @click="handleSelect(conv)">
        <div class="item-title">
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ conv.title }}</span>
        </div>
        <div class="item-actions">
          <el-icon class="action-btn" @click.stop="handleRename(conv)">
            <Edit />
          </el-icon>
          <el-icon class="action-btn danger" @click.stop="handleDelete(conv)">
            <Delete />
          </el-icon>
        </div>
      </div>

      <div v-if="convStore.loading" class="sidebar-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
    </div>

    <div class="sidebar-footer">
      <div class="user-info">
        <el-icon><User /></el-icon>
        <span>{{ userStore.username }}</span>
      </div>
      <el-button text @click="handleLogout" :icon="SwitchButton">退出</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Plus, ChatDotRound, Edit, Delete, Loading, User, SwitchButton } from '@element-plus/icons-vue'
import { useConversationStore, useChatStore, useUserStore } from '@/store'
import { renameConversation } from '@/api/conversation'
import { ElMessageBox, ElMessage } from 'element-plus'
import router from '@/router'

const convStore = useConversationStore()
const chatStore = useChatStore()
const userStore = useUserStore()
const listRef = ref(null)

function handleNewChat() {
  convStore.setCurrent(null)
  chatStore.loadMessages(null)
}

function handleSelect(conv) {
  convStore.setCurrent(conv.id)
  chatStore.loadMessages(conv.id)
}

async function handleRename(conv) {
  const { value } = await ElMessageBox.prompt('请输入新标题', '重命名', {
    inputValue: conv.title,
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
  if (value && value.trim()) {
    await renameConversation(conv.id, value.trim())
    convStore.updateTitle(conv.id, value.trim())
  }
}

async function handleDelete(conv) {
  await ElMessageBox.confirm('确定删除该会话？', '提示', { type: 'warning' })
  await convStore.removeConversation(conv.id)
  ElMessage.success('已删除')
}

function handleScroll(e) {
  const { scrollTop, scrollHeight, clientHeight } = e.target
  if (scrollHeight - scrollTop - clientHeight < 50 && convStore.hasMore && !convStore.loading) {
    convStore.fetchList()
  }
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.sidebar {
  width: 260px;
  background: #1a1a2e;
  color: #e0e0e0;
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.sidebar-header .el-button {
  width: 100%;
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.sidebar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}
.sidebar-item:hover {
  background: rgba(255,255,255,0.08);
}
.sidebar-item.active {
  background: rgba(102, 126, 234, 0.3);
}
.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  font-size: 14px;
}
.item-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-actions {
  display: none;
  gap: 4px;
}
.sidebar-item:hover .item-actions {
  display: flex;
}
.action-btn {
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  font-size: 14px;
}
.action-btn:hover {
  background: rgba(255,255,255,0.15);
}
.action-btn.danger:hover {
  color: #f56c6c;
}
.sidebar-loading {
  text-align: center;
  padding: 12px;
}
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}
.sidebar-footer .el-button {
  color: #999;
  font-size: 13px;
}
</style>
