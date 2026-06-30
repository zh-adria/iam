<template>
  <div>
    <div class="toolbar">
      <el-input v-model="tenant" placeholder="按租户过滤" style="width:200px" clearable />
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="showCreate = true">新建用户</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="手机" />
      <el-table-column prop="tenant" label="租户" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色">
        <template #default="{ row }">
          <el-tag v-for="r in row.roles" :key="r" size="small" style="margin-right:4px">{{ r }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="onReset(row)">重置密码</el-button>
          <el-button size="small" @click="onToggle(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button size="small" @click="onUnlock(row)" :disabled="row.status !== 2">解锁</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建用户" width="480px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
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
import { adminApi, type UserRow } from '../../../api/admin'

const rows = ref<UserRow[]>([])
const loading = ref(false)
const tenant = ref('')
const showCreate = ref(false)
const form = ref({ username: '', password: '', email: '', phone: '', tenantCode: 'default' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const r = await adminApi.listUsers(1, 50, tenant.value || undefined)
    rows.value = r.rows
  } finally { loading.value = false }
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
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
