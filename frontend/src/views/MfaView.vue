<template>
  <div class="mfa-view">
    <div class="mfa-container glass-card animate-in-up">
      <div class="mfa-header">
        <span class="brand-mark small">
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <h1>多因素认证</h1>
        <p class="subtitle">请输入 Authenticator 应用显示的 6 位代码</p>
      </div>

      <form @submit.prevent="submit">
        <div class="otp-row">
          <input
            v-for="i in 6"
            :key="i"
            :ref="el => setRef(el as HTMLInputElement, i - 1)"
            class="otp-input"
            maxlength="1"
            inputmode="numeric"
            autocomplete="one-time-code"
            :value="digits[i - 1]"
            @input="onInput($event, i - 1)"
            @keydown="onKey($event, i - 1)"
          />
        </div>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">验证</el-button>
        <el-button text @click="router.push('/login')">返回登录</el-button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { api } from '../api'

const code = ref('')
const digits = ref<string[]>(['', '', '', '', '', ''])
const loading = ref(false)
const router = useRouter()
const inputs = ref<HTMLInputElement[]>([])

function setRef(el: HTMLInputElement | null, i: number) {
  if (el) inputs.value[i] = el
}
onMounted(() => nextTick(() => inputs.value[0]?.focus()))

function onInput(e: Event, i: number) {
  const v = (e.target as HTMLInputElement).value.replace(/\D/g, '')
  digits.value[i] = v
  code.value = digits.value.join('')
  if (v && i < 5) inputs.value[i + 1]?.focus()
}
function onKey(e: KeyboardEvent, i: number) {
  if (e.key === 'Backspace' && !digits.value[i] && i > 0) {
    inputs.value[i - 1]?.focus()
  }
}

async function submit() {
  const v = code.value
  if (v.length !== 6) { ElMessage.warning('请输入完整的 6 位验证码'); return }
  const mfaToken = sessionStorage.getItem('mfa_token') || ''
  if (!mfaToken) { ElMessage.error('MFA 会话丢失，请重新登录'); router.push('/login'); return }
  loading.value = true
  try {
    const r = await api.verifyMfa(mfaToken, v)
    localStorage.setItem('access_token', r.accessToken)
    if (r.refreshToken) localStorage.setItem('refresh_token', r.refreshToken)
    sessionStorage.removeItem('mfa_token')
    router.push('/dashboard')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '验证失败')
    digits.value = ['', '', '', '', '', '']
    nextTick(() => inputs.value[0]?.focus())
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
  z-index: 1;
}
.mfa-container {
  width: 400px;
  max-width: 100%;
  padding: 40px 36px;
}
.mfa-header { text-align: center; margin-bottom: 28px; }
.mfa-header h1 {
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 12px;
}
.subtitle { color: var(--text-muted); font-size: 0.9rem; margin-top: 6px; }
.brand-mark.small { width: 32px; height: 32px; }
.brand-mark.small svg { width: 18px; height: 18px; }

.otp-row { display: flex; justify-content: center; gap: 8px; margin-bottom: 20px; }
.otp-input {
  width: 46px; height: 54px;
  padding: 0;
  font-family: var(--font-mono);
  font-size: 1.4rem;
  font-weight: 600;
  text-align: center;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  transition: all var(--dur-fast) var(--ease-out);
  outline: none;
  caret-color: var(--accent);
}
.otp-input:hover { border-color: var(--border-hover); }
.otp-input:focus {
  border-color: var(--accent);
  box-shadow: var(--shadow-accent);
}
.submit-btn {
  width: 100%;
  height: 42px !important;
  font-size: 0.95rem !important;
  margin-bottom: 8px;
}
</style>
