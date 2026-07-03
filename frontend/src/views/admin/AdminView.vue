<template>
  <div class="admin-view">
    <!-- Sidebar -->
    <nav class="sidebar glass">
      <div class="sidebar-brand">
        <div class="brand-icon">
          <svg width="28" height="28" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="4" width="32" height="32" rx="8" stroke="currentColor" stroke-width="2" />
            <path d="M14 20l4 4 8-8" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div>
          <div class="brand-name">IAM</div>
          <div class="brand-role">管理后台</div>
        </div>
      </div>

      <div class="nav-divider" />

      <div class="nav-items">
        <button v-for="item in navItems" :key="item.key" :class="['nav-item', { active: tab === item.key }]" @click="tab = item.key">
          <span class="nav-icon" v-html="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      </div>

      <div class="sidebar-footer">
        <button class="nav-item back-btn" @click="router.push('/dashboard')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          <span>返回控制台</span>
        </button>
      </div>
    </nav>

    <!-- Content -->
    <main class="admin-content">
      <header class="content-header">
        <h2>{{ currentItem?.label }}</h2>
        <div class="breadcrumb">
          <span>管理后台</span>
          <span class="sep">/</span>
          <span>{{ currentItem?.label }}</span>
        </div>
      </header>
      <div class="content-body">
        <component :is="views[tab]" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, defineComponent, computed } from 'vue'
import { useRouter } from 'vue-router'
import UsersPane from './panes/UsersPane.vue'
import RolesPane from './panes/RolesPane.vue'
import PermsPane from './panes/PermsPane.vue'
import ClientsPane from './panes/ClientsPane.vue'
import TenantsPane from './panes/TenantsPane.vue'
import AuditPane from './panes/AuditPane.vue'
import ConfigPane from './panes/ConfigPane.vue'

const router = useRouter()
const tab = ref('users')

const views: Record<string, ReturnType<typeof defineComponent>> = {
  users: UsersPane,
  roles: RolesPane,
  perms: PermsPane,
  clients: ClientsPane,
  tenants: TenantsPane,
  audit: AuditPane,
  config: ConfigPane
}

const navItems = [
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

const currentItem = computed(() => navItems.find(i => i.key === tab.value))
</script>

<style scoped>
.admin-view {
  display: flex;
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

/* ── Sidebar ── */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  border-right: 1px solid var(--border) !important;
  background: rgba(11, 15, 26, 0.7) !important;
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  height: 100vh;
  border-radius: 0 !important;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 12px;
}
.brand-icon {
  width: 36px; height: 36px;
  color: var(--accent);
  filter: drop-shadow(0 0 8px var(--accent-glow));
  flex-shrink: 0;
}
.brand-name {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 1.3rem;
  background: linear-gradient(135deg, var(--accent), var(--secondary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.03em;
}
.brand-role {
  font-size: 0.7rem;
  color: var(--text-muted);
  font-weight: 500;
}
.nav-divider {
  height: 1px;
  background: var(--border);
  margin: 8px 0 12px;
}
.nav-items { flex: 1; display: flex; flex-direction: column; gap: 2px; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: var(--radius-md);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-body);
  font-size: 0.88rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  width: 100%;
  text-align: left;
}
.nav-item:hover { color: var(--text-primary); background: rgba(255,255,255,0.04); }
.nav-item.active {
  color: var(--accent);
  background: rgba(0, 212, 255, 0.08);
  box-shadow: inset 3px 0 0 var(--accent);
}
.nav-icon { display: flex; flex-shrink: 0; }
.nav-icon svg { width: 18px; height: 18px; }

.sidebar-footer {
  border-top: 1px solid var(--border);
  padding-top: 8px;
  margin-top: 8px;
}
.back-btn { color: var(--text-muted); }
.back-btn:hover { color: var(--danger) !important; }

/* ── Content ── */
.admin-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.content-header {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 20px 32px;
  background: rgba(11, 15, 26, 0.8);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--border);
}
.content-header h2 {
  font-size: 1.3rem;
  color: var(--text-primary);
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 0.8rem;
}
.breadcrumb .sep { color: var(--border-hover); }

.content-body {
  flex: 1;
  padding: 24px 32px;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .sidebar { width: 200px; }
  .content-body { padding: 16px; }
}
</style>