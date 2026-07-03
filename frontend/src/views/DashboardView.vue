<template>
  <div class="dashboard-view">
    <!-- Header -->
    <header class="dash-header glass">
      <div class="header-left">
        <div class="logo">
          <svg width="28" height="28" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="4" width="32" height="32" rx="8" stroke="currentColor" stroke-width="2" />
            <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span class="logo-text">IAM 控制台</span>
        </div>
      </div>
      <div class="header-right">
        <button class="icon-btn" title="管理后台" v-if="isAdmin" @click="router.push('/admin')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
        </button>
        <div class="user-menu">
          <button class="user-btn" @click="showMenu = !showMenu">
            <div class="avatar">{{ profile?.username?.charAt(0)?.toUpperCase() || 'U' }}</div>
            <span class="user-name">{{ profile?.username }}</span>
            <svg :class="['chevron', { open: showMenu }]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
          <div v-if="showMenu" class="dropdown glass" @click.self="showMenu = false">
            <div class="dropdown-item" @click="onCmd('setupMfa')">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
              启用 MFA
            </div>
            <div class="dropdown-divider" />
            <div class="dropdown-item danger" @click="onCmd('logout')">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
              登出
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- Main -->
    <main class="dash-main">
      <div class="dash-grid">
        <!-- User Profile Card -->
        <div class="profile-card glass-card animate-in-up" style="--delay: 0">
          <div class="card-header">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
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
              <span class="value"><span class="badge">{{ profile?.tenant }}</span></span>
            </div>
            <div class="profile-row">
              <span class="label">MFA</span>
              <span class="value"><span :class="['badge', profile?.mfaEnabled ? 'success' : 'muted']">{{ profile?.mfaEnabled ? '已启用' : '未启用' }}</span></span>
            </div>
          </div>
        </div>

        <!-- Roles Card -->
        <div class="glass-card animate-in-up" style="--delay: 0.1s">
          <div class="card-header">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            <h3>权限</h3>
          </div>
          <div class="tags-wrap">
            <span v-for="p in permissions" :key="p" class="neo-tag dim">{{ p }}</span>
            <span v-if="!permissions.length" class="no-data">暂无权限</span>
          </div>
        </div>
      </div>
    </main>

    <!-- MFA Dialog -->
    <Transition name="fade">
      <div v-if="mfaDialog.visible" class="overlay" @click="mfaDialog.visible = false">
        <div class="dialog glass-card" @click.stop>
          <div class="dialog-header">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            <h3>启用 MFA (TOTP)</h3>
          </div>
          <div class="dialog-body">
            <template v-if="mfaDialog.uri">
              <p class="step">1. 在 Authenticator 中扫描或手动添加密钥</p>
              <pre class="mfa-uri">{{ mfaDialog.uri }}</pre>
              <p class="step">2. 输入 6 位动态码确认</p>
              <input v-model="mfaDialog.code" class="neo-input" maxlength="6" placeholder="6 位动态码" />
            </template>
          </div>
          <div class="dialog-footer">
            <button class="neo-btn ghost" @click="mfaDialog.visible = false">取消</button>
            <button class="neo-btn primary" style="width:auto" @click="confirmMfa">确认</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { api, hasRole } from '../api'

const profile = ref<any>(null)
const permissions = ref<string[]>([])
const router = useRouter()
const showMenu = ref(false)
const mfaDialog = ref({ visible: false, uri: '', code: '' })
const isAdmin = computed(() => hasRole('ROLE_ADMIN'))

function closeMenu(e: MouseEvent) {
  if (showMenu.value) showMenu.value = false
}
onMounted(() => {
  document.addEventListener('click', closeMenu)
  ;(async () => {
    try {
      profile.value = await api.me()
    } catch { /* token may expire during init */ }
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
    await api.logout()
    router.push('/login')
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
.dash-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  height: 64px;
  border-bottom: 1px solid var(--border);
  background: rgba(11, 15, 26, 0.85);
  backdrop-filter: blur(20px);
}
.header-left, .header-right { display: flex; align-items: center; gap: 16px; }
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--accent);
}
.logo svg { filter: drop-shadow(0 0 8px var(--accent-glow)); }
.logo-text {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--text-primary);
}

.icon-btn {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
}
.icon-btn:hover {
  color: var(--accent);
  border-color: var(--border-hover);
  background: rgba(0,212,255,0.08);
}

/* ── User Menu ── */
.user-menu { position: relative; }
.user-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--border);
  border-radius: 100px;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  color: var(--text-primary);
  font-family: var(--font-body);
}
.user-btn:hover { border-color: var(--border-hover); background: rgba(255,255,255,0.08); }
.avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-dim), var(--secondary));
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
  width: 200px;
  padding: 6px;
  background: rgba(17, 24, 39, 0.95);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  z-index: 200;
  animation: fadeIn 0.15s var(--ease-out);
}
.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 0.85rem;
  cursor: pointer;
  transition: background var(--dur-fast);
}
.dropdown-item:hover { background: rgba(0,212,255,0.1); }
.dropdown-item.danger:hover { background: rgba(255,71,87,0.1); color: var(--danger); }
.dropdown-divider { height: 1px; background: var(--border); margin: 4px 0; }

/* ── Main ── */
.dash-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* ── Grid ── */
.dash-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 24px;
}
@media (max-width: 900px) { .dash-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .dash-grid { grid-template-columns: 1fr; } }

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
}
.card-header svg { color: var(--accent); flex-shrink: 0; }
.card-header h3 {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

/* ── Profile ── */
.profile-rows { display: flex; flex-direction: column; gap: 10px; }
.profile-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.profile-row .label { color: var(--text-secondary); font-size: 0.85rem; }
.profile-row .value { font-size: 0.9rem; font-weight: 500; }

/* ── Badges / Tags ── */
.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 0.8rem;
  font-weight: 600;
  background: rgba(0, 212, 255, 0.12);
  color: var(--accent);
}
.badge.success { background: rgba(46, 213, 115, 0.12); color: var(--success); }
.badge.muted { background: rgba(136, 146, 176, 0.12); color: var(--text-secondary); }

.tags-wrap { display: flex; flex-wrap: wrap; gap: 6px; }
.neo-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  font-weight: 500;
  font-family: var(--font-mono);
}
.neo-tag.accent {
  background: rgba(0, 212, 255, 0.1);
  color: var(--accent);
  border: 1px solid rgba(0, 212, 255, 0.2);
}
.neo-tag.dim {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-secondary);
  border: 1px solid var(--border);
}
.no-data { color: var(--text-muted); font-size: 0.85rem; font-style: italic; }

/* ── MFA Dialog ── */
.overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s;
}
.dialog {
  width: 440px;
  max-width: 90vw;
  padding: 0;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}
.dialog-header svg { color: var(--accent); }
.dialog-header h3 { margin: 0; font-size: 1.1rem; }
.dialog-body { padding: 24px; }
.dialog-body .step {
  color: var(--text-secondary);
  font-size: 0.85rem;
  margin-bottom: 8px;
}
.dialog-body .neo-input { margin-top: 4px; }
.mfa-uri {
  background: rgba(0, 0, 0, 0.3);
  padding: 12px;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 0.75rem;
  word-break: break-all;
  margin-bottom: 18px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  line-height: 1.5;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}
.dialog-footer .neo-btn { margin-bottom: 0; }
.dialog-footer .neo-btn.primary { width: auto; }

/* ── Transitions ── */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.animate-in-up {
  animation: fadeInUp 0.5s var(--ease-out) both;
  animation-delay: calc(var(--delay, 0) + 0.05s);
}
</style>
