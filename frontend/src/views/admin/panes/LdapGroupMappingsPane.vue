<template>
  <div>
    <PaneToolbar
      :show-create="true"
      create-label="新增映射"
      @create="openCreate"
    >
      <template #action>
        <el-select
          v-model="tenantFilter"
          class="tenant-select"
          placeholder="选择租户"
          filterable
          @change="loadMappings"
        >
          <el-option
            v-for="tenant in tenants"
            :key="tenant.code"
            :label="`${tenant.name || tenant.code} (${tenant.code})`"
            :value="tenant.code"
          />
        </el-select>
        <el-button plain :loading="loading" @click="loadMappings">刷新</el-button>
      </template>
    </PaneToolbar>

    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="tenantCode" label="租户" width="130" />
      <el-table-column prop="ldapGroupDn" label="LDAP Group DN" min-width="360" show-overflow-tooltip />
      <el-table-column prop="roleCode" label="角色" min-width="180">
        <template #default="{ row }">
          <span class="neo-tag accent">{{ row.roleCode }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="170" align="right">
        <template #default="{ row }">
          <el-button size="small" plain type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog
      v-model="showEdit"
      :title="editing ? '编辑 LDAP 映射' : '新增 LDAP 映射'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="120px">
        <el-form-item label="租户">
          <el-select
            v-model="form.tenantCode"
            placeholder="选择租户"
            filterable
            :disabled="editing"
            style="width:100%"
            @change="loadRoles"
          >
            <el-option
              v-for="tenant in tenants"
              :key="tenant.code"
              :label="`${tenant.name || tenant.code} (${tenant.code})`"
              :value="tenant.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="LDAP Group DN">
          <el-input
            v-model="form.ldapGroupDn"
            :disabled="editing"
            placeholder="cn=developers,ou=groups,dc=example,dc=com"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleCode"
            placeholder="选择角色"
            filterable
            style="width:100%"
            :loading="rolesLoading"
          >
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="`${role.name || role.code} (${role.code})`"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type LdapGroupMappingRow, type RoleRow, type TenantRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'
import { usePagination } from '../../../composables/usePagination'

const DEFAULT_FORM = () => ({ tenantCode: 'default', ldapGroupDn: '', roleCode: '' })

const tenants = ref<TenantRow[]>([])
const roles = ref<RoleRow[]>([])
const allRows = ref<LdapGroupMappingRow[]>([])
const tenantFilter = ref('default')
const loading = ref(false)
const rolesLoading = ref(false)
const showEdit = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = ref(DEFAULT_FORM())

const { page, size, rows, total, reset } = usePagination(allRows)

const selectedTenant = computed(() => tenantFilter.value || tenants.value[0]?.code || 'default')

async function loadTenants(): Promise<void> {
  const res = await adminApi.listTenants(1, 500)
  tenants.value = res.rows
  if (!tenants.value.some(t => t.code === tenantFilter.value)) {
    tenantFilter.value = tenants.value[0]?.code || 'default'
  }
}

async function loadRoles(tenantCode = form.value.tenantCode): Promise<void> {
  rolesLoading.value = true
  try {
    const res = await adminApi.listRoles(1, 500, tenantCode)
    roles.value = res.rows
    if (!roles.value.some(r => r.code === form.value.roleCode)) {
      form.value.roleCode = roles.value[0]?.code || ''
    }
  } finally {
    rolesLoading.value = false
  }
}

async function loadMappings(): Promise<void> {
  loading.value = true
  try {
    allRows.value = await adminApi.listLdapGroupMappings(selectedTenant.value)
    reset()
  } finally {
    loading.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    await loadTenants()
    await Promise.all([loadMappings(), loadRoles(selectedTenant.value)])
  } finally {
    loading.value = false
  }
}

async function openCreate(): Promise<void> {
  form.value = { ...DEFAULT_FORM(), tenantCode: selectedTenant.value }
  editing.value = false
  await loadRoles(form.value.tenantCode)
  showEdit.value = true
}

async function openEdit(row: LdapGroupMappingRow): Promise<void> {
  form.value = { tenantCode: row.tenantCode, ldapGroupDn: row.ldapGroupDn, roleCode: row.roleCode }
  editing.value = true
  await loadRoles(row.tenantCode)
  showEdit.value = true
}

async function onSave(): Promise<void> {
  const tenantCode = form.value.tenantCode.trim()
  const ldapGroupDn = form.value.ldapGroupDn.trim()
  const roleCode = form.value.roleCode.trim()
  if (!tenantCode) { ElMessage.warning('请选择租户'); return }
  if (!ldapGroupDn) { ElMessage.warning('请输入 LDAP Group DN'); return }
  if (!roleCode) { ElMessage.warning('请选择角色'); return }

  saving.value = true
  try {
    await adminApi.upsertLdapGroupMapping({ tenantCode, ldapGroupDn, roleCode })
    ElMessage.success('已保存')
    showEdit.value = false
    tenantFilter.value = tenantCode
    await loadMappings()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: LdapGroupMappingRow): Promise<void> {
  await ElMessageBox.confirm(`删除 ${row.ldapGroupDn} → ${row.roleCode}?`, '确认删除', { type: 'warning' })
  await adminApi.deleteLdapGroupMapping(row.tenantCode, row.ldapGroupDn)
  ElMessage.success('已删除')
  await loadMappings()
}

onMounted(load)
</script>

<style scoped>
.tenant-select { width: 240px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
