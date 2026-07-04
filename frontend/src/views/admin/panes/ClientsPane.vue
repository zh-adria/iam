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
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="onEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
})

const DEFAULT_ITEM = () => ({ _origClientId: '', clientId: '', clientSecret: '', grantTypesArr: [] as string[], redirectUris: '', scopes: '' })

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
  }
  showEdit.value = true
}
async function onSave(): Promise<void> {
  const payload = {
    clientId: form.clientId,
    clientSecret: form.clientSecret,
    grantTypes: form.grantTypesArr.join(','),
    redirectUris: form.redirectUris,
    scopes: form.scopes,
  }
  await adminApi.upsertClient(payload)
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
