<template>
  <div>
    <div class="pane-toolbar">
      <div class="spacer" />
      <button class="neo-btn primary" style="width:auto;padding:8px 20px" @click="showCreate = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建角色
      </button>
    </div>

    <el-table :data="rows" v-loading="loading" class="neo-table">
      <el-table-column prop="code" label="编码" min-width="160" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="tenant" label="租户" width="120" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <button class="action-btn danger" @click="onDelete(row)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            删除
          </button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建角色" width="420px" class="neo-dialog">
      <div class="dialog-form">
        <div class="input-group">
          <label class="input-label">编码</label>
          <input v-model="form.code" class="neo-input" placeholder="ROLE_ADMIN" />
        </div>
        <div class="input-group">
          <label class="input-label">名称</label>
          <input v-model="form.name" class="neo-input" placeholder="管理员" />
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
import { adminApi, type RoleRow } from '../../../api/admin'

const rows = ref<RoleRow[]>([])
const loading = ref(false)
const showCreate = ref(false)
const form = ref({ code: '', name: '', tenantCode: 'default' })

async function load(): Promise<void> {
  loading.value = true
  try { rows.value = await adminApi.listRoles() } finally { loading.value = false }
}
async function onCreate(): Promise<void> {
  await adminApi.createRole(form.value.code, form.value.name, form.value.tenantCode)
  ElMessage.success('已创建')
  showCreate.value = false
  await load()
}
async function onDelete(row: RoleRow): Promise<void> {
  await ElMessageBox.confirm(`删除角色 ${row.code}?`, '确认', { type: 'warning' })
  await adminApi.deleteRole(row.code)
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
.input-label { display: block; color: var(--text-secondary); font-size: 0.8rem; font-weight: 500; margin-bottom: 6px; }
</style>