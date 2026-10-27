<template>
  <div class="chat-layout">
    <Sidebar />
    <div class="chat-main">
      <ChatHeader />
      <div class="chat-body">
        <WelcomePage v-if="chatStore.messages.length === 0 && !chatStore.streaming" />
        <MessageList v-else />
      </div>
      <ChatInput />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useConversationStore, useChatStore } from '@/store'
import Sidebar from '@/components/layout/Sidebar.vue'
import ChatHeader from '@/components/layout/ChatHeader.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import MessageList from '@/components/chat/MessageList.vue'
import WelcomePage from '@/components/chat/WelcomePage.vue'

const convStore = useConversationStore()
const chatStore = useChatStore()

onMounted(() => {
  convStore.fetchList(true)
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
</style>
