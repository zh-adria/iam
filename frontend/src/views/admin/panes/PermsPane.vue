<template>
  <div>
    <PaneToolbar
      :show-create="true"
      create-label="新建权限"
      @create="showCreate = true"
    >
      <template #action>
        <el-button @click="showGrant = true">角色授权</el-button>
      </template>
    </PaneToolbar>

    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="code" label="编码" min-width="160" />
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <span class="neo-tag dim">{{ row.type }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="resource" label="资源" min-width="120" />
      <el-table-column prop="action" label="动作" width="100" />
      <el-table-column prop="spel" label="SpEL" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建权限" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="user:create" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option value="API" label="API" />
            <el-option value="MENU" label="MENU" />
            <el-option value="BUTTON" label="BUTTON" />
            <el-option value="DATA" label="DATA" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="创建用户" /></el-form-item>
        <el-form-item label="资源"><el-input v-model="form.resource" placeholder="/api/users" /></el-form-item>
        <el-form-item label="动作"><el-input v-model="form.action" placeholder="CREATE" /></el-form-item>
        <el-form-item label="SpEL"><el-input v-model="form.spel" placeholder="#tenant == authentication.details.tenant" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGrant" title="角色授权" width="400px">
      <el-form :model="grant" label-width="90px">
        <el-form-item label="角色编码"><el-input v-model="grant.role" placeholder="ROLE_ADMIN" /></el-form-item>
        <el-form-item label="权限编码"><el-input v-model="grant.perm" placeholder="user:create" /></el-form-item>
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
import PaneToolbar from '../../../components/PaneToolbar.vue'

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
