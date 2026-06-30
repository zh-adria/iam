<template>
  <div class="loading-wrap"><el-icon class="is-loading"><Loading /></el-icon> 正在登录...</div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { api, saveSession } from '../api'

const router = useRouter()
const route = useRoute()

onMounted(async () => {
  const token = route.query.token as string
  if (!token) { ElMessage.error('缺少 token'); router.push('/login'); return }
  try {
    const r = await api.magicVerify(token)
    saveSession(r)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '链接无效')
    router.push('/login')
  }
})
</script>

<style scoped>
.loading-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; gap: 8px; }
</style>
