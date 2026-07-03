<template>
  <div class="mfa-view">
    <div class="orb orb-1" />
    <div class="orb orb-2" />

    <div class="mfa-container glass-card animate-in-up">
      <div class="mfa-header">
        <div class="mfa-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
        </div>
        <h1>多因素认证</h1>
        <p class="subtitle">请打开 Authenticator 输入 6 位动态码</p>
      </div>

      <form @submit.prevent="submit" class="mfa-form">
        <input
          v-model="code"
          class="mfa-input"
          maxlength="6"
          placeholder="6 位动态码"
          @keyup.enter="submit"
          autocomplete="one-time-code"
        />
        <button type="submit" class="neo-btn primary" :disabled="loading">
          <span v-if="loading" class="btn-spinner" />
          <span v-else>验证</span>
        </button>
      </form>
    </div>
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
.mfa-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  overflow: hidden;
}
.mfa-view .orb {
  position: fixed;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.3;
  pointer-events: none;
  z-index: 0;
}
.mfa-view .orb-1 {
  width: 400px; height: 400px;
  top: -100px; left: -80px;
  background: radial-gradient(circle, rgba(0,212,255,0.2) 0%, transparent 70%);
  animation: orbFloat1 20s ease-in-out infinite;
}
.mfa-view .orb-2 {
  width: 300px; height: 300px;
  bottom: -60px; right: -40px;
  background: radial-gradient(circle, rgba(108,92,231,0.18) 0%, transparent 70%);
  animation: orbFloat2 25s ease-in-out infinite;
}

.mfa-container {
  position: relative;
  z-index: 1;
  width: 380px;
  max-width: 100%;
  padding: 40px 36px;
}

.mfa-header { text-align: center; margin-bottom: 28px; }
.mfa-icon {
  width: 56px; height: 56px;
  margin: 0 auto 16px;
  border-radius: var(--radius-lg);
  background: rgba(0, 212, 255, 0.1);
  border: 1px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  color: var(--accent);
  box-shadow: 0 0 20px var(--accent-glow);
}
.mfa-header h1 {
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--text-primary);
}
.subtitle { color: var(--text-secondary); font-size: 0.9rem; margin-top: 6px; }

.mfa-form { display: flex; flex-direction: column; gap: 16px; }

.mfa-input {
  width: 100%;
  padding: 14px 18px;
  font-size: 1.2rem;
  letter-spacing: 0.3em;
  text-align: center;
  font-family: var(--font-mono);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  transition: all var(--dur-fast) var(--ease-out);
  outline: none;
}
.mfa-input::placeholder { color: var(--text-muted); letter-spacing: 0.05em; }
.mfa-input:hover { border-color: var(--border-hover); }
.mfa-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-glow);
}

.neo-btn.primary { padding: 14px 24px; font-size: 0.95rem; }
.btn-spinner { width: 20px; height: 20px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: btnSpin 0.6s linear infinite; }
@keyframes btnSpin { to { transform: rotate(360deg); } }
</style>