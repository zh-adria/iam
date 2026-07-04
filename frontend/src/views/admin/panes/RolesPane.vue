<template>
  <div>
    <PaneToolbar
      :show-create="true"
      create-label="新建角色"
      @create="showCreate = true"
    >
      <template #action>
        <span class="hint">点击表格行的 <b>权限管理</b> 为该角色配置权限</span>
      </template>
    </PaneToolbar>

    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="code" label="编码" min-width="160" />
      <el-table-column prop="name" label="名称" min-width="170" />
      <el-table-column prop="tenant" label="租户" width="120" />
      <el-table-column label="权限" min-width="220">
        <template #default="{ row }">
          <span v-for="p in (rolePerms[row.code] || [])" :key="p" class="neo-tag dim">{{ p }}</span>
          <span v-if="!rolePerms[row.code]?.length" class="no-data">未关联</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="180" align="right">
        <template #default="{ row }">
          <el-button size="small" plain type="primary" @click="openPerms(row)">权限管理</el-button>
          <el-button size="small" plain type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="新建角色" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="ROLE_STAFF" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="员工" /></el-form-item>
        <el-form-item label="租户"><el-input v-model="form.tenantCode" placeholder="default" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- Manage Permissions Dialog -->
    <el-dialog
      v-model="showManagePerms"
      :title="`配置权限 — ${editingRole?.code || ''}`"
      width="580px"
      :close-on-click-modal="false"
    >
      <div class="perm-toolbar">
        <el-input
          v-model="permQuery"
          class="search-input"
          placeholder="按编码/名称过滤..."
          clearable
          style="width:320px"
        >
          <template #suffix>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          </template>
        </el-input>
        <span class="perm-count">已选 {{ editingPerms.length }} / {{ filteredPerms.length }}</span>
      </div>

      <div class="perm-list">
        <label v-for="p in filteredPerms" :key="p.code" class="perm-row">
          <el-checkbox :model-value="editingPerms.includes(p.code)" @change="togglePerm(p.code, $event)" />
          <span class="perm-code">{{ p.code }}</span>
          <span class="perm-type neo-tag dim">{{ p.type }}</span>
          <span class="perm-name">{{ p.name }}</span>
        </label>
        <p v-if="!filteredPerms.length" class="no-data">无权限，请先到「权限管理」新建</p>
      </div>

      <template #footer>
        <el-button @click="showManagePerms = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type RoleRow, type PermRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'
import { usePagination } from '../../../composables/usePagination'

const allRows = ref<RoleRow[]>([])
const allPerms = ref<PermRow[]>([])
const rolePerms = ref<Record<string, string[]>>({})
const loading = ref(false)
const showCreate = ref(false)
const form = ref({ code: '', name: '', tenantCode: 'default' })

const { page, size, rows, total, reset: resetPage } = usePagination(allRows)

const showManagePerms = ref(false)
const editingRole = ref<RoleRow | null>(null)
const editingPerms = ref<string[]>([])
const permQuery = ref('')
const saving = ref(false)

const filteredPerms = computed(() => {
  const q = permQuery.value.trim().toLowerCase()
  if (!q) return allPerms.value
  return allPerms.value.filter(p => p.code.toLowerCase().includes(q) || (p.name || '').toLowerCase().includes(q))
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const [roleRes, permRes] = await Promise.all([adminApi.listRoles(1, 500), adminApi.listPermissions(1, 500)])
    allRows.value = roleRes.rows
    allPerms.value = permRes.rows
    resetPage()
    const entries = await Promise.all(
      allRows.value.map(async r => [r.code, await adminApi.listRolePermissions(r.code)] as const)
    )
    rolePerms.value = Object.fromEntries(entries)
  } finally {
    loading.value = false
  }
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

async function openPerms(row: RoleRow): Promise<void> {
  editingRole.value = row
  editingPerms.value = [...(rolePerms.value[row.code] || [])]
  permQuery.value = ''
  showManagePerms.value = true
}

function togglePerm(code: string, checked: boolean) {
  const set = new Set(editingPerms.value)
  if (checked) set.add(code); else set.delete(code)
  editingPerms.value = [...set]
}

async function savePerms(): Promise<void> {
  if (!editingRole.value) return
  saving.value = true
  try {
    const role = editingRole.value.code
    const before = rolePerms.value[role] || []
    const after = editingPerms.value
    for (const p of after.filter(x => !before.includes(x))) await adminApi.grantPermission(role, p)
    for (const p of before.filter(x => !after.includes(x))) await adminApi.revokePermission(role, p)
    ElMessage.success('权限已更新')
    showManagePerms.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.hint { font-size: .78rem; color: var(--text-muted); }
.hint b { color: var(--accent); font-weight: 600; }
.no-data { color: var(--text-muted); font-size: .82rem; padding: 8px 0; }
.perm-toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 12px; }
.perm-count { font-size: .78rem; color: var(--text-muted); }
.perm-list {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 4px 0;
}
.perm-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background var(--dur-fast);
}
.perm-row:last-child { border-bottom: none; }
.perm-row:hover { background: var(--accent-soft); }
.perm-code {
  font-family: var(--font-mono);
  font-size: .82rem;
  color: var(--text-primary);
  font-weight: 500;
  min-width: 140px;
}
.perm-type { font-size: .7rem; }
.perm-name { font-size: .82rem; color: var(--text-muted); }
</style>
