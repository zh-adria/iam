<template>
  <div>
    <div class="pane-toolbar">
      <div class="spacer" />
      <button class="neo-btn primary" style="width:auto;padding:8px 20px" @click="onEdit(null)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建租户
      </button>
    </div>

    <el-table :data="rows" v-loading="loading" class="neo-table">
      <el-table-column prop="code" label="编码" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="isolationMode" label="隔离模式" width="160" />
      <el-table-column prop="ldapUrl" label="LDAP URL" min-width="180" show-overflow-tooltip />
      <el-table-column prop="ldapBase" label="LDAP Base" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <span :class="['status-dot', row.enabled ? 'active' : 'inactive']" />
          <span class="status-text">{{ row.enabled ? '启用' : '停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <button class="action-btn" @click="onEdit(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            编辑
          </button>
          <button class="action-btn danger" @click="onDelete(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            删除
          </button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showEdit" title="租户配置" width="520px" class="neo-dialog">
      <div class="dialog-form">
        <div class="input-group">
          <label class="input-label">编码</label>
          <input v-model="form.code" class="neo-input" placeholder="tenant-code" />
        </div>
        <div class="input-group">
          <label class="input-label">名称</label>
          <input v-model="form.name" class="neo-input" placeholder="租户名称" />
        </div>
        <div class="input-group">
          <label class="input-label">隔离模式</label>
          <select v-model="form.isolationMode" class="neo-input">
            <option value="SHARED">SHARED（共享 Schema）</option>
            <option value="SCHEMA_PER_TENANT">SCHEMA_PER_TENANT</option>
          </select>
        </div>
        <div class="input-group">
          <label class="input-label">LDAP URL</label>
          <input v-model="form.ldapUrl" class="neo-input" placeholder="ldap://host:389" />
        </div>
        <div class="input-group">
          <label class="input-label">LDAP Base</label>
          <input v-model="form.ldapBase" class="neo-input" placeholder="dc=iam,dc=local" />
        </div>
        <div class="input-group">
          <label class="input-label">启用</label>
          <label class="switch-label">
            <input type="checkbox" v-model="form.enabled" class="neo-switch" />
            <span class="switch-track"><span class="switch-thumb" /></span>
          </label>
        </div>
      </div>
      <template #footer>
        <button class="neo-btn ghost" @click="showEdit = false">取消</button>
        <button class="neo-btn primary" style="width:auto" @click="onSave">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type TenantRow } from '../../../api/admin'

const rows = ref<TenantRow[]>([])
const loading = ref(false)
const showEdit = ref(false)
const form = ref({ code: '', name: '', isolationMode: 'SHARED', ldapUrl: '', ldapBase: '', enabled: true })

async function load(): Promise<void> {
  loading.value = true
  try { rows.value = await adminApi.listTenants() } finally { loading.value = false }
}
function onEdit(row: TenantRow | null): void {
  if (row) {
    form.value = { code: row.code, name: row.name, isolationMode: row.isolationMode, ldapUrl: row.ldapUrl || '', ldapBase: row.ldapBase || '', enabled: row.enabled }
  } else {
    form.value = { code: '', name: '', isolationMode: 'SHARED', ldapUrl: '', ldapBase: '', enabled: true }
  }
  showEdit.value = true
}
async function onSave(): Promise<void> {
  await adminApi.upsertTenant(form.value)
  ElMessage.success('已保存')
  showEdit.value = false
  await load()
}
async function onDelete(row: TenantRow): Promise<void> {
  await ElMessageBox.confirm(`删除租户 ${row.code}?`, '确认', { type: 'warning' })
  await adminApi.deleteTenant(row.code)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.pane-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
.spacer { flex: 1; }
.status-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; margin-right: 6px; }
.status-dot.active { background: var(--success); box-shadow: 0 0 6px var(--success-glow); }
.status-dot.inactive { background: var(--text-muted); }
.status-text { font-size: 0.85rem; }
.action-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 10px; font-size: 0.78rem;
  font-family: var(--font-body); font-weight: 500;
  background: rgba(255,255,255,0.05); border: 1px solid var(--border);
  border-radius: var(--radius-sm); color: var(--text-secondary);
  cursor: pointer; transition: all var(--dur-fast) var(--ease-out);
}
.action-btn:hover { color: var(--text-primary); border-color: var(--border-hover); background: rgba(255,255,255,0.08); }
.action-btn.danger { color: var(--danger); }
.action-btn.danger:hover { border-color: var(--danger); background: rgba(255,71,87,0.1); }
.dialog-form { display: flex; flex-direction: column; gap: 14px; }
.neo-input { width: 100%; padding: 11px 14px; background: rgba(255,255,255,0.04); border: 1px solid var(--border); border-radius: var(--radius-md); color: var(--text-primary); font-family: var(--font-body); font-size: 0.9rem; outline: none; transition: all var(--dur-fast) var(--ease-out); }
.neo-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.neo-input option { background: var(--bg-secondary); color: var(--text-primary); }
.input-label { display: block; color: var(--text-secondary); font-size: 0.8rem; font-weight: 500; margin-bottom: 6px; }

/* Custom switch */
.switch-label { position: relative; display: inline-block; width: 44px; height: 24px; cursor: pointer; }
.neo-switch { opacity: 0; width: 0; height: 0; }
.switch-track { position: absolute; inset: 0; background: rgba(255,255,255,0.08); border: 1px solid var(--border); border-radius: 12px; transition: all var(--dur-fast) var(--ease-out); }
.switch-thumb { position: absolute; top: 3px; left: 3px; width: 16px; height: 16px; border-radius: 50%; background: var(--text-secondary); transition: all var(--dur-fast) var(--ease-spring); box-shadow: 0 1px 4px rgba(0,0,0,0.3); }
.neo-switch:checked + .switch-track { background: rgba(0, 212, 255, 0.2); border-color: var(--accent); }
.neo-switch:checked + .switch-track .switch-thumb { transform: translateX(20px); background: var(--accent); box-shadow: 0 0 8px var(--accent-glow); }
</style>