<template>
  <div>
    <PaneToolbar
      :show-create="true"
      create-label="新建租户"
      @create="onEdit(null)"
    />
    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="code" label="编码" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="isolationMode" label="隔离模式" width="100">
        <template #default="{ row }">
          <span class="neo-tag accent">{{ row.isolationMode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="schemaName" label="Schema" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span :class="['neo-tag', row.enabled ? 'success' : 'danger']">{{ row.enabled ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ldapUrl" label="LDAP" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" min-width="160" align="right">
        <template #default="{ row }">
          <el-button size="small" plain type="primary" @click="onEdit(row)">编辑</el-button>
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

    <el-dialog v-model="showEdit" title="租户配置" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="tenant-code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="租户名称" /></el-form-item>
        <el-form-item label="隔离模式">
          <el-radio-group v-model="form.isolationMode">
            <el-radio value="SHARED">SHARED</el-radio>
            <el-radio value="SCHEMA">SCHEMA</el-radio>
            <el-radio value="DATABASE">DATABASE</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Schema"><el-input v-model="form.schemaName" placeholder="tenant-a" /></el-form-item>
        <el-form-item label="LDAP URL"><el-input v-model="form.ldapUrl" placeholder="ldap://host:389" /></el-form-item>
        <el-form-item label="LDAP Base"><el-input v-model="form.ldapBase" placeholder="dc=iam,dc=local" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
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
import PaneToolbar from '../../../components/PaneToolbar.vue'
import { usePagination } from '../../../composables/usePagination'

const DEFAULT_FORM = () => ({ code: '', name: '', isolationMode: 'SHARED', schemaName: '', ldapUrl: '', ldapBase: '', enabled: true })

const allRows = ref<TenantRow[]>([])
const loading = ref(false)
const showEdit = ref(false)
const form = ref(DEFAULT_FORM())

const { page, size, rows, total, reset } = usePagination(allRows)

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await adminApi.listTenants(1, 500)
    allRows.value = res.rows
    reset()
  } finally { loading.value = false }
}
function onEdit(row: TenantRow | null): void {
  if (row) {
    form.value = { code: row.code, name: row.name, isolationMode: row.isolationMode, schemaName: row.schemaName, ldapUrl: row.ldapUrl || '', ldapBase: row.ldapBase || '', enabled: row.enabled }
  } else {
    form.value = DEFAULT_FORM()
  }
  showEdit.value = true
}
async function onSave(): Promise<void> {
  if (!form.value.code.trim()) { ElMessage.warning('请输入租户编码'); return }
  try {
    await adminApi.upsertTenant(form.value)
    ElMessage.success('已保存')
    form.value = DEFAULT_FORM()
    showEdit.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败（编码可能已存在）')
  }
}
async function onDelete(row: TenantRow): Promise<void> {
  await ElMessageBox.confirm(`删除租户 ${row.code}?`, '确认删除', { type: 'warning' })
  try {
    await adminApi.deleteTenant(row.code)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '删除失败')
  }
}
onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
