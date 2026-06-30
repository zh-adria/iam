<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="onEdit(null)">新建客户端</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="clientId" label="Client ID" />
      <el-table-column prop="grantTypes" label="Grant Types" />
      <el-table-column prop="redirectUris" label="Redirect URIs" />
      <el-table-column prop="scopes" label="Scopes" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showEdit" :title="form.clientId && exist ? '编辑客户端' : '新建客户端'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="Client ID"><el-input v-model="form.clientId" :disabled="exist" /></el-form-item>
        <el-form-item label="Client Secret"><el-input v-model="form.clientSecret" type="password" show-password /></el-form-item>
        <el-form-item label="Grant Types"><el-input v-model="form.grantTypes" placeholder="authorization_code,refresh_token,password,client_credentials" /></el-form-item>
        <el-form-item label="Redirect URIs"><el-input v-model="form.redirectUris" type="textarea" /></el-form-item>
        <el-form-item label="Scopes"><el-input v-model="form.scopes" placeholder="openid,profile" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
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
.toolbar { margin-bottom: 12px; }
</style>
