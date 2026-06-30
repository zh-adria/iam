<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="showCreate = true">新建角色</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="tenant" label="租户" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建角色" width="400px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="租户"><el-input v-model="form.tenantCode" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
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
.toolbar { margin-bottom: 12px; }
</style>
