<template>
  <div class="login-view">
    <div class="login-container">
      <!-- Brand -->
      <div class="brand animate-in">
        <div class="brand-icon">
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="4" width="32" height="32" rx="8" stroke="currentColor" stroke-width="2" />
            <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1 class="brand-title">IAM</h1>
        <p class="brand-sub">统一身份认证平台</p>
      </div>

      <!-- Login Card -->
      <div class="auth-card glass-card animate-in-up" style="animation-delay: 0.15s">
        <!-- Tabs -->
        <div class="auth-tabs">
          <button
            v-for="t in tabs"
            :key="t.key"
            :class="['auth-tab', { active: tab === t.key }]"
            @click="tab = t.key"
          >
            <span class="tab-icon" v-html="t.icon" />
            <span class="tab-label">{{ t.label }}</span>
          </button>
        </div>

        <!-- Password Login -->
        <div v-show="tab === 'password'" class="tab-content animate-in-up">
          <form @submit.prevent="onLogin">
            <div class="input-group">
              <label class="input-label">租户</label>
              <input v-model="form.tenantCode" class="neo-input" placeholder="default" />
            </div>
            <div class="input-group">
              <label class="input-label">用户名</label>
              <input v-model="form.username" class="neo-input" placeholder="输入用户名" />
            </div>
            <div class="input-group">
              <label class="input-label">密码</label>
              <input v-model="form.password" type="password" class="neo-input" placeholder="输入密码" />
            </div>
            <button type="submit" class="neo-btn primary" :disabled="loading" @click="onLogin">
              <span v-if="loading" class="btn-spinner" />
              <span v-else>登录</span>
            </button>
          </form>
        </div>

        <!-- SMS Login -->
        <div v-show="tab === 'sms'" class="tab-content animate-in-up">
          <div class="input-group">
            <label class="input-label">手机号</label>
            <input v-model="smsForm.phone" class="neo-input" placeholder="输入手机号" />
          </div>
          <div class="input-group">
            <label class="input-label">验证码</label>
            <div class="input-row">
              <input v-model="smsForm.code" class="neo-input" placeholder="6 位验证码" />
              <button class="neo-btn ghost code-btn" :disabled="smsCountdown > 0" @click="onSmsSend">
                {{ smsCountdown > 0 ? `${smsCountdown}s` : '发送' }}
              </button>
            </div>
          </div>
          <button class="neo-btn primary" :disabled="loading" @click="onSmsLogin">
            <span v-if="loading" class="btn-spinner" />
            <span v-else>登录</span>
          </button>
        </div>

        <!-- Magic Link -->
        <div v-show="tab === 'magic'" class="tab-content animate-in-up">
          <div class="input-group">
            <label class="input-label">邮箱</label>
            <input v-model="magicEmail" class="neo-input" placeholder="you@example.com" />
          </div>
          <button class="neo-btn primary" :disabled="loading" @click="onMagicSend">
            <span v-if="loading" class="btn-spinner" />
            <span v-else>发送登录链接</span>
          </button>
          <p class="tab-hint">链接发送到邮箱，在 stub 模式下会打印在后端控制台</p>
        </div>

        <!-- Social -->
        <div v-show="tab === 'social'" class="tab-content animate-in-up">
          <div class="social-grid">
            <button v-for="p in socialProviders" :key="p.id" class="social-btn" @click="social(p.id)">
              <span v-html="p.icon" />
              <span>{{ p.name }}</span>
            </button>
          </div>
          <p class="tab-hint">需在 application.yml 配置对应 appId/appSecret</p>
        </div>

        <!-- SSO -->
        <div v-show="tab === 'sso'" class="tab-content animate-in-up">
          <button class="neo-btn secondary" @click="casLogin">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>
            CAS SSO
          </button>
          <button class="neo-btn secondary" @click="samlLogin">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
            SAML 2.0
          </button>
          <p class="tab-hint">需配置 iam.cas.server-url / iam.saml.idp.*</p>
        </div>

        <!-- OAuth2 -->
        <div v-show="tab === 'oauth2'" class="tab-content animate-in-up">
          <p class="tab-hint">演示客户端：<code>demo-client</code></p>
          <button class="neo-btn primary" @click="oauth2Authorize">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>
            跳转 OAuth2 授权
          </button>
        </div>
      </div>

      <p class="footer-text animate-in" style="animation-delay: 0.3s">
        IAM Platform · 15 种认证协议统一管理
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { api, saveSession } from '../api'

const tab = ref('password')
const loading = ref(false)
const router = useRouter()
const form = ref({ tenantCode: 'default', username: '', password: '' })
const smsForm = ref({ phone: '', code: '' })
const magicEmail = ref('')
const smsCountdown = ref(0)

const tabs = [
  { key: 'password', label: '密码', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>' },
  { key: 'sms', label: '短信', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/></svg>' },
  { key: 'magic', label: 'Magic Link', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg>' },
  { key: 'social', label: '社交', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>' },
  { key: 'sso', label: '企业 SSO', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>' },
  { key: 'oauth2', label: 'OAuth2', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>' },
]

const socialProviders = [
  { id: 'wechat', name: '微信', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="10" r="1.5" fill="currentColor"/><circle cx="16" cy="10" r="1.5" fill="currentColor"/><path d="M12 16c-3.5 0-6-2-6-4.5S8.5 7 12 7s6 2 6 4.5c0 1.2-.7 2.3-1.8 3.2L17 17l-2.5-1.4c-.8.2-1.6.4-2.5.4z"/></svg>' },
  { id: 'alipay', name: '支付宝', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 4h16v16H4z"/><path d="M8 12h8"/><path d="M12 8v8"/></svg>' },
  { id: 'qq', name: 'QQ', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="8" r="4"/><path d="M6 18c0-2 2.5-4 6-4s6 2 6 4"/></svg>' },
  { id: 'dingtalk', name: '钉钉', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>' },
  { id: 'wecom', name: '企业微信', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="2" width="9" height="9" rx="2"/><rect x="13" y="2" width="9" height="9" rx="2"/><rect x="2" y="13" width="9" height="9" rx="2"/><rect x="13" y="13" width="9" height="9" rx="2"/></svg>' },
]

async function onLogin(): Promise<void> {
  loading.value = true
  try {
    const r = await api.login(form.value)
    if (r.mfaRequired) {
      sessionStorage.setItem('mfa_token', r.mfaToken || '')
      router.push('/mfa')
      return
    }
    saveSession(r)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function onSmsSend(): Promise<void> {
  if (!smsForm.value.phone) { ElMessage.warning('请输入手机号'); return }
  await api.smsSend(smsForm.value.phone)
  ElMessage.success('验证码已发送')
  smsCountdown.value = 60
  const t = setInterval(() => {
    smsCountdown.value--
    if (smsCountdown.value <= 0) clearInterval(t)
  }, 1000)
}

async function onSmsLogin(): Promise<void> {
  loading.value = true
  try {
    const r = await api.smsLogin(smsForm.value.phone, smsForm.value.code)
    saveSession(r)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '登录失败')
  } finally { loading.value = false }
}

async function onMagicSend(): Promise<void> {
  if (!magicEmail.value) { ElMessage.warning('请输入邮箱'); return }
  await api.magicSend(magicEmail.value)
  ElMessage.success('登录链接已发送到邮箱')
}

async function social(provider: string): Promise<void> {
  try {
    const url = await api.socialAuthorize(provider)
    location.href = url
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '未配置')
  }
}

async function casLogin(): Promise<void> {
  try {
    const url = await api.casAuthorize()
    location.href = url
  } catch (e: unknown) {
    ElMessage.error((e as { response?: { data?: { message?: string } } })?.response?.data?.message || 'CAS 未配置')
  }
}

function samlLogin(): void {
  location.href = '/iam/saml2/authenticate/default'
}

function oauth2Authorize(): void {
  const url = `/iam/oauth/authorize?response_type=code&client_id=demo-client&redirect_uri=${encodeURIComponent('http://localhost:5173/callback')}&scope=openid`
  location.href = url
}
</script>

<style scoped>
.login-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  z-index: 1;
}
.login-container {
  width: 440px;
  max-width: 100%;
}

/* ── Brand ── */
.brand {
  text-align: center;
  margin-bottom: 36px;
}
.brand-icon {
  width: 56px; height: 56px;
  margin: 0 auto 16px;
  color: var(--accent);
  filter: drop-shadow(0 0 16px var(--accent-glow));
}
.brand-icon svg { width: 100%; height: 100%; }
.brand-title {
  font-family: var(--font-heading);
  font-size: 2.4rem;
  font-weight: 800;
  background: linear-gradient(135deg, var(--accent) 0%, #6c5ce7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.04em;
}
.brand-sub {
  color: var(--text-secondary);
  margin-top: 6px;
  font-size: 0.95rem;
}

/* ── Auth Card ── */
.auth-card {
  padding: 32px;
  animation-delay: 0.15s;
}

/* ── Tabs ── */
.auth-tabs {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
  margin-bottom: 28px;
}
.auth-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px 8px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  font-family: var(--font-body);
  font-size: 0.65rem;
  font-weight: 500;
  white-space: nowrap;
}
.auth-tab:hover { color: var(--text-secondary); background: rgba(255,255,255,0.03); }
.auth-tab.active {
  color: var(--accent);
  background: rgba(0, 212, 255, 0.08);
  box-shadow: 0 0 12px var(--accent-glow);
}
.tab-icon { width: 18px; height: 18px; }
.tab-icon svg { width: 100%; height: 100%; }

/* ── Tab Content ── */
.tab-content {
  animation-duration: 0.4s;
}
.tab-hint {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-top: 14px;
  text-align: center;
}

/* ── Inputs ── */
.input-group {
  margin-bottom: 18px;
}
.input-label {
  display: block;
  color: var(--text-secondary);
  font-size: 0.8rem;
  font-weight: 500;
  margin-bottom: 6px;
  letter-spacing: 0.01em;
}
.neo-input {
  width: 100%;
  padding: 11px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 0.9rem;
  transition: all var(--dur-fast) var(--ease-out);
  outline: none;
}
.neo-input::placeholder { color: var(--text-muted); }
.neo-input:hover { border-color: var(--border-hover); }
.neo-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-glow);
}
.input-row {
  display: flex;
  gap: 8px;
}
.input-row .neo-input { flex: 1; }

/* ── Buttons ── */
.neo-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px 20px;
  border-radius: var(--radius-md);
  font-family: var(--font-body);
  font-size: 0.9rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  border: 1px solid transparent;
  margin-bottom: 8px;
}
.neo-btn.primary {
  background: linear-gradient(135deg, var(--accent-dim), var(--accent));
  color: #fff;
  border-color: var(--accent);
  box-shadow: 0 0 16px var(--accent-glow), 0 4px 16px rgba(0, 212, 255, 0.15);
}
.neo-btn.primary:hover {
  box-shadow: 0 0 28px var(--accent-glow-strong), 0 6px 24px rgba(0, 212, 255, 0.25);
  transform: translateY(-2px);
}
.neo-btn.primary:active { transform: translateY(0); }
.neo-btn.primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.neo-btn.secondary {
  background: rgba(108, 92, 231, 0.12);
  color: var(--secondary);
  border-color: rgba(108, 92, 231, 0.3);
}
.neo-btn.secondary:hover {
  background: rgba(108, 92, 231, 0.2);
  box-shadow: 0 0 16px var(--secondary-glow);
  transform: translateY(-1px);
}
.neo-btn.ghost {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-accent);
  border-color: var(--border);
  width: auto;
  white-space: nowrap;
  padding: 11px 16px;
  margin-bottom: 0;
}
.neo-btn.ghost:hover { border-color: var(--border-hover); background: rgba(0, 212, 255, 0.08); }
.neo-btn.ghost:disabled { opacity: 0.4; cursor: not-allowed; }

.code-btn { flex-shrink: 0; width: auto !important; }
.btn-spinner {
  width: 18px; height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: btnSpin 0.6s linear infinite;
}
@keyframes btnSpin { to { transform: rotate(360deg); } }

/* ── Social ── */
.social-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.social-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  font-family: var(--font-body);
  font-size: 0.8rem;
  font-weight: 500;
}
.social-btn:hover {
  border-color: var(--border-hover);
  background: rgba(255, 255, 255, 0.07);
  color: var(--text-primary);
  transform: translateY(-2px);
}

/* ── Footer ── */
.footer-text {
  text-align: center;
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-top: 24px;
}

/* ── Responsive ── */
@media (max-width: 480px) {
  .auth-tabs { grid-template-columns: repeat(3, 1fr); }
  .auth-card { padding: 24px 16px; }
  .social-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
