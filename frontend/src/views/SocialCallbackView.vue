<template>
  <div class="callback-view">
    <div class="callback-container glass-card">
      <div class="loading-icon">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 11-6.219-8.56"/></svg>
      </div>
      <p class="callback-text">正在登录...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

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
.callback-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
}
.callback-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 56px;
}
.loading-icon {
  color: var(--accent);
  animation: spin 1s linear infinite;
}
.loading-icon svg { width: 32px; height: 32px; }
@keyframes spin { to { transform: rotate(360deg); } }
.callback-text {
  font-family: var(--font-heading);
  font-weight: 600;
  color: var(--text-secondary);
}
</style>
