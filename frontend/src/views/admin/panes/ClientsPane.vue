<template>
  <div>
    <div class="pane-toolbar">
      <div class="spacer" />
      <button class="neo-btn primary" style="width:auto;padding:8px 20px" @click="onEdit(null)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建客户端
      </button>
    </div>

    <el-table :data="rows" v-loading="loading" class="neo-table">
      <el-table-column prop="clientId" label="Client ID" min-width="160" />
      <el-table-column prop="grantTypes" label="Grant Types" min-width="180" show-overflow-tooltip />
      <el-table-column prop="redirectUris" label="Redirect URIs" min-width="200" show-overflow-tooltip />
      <el-table-column prop="scopes" label="Scopes" min-width="120" />
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

    <el-dialog v-model="showEdit" :title="form.clientId && exist ? '编辑客户端' : '新建客户端'" width="520px" class="neo-dialog">
      <div class="dialog-form">
        <div class="input-group">
          <label class="input-label">Client ID</label>
          <input v-model="form.clientId" class="neo-input" placeholder="demo-client" :disabled="exist" />
        </div>
        <div class="input-group">
          <label class="input-label">Client Secret</label>
          <input v-model="form.clientSecret" type="password" class="neo-input" placeholder="demo-secret" />
        </div>
        <div class="input-group">
          <label class="input-label">Grant Types</label>
          <input v-model="form.grantTypes" class="neo-input" placeholder="authorization_code,refresh_token,password,client_credentials" />
        </div>
        <div class="input-group">
          <label class="input-label">Redirect URIs</label>
          <textarea v-model="form.redirectUris" class="neo-input" rows="3" placeholder="http://localhost/callback" />
        </div>
        <div class="input-group">
          <label class="input-label">Scopes</label>
          <input v-model="form.scopes" class="neo-input" placeholder="openid,profile" />
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
import { adminApi, type ClientRow } from '../../../api/admin'

const rows = ref<ClientRow[]>([])
const loading = ref(false)
const showEdit = ref(false)
const exist = ref(false)
const form = ref({ clientId: '', clientSecret: '', grantTypes: 'authorization_code,refresh_token', redirectUris: '', scopes: 'openid,profile' })

async function load(): Promise<void> {
  loading.value = true
  try { rows.value = await adminApi.listClients() } finally { loading.value = false }
}
function onEdit(row: ClientRow | null): void {
  exist.value = row !== null
  if (row) {
    form.value = { clientId: row.clientId, clientSecret: '', grantTypes: row.grantTypes, redirectUris: row.redirectUris, scopes: row.scopes }
  } else {
    form.value = { clientId: '', clientSecret: '', grantTypes: 'authorization_code,refresh_token', redirectUris: '', scopes: 'openid,profile' }
  }
  showEdit.value = true
}
async function onSave(): Promise<void> {
  await adminApi.upsertClient(form.value)
  ElMessage.success('已保存')
  showEdit.value = false
  await load()
}
async function onDelete(row: ClientRow): Promise<void> {
  await ElMessageBox.confirm(`删除客户端 ${row.clientId}?`, '确认', { type: 'warning' })
  await adminApi.deleteClient(row.clientId)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.pane-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
.spacer { flex: 1; }
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
.neo-input { width: 100%; padding: 11px 14px; background: rgba(255,255,255,0.04); border: 1px solid var(--border); border-radius: var(--radius-md); color: var(--text-primary); font-family: var(--font-body); font-size: 0.9rem; outline: none; transition: all var(--dur-fast) var(--ease-out); resize: vertical; }
.neo-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.input-label { display: block; color: var(--text-secondary); font-size: 0.8rem; font-weight: 500; margin-bottom: 6px; }
</style>