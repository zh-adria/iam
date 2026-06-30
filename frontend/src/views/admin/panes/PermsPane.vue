<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="showCreate = true">新建权限</el-button>
      <el-button @click="showGrant = true">角色授权</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="code" label="编码" width="200" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="resource" label="资源" />
      <el-table-column prop="action" label="动作" width="80" />
      <el-table-column prop="spel" label="SpEL" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建权限" width="500px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="API" value="API" />
            <el-option label="MENU" value="MENU" />
            <el-option label="BUTTON" value="BUTTON" />
            <el-option label="DATA" value="DATA" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="资源"><el-input v-model="form.resource" /></el-form-item>
        <el-form-item label="动作"><el-input v-model="form.action" /></el-form-item>
        <el-form-item label="SpEL"><el-input v-model="form.spel" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGrant" title="角色授权" width="400px">
      <el-form label-width="80px">
        <el-form-item label="角色"><el-input v-model="grant.role" /></el-form-item>
        <el-form-item label="权限"><el-input v-model="grant.perm" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGrant = false">取消</el-button>
        <el-button type="primary" @click="onGrant">授权</el-button>
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
.toolbar { margin-bottom: 12px; }
</style>
