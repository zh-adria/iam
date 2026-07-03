<template>
  <CallbackView msg="正在登录..." />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CallbackView from '../components/CallbackView.vue'

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
