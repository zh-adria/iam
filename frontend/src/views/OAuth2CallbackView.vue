<template>
  <CallbackView msg="正在交换令牌..." />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import CallbackView from '../components/CallbackView.vue'

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
