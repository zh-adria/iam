<template>
  <div class="login-split">
    <!-- ── Left Hero ── -->
    <aside class="login-hero">
      <div class="hero-inner">
        <div class="hero-brand">
          <span class="hero-mark">
            <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </span>
          <span class="hero-word">IAM</span>
        </div>

        <h2 class="hero-title">统一身份<br/>与访问管理</h2>
        <p class="hero-sub">一个控制台，管理用户、角色、权限、OAuth2 / OIDC、SAML、LDAP 与多种登录方式。</p>

        <ul class="hero-features">
          <li>
            <span class="feat-dot" />
            <span><b>多协议认证</b>，账密、MFA、OAuth2/OIDC、SAML、LDAP/AD、CAS、SCIM、短信、Magic Link、社交登录</span>
          </li>
          <li>
            <span class="feat-dot" />
            <span><b>多租户隔离</b>，SHARED · SCHEMA · DATABASE 三种策略</span>
          </li>
          <li>
            <span class="feat-dot" />
            <span><b>RBAC + ABAC</b>，用户 · 角色 · 权限 · SpEL 策略</span>
          </li>
          <li>
            <span class="feat-dot" />
            <span><b>审计溯源</b>，链式哈希 + 不可篡改日志</span>
          </li>
        </ul>

        <div class="hero-foot">
          <span class="trust-badge">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            企业级安全合规
          </span>
        </div>
      </div>
      <!-- signature background circles -->
      <div class="hero-decor" aria-hidden="true" />
    </aside>

    <!-- ── Right Login Card ── -->
    <main class="login-panel">
      <div class="auth-card glass-card animate-in">
        <div class="auth-title">
          <span class="eyebrow">欢迎回来</span>
          <h1>登录到您的账户</h1>
          <p class="auth-sub">选择认证方式以继续</p>
        </div>

        <!-- Tabs -->
        <div class="tab-chip-group" role="tablist">
          <button
            v-for="t in tabs"
            :key="t.key"
            :class="['tab-chip', { active: tab === t.key }]"
            role="tab"
            :aria-selected="tab === t.key"
            @click="tab = t.key"
          >
            <span class="chip-icon" v-html="t.icon" />
            {{ t.label }}
          </button>
        </div>

        <!-- ── Password ── -->
        <div v-show="tab === 'password'" class="tab-content">
          <form @submit.prevent="onLogin" novalidate>
            <div class="field">
              <label class="field-label">租户</label>
              <el-input v-model="form.tenantCode" placeholder="default" autocomplete="organization" />
            </div>
            <div class="field">
              <label class="field-label">用户名</label>
              <el-input v-model="form.username" placeholder="输入用户名" autocomplete="username" />
              <p v-if="!form.username && touched.username" class="field-error">请输入用户名</p>
            </div>
            <div class="field">
              <label class="field-label">密码</label>
              <el-input v-model="form.password" type="password" placeholder="输入密码" autocomplete="current-password" show-password />
              <p v-if="!form.password && touched.password" class="field-error">请输入密码</p>
            </div>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="onLogin">登录</el-button>
          </form>
        </div>

        <!-- ── SMS ── -->
        <div v-show="tab === 'sms'" class="tab-content">
          <form @submit.prevent="onSmsLogin">
            <div class="field">
              <label class="field-label">手机号</label>
              <el-input v-model="smsForm.phone" placeholder="输入手机号" />
            </div>
            <div class="field">
              <label class="field-label">验证码</label>
              <div class="input-row">
                <el-input v-model="smsForm.code" placeholder="6 位验证码" maxlength="6" />
                <el-button class="code-btn" :disabled="smsCountdown > 0" @click="onSmsSend">
                  {{ smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码' }}
                </el-button>
              </div>
            </div>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="onSmsLogin">登录</el-button>
          </form>
        </div>

        <!-- ── Magic Link ── -->
        <div v-show="tab === 'magic'" class="tab-content">
          <form @submit.prevent="onMagicSend">
            <div class="field">
              <label class="field-label">邮箱</label>
              <el-input v-model="magicEmail" type="email" placeholder="you@example.com" autocomplete="email" />
            </div>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="onMagicSend">发送登录链接</el-button>
            <p class="tab-hint">链接将发送到您的邮箱，stub 模式下会打印在后端控制台</p>
          </form>
        </div>

        <!-- ── Social ── -->
        <div v-show="tab === 'social'" class="tab-content">
          <div v-if="socialProviders.length" class="social-grid">
            <button v-for="p in socialProviders" :key="p.id" class="social-btn" @click="social(p.id)">
              <span v-html="p.icon" />
              <span>{{ p.name }}</span>
            </button>
          </div>
          <p v-else class="tab-hint">当前未启用社交登录提供商</p>
          <p class="tab-hint">需在 application.yml 配置对应 appId/appSecret</p>
        </div>

        <!-- ── SSO ── -->
        <div v-show="tab === 'sso'" class="tab-content">
          <div class="sso-stack">
            <el-button @click="casLogin" class="sso-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>
              CAS SSO
            </el-button>
            <el-button @click="samlLogin" class="sso-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
              SAML 2.0
            </el-button>
          </div>
          <p class="tab-hint">需配置 iam.cas.server-url / iam.saml.idp.*</p>
        </div>

        <!-- ── OAuth2 ── -->
        <div v-show="tab === 'oauth2'" class="tab-content">
          <p class="tab-hint">演示客户端：<code>demo-client</code></p>
          <el-button type="primary" @click="oauth2Authorize" class="submit-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>
            跳转 OAuth2 授权
          </el-button>
        </div>

        <div class="auth-foot">
          <span>© 2026 IAM Platform</span>
          <span class="divider">·</span>
          <a href="#" class="foot-link">技术支持</a>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { api, saveSession } from '../api'

const tab = ref('password')
const loading = ref(false)
const router = useRouter()
const route = useRoute()
const form = ref({ tenantCode: 'default', username: '', password: '' })
const smsForm = ref({ phone: '', code: '' })
const magicEmail = ref('')
const smsCountdown = ref(0)
const touched = ref<Record<string, boolean>>({})

const DEFAULT_LOGIN_METHODS = ['password', 'sms', 'magic', 'social', 'sso', 'oauth2']
const DEFAULT_SOCIAL_PROVIDERS = ['wechat', 'alipay', 'qq', 'dingtalk', 'wecom']

const enabledMethods = ref<string[]>([...DEFAULT_LOGIN_METHODS])
const enabledSocialProviders = ref<string[]>([...DEFAULT_SOCIAL_PROVIDERS])

const allTabs = [
  { key: 'password', label: '密码', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>' },
  { key: 'sms', label: '短信', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/></svg>' },
  { key: 'magic', label: 'Magic Link', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg>' },
  { key: 'social', label: '社交', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>' },
  { key: 'sso', label: '企业 SSO', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>' },
  { key: 'oauth2', label: 'OAuth2', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>' },
]

const tabs = computed(() => {
  const enabled = new Set(enabledMethods.value)
  const filtered = allTabs.filter(t => enabled.has(t.key))
  return filtered.length ? filtered : allTabs.filter(t => t.key === 'password')
})

const allSocialProviders = [
  { id: 'wechat', name: '微信', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="10" r="1.5" fill="currentColor"/><circle cx="16" cy="10" r="1.5" fill="currentColor"/><path d="M12 16c-3.5 0-6-2-6-4.5S8.5 7 12 7s6 2 6 4.5c0 1.2-.7 2.3-1.8 3.2L17 17l-2.5-1.4c-.8.2-1.6.4-2.5.4z"/></svg>' },
  { id: 'alipay', name: '支付宝', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 4h16v16H4z" stroke="none"/><path d="M8 12h8"/><path d="M12 8v8"/></svg>' },
  { id: 'qq', name: 'QQ', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="8" r="4"/><path d="M6 18c0-2 2.5-4 6-4s6 2 6 4"/></svg>' },
  { id: 'dingtalk', name: '钉钉', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 2L2 7l10 5 10-5-10-5z" stroke="none"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>' },
  { id: 'wecom', name: '企业微信', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="2" width="9" height="9" rx="2"/><rect x="13" y="2" width="9" height="9" rx="2"/><rect x="2" y="13" width="9" height="9" rx="2"/><rect x="13" y="13" width="9" height="9" rx="2"/></svg>' },
]

const socialProviders = computed(() => {
  const enabled = new Set(enabledSocialProviders.value)
  return allSocialProviders.filter(p => enabled.has(p.id))
})

async function loadLoginOptions(): Promise<void> {
  try {
    const options = await api.loginOptions()
    enabledMethods.value = normalizeKeys(options.methods, DEFAULT_LOGIN_METHODS)
    enabledSocialProviders.value = normalizeKeys(options.socialProviders, DEFAULT_SOCIAL_PROVIDERS)
    if (!enabledMethods.value.includes(tab.value)) {
      tab.value = tabs.value[0]?.key || 'password'
    }
  } catch (e) {
    enabledMethods.value = [...DEFAULT_LOGIN_METHODS]
    enabledSocialProviders.value = [...DEFAULT_SOCIAL_PROVIDERS]
  }
}

function normalizeKeys(value: string[] | undefined, fallback: string[]): string[] {
  const keys = (value || []).map(v => String(v).trim()).filter(Boolean)
  return keys.length ? [...new Set(keys)] : [...fallback]
}

async function onLogin(): Promise<void> {
  touched.value = { ...touched.value, username: true, password: true }
  if (!form.value.username || !form.value.password) return
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
    const returnTo = route.query.return_to as string | undefined
    if (returnTo?.startsWith('/iam/')) {
      location.href = returnTo
      return
    }
    router.push('/dashboard')
  } catch (e: any) {
    // 后端可能返回 JSON { message: "服务异常" } 或 HTML white-label page — 兼容取 message
    const data = e.response?.data
    const msg = (data && typeof data === 'object' && data.message)
      ? data.message
      : (e.response?.status === 302 ? '后端异常重定向 (302)，请检查后端日志' : '登录失败')
    ElMessage.error(msg)
    console.error('[login]', e.response?.status, data, e)
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
  if (!smsForm.value.phone || !smsForm.value.code) return
  loading.value = true
  try {
    const r = await api.smsLogin(smsForm.value.phone, smsForm.value.code)
    saveSession(r)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: any) {
    const data = e.response?.data
    const msg = (data && typeof data === 'object' && data.message) ? data.message : '登录失败'
    ElMessage.error(msg)
  } finally { loading.value = false }
}

async function onMagicSend(): Promise<void> {
  if (!magicEmail.value) return
  await api.magicSend(magicEmail.value)
  ElMessage.success('登录链接已发送到邮箱')
}

async function social(provider: string): Promise<void> {
  try {
    const url = await api.socialAuthorize(provider)
    location.href = url
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '未配置')
  }
}

async function casLogin(): Promise<void> {
  try {
    const url = await api.casAuthorize()
    location.href = url
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || 'CAS 未配置')
  }
}

function samlLogin(): void {
  location.href = '/iam/saml2/authenticate/default'
}

function oauth2Authorize(): void {
  const url = `/iam/oauth/authorize?response_type=code&client_id=demo-client&redirect_uri=${encodeURIComponent('http://localhost:5173/callback')}&scope=openid`
  location.href = url
}

onMounted(loadLoginOptions)
</script>

<style scoped>
/* ═══════════════════════════════════════════
   Split-screen Login · Stripe / Vercel 风
   ═══════════════════════════════════════════ */
.login-split {
  display: flex;
  min-height: 100vh;
}

/* ── Left hero ── */
.login-hero {
  flex: 1 1 50%;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #0b0d1f 0%, #1c1e54 60%, #26287a 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 72px 64px;
}
.hero-decor {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 60% 50% at 90% 80%, rgba(91, 77, 255, 0.32) 0%, transparent 65%),
    radial-gradient(ellipse 80% 60% at 0% 100%, rgba(111, 92, 240, 0.20) 0%, transparent 70%);
  pointer-events: none;
}
.hero-inner { position: relative; z-index: 1; width: 100%; max-width: 480px; color: #fff; }

.hero-brand { display: flex; align-items: center; gap: 12px; margin-bottom: 56px; }
.hero-mark {
  width: 44px; height: 44px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #5b4dff 0%, #7f73ff 100%);
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  box-shadow: 0 8px 24px -8px rgba(91, 77, 255, 0.6);
}
.hero-mark svg { width: 24px; height: 24px; }
.hero-word {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 1.6rem;
  background: linear-gradient(135deg, #fff 0%, #c9c3ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -0.04em;
}

.hero-title {
  font-family: var(--font-heading);
  font-size: 2.75rem;
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.04em;
  margin-bottom: 18px;
  color: #fff;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}
.hero-sub {
  font-size: 1.05rem;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.7;
  margin-bottom: 36px;
}

.hero-features {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 40px;
}
.hero-features li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  color: rgba(255, 255, 255, 0.85);
  font-size: 0.92rem;
  line-height: 1.6;
}
.feat-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #7f73ff;
  box-shadow: 0 0 10px #7f73ff;
  flex-shrink: 0;
  margin-top: 7px;
}
.hero-features b { color: #fff; font-weight: 600; }

.trust-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.12);
  padding: 6px 14px;
  border-radius: var(--radius-pill);
}

/* ── Right panel ── */
.login-panel {
  flex: 1 1 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background: var(--bg-wash);
  position: relative;
}
.auth-card {
  width: 440px;
  max-width: 100%;
  padding: 40px;
  position: relative;
  z-index: 1;
}
.eyebrow {
  display: inline-block;
  font-family: var(--font-body);
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--accent);
  background: var(--accent-glow);
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  margin-bottom: 14px;
}
.auth-title h1 {
  font-size: 1.6rem;
  color: var(--text-primary);
  letter-spacing: -0.03em;
}
.auth-sub {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 0.9rem;
  margin-bottom: 22px;
}

/* ── Form (stacked labels avoid the el-form-item misalignment issue) ── */
.tab-content { padding-top: 4px; }
.field { margin-bottom: 16px; }
.field-label {
  display: block;
  font-size: 0.82rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.field-error {
  color: var(--danger);
  font-size: 0.72rem;
  margin-top: 4px;
  font-weight: 500;
}
.tab-hint {
  color: var(--text-muted);
  font-size: 0.78rem;
  margin-top: 14px;
  text-align: center;
}
.tab-hint code { padding: 2px 6px; font-size: 0.75rem; }

.submit-btn {
  width: 100%;
  height: 44px !important;
  font-size: 0.95rem !important;
  margin-top: 6px;
}

.input-row { display: flex; gap: 8px; width: 100%; }
.input-row :deep(.el-input__wrapper) { flex: 1; }
.code-btn {
  white-space: nowrap;
  padding: 0 18px !important;
  height: 36px !important;
  align-self: flex-end;
  margin-bottom: 1px;
}

/* ── Social ── */
.social-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  font-family: var(--font-body);
  font-size: 0.85rem;
  font-weight: 500;
}
.social-btn:hover { border-color: var(--accent); color: var(--accent); transform: translateY(-2px); box-shadow: var(--shadow-md); }
.social-btn :deep(svg) { flex-shrink: 0; }

.sso-stack { display: flex; flex-direction: column; gap: 10px; }
.sso-btn { width: 100% !important; height: 44px !important; justify-content: center !important; }
.sso-btn svg { margin-right: 8px; }

/* ── Card footer ── */
.auth-foot {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-muted);
  font-size: 0.78rem;
}
.foot-link { color: var(--text-accent); text-decoration: none; font-weight: 500; }
.foot-link:hover { text-decoration: underline; }
.divider { color: var(--border-hover); }

/* ── Responsive ── */
@media (max-width: 900px) {
  .login-split { flex-direction: column; }
  .login-hero { padding: 40px 28px; }
  .hero-title { font-size: 2rem; }
  .login-panel { padding: 40px 24px; }
  .auth-card { padding: 32px 26px; }
}
@media (max-width: 480px) {
  .hero-features { gap: 12px; }
  .social-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
