<template>
  <div class="callback-view">
    <div class="callback-container glass-card">
      <div class="loading-icon">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 11-6.219-8.56"/></svg>
      </div>
      <p class="callback-text">正在交换令牌...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const route = useRoute()

onMounted(async () => {
  const code = route.query.code as string
  if (!code) { ElMessage.error('缺少 code'); router.push('/login'); return }
  try {
    const { data } = await axios.post('/iam/oauth/token', new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: 'demo-client',
      client_secret: 'demo-secret',
      code,
      redirect_uri: 'http://localhost:5173/callback'
    }), { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
    localStorage.setItem('access_token', data.access_token)
    if (data.refresh_token) localStorage.setItem('refresh_token', data.refresh_token)
    ElMessage.success('授权成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { error_description?: string } } })?.response?.data?.error_description || '令牌交换失败')
    router.push('/login')
  }
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