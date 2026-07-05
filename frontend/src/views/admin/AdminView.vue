<template>
  <div class="admin-view">
    <!-- Sidebar -->
    <nav class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-icon">
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div>
          <div class="brand-name">IAM</div>
          <div class="brand-role">管理后台</div>
        </div>
      </div>

      <div class="nav-section">管理</div>

      <div class="nav-items">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="['nav-item', { active: tab === item.key }]"
          @click="tab = item.key"
        >
          <span class="nav-icon" v-html="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      </div>

      <div class="sidebar-footer">
        <el-button text class="back-btn" @click="router.push('/dashboard')">
          <el-icon><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg></el-icon>
          返回控制台
        </el-button>
      </div>
    </nav>

    <!-- Content -->
    <main class="admin-content">
      <header class="topbar">
        <div class="topbar-inner">
          <div class="topbar-left">
            <h2>{{ currentItem?.label }}</h2>
            <div class="breadcrumb-bar">
              <span>管理后台</span>
              <span class="sep">/</span>
              <span>{{ currentItem?.label }}</span>
            </div>
          </div>
          <div class="topbar-right">
            <el-button text class="icon-btn" title="返回控制台" @click="router.push('/dashboard')">
              <el-icon><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg></el-icon>
            </el-button>
            <div class="user-menu">
              <div class="user-dropdown" v-if="userMenuOpen" @mousedown.self="userMenuOpen = false">
                <div class="menu-item" @click="onUserCmd('back')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
                  返回控制台
                </div>
                <div class="menu-divider"></div>
                <div class="menu-item danger" @click="onUserCmd('logout')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                  登出
                </div>
              </div>
              <button class="user-btn" @click.stop="userMenuOpen = !userMenuOpen" ref="userBtn">
                <div class="avatar">{{ avatarText }}</div>
                <svg :class="['chevron', { open: userMenuOpen }]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </button>
            </div>
          </div>
        </div>
      </header>
      <div class="content-body">
        <component :is="views[tab]" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, defineComponent, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, hasRole } from '../../api'
import UsersPane from './panes/UsersPane.vue'
import RolesPane from './panes/RolesPane.vue'
import PermsPane from './panes/PermsPane.vue'
import ClientsPane from './panes/ClientsPane.vue'
import TenantsPane from './panes/TenantsPane.vue'
import AuditPane from './panes/AuditPane.vue'
import ConfigPane from './panes/ConfigPane.vue'

const router = useRouter()
const tab = ref('users')
const userMenuOpen = ref(false)
const username = ref('')

type Cmd = 'back' | 'logout'
const onUserCmd = async (c: Cmd | string) => {
  if (c === 'back') {
    router.push('/dashboard')
  } else if (c === 'logout') {
    try {
      await api.logout()
      ElMessage.success('已登出')
    } catch { /* ignore */ }
    router.push('/login')
  }
}

type NavItem = { key: string; label: string; icon: string }
const navItems: NavItem[] = [
  { key: 'users', label: '用户管理',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>' },
  { key: 'roles', label: '角色管理',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>' },
  { key: 'perms', label: '权限管理',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>' },
  { key: 'clients', label: 'OAuth2 客户端',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>' },
  { key: 'tenants', label: '租户管理',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>' },
  { key: 'audit', label: '审计日志',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>' },
  { key: 'config', label: '系统配置',
    icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>' },
]

const views: Record<string, ReturnType<typeof defineComponent>> = {
  users: UsersPane,
  roles: RolesPane,
  perms: PermsPane,
  clients: ClientsPane,
  tenants: TenantsPane,
  audit: AuditPane,
  config: ConfigPane
}

const currentItem = computed(() => navItems.find(i => i.key === tab.value))
const isAdmin = computed(() => hasRole('ROLE_ADMIN'))

onMounted(async () => {
  try {
    const me = await api.me()
    username.value = String(me.username || '')
  } catch { /* ignore */ }
})

const avatarText = computed(() => (username.value || 'U').charAt(0).toUpperCase())
</script>

<style scoped>
.admin-view {
  display: flex;
  min-height: 100vh;
  position: relative;
  z-index: 1;
  background: var(--bg-wash);
}

/* ═══════════════════════════════════════════
   Sidebar — deep indigo, stable dark surface
   ═══════════════════════════════════════════ */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
  background: #0b0d1f;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  color: #c9c3ff;
  position: sticky;
  top: 0;
  height: 100vh;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 18px;
}
.brand-icon {
  width: 32px; height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6f5cf0, #1c1e54);
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}
.brand-icon svg { width: 18px; height: 18px; }
.brand-name {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 1.2rem;
  color: #fff;
  letter-spacing: -0.03em;
  line-height: 1;
}
.brand-role {
  font-size: 0.65rem;
  color: rgba(201, 195, 255, 0.55);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-top: 2px;
}
.nav-section {
  padding: 12px 10px 6px;
  font-size: 0.66rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: rgba(201, 195, 255, 0.32);
}
.nav-items { flex: 1; display: flex; flex-direction: column; gap: 1px; overflow-y: auto; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  border: none;
  background: transparent;
  color: rgba(201, 195, 255, 0.7);
  font-family: var(--font-body);
  font-size: 0.86rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  width: 100%;
  text-align: left;
}
.nav-item:hover { color: #fff; background: rgba(255, 255, 255, 0.04); }
.nav-item.active {
  color: #fff;
  background: linear-gradient(90deg, rgba(91, 77, 255, 0.18), transparent);
  box-shadow: inset 3px 0 0 0 var(--accent);
}
.nav-icon { display: flex; flex-shrink: 0; }
.nav-icon svg { width: 18px; height: 18px; }

.sidebar-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 8px;
  margin-top: 8px;
}
.back-btn {
  width: 100% !important;
  justify-content: flex-start !important;
  color: rgba(201, 195, 255, 0.55) !important;
  padding: 8px 12px !important;
}
.back-btn:hover { color: #fff !important; }

/* ═══════════════════════════════════════════
   Content — light canvas (same as dashboard)
   ═══════════════════════════════════════════ */
.admin-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-wash);
}
.admin-content .topbar { background: var(--bg-card); }
.admin-content .topbar-inner { max-width: 1400px; }
.admin-content .topbar-left h2 {
  font-size: 0.95rem;
  font-weight: 700;
}

.icon-btn { color: var(--text-secondary) !important; }
.icon-btn:hover { color: var(--accent) !important; }

.user-menu { position: relative; }
.user-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 6px 6px;
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
.chevron { transition: transform var(--dur-fast) ease; }
.chevron.open { transform: rotate(180deg); }

.user-menu { position: relative; }
.user-dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 160px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  z-index: 100;
  padding: 4px;
  display: flex;
  flex-direction: column;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--text-primary);
  transition: background 0.15s;
  white-space: nowrap;
}
.menu-item:hover { background: var(--accent-soft); }
.menu-item.danger { color: #f56c6c; }
.menu-item.danger:hover { background: rgba(245, 108, 108, 0.08); }
.menu-divider { height: 1px; background: var(--border); margin: 4px 0; }

.content-body {
  flex: 1;
  padding: 24px 28px;
  min-height: 0;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .sidebar { width: 200px; }
  .content-body { padding: 16px; }
}
</style>
