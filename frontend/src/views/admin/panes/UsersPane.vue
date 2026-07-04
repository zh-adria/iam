<template>
  <div>
    <PaneToolbar
      v-model:query="tenant"
      search-placeholder="按租户过滤..."
      :show-search="true"
      :show-create="true"
      create-label="新建用户"
      @search="load"
      @create="showCreate = true"
    />
    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="110" />
      <el-table-column prop="email" label="邮箱" min-width="170" />
      <el-table-column prop="phone" label="手机" width="120" />
      <el-table-column prop="tenant" label="租户" width="100" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <span :class="['neo-tag', row.status === 1 ? 'success' : 'danger']">{{ row.status === 1 ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="200">
        <template #default="{ row }">
          <span v-for="r in row.roles" :key="r" class="neo-tag accent">{{ r }}</span>
          <span v-if="!row.roles?.length" class="no-data">未分配</span>
        </template>
      </el-table-column>
      <!-- 操作列：用 min-width 替代固定宽度，避免水平滚动时和滚动条重叠 -->
      <el-table-column label="操作" min-width="340" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" plain @click="openAssignRoles(row)">角色分配</el-button>
          <el-button size="small" plain @click="onReset(row)">重置密码</el-button>
          <el-button size="small" plain @click="onToggle(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button size="small" plain :disabled="row.status !== 2" @click="onUnlock(row)">解锁</el-button>
          <el-button type="danger" size="small" plain @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="新建用户" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" placeholder="输入用户名" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" placeholder="输入密码" show-password /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="user@example.com" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" placeholder="138xxxx" /></el-form-item>
        <el-form-item label="租户"><el-input v-model="form.tenantCode" placeholder="default" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- Assign Roles Dialog -->
    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        background
      />
    </div>

    <el-dialog v-model="showAssignRoles" :title="`分配角色 — ${editingRow?.username || ''}`" width="460px">
      <el-form label-width="60px">
        <el-form-item label="角色">
          <el-checkbox-group v-model="editingRoles">
            <el-checkbox v-for="r in availableRoles" :key="r.code" :value="r.code" style="display:block;margin-bottom:6px">
              {{ r.name }} <span style="color:var(--text-muted);font-size:.78rem;margin-left:6px">{{ r.code }}</span>
            </el-checkbox>
          </el-checkbox-group>
          <p v-if="!availableRoles.length" class="no-data">暂无可用角色，请先到「角色管理」创建</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAssignRoles = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type UserRow, type RoleRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'
import { usePagination } from '../../../composables/usePagination'

const allRows = ref<UserRow[]>([])
const loading = ref(false)
const tenant = ref('')
const showCreate = ref(false)
const form = ref({ username: '', password: '', email: '', phone: '', tenantCode: 'default' })

const { page, size, rows, total, reset } = usePagination(allRows)

const availableRoles = ref<RoleRow[]>([])
const showAssignRoles = ref(false)
const editingRow = ref<UserRow | null>(null)
const editingRoles = ref<string[]>([])
const saving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    allRows.value = (await adminApi.listUsers(1, 100, tenant.value || undefined)).rows
    reset()
  } finally { loading.value = false }
}

// 租户过滤变化时回到第 1 页
watch(tenant, () => load())

async function loadAvailableRoles(): Promise<void> {
  if (availableRoles.value.length === 0) {
    availableRoles.value = await adminApi.listRoles()
  }
}

async function onCreate(): Promise<void> {
  try {
    await adminApi.createUser(form.value)
    ElMessage.success('已创建')
    showCreate.value = false
    form.value = { username: '', password: '', email: '', phone: '', tenantCode: 'default' }
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}

async function openAssignRoles(row: UserRow): Promise<void> {
  await loadAvailableRoles()
  editingRow.value = row
  editingRoles.value = [...(row.roles || [])]
  showAssignRoles.value = true
}

async function saveRoles(): Promise<void> {
  if (!editingRow.value) return
  saving.value = true
  try {
    const before = editingRow.value.roles || []
    const after = editingRoles.value
    const toAdd = after.filter(r => !before.includes(r))
    const toRemove = before.filter(r => !after.includes(r))
    for (const r of toAdd) await adminApi.assignRole(editingRow.value!.id, r)
    for (const r of toRemove) await adminApi.revokeRole(editingRow.value!.id, r)
    ElMessage.success('角色已更新')
    showAssignRoles.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

async function onReset(row: UserRow): Promise<void> {
  const { value } = await ElMessageBox.prompt('新密码', '重置密码', { inputType: 'password' })
  await adminApi.resetPassword(row.id, value)
  if (!value) return
}

async function onToggle(row: UserRow): Promise<void> {
  await adminApi.setUserStatus(row.id, row.status === 1 ? 0 : 1)
  await load()
}

async function onUnlock(row: UserRow): Promise<void> {
  await adminApi.unlockUser(row.id)
  await load()
}

async function onDelete(row: UserRow): Promise<void> {
  await ElMessageBox.confirm(`删除 ${row.username}?`, '确认删除', { type: 'warning' })
  await adminApi.deleteUser(row.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
