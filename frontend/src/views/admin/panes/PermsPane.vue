<template>
  <div>
    <div class="pane-toolbar">
      <button class="neo-btn primary" style="width:auto;padding:8px 20px" @click="showCreate = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建权限
      </button>
      <button class="neo-btn ghost" style="width:auto;padding:8px 16px" @click="showGrant = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
        角色授权
      </button>
    </div>

    <el-table :data="rows" v-loading="loading" class="neo-table">
      <el-table-column prop="code" label="编码" min-width="160" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="resource" label="资源" min-width="120" />
      <el-table-column prop="action" label="动作" width="100" />
      <el-table-column prop="spel" label="SpEL" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <button class="action-btn danger" @click="onDelete(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            删除
          </button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建权限" width="480px" class="neo-dialog">
      <div class="dialog-form">
        <div class="input-group">
          <label class="input-label">编码</label>
          <input v-model="form.code" class="neo-input" placeholder="user:create" />
        </div>
        <div class="input-group">
          <label class="input-label">类型</label>
          <select v-model="form.type" class="neo-input">
            <option value="API">API</option>
            <option value="MENU">MENU</option>
            <option value="BUTTON">BUTTON</option>
            <option value="DATA">DATA</option>
          </select>
        </div>
        <div class="input-group">
          <label class="input-label">名称</label>
          <input v-model="form.name" class="neo-input" placeholder="创建用户" />
        </div>
        <div class="input-group">
          <label class="input-label">资源</label>
          <input v-model="form.resource" class="neo-input" placeholder="/api/users" />
        </div>
        <div class="input-group">
          <label class="input-label">动作</label>
          <input v-model="form.action" class="neo-input" placeholder="CREATE" />
        </div>
        <div class="input-group">
          <label class="input-label">SpEL</label>
          <input v-model="form.spel" class="neo-input" placeholder="#tenant == authentication.details.tenant" />
        </div>
      </div>
      <template #footer>
        <button class="neo-btn ghost" @click="showCreate = false">取消</button>
        <button class="neo-btn primary" style="width:auto" @click="onCreate">创建</button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGrant" title="角色授权" width="400px" class="neo-dialog">
      <div class="dialog-form">
        <div class="input-group">
          <label class="input-label">角色编码</label>
          <input v-model="grant.role" class="neo-input" placeholder="ROLE_ADMIN" />
        </div>
        <div class="input-group">
          <label class="input-label">权限编码</label>
          <input v-model="grant.perm" class="neo-input" placeholder="user:create" />
        </div>
      </div>
      <template #footer>
        <button class="neo-btn ghost" @click="showGrant = false">取消</button>
        <button class="neo-btn primary" style="width:auto" @click="onGrant">授权</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type PermRow } from '../../../api/admin'

const rows = ref<PermRow[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showGrant = ref(false)
const form = ref({ code: '', type: 'API', name: '', resource: '', action: '', spel: '' })
const grant = ref({ role: '', perm: '' })

async function load(): Promise<void> {
  loading.value = true
  try { rows.value = await adminApi.listPermissions() } finally { loading.value = false }
}
async function onCreate(): Promise<void> {
  await adminApi.createPermission(form.value)
  ElMessage.success('已创建')
  showCreate.value = false
  await load()
}
async function onDelete(row: PermRow): Promise<void> {
  await ElMessageBox.confirm(`删除权限 ${row.code}?`, '确认', { type: 'warning' })
  await adminApi.deletePermission(row.code)
  ElMessage.success('已删除')
  await load()
}
async function onGrant(): Promise<void> {
  await adminApi.grantPermission(grant.value.role, grant.value.perm)
  ElMessage.success('已授权')
  showGrant.value = false
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
}
.action-btn:hover { color: var(--text-primary); border-color: var(--border-hover); background: rgba(255,255,255,0.08); }
.action-btn.danger { color: var(--danger); }
.action-btn.danger:hover { border-color: var(--danger); background: rgba(255,71,87,0.1); }
.dialog-form { display: flex; flex-direction: column; gap: 14px; }
.neo-input { width: 100%; padding: 11px 14px; background: rgba(255,255,255,0.04); border: 1px solid var(--border); border-radius: var(--radius-md); color: var(--text-primary); font-family: var(--font-body); font-size: 0.9rem; outline: none; transition: all var(--dur-fast) var(--ease-out); }
.neo-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.neo-input option { background: var(--bg-secondary); color: var(--text-primary); }
.input-label { display: block; color: var(--text-secondary); font-size: 0.8rem; font-weight: 500; margin-bottom: 6px; }
</style>