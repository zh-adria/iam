<template>
  <div>
    <PaneToolbar
      :show-create="true"
      create-label="新建客户端"
      @create="onEdit(null)"
    />

    <el-table :data="rows" v-loading="loading" style="width: 100%">
      <el-table-column prop="clientId" label="Client ID" min-width="160" />
      <el-table-column label="Grant Types" min-width="220">
        <template #default="{ row }">
          <span v-for="g in split(row.grantTypes)" :key="g" class="neo-tag dim">{{ g }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="redirectUris" label="Redirect URIs" min-width="220" show-overflow-tooltip />
      <el-table-column label="Scopes" min-width="220">
        <template #default="{ row }">
          <span v-for="s in split(row.scopes)" :key="s" class="scope-tag">{{ s }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Access TTL" width="100" align="center">
        <template #default="{ row }">{{ row.accessTokenTtlMinutes ?? 30 }} min</template>
      </el-table-column>
      <el-table-column label="Refresh TTL" width="110" align="center">
        <template #default="{ row }">{{ row.refreshTokenTtlDays ?? 7 }} days</template>
      </el-table-column>
      <el-table-column label="Auto Approve" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.autoApprove" type="success" size="small">Yes</el-tag>
          <el-tag v-else type="info" size="small">No</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Actions" min-width="160" align="right">
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

    <el-dialog v-model="showEdit" :title="exist ? '编辑客户端' : '新建客户端'" width="760px">
      <el-form :model="form" label-width="130px">
        <el-form-item label="Client ID">
          <el-input v-model="form.clientId" placeholder="demo-client" :disabled="exist" />
          <p class="field-tip">全局唯一；创建后不建议修改。</p>
        </el-form-item>

        <el-form-item label="Client Secret">
          <el-input v-model="form.clientSecret" placeholder="编辑时留空表示保留原值" show-password />
        </el-form-item>

        <el-form-item label="Grant Types">
          <el-checkbox-group v-model="form.grantTypesArr">
            <el-checkbox value="authorization_code">authorization_code</el-checkbox>
            <el-checkbox value="refresh_token">refresh_token</el-checkbox>
            <el-checkbox value="password">password</el-checkbox>
            <el-checkbox value="client_credentials">client_credentials</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="Redirect URIs">
          <el-input
            v-model="form.redirectUris"
            type="textarea"
            :rows="2"
            placeholder="http://localhost:5173/callback，每行或逗号分隔"
          />
        </el-form-item>

        <el-form-item label="Standard Scopes">
          <el-checkbox-group v-model="form.scopeArr">
            <el-checkbox v-for="s in STANDARD_SCOPES" :key="s" :value="s">{{ s }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="Permission Scopes">
          <div class="scope-panel">
            <div class="scope-actions">
              <el-input v-model="permissionQuery" placeholder="搜索权限码" clearable />
              <el-button @click="selectAllPermissions">全选权限</el-button>
              <el-button @click="clearPermissions">清空权限</el-button>
            </div>
            <el-checkbox-group v-model="form.scopeArr" class="permission-grid">
              <el-checkbox
                v-for="perm in filteredPermissions"
                :key="perm.code"
                :value="perm.code"
                :title="perm.name || perm.code"
              >
                <span class="perm-code">{{ perm.code }}</span>
                <span class="perm-type">{{ perm.type }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </el-form-item>

        <el-form-item label="Custom Scopes">
          <el-input
            v-model="form.customScopes"
            type="textarea"
            :rows="2"
            placeholder="其他 scope，逗号、空格或换行分隔"
          />
        </el-form-item>

        <el-form-item label="Access Token TTL">
          <el-input-number v-model="form.accessTokenTtlMinutes" :min="1" :max="1440" />
          <span class="field-tip inline">分钟，1-1440</span>
        </el-form-item>

        <el-form-item label="Refresh Token TTL">
          <el-input-number v-model="form.refreshTokenTtlDays" :min="1" :max="3650" />
          <span class="field-tip inline">天，1-3650</span>
        </el-form-item>

        <el-form-item label="Auto Approve">
          <el-switch v-model="form.autoApprove" />
        </el-form-item>

        <el-form-item label="ID Token Claims">
          <el-input v-model="form.idTokenClaims" type="textarea" :rows="3" placeholder='{"department":"Engineering"}' />
          <p class="field-tip">JSON 格式，写入 ID Token 的额外 claims。</p>
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
import { computed, ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type ClientRow, type PermRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'
import { usePagination } from '../../../composables/usePagination'

const STANDARD_SCOPES = ['openid', 'profile', 'email', 'phone']

const allRows = ref<ClientRow[]>([])
const permissionRows = ref<PermRow[]>([])
const permissionQuery = ref('')
const loading = ref(false)
const showEdit = ref(false)
const exist = ref(false)

const form = reactive({
  clientId: '',
  clientSecret: '',
  grantTypesArr: [] as string[],
  redirectUris: '',
  scopeArr: [] as string[],
  customScopes: '',
  accessTokenTtlMinutes: 30,
  refreshTokenTtlDays: 7,
  autoApprove: false,
  idTokenClaims: '',
})

const { page, size, rows, total, reset } = usePagination(allRows)

const knownScopeSet = computed(() => new Set([...STANDARD_SCOPES, ...permissionRows.value.map(p => p.code)]))
const filteredPermissions = computed(() => {
  const q = permissionQuery.value.trim().toLowerCase()
  if (!q) return permissionRows.value
  return permissionRows.value.filter(p =>
    p.code.toLowerCase().includes(q)
    || (p.name || '').toLowerCase().includes(q)
    || (p.type || '').toLowerCase().includes(q)
  )
})

function split(value?: string): string[] {
  return (value || '').split(/[\s,]+/).map(s => s.trim()).filter(Boolean)
}

function resetForm(): void {
  Object.assign(form, {
    clientId: '',
    clientSecret: '',
    grantTypesArr: [],
    redirectUris: '',
    scopeArr: [],
    customScopes: '',
    accessTokenTtlMinutes: 30,
    refreshTokenTtlDays: 7,
    autoApprove: false,
    idTokenClaims: '',
  })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [clients, permissions] = await Promise.all([
      adminApi.listClients(1, 500),
      adminApi.listPermissions(1, 1000),
    ])
    allRows.value = clients.rows
    permissionRows.value = permissions.rows
    reset()
  } finally {
    loading.value = false
  }
}

function onEdit(row: ClientRow | null): void {
  resetForm()
  exist.value = row !== null
  if (row) {
    const scopes = split(row.scopes)
    form.clientId = row.clientId
    form.grantTypesArr = split(row.grantTypes)
    form.redirectUris = row.redirectUris || ''
    form.scopeArr = scopes.filter(s => knownScopeSet.value.has(s))
    form.customScopes = scopes.filter(s => !knownScopeSet.value.has(s)).join('\n')
    form.accessTokenTtlMinutes = row.accessTokenTtlMinutes ?? 30
    form.refreshTokenTtlDays = row.refreshTokenTtlDays ?? 7
    form.autoApprove = row.autoApprove ?? false
    form.idTokenClaims = row.idTokenClaims || ''
  }
  showEdit.value = true
}

function mergedScopes(): string {
  return Array.from(new Set([...form.scopeArr, ...split(form.customScopes)])).join(',')
}

function selectAllPermissions(): void {
  form.scopeArr = Array.from(new Set([...form.scopeArr, ...permissionRows.value.map(p => p.code)]))
}

function clearPermissions(): void {
  const permissionSet = new Set(permissionRows.value.map(p => p.code))
  form.scopeArr = form.scopeArr.filter(s => !permissionSet.has(s))
}

async function onSave(): Promise<void> {
  if (!form.clientId.trim()) {
    ElMessage.warning('Client ID 不能为空')
    return
  }
  const payload: Record<string, unknown> = {
    clientId: form.clientId.trim(),
    clientSecret: form.clientSecret,
    grantTypes: form.grantTypesArr.join(','),
    redirectUris: form.redirectUris,
    scopes: mergedScopes(),
    accessTokenTtlMinutes: form.accessTokenTtlMinutes,
    refreshTokenTtlDays: form.refreshTokenTtlDays,
    autoApprove: form.autoApprove,
    idTokenClaims: form.idTokenClaims,
  }
  await adminApi.upsertClient(payload as Record<string, string>)
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
.field-tip {
  font-size: .72rem;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.4;
}
.field-tip.inline {
  margin-left: 10px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.scope-tag {
  display: inline-block;
  margin: 2px 4px 2px 0;
  padding: 2px 6px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: .72rem;
  color: var(--text-secondary);
}
.scope-panel {
  width: 100%;
}
.scope-actions {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto auto;
  gap: 8px;
  margin-bottom: 10px;
}
.permission-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 6px 10px;
  max-height: 260px;
  overflow: auto;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.perm-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: .78rem;
}
.perm-type {
  margin-left: 6px;
  color: var(--text-muted);
  font-size: .7rem;
}
</style>
