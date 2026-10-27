<template>
  <div class="welcome">
    <div class="welcome-logo">
      <svg width="80" height="80" viewBox="0 0 80 80" fill="none">
        <circle cx="40" cy="40" r="38" stroke="#667eea" stroke-width="3" />
        <text x="40" y="48" text-anchor="middle" fill="#667eea" font-size="28" font-weight="bold">AI</text>
      </svg>
    </div>
    <h1 class="welcome-title">AI Assistant</h1>
    <p class="welcome-subtitle">有什么可以帮你的？</p>
    <div class="welcome-hints">
      <div class="hint-card" v-for="hint in hints" :key="hint" @click="handleHint(hint)">
        {{ hint }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { useChatStore } from '@/store'

const chatStore = useChatStore()

const hints = [
  '帮我写一首关于春天的诗',
  '解释一下量子计算的基本原理',
  '用 Python 写一个快速排序',
  '推荐几本值得读的技术书籍'
]

function handleHint(text) {
  chatStore.sendMessage(text)
}
</script>

<style scoped>
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}
.welcome-logo {
  margin-bottom: 20px;
}
.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin: 0 0 8px;
}
.welcome-subtitle {
  font-size: 16px;
  color: #999;
  margin: 0 0 40px;
}
.welcome-hints {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 500px;
}
.hint-card {
  padding: 14px 18px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  color: #555;
  transition: all 0.2s;
}
.hint-card:hover {
  border-color: #667eea;
  color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}
</style>
