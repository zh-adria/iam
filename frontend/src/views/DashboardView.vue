<template>
  <div class="dashboard-view">
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
            <button class="user-btn" @click.stop="showMenu = !showMenu">
              <div class="avatar">{{ avatarText }}</div>
              <span class="user-name">{{ profile?.username || '用户' }}</span>
              <svg :class="['chevron', { open: showMenu }]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </button>
            <transition name="fade">
              <div v-if="showMenu" class="dropdown" @click.self="showMenu = false">
                <div class="dropdown-header">
                  <div class="dropdown-name">{{ profile?.username || '用户' }}</div>
                  <div class="dropdown-email">{{ profile?.email || '—' }}</div>
                </div>
                <div class="dropdown-divider" />
                <div class="dropdown-item" @click.stop="onCmd('setupMfa')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
                  启用 MFA
                </div>
                <div class="dropdown-item danger" @click.stop="onCmd('logout')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                  登出
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </header>

    <main class="dash-main">
      <section class="dash-hero animate-in">
        <div>
          <div class="hero-eyebrow">
            <span class="eyebrow-dot" /> 安全活跃概览
          </div>
          <h1 class="hero-title">欢迎回来，{{ profile?.username || '用户' }}</h1>
          <p class="hero-sub">租户 {{ profile?.tenant || 'default' }} · {{ sessionLabel }}</p>
        </div>
        <button v-if="isAdmin" class="primary-link" @click="router.push('/admin')">
          管理后台
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </section>

      <section class="stats-row animate-in" style="--delay:.05s">
        <StatCard :value="primaryAccountValue" :label="isAdmin ? '活跃账户' : '会话状态'" :delta="accountDelta" :delta-up="true" accent>
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          </template>
        </StatCard>
        <StatCard :value="auditTodayValue" :label="isAdmin ? '今日审计事件' : '登录租户'" :delta="auditDelta" :delta-up="auditFailures === 0">
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/></svg>
          </template>
        </StatCard>
        <StatCard :value="mfaValue" :label="isAdmin ? 'MFA 覆盖' : 'MFA 状态'" :delta="mfaDelta" :delta-up="mfaHealthy">
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
          </template>
        </StatCard>
        <StatCard :value="permissions.length" label="当前权限" :delta="roleDelta" :delta-up="true">
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          </template>
        </StatCard>
      </section>

      <section v-if="isAdmin" class="dashboard-grid">
        <article class="panel glass-card trend-panel animate-in-up" style="--delay:.08s">
          <div class="panel-head">
            <div>
              <h3>访问活跃趋势</h3>
              <p>最近 7 天审计事件</p>
            </div>
            <span class="neo-tag dim">{{ isAdmin ? `${auditRows.length} 条样本` : '个人视图' }}</span>
          </div>
          <div class="trend-chart" :class="{ empty: !isAdmin || !trendBars.length }">
            <div v-if="isAdmin && trendBars.length" v-for="bar in trendBars" :key="bar.label" class="trend-bar">
              <div class="bar-track">
                <span class="bar-fill" :style="{ height: `${bar.height}%` }" />
              </div>
              <span class="bar-value">{{ bar.value }}</span>
              <span class="bar-label">{{ bar.label }}</span>
            </div>
            <div v-else class="empty-state">暂无可展示的审计趋势</div>
          </div>
        </article>

        <article class="panel glass-card animate-in-up" style="--delay:.14s">
          <div class="panel-head compact">
            <div>
              <h3>安全信号</h3>
              <p>账户、MFA 与失败事件</p>
            </div>
          </div>
          <div class="signal-list">
            <div class="signal-row">
              <span class="signal-dot success" />
              <span class="signal-label">账户启用率</span>
              <span class="signal-value">{{ activeRateLabel }}</span>
            </div>
            <div class="signal-row">
              <span :class="['signal-dot', mfaHealthy ? 'success' : 'warning']" />
              <span class="signal-label">MFA 状态</span>
              <span class="signal-value">{{ mfaSignalLabel }}</span>
            </div>
            <div class="signal-row">
              <span :class="['signal-dot', auditFailures ? 'danger' : 'success']" />
              <span class="signal-label">失败事件</span>
              <span class="signal-value">{{ isAdmin ? auditFailures : '—' }}</span>
            </div>
            <div class="signal-row">
              <span class="signal-dot info" />
              <span class="signal-label">角色范围</span>
              <span class="signal-value">{{ roleList.length }}</span>
            </div>
          </div>
        </article>

        <article class="panel glass-card recent-panel animate-in-up" style="--delay:.2s">
          <div class="panel-head">
            <div>
              <h3>最近审计</h3>
              <p>最新访问与管理动作</p>
            </div>
          </div>
          <div class="event-list">
            <div v-if="recentEvents.length" v-for="event in recentEvents" :key="event.id" class="event-row">
              <span :class="['event-status', isSuccess(event.result) ? 'success' : 'danger']" />
              <div class="event-main">
                <div class="event-title">{{ translateAction(event.action) }}</div>
                <div class="event-meta">{{ event.principal || 'system' }} · {{ formatTime(event.occurredAt) }}</div>
              </div>
              <span class="neo-tag dim">{{ translateResult(event.result) }}</span>
            </div>
            <div v-else class="empty-state">暂无审计事件</div>
          </div>
        </article>

        <article class="panel glass-card identity-panel animate-in-up" style="--delay:.26s">
          <div class="panel-head">
            <div>
              <h3>访问范围</h3>
              <p>当前会话角色与权限</p>
            </div>
          </div>
          <div class="identity-grid">
            <div class="identity-block">
              <span class="block-label">角色</span>
              <div class="tags-wrap">
                <span v-for="role in roleList" :key="role" class="neo-tag accent">{{ translateRole(role) }}</span>
                <span v-if="!roleList.length" class="no-data">暂无角色</span>
              </div>
            </div>
            <div class="identity-block">
              <span class="block-label">权限</span>
              <div class="tags-wrap scroll-tags">
                <span v-for="perm in permissions" :key="perm" class="neo-tag dim">{{ translatePermission(perm) }}</span>
                <span v-if="!permissions.length" class="no-data">暂无权限</span>
              </div>
            </div>
          </div>
        </article>
      </section>

      <section v-else class="dashboard-grid personal-grid">
        <article class="panel glass-card animate-in-up" style="--delay:.08s">
          <div class="panel-head">
            <div>
              <h3>个人安全</h3>
              <p>当前账号保护状态</p>
            </div>
          </div>
          <div class="signal-list">
            <div class="signal-row">
              <span class="signal-dot success" />
              <span class="signal-label">账号状态</span>
              <span class="signal-value">正常</span>
            </div>
            <div class="signal-row">
              <span :class="['signal-dot', profile?.mfaEnabled ? 'success' : 'warning']" />
              <span class="signal-label">多因素认证</span>
              <span class="signal-value">{{ profile?.mfaEnabled ? '已启用' : '未启用' }}</span>
            </div>
            <div class="signal-row">
              <span class="signal-dot info" />
              <span class="signal-label">所属租户</span>
              <span class="signal-value">{{ profile?.tenant || 'default' }}</span>
            </div>
            <div class="signal-row">
              <span class="signal-dot info" />
              <span class="signal-label">角色数量</span>
              <span class="signal-value">{{ roleList.length }}</span>
            </div>
          </div>
        </article>

        <article class="panel glass-card identity-panel animate-in-up" style="--delay:.14s">
          <div class="panel-head">
            <div>
              <h3>访问权限</h3>
              <p>当前会话可访问范围</p>
            </div>
          </div>
          <div class="identity-grid">
            <div class="identity-block">
              <span class="block-label">角色</span>
              <div class="tags-wrap">
                <span v-for="role in roleList" :key="role" class="neo-tag accent">{{ translateRole(role) }}</span>
                <span v-if="!roleList.length" class="no-data">暂无角色</span>
              </div>
            </div>
            <div class="identity-block">
              <span class="block-label">权限</span>
              <div class="tags-wrap scroll-tags">
                <span v-for="perm in permissions" :key="perm" class="neo-tag dim">{{ translatePermission(perm) }}</span>
                <span v-if="!permissions.length" class="no-data">暂无权限</span>
              </div>
            </div>
          </div>
        </article>
      </section>
    </main>

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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { api, hasRole } from '../api'
import { adminApi, type AuditRow, type UserRow } from '../api/admin'
import StatCard from '../components/StatCard.vue'

type Profile = {
  username?: string
  email?: string
  phone?: string
  tenant?: string
  mfaEnabled?: boolean
  roles?: string[]
}

type TrendBar = { label: string; value: number; height: number }

const router = useRouter()
const profile = ref<Profile | null>(null)
const permissions = ref<string[]>([])
const showMenu = ref(false)
const mfaDialog = ref({ visible: false, uri: '', code: '' })
const auditRows = ref<AuditRow[]>([])
const users = ref<UserRow[]>([])
const rolesTotal = ref(0)

const isAdmin = computed(() => hasRole('ROLE_ADMIN'))
const avatarText = computed(() => (profile.value?.username || 'U').charAt(0).toUpperCase())
const roleList = computed(() => profile.value?.roles || [])
const activeUsers = computed(() => users.value.filter(u => u.status === 1).length)
const mfaUsers = computed(() => users.value.filter(u => u.mfaEnabled).length)
const auditFailures = computed(() => auditRows.value.filter(row => !isSuccess(row.result)).length)
const todayAudits = computed(() => auditRows.value.filter(row => isToday(row.occurredAt)).length)

const primaryAccountValue = computed(() => isAdmin.value ? activeUsers.value : '正常')
const accountDelta = computed(() => isAdmin.value ? `${users.value.length} 总账户` : '当前会话有效')
const auditTodayValue = computed(() => isAdmin.value ? todayAudits.value : (profile.value?.tenant || 'default'))
const auditDelta = computed(() => isAdmin.value ? `${auditFailures.value} 失败` : '当前租户')
const mfaValue = computed(() => {
  if (!isAdmin.value) return profile.value?.mfaEnabled ? '已启用' : '未启用'
  if (!users.value.length) return '—'
  return `${Math.round((mfaUsers.value / users.value.length) * 100)}%`
})
const mfaHealthy = computed(() => isAdmin.value ? mfaUsers.value >= users.value.length / 2 : !!profile.value?.mfaEnabled)
const mfaDelta = computed(() => isAdmin.value ? `${mfaUsers.value}/${users.value.length} 用户` : (profile.value?.mfaEnabled ? '已保护' : '待开启'))
const roleDelta = computed(() => isAdmin.value ? `${rolesTotal.value} 角色` : `${roleList.value.length} 个角色`)
const activeRateLabel = computed(() => {
  if (!isAdmin.value || !users.value.length) return '—'
  return `${Math.round((activeUsers.value / users.value.length) * 100)}%`
})
const mfaSignalLabel = computed(() => isAdmin.value ? mfaValue.value : (profile.value?.mfaEnabled ? '已启用' : '未启用'))
const sessionLabel = computed(() => {
  if (isAdmin.value) return '管理员会话'
  return roleList.value.length ? roleList.value.map(translateRole).join(' · ') : '普通会话'
})
const recentEvents = computed(() => auditRows.value.slice(0, 6))
const trendBars = computed<TrendBar[]>(() => {
  const buckets = lastSevenDays()
  for (const row of auditRows.value) {
    const key = dayKey(row.occurredAt)
    if (key && buckets.has(key)) buckets.set(key, (buckets.get(key) || 0) + 1)
  }
  const values = [...buckets.values()]
  const max = Math.max(1, ...values)
  return [...buckets.entries()].map(([label, value]) => ({ label, value, height: Math.max(8, Math.round((value / max) * 100)) }))
})

onMounted(loadDashboard)

async function loadDashboard(): Promise<void> {
  try {
    profile.value = await api.me()
  } catch {
    ElMessage.warning('会话已过期，请重新登录')
    router.push('/login')
    return
  }
  permissions.value = decodePermissions()
  if (isAdmin.value) await loadAdminMetrics()
}

async function loadAdminMetrics(): Promise<void> {
  try {
    const [userPage, rolePage, auditPage] = await Promise.all([
      adminApi.listUsers(1, 500),
      adminApi.listRoles(1, 500),
      adminApi.listAudit(1, 200)
    ])
    users.value = userPage.rows
    rolesTotal.value = Number(rolePage.total || rolePage.rows.length)
    auditRows.value = auditPage.rows
  } catch (e: any) {
    ElMessage.warning(e.response?.data?.message || '管理指标加载失败')
  }
}

async function onCmd(cmd: string): Promise<void> {
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

async function confirmMfa(): Promise<void> {
  try {
    await api.confirmMfa(mfaDialog.value.code)
    ElMessage.success('MFA 已启用')
    mfaDialog.value.visible = false
    profile.value = await api.me()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '确认失败')
  }
}

function decodePermissions(): string[] {
  const token = localStorage.getItem('access_token') || ''
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.perms || []
  } catch {
    return []
  }
}

const ROLE_LABELS: Record<string, string> = {
  ROLE_ADMIN: '系统管理员',
  ROLE_USER: '普通用户',
  ROLE_AUDITOR: '审计员',
  ROLE_STAFF: '员工',
  ROLE_DEVELOPER: '开发人员'
}

const PERMISSION_LABELS: Record<string, string> = {
  'iam:user:assign-role': '用户分配角色',
  'iam:user:create': '创建用户',
  'iam:user:delete': '删除用户',
  'iam:role:create': '创建角色',
  'iam:role:grant': '角色授权',
  'iam:permission:create': '创建权限',
  'iam:permission:delete': '删除权限',
  'iam:client:create': '注册 OAuth2 客户端',
  'iam:tenant:write': '配置租户',
  'iam:config:read': '读取系统配置',
  'iam:audit:read': '读取审计日志',
  'iam:menu:dashboard': '访问仪表盘'
}

const RESOURCE_LABELS: Record<string, string> = {
  user: '用户',
  role: '角色',
  permission: '权限',
  client: '客户端',
  tenant: '租户',
  config: '系统配置',
  audit: '审计日志',
  menu: '菜单'
}

const ACTION_LABELS: Record<string, string> = {
  'assign-role': '分配角色',
  create: '创建',
  delete: '删除',
  grant: '授权',
  read: '读取',
  write: '配置',
  dashboard: '仪表盘'
}

const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN: '账号登录',
  LOGIN_MFA_PENDING: '登录待 MFA 验证',
  MFA_FAIL: 'MFA 验证失败',
  MFA_PASS: 'MFA 验证通过',
  TOKEN_REFRESH: '刷新令牌',
  TOKEN_ISSUE: '签发令牌',
  LOGOUT: '退出登录',
  LDAP_LOGIN: 'LDAP 登录',
  SAML_LOGIN: 'SAML 登录',
  CAS_LOGIN: 'CAS 登录',
  SMS_LOGIN: '短信登录',
  MAGIC_LINK_SEND: '发送 Magic Link',
  MAGIC_LINK_LOGIN: 'Magic Link 登录',
  SOCIAL_LOGIN: '社交登录',
  OAUTH_AUTHORIZE: 'OAuth2 授权',
  OAUTH_TOKEN: 'OAuth2 换取令牌'
}

const RESULT_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  OK: '成功',
  FAIL: '失败',
  FAILED: '失败',
  DENY: '拒绝',
  ALLOW: '允许'
}

function translateRole(code?: string): string {
  if (!code) return '未分配角色'
  return ROLE_LABELS[code] || '自定义角色'
}

function translatePermission(code?: string): string {
  if (!code) return '未命名权限'
  if (PERMISSION_LABELS[code]) return PERMISSION_LABELS[code]
  const parts = code.split(':')
  if (parts.length >= 3) {
    const resource = RESOURCE_LABELS[parts[1]] || '业务资源'
    const action = ACTION_LABELS[parts.slice(2).join(':')] || '访问'
    return `${resource}${action}`
  }
  return '自定义权限'
}

function translateAction(code?: string): string {
  if (!code) return '未知操作'
  return AUDIT_ACTION_LABELS[code] || '业务操作'
}

function translateResult(code?: string): string {
  if (!code) return '未知'
  return RESULT_LABELS[String(code).toUpperCase()] || '其他'
}

function isSuccess(result?: string): boolean {
  const value = String(result || '').toUpperCase()
  return !value || value.includes('SUCCESS') || value.includes('OK') || value.includes('ALLOW')
}

function isToday(value?: string): boolean {
  if (!value) return false
  const date = new Date(value)
  const now = new Date()
  return date.toDateString() === now.toDateString()
}

function dayKey(value?: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function lastSevenDays(): Map<string, number> {
  const map = new Map<string, number>()
  for (let i = 6; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    map.set(`${date.getMonth() + 1}/${date.getDate()}`, 0)
  }
  return map
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
</script>

<style scoped>
.dashboard-view { min-height: 100vh; position: relative; z-index: 1; }
.brand-inline { display: flex; align-items: center; gap: 8px; }
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
.icon-btn:hover { color: var(--accent); border-color: var(--accent); background: var(--accent-soft); }
.user-menu { position: relative; }
.user-btn {
  display: flex; align-items: center; gap: 10px;
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
}
.dropdown-header { padding: 8px 12px; }
.dropdown-name { font-weight: 600; font-size: 0.88rem; color: var(--text-primary); }
.dropdown-email { font-size: 0.78rem; color: var(--text-muted); margin-top: 2px; }
.dropdown-divider { height: 1px; background: var(--border); margin: 4px 0; }
.dropdown-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 0.85rem;
  cursor: pointer;
  transition: background var(--dur-fast);
}
.dropdown-item:hover { background: var(--accent-soft); color: var(--accent); }
.dropdown-item.danger { color: var(--danger); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.dash-main {
  max-width: 1320px;
  margin: 0 auto;
  padding: 32px 28px 64px;
}
.dash-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}
.hero-eyebrow {
  display: inline-flex; align-items: center; gap: 8px;
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
  font-size: 2rem;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.hero-sub { margin-top: 8px; color: var(--text-muted); font-size: 0.95rem; }
.primary-link {
  display: inline-flex; align-items: center; gap: 6px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--accent);
  border-radius: var(--radius-md);
  color: var(--accent);
  background: var(--accent-soft);
  font-weight: 600;
  cursor: pointer;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.75fr);
  gap: 18px;
}
.panel { padding: 18px; min-width: 0; }
.trend-panel, .identity-panel { grid-column: span 1; }
.recent-panel { grid-column: span 1; }
.panel-head {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 12px;
  margin-bottom: 16px;
}
.panel-head h3 {
  margin: 0;
  font-size: 1rem;
  color: var(--text-primary);
  font-weight: 700;
}
.panel-head p {
  margin: 4px 0 0;
  font-size: 0.78rem;
  color: var(--text-muted);
}
.trend-chart {
  min-height: 260px;
  display: grid;
  grid-template-columns: repeat(7, minmax(42px, 1fr));
  align-items: end;
  gap: 12px;
}
.trend-chart.empty { display: flex; align-items: center; justify-content: center; }
.trend-bar {
  height: 240px;
  display: grid;
  grid-template-rows: 1fr auto auto;
  gap: 6px;
  text-align: center;
}
.bar-track {
  position: relative;
  width: 100%;
  min-height: 160px;
  border-radius: 7px;
  background: var(--bg-tertiary);
  overflow: hidden;
}
.bar-fill {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  min-height: 8px;
  border-radius: 7px 7px 0 0;
  background: linear-gradient(180deg, var(--accent), var(--accent-dim));
}
.bar-value { font-size: 0.76rem; font-weight: 700; color: var(--text-primary); }
.bar-label { font-size: 0.72rem; color: var(--text-muted); }
.signal-list { display: flex; flex-direction: column; gap: 10px; }
.signal-row {
  min-height: 42px;
  display: grid;
  grid-template-columns: 10px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 0 2px;
  border-bottom: 1px solid var(--border);
}
.signal-row:last-child { border-bottom: none; }
.signal-dot, .event-status {
  width: 8px; height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.signal-dot.success, .event-status.success { background: var(--success); box-shadow: 0 0 8px var(--success-glow); }
.signal-dot.warning { background: #e6a23c; box-shadow: 0 0 8px rgba(230, 162, 60, .24); }
.signal-dot.danger, .event-status.danger { background: var(--danger); box-shadow: 0 0 8px var(--danger-glow); }
.signal-dot.info { background: var(--accent); box-shadow: 0 0 8px var(--accent-glow); }
.signal-label { font-size: 0.85rem; color: var(--text-secondary); }
.signal-value { font-size: 0.86rem; color: var(--text-primary); font-weight: 700; }
.event-list { display: flex; flex-direction: column; gap: 10px; min-height: 260px; }
.event-row {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 44px;
}
.event-title {
  color: var(--text-primary);
  font-size: 0.85rem;
  font-weight: 650;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.event-meta { margin-top: 2px; color: var(--text-muted); font-size: 0.74rem; }
.identity-grid { display: grid; grid-template-columns: 1fr; gap: 18px; }
.identity-block { min-width: 0; }
.block-label {
  display: block;
  color: var(--text-muted);
  font-size: 0.78rem;
  font-weight: 600;
  margin-bottom: 8px;
}
.tags-wrap { display: flex; flex-wrap: wrap; gap: 7px; }
.scroll-tags { max-height: 134px; overflow-y: auto; padding-right: 4px; }
.neo-tag {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}
.neo-tag.dim { background: var(--bg-tertiary); color: var(--text-secondary); }
.neo-tag.accent { background: var(--accent-glow-strong); color: var(--accent); }
.empty-state, .no-data {
  color: var(--text-muted);
  font-size: 0.84rem;
  padding: 12px 0;
}
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
@media (max-width: 1000px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .dashboard-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .dash-main { padding: 24px 16px 48px; }
  .dash-hero { align-items: flex-start; flex-direction: column; }
  .hero-title { font-size: 1.55rem; }
  .stats-row { grid-template-columns: 1fr; }
  .trend-chart { gap: 7px; grid-template-columns: repeat(7, minmax(26px, 1fr)); }
  .bar-label, .bar-value { font-size: 0.66rem; }
  .user-name { display: none; }
}
</style>
