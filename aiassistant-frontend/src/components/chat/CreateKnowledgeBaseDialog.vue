<template>
  <el-dialog v-model="visible" title="新建知识库" width="400px">
    <el-form @submit.prevent="handleCreate">
      <el-form-item label="知识库名称">
        <el-input v-model="name" placeholder="请输入知识库名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="description" type="textarea" placeholder="请输入描述（可选）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate" :loading="loading">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { useKnowledgeStore } from '@/store'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['created'])

const knowledgeStore = useKnowledgeStore()
const visible = ref(false)
const name = ref('')
const description = ref('')
const loading = ref(false)

function open() {
  name.value = ''
  description.value = ''
  visible.value = true
}

async function handleCreate() {
  if (!name.value.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  loading.value = true
  try {
    await knowledgeStore.create(name.value.trim(), description.value.trim())
    ElMessage.success('创建成功')
    visible.value = false
    emit('created')
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
