<template>
  <div class="dashboard-view">
    <!-- Header -->
    <header class="topbar">
      <div class="topbar-inner">
        <div class="topbar-left">
          <div class="brand-inline">
            <span class="brand-mark">
              <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </span>
            <span class="brand-word">IAM 控制台</span>
          </div>
        </div>
        <div class="topbar-right">
          <button class="icon-btn" title="管理后台" v-if="isAdmin" @click="router.push('/admin')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
          </button>
          <div class="user-menu">
            <button class="user-btn" @click="showMenu = !showMenu">
              <div class="avatar">{{ profile?.username?.charAt(0)?.toUpperCase() || 'U' }}</div>
              <span class="user-name">{{ profile?.username }}</span>
              <svg :class="['chevron', { open: showMenu }]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </button>
            <transition name="fade">
              <div v-if="showMenu" class="dropdown" @click.self="showMenu = false">
                <div class="dropdown-header">
                  <div class="dropdown-name">{{ profile?.username }}</div>
                  <div class="dropdown-email">{{ profile?.email || '—' }}</div>
                </div>
                <div class="dropdown-divider" />
                <div class="dropdown-item" @click="onCmd('setupMfa')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
                  启用 MFA
                </div>
                <div class="dropdown-item" @click="onCmd('logout')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                  登出
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </header>

    <!-- Main -->
    <main class="dash-main">
      <section class="dash-hero animate-in">
        <div class="hero-eyebrow">
          <span class="eyebrow-dot" /> 身份与访问管理
        </div>
        <h1 class="hero-title">欢迎回来，{{ profile?.username || '用户' }}</h1>
        <p class="hero-sub">管理您的认证状态、角色权限与访问策略。</p>
      </section>

      <!-- Quick Stats -->
      <section class="stats-row animate-in" style="--delay:.05s">
        <StatCard
          :value="stats.users"
          label="账户总数"
          delta="+3 今日"
          :delta-up="true"
          accent
        >
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          </template>
        </StatCard>
        <StatCard
          :value="stats.roles"
          label="角色数量"
          delta="+1 本周"
          :delta-up="true"
        >
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          </template>
        </StatCard>
        <StatCard
          :value="stats.mfa + '%'"
          label="MFA 启用率"
          delta="较昨日 ↑ 5%"
          :delta-up="true"
        >
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
          </template>
        </StatCard>
      </section>

      <div class="dash-grid">
        <!-- User Profile Card -->
        <div class="profile-card glass-card accent-edge animate-in-up" style="--delay: 0">
          <div class="card-header">
            <span class="header-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </span>
            <h3>用户信息</h3>
          </div>
          <div class="profile-rows">
            <div class="profile-row">
              <span class="label">用户名</span>
              <span class="value">{{ profile?.username }}</span>
            </div>
            <div class="profile-row">
              <span class="label">邮箱</span>
              <span class="value">{{ profile?.email || '—' }}</span>
            </div>
            <div class="profile-row">
              <span class="label">手机</span>
              <span class="value">{{ profile?.phone || '—' }}</span>
            </div>
            <div class="profile-row">
              <span class="label">租户</span>
              <span class="value"><span class="neo-tag dim">{{ profile?.tenant }}</span></span>
            </div>
            <div class="profile-row">
              <span class="label">MFA</span>
              <span class="value">
                <span :class="['neo-tag', profile?.mfaEnabled ? 'success' : 'danger']">{{ profile?.mfaEnabled ? '已启用' : '未启用' }}</span>
              </span>
            </div>
            <div class="profile-row">
              <span class="label">状态</span>
              <span class="value">
                <span class="status-dot active" />
                <span class="status-text">正常</span>
              </span>
            </div>
          </div>
        </div>

        <!-- Roles Card -->
        <div class="glass-card animate-in-up" style="--delay: 0.1s">
          <div class="card-header">
            <span class="header-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </span>
            <h3>角色</h3>
          </div>
          <div class="tags-wrap">
            <span v-for="r in profile?.roles" :key="r" class="neo-tag accent">{{ r }}</span>
            <span v-if="!profile?.roles?.length" class="no-data">暂无角色</span>
          </div>
        </div>

        <!-- Permissions Card -->
        <div class="glass-card animate-in-up" style="--delay: 0.2s">
          <div class="card-header">
            <span class="header-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            </span>
            <h3>权限</h3>
          </div>
          <div class="tags-wrap">
            <span v-for="p in permissions" :key="p" class="neo-tag dim">{{ p }}</span>
            <span v-if="!permissions.length" class="no-data">暂无权限</span>
          </div>
        </div>
      </div>

      <!-- Quick actions -->
      <section class="quick-actions animate-in-up" style="--delay: 0.3s">
        <h3 class="qa-title">快捷操作</h3>
        <div class="qa-grid">
          <button v-if="isAdmin" class="qa-card" @click="router.push('/admin')">
            <span class="qa-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
            </span>
            <div class="qa-card-text">
              <div class="qa-card-title">管理后台</div>
              <div class="qa-card-desc">用户、角色、权限、审计</div>
            </div>
            <svg class="qa-arrow" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <button class="qa-card" @click="onCmd('setupMfa')">
            <span class="qa-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            </span>
            <div class="qa-card-text">
              <div class="qa-card-title">安全设置</div>
              <div class="qa-card-desc">启用多因素认证</div>
            </div>
            <svg class="qa-arrow" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </section>
    </main>

    <!-- MFA Dialog -->
    <el-dialog v-model="mfaDialog.visible" title="启用 MFA (TOTP)" width="460px" class="neo-dialog">
      <div class="dialog-form">
        <template v-if="mfaDialog.uri">
          <p class="step">1. 在 Authenticator 中扫描或手动添加密钥</p>
          <pre class="mfa-uri">{{ mfaDialog.uri }}</pre>
          <p class="step">2. 输入 6 位动态码确认</p>
          <el-input v-model="mfaDialog.code" maxlength="6" placeholder="6 位动态码" />
        </template>
      </div>
      <template #footer>
        <el-button @click="mfaDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmMfa">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { api, hasRole } from '../api'
import StatCard from '../components/StatCard.vue'

const profile = ref<any>(null)
const permissions = ref<string[]>([])
const router = useRouter()
const showMenu = ref(false)
const mfaDialog = ref({ visible: false, uri: '', code: '' })
const isAdmin = computed(() => hasRole('ROLE_ADMIN'))

const stats = reactive({
  users: 128,
  roles: 8,
  mfa: 64
})

function closeMenu(e: MouseEvent) {
  if (showMenu.value) showMenu.value = false
}
onMounted(() => {
  document.addEventListener('click', closeMenu)
  ;(async () => {
    try {
      profile.value = await api.me()
    } catch {
      ElMessage.warning('会话已过期，请重新登录')
      router.push('/login')
    }
    const token = localStorage.getItem('access_token') || ''
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      permissions.value = payload.perms || []
    } catch { permissions.value = [] }
  })()
})
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))

async function onCmd(cmd: string) {
  showMenu.value = false
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', { confirmButtonText: '退出', cancelButtonText: '取消', type: 'info' })
      await api.logout()
      router.push('/login')
    } catch { /* cancelled */ }
  } else if (cmd === 'setupMfa') {
    const r = await api.setupMfa()
    mfaDialog.value = { visible: true, uri: r.otpauth, code: '' }
  }
}

async function confirmMfa() {
  try {
    await api.confirmMfa(mfaDialog.value.code)
    ElMessage.success('MFA 已启用')
    mfaDialog.value.visible = false
    profile.value = await api.me()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '确认失败')
  }
}
</script>

<style scoped>
.dashboard-view {
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

/* ── Header ── */
.brand-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
}
.icon-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
}

/* ── User Menu ── */
.user-menu { position: relative; }
.user-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px 6px 6px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  color: var(--text-primary);
  font-family: var(--font-body);
}
.user-btn:hover { border-color: var(--border-hover); background: var(--accent-soft); }
.avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dim) 100%);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 0.8rem;
  color: #fff;
}
.user-name { font-size: 0.85rem; font-weight: 500; }
.chevron { transition: transform var(--dur-fast) var(--ease-out); }
.chevron.open { transform: rotate(180deg); }

.dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  width: 240px;
  padding: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 200;
  animation: fadeIn 0.15s var(--ease-out);
}
.dropdown-header { padding: 8px 12px; }
.dropdown-name { font-weight: 600; font-size: 0.88rem; color: var(--text-primary); }
.dropdown-email { font-size: 0.78rem; color: var(--text-muted); margin-top: 2px; }
.dropdown-divider { height: 1px; background: var(--border); margin: 4px 0; }
.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 0.85rem;
  cursor: pointer;
  transition: background var(--dur-fast);
}
.dropdown-item:hover { background: var(--accent-soft); color: var(--accent); }

.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ── Main ── */
.dash-main {
  max-width: 1240px;
  margin: 0 auto;
  padding: 36px 28px 64px;
}

/* Hero */
.dash-hero { margin-bottom: 28px; }
.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--accent);
  background: var(--accent-glow);
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  margin-bottom: 12px;
}
.eyebrow-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
}
.hero-title {
  font-size: 2.25rem;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.04em;
  line-height: 1.1;
}
.hero-sub {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 1rem;
  max-width: 560px;
}

/* ── Quick Stats ── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-bottom: 28px;
}
@media (max-width: 700px) { .stats-row { grid-template-columns: 1fr 1fr; } }
@media (max-width: 480px) { .stats-row { grid-template-columns: 1fr; } }

/* ── Grid ── */
.dash-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
  margin-bottom: 28px;
}
@media (max-width: 900px) { .dash-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .dash-grid { grid-template-columns: 1fr; } }

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.header-icon {
  width: 32px; height: 32px;
  border-radius: var(--radius-md);
  background: var(--accent-glow);
  display: flex; align-items: center; justify-content: center;
  color: var(--accent);
  flex-shrink: 0;
}
.card-header h3 {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

/* ── Profile ── */
.profile-rows { display: flex; flex-direction: column; gap: 0; }
.profile-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px solid var(--border);
  font-size: 0.88rem;
}
.profile-row:last-child { border-bottom: none; }
.profile-row .label { color: var(--text-muted); font-weight: 500; }
.profile-row .value { color: var(--text-primary); font-weight: 500; display: flex; align-items: center; }
.status-dot {
  display: inline-block;
  width: 7px; height: 7px;
  border-radius: 50%;
  margin-right: 6px;
}
.status-dot.active { background: var(--success); box-shadow: 0 0 6px var(--success-glow); }
.status-text { font-weight: 500; }

/* ── Tags ── */
.tags-wrap { display: flex; flex-wrap: wrap; gap: 6px; }
.neo-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: 0.75rem;
  font-weight: 500;
}
.neo-tag.dim { background: var(--bg-tertiary); color: var(--text-secondary); }
.neo-tag.accent { background: var(--accent-glow-strong); color: var(--accent); }
.neo-tag.success { background: var(--success-glow); color: var(--success); }
.neo-tag.danger { background: var(--danger-glow); color: var(--danger); }

.no-data { color: var(--text-muted); font-size: 0.85rem; padding: 8px 0; }

/* ── Quick actions ── */
.qa-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 14px;
  letter-spacing: -0.01em;
}
.qa-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.qa-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  text-align: left;
  font-family: var(--font-body);
  color: var(--text-primary);
}
.qa-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.qa-icon {
  width: 44px; height: 44px;
  border-radius: var(--radius-md);
  background: var(--accent-glow);
  display: flex; align-items: center; justify-content: center;
  color: var(--accent);
  flex-shrink: 0;
}
.qa-card-text { flex: 1; }
.qa-card-title { font-size: 0.95rem; font-weight: 600; }
.qa-card-desc { font-size: 0.82rem; color: var(--text-muted); margin-top: 2px; }
.qa-arrow { color: var(--text-muted); transition: transform var(--dur-fast) var(--ease-out); }
.qa-card:hover .qa-arrow { color: var(--accent); transform: translateX(4px); }

/* ── Dialog ── */
.dialog-form .step { font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 10px; }
.mfa-uri {
  font-size: 0.78rem;
  word-break: break-all;
  padding: 10px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
  margin-bottom: 16px;
  color: var(--text-primary);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
