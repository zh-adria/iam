<template>
  <div>
    <div class="pane-toolbar">
      <input v-model="tenant" class="neo-input" placeholder="按租户过滤" @keyup.enter="load" />
      <button class="neo-btn ghost" style="width:auto;padding:8px 16px" @click="load">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        查询
      </button>
      <div class="spacer" />
      <button class="neo-btn primary" style="width:auto;padding:8px 20px" @click="showCreate = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建用户
      </button>
    </div>

    <el-table :data="rows" v-loading="loading" class="neo-table">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="phone" label="手机" width="120" />
      <el-table-column prop="tenant" label="租户" width="100" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <span :class="['status-dot', row.status === 1 ? 'active' : 'inactive']" />
          <span class="status-text">{{ row.status === 1 ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="150">
        <template #default="{ row }">
          <span v-for="r in row.roles" :key="r" class="neo-tag accent">{{ r }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <button class="action-btn" @click="onReset(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            重置密码
          </button>
          <button class="action-btn" @click="onToggle(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>
            {{ row.status === 1 ? '禁用' : '启用' }}
          </button>
          <button class="action-btn" :disabled="row.status !== 2" @click="onUnlock(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 019.9-1"/></svg>
            解锁
          </button>
          <button class="action-btn danger" @click="onDelete(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            删除
          </button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建用户" width="460px" class="neo-dialog">
      <div class="dialog-form">
        <div class="input-group">
          <label class="input-label">用户名</label>
          <input v-model="form.username" class="neo-input" placeholder="输入用户名" />
        </div>
        <div class="input-group">
          <label class="input-label">密码</label>
          <input v-model="form.password" type="password" class="neo-input" placeholder="输入密码" />
        </div>
        <div class="input-group">
          <label class="input-label">邮箱</label>
          <input v-model="form.email" class="neo-input" placeholder="user@example.com" />
        </div>
        <div class="input-group">
          <label class="input-label">手机</label>
          <input v-model="form.phone" class="neo-input" placeholder="138xxxx" />
        </div>
        <div class="input-group">
          <label class="input-label">租户</label>
          <input v-model="form.tenantCode" class="neo-input" placeholder="default" />
        </div>
      </div>
      <template #footer>
        <button class="neo-btn ghost" @click="showCreate = false">取消</button>
        <button class="neo-btn primary" style="width:auto" @click="onCreate">创建</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type UserRow } from '../../../api/admin'

const rows = ref<UserRow[]>([])
const loading = ref(false)
const tenant = ref('')
const showCreate = ref(false)
const form = ref({ username: '', password: '', email: '', phone: '', tenantCode: 'default' })

async function load(): Promise<void> {
  loading.value = true
  try { rows.value = (await adminApi.listUsers(1, 50, tenant.value || undefined)).rows }
  finally { loading.value = false }
}
async function onCreate(): Promise<void> {
  await adminApi.createUser(form.value)
  ElMessage.success('已创建')
  showCreate.value = false
  await load()
}
async function onReset(row: UserRow): Promise<void> {
  const { value } = await ElMessageBox.prompt('新密码', '重置密码', { inputType: 'password' })
  await adminApi.resetPassword(row.id, value)
  ElMessage.success('已重置')
}
async function onToggle(row: UserRow): Promise<void> {
  await adminApi.setUserStatus(row.id, row.status === 1 ? 0 : 1)
  await load()
}
async function onUnlock(row: UserRow): Promise<void> {
  await adminApi.unlockUser(row.id)
  ElMessage.success('已解锁')
  await load()
}
async function onDelete(row: UserRow): Promise<void> {
  await ElMessageBox.confirm(`删除 ${row.username}?`, '确认', { type: 'warning' })
  await adminApi.deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.pane-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.spacer { flex: 1; }

.neo-input { width: 200px; }

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  font-size: 0.78rem;
  font-family: var(--font-body);
  font-weight: 500;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
  margin-right: 4px;
}
.action-btn:hover { color: var(--text-primary); border-color: var(--border-hover); background: rgba(255,255,255,0.08); }
.action-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.action-btn.danger { color: var(--danger); }
.action-btn.danger:hover { border-color: var(--danger); background: rgba(255,71,87,0.1); }

.neo-tag.accent {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  background: rgba(0, 212, 255, 0.1);
  color: var(--accent);
  border: 1px solid rgba(0, 212, 255, 0.2);
  margin-right: 4px;
  margin-bottom: 2px;
}

.status-dot {
  display: inline-block;
  width: 7px; height: 7px;
  border-radius: 50%;
  margin-right: 6px;
}
.status-dot.active { background: var(--success); box-shadow: 0 0 6px var(--success-glow); }
.status-dot.inactive { background: var(--danger); box-shadow: 0 0 6px var(--danger-glow); }
.status-text { font-size: 0.85rem; }

.dialog-form { display: flex; flex-direction: column; gap: 14px; }
</style>