<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="onEdit(null)">新建租户</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="isolationMode" label="隔离模式" width="140" />
      <el-table-column prop="ldapUrl" label="LDAP URL" />
      <el-table-column prop="ldapBase" label="LDAP Base" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showEdit" title="租户配置" width="520px">
      <el-form label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="隔离模式">
          <el-select v-model="form.isolationMode">
            <el-option label="SHARED (共享 Schema)" value="SHARED" />
            <el-option label="SCHEMA_PER_TENANT" value="SCHEMA_PER_TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item label="LDAP URL"><el-input v-model="form.ldapUrl" placeholder="ldap://host:389" /></el-form-item>
        <el-form-item label="LDAP Base"><el-input v-model="form.ldapBase" placeholder="dc=iam,dc=local" /></el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
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
.toolbar { margin-bottom: 12px; }
</style>
