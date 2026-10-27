<template>
  <div class="thinking-block">
    <div class="thinking-header" @click="expanded = !expanded">
      <el-icon><MagicStick /></el-icon>
      <span>思考过程</span>
      <el-icon class="arrow" :class="{ open: expanded }"><ArrowDown /></el-icon>
    </div>
    <div v-show="expanded" class="thinking-content">{{ content }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { MagicStick, ArrowDown } from '@element-plus/icons-vue'

defineProps({
  content: { type: String, default: '' }
})

const expanded = ref(false)
</script>

<style scoped>
.thinking-block {
  margin-bottom: 8px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}
.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: #8b5cf6;
  user-select: none;
}
.thinking-header:hover {
  background: #f0f0f0;
}
.arrow {
  margin-left: auto;
  transition: transform 0.2s;
}
.arrow.open {
  transform: rotate(180deg);
}
.thinking-content {
  padding: 8px 12px 12px;
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  border-top: 1px solid #e8e8e8;
  white-space: pre-wrap;
  font-style: italic;
}
</style>
