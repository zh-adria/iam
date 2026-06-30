<template>
  <div class="mfa-wrap">
    <el-card class="mfa-card">
      <h3>多因素认证 (TOTP)</h3>
      <p>请打开 Google / Microsoft Authenticator 输入 6 位动态码</p>
      <el-input v-model="code" maxlength="6" placeholder="6 位动态码" @keyup.enter="submit" />
      <el-button type="primary" :loading="loading" @click="submit" style="margin-top:12px;width:100%">验证</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { api } from '../api'

const code = ref('')
const loading = ref(false)
const router = useRouter()

async function submit() {
  const mfaToken = sessionStorage.getItem('mfa_token') || ''
  if (!mfaToken) { ElMessage.error('MFA 会话丢失，请重新登录'); router.push('/login'); return }
  loading.value = true
  try {
    const r = await api.verifyMfa(mfaToken, code.value)
    localStorage.setItem('access_token', r.accessToken)
    localStorage.setItem('refresh_token', r.refreshToken)
    sessionStorage.removeItem('mfa_token')
    router.push('/dashboard')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '验证失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.mfa-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f0f2f5; }
.mfa-card { width: 360px; text-align: center; }
</style>
