<template>
  <div>
    <PaneToolbar
      :show-create="true"
      create-label="新建客户端"
      @create="onEdit(null)"
    />
    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="clientId" label="Client ID" min-width="160" />
      <el-table-column label="授权类型" min-width="220">
        <template #default="{ row }">
          <span v-for="g in (row.grantTypes || '').split(',').filter(Boolean)" :key="g" class="neo-tag dim">{{ g }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="redirectUris" label="回调地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="scopes" label="Scopes" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="Access TTL" width="100" align="center">
        <template #default="{ row }">{{ row.accessTokenTtlMinutes ?? 30 }} min</template>
      </el-table-column>
      <el-table-column label="Refresh TTL" width="110" align="center">
        <template #default="{ row }">{{ row.refreshTokenTtlDays ?? 7 }} 天</template>
      </el-table-column>
      <el-table-column label="自动授权" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.autoApprove" type="success" size="small">是</el-tag>
          <el-tag v-else type="info" size="small">否</el-tag>
        </template>
      </el-table-column>
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

    <el-dialog v-model="showEdit" :title="exist ? '编辑客户端' : '新建客户端'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="Client ID">
          <el-input v-model="form.clientId" placeholder="demo-client" :disabled="exist" />
          <p class="field-tip">全局唯一；不可修改，如需更换请新建</p>
        </el-form-item>
        <el-form-item label="Client Secret">
          <el-input v-model="form.clientSecret" placeholder="保存后不可再次查看，请妥善保管" show-password />
          <p class="field-tip" :style="exist && 'color: var(--warning)'">编辑模式下留空表示保留原值</p>
        </el-form-item>
        <el-form-item label="授权类型">
          <el-checkbox-group v-model="form.grantTypesArr">
            <el-checkbox value="authorization_code">authorization_code</el-checkbox>
            <el-checkbox value="refresh_token">refresh_token</el-checkbox>
            <el-checkbox value="password">password</el-checkbox>
            <el-checkbox value="client_credentials">client_credentials</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="回调地址">
          <el-input v-model="form.redirectUris" type="textarea" :rows="2" placeholder="http://localhost:5173/callback（每行一条）" />
        </el-form-item>
        <el-form-item label="Scopes">
          <el-input v-model="form.scopes" placeholder="openid,profile,email" />
        </el-form-item>
        <el-form-item label="Access Token TTL">
          <el-input-number v-model="form.accessTokenTtlMinutes" :min="1" :max="1440" />
          <span class="field-tip">分钟（1-1440）</span>
        </el-form-item>
        <el-form-item label="Refresh Token TTL">
          <el-input-number v-model="form.refreshTokenTtlDays" :min="1" :max="3650" />
          <span class="field-tip">天（1-3650）</span>
        </el-form-item>
        <el-form-item label="自动授权">
          <el-switch v-model="form.autoApprove" />
        </el-form-item>
        <el-form-item label="ID Token Claims">
          <el-input v-model="form.idTokenClaims" type="textarea" :rows="3" placeholder='{"department":"Engineering"}' />
          <p class="field-tip">JSON 格式，额外写入 ID Token 的自定义 claims</p>
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
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type ClientRow } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'
import { usePagination } from '../../../composables/usePagination'

const allRows = ref<ClientRow[]>([])
const loading = ref(false)
const showEdit = ref(false)
const exist = ref(false)
const form = reactive({
  _origClientId: '',
  clientId: '',
  clientSecret: '',
  grantTypesArr: [] as string[],
  redirectUris: '',
  scopes: '',
  accessTokenTtlMinutes: 30 as number,
  refreshTokenTtlDays: 7 as number,
  autoApprove: false,
  idTokenClaims: '',
})

const DEFAULT_ITEM = () => ({ _origClientId: '', clientId: '', clientSecret: '', grantTypesArr: [] as string[], redirectUris: '', scopes: '', accessTokenTtlMinutes: 30, refreshTokenTtlDays: 7, autoApprove: false, idTokenClaims: '' })

const { page, size, rows, total, reset } = usePagination(allRows)

function resetForm() {
  Object.assign(form, DEFAULT_ITEM())
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await adminApi.listClients(1, 500)
    allRows.value = res.rows
    reset()
  } finally { loading.value = false }
}
function onEdit(row: ClientRow | null): void {
  resetForm()
  exist.value = row !== null
  if (row) {
    form._origClientId = row.clientId
    form.clientId = row.clientId
    form.grantTypesArr = (row.grantTypes || '').split(',').filter(Boolean)
    form.redirectUris = row.redirectUris
    form.scopes = row.scopes
    form.accessTokenTtlMinutes = row.accessTokenTtlMinutes ?? 30
    form.refreshTokenTtlDays = row.refreshTokenTtlDays ?? 7
    form.autoApprove = row.autoApprove ?? false
    form.idTokenClaims = row.idTokenClaims || ''
  }
  showEdit.value = true
}
async function onSave(): Promise<void> {
  const payload: Record<string, unknown> = {
    clientId: form.clientId,
    clientSecret: form.clientSecret,
    grantTypes: form.grantTypesArr.join(','),
    redirectUris: form.redirectUris,
    scopes: form.scopes,
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
.field-tip { font-size: .72rem; color: var(--text-muted); margin-top: 4px; line-height: 1.4; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
