<template>
  <div class="loading-wrap"><el-icon class="is-loading"><Loading /></el-icon> 正在登录...</div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

onMounted(() => {
  const token = route.query.token as string
  if (!token) { ElMessage.error('未收到令牌'); router.push('/login'); return }
  localStorage.setItem('access_token', token)
  ElMessage.success('登录成功')
  router.push('/dashboard')
})
</script>

<style scoped>
.loading-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; gap: 8px; }
</style>
