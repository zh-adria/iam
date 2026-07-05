<template>
  <div>
    <PaneToolbar
      v-model:query="query"
      search-placeholder="搜索配置 key..."
      :show-search="true"
      :show-create="true"
      @create="createItem"
      @search="page = 1"
    />

    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="key" label="配置项" min-width="260" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <span :class="['neo-tag', row.type === 'secret' ? 'danger' : 'accent']">{{ row.type }}</span>
        </template>
      </el-table-column>
      <el-table-column label="值" min-width="300">
        <template #default="{ row }">
          <el-input
            v-model="drafts[row.key]"
            :type="row.type === 'secret' && !visibleSecrets[row.key] ? 'password' : 'text'"
            show-password
            placeholder="未配置"
          />
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" />
      <el-table-column label="操作" width="150" align="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain :loading="savingKey === row.key" @click="save(row)">保存</el-button>
          <el-button size="small" type="danger" plain :loading="deletingKey === row.key" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="createDialog" title="新建配置项" width="520px">
      <div class="form-row">
        <span class="form-label">配置项 key</span>
        <el-input v-model="newKey" placeholder="如 iam.social.qq.app-id" />
      </div>
      <div class="form-row">
        <span class="form-label">值</span>
        <el-input v-model="newValue" placeholder="配置值" />
      </div>
      <div class="form-row">
        <span class="form-label">类型</span>
        <el-select v-model="newType" style="width:100%">
          <el-option label="string" value="string" />
          <el-option label="secret" value="secret" />
          <el-option label="int" value="int" />
        </el-select>
      </div>
      <div class="form-row">
        <span class="form-label">说明</span>
        <el-input v-model="newDesc" placeholder="简要说明" />
      </div>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type ConfigItem } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'

const loading = ref(false)
const savingKey = ref('')
const deletingKey = ref('')
const query = ref('')
const page = ref(1)
const size = ref(20)
const items = ref<ConfigItem[]>([])
const drafts = ref<Record<string, string>>({})
const visibleSecrets = ref<Record<string, boolean>>({})

const createDialog = ref(false)
const newKey = ref('')
const newValue = ref('')
const newType = ref('string')
const newDesc = ref('')

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return items.value
  return items.value.filter(item =>
    item.key.toLowerCase().includes(q) || (item.description || '').toLowerCase().includes(q))
})
const total = computed(() => filtered.value.length)
const rows = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await adminApi.config()
    items.value = data.items || []
    drafts.value = Object.fromEntries(items.value.map(item => [item.key, item.value || '']))
  } finally {
    loading.value = false
  }
}

async function save(item: ConfigItem): Promise<void> {
  savingKey.value = item.key
  try {
    await adminApi.updateConfig({ ...item, value: drafts.value[item.key] || '' })
    item.value = drafts.value[item.key] || ''
    ElMessage.success('已保存')
  } finally {
    savingKey.value = ''
  }
}

async function remove(item: ConfigItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除配置项 ${item.key}？`, '确认', { type: 'warning' })
    deletingKey.value = item.key
    await adminApi.deleteConfig(item.key)
    items.value = items.value.filter(i => i.key !== item.key)
    delete drafts.value[item.key]
    ElMessage.success('已删除')
  } catch { /* cancelled */ } finally {
    deletingKey.value = ''
  }
}

function createItem() {
  newKey.value = ''
  newValue.value = ''
  newType.value = 'string'
  newDesc.value = ''
  createDialog.value = true
}

async function confirmCreate() {
  const key = newKey.value.trim()
  if (!key) return ElMessage.warning('配置项 key 不能为空')
  try {
    await adminApi.updateConfig({ key, value: newValue.value, type: newType.value, description: newDesc.value })
    items.value.push({ key, value: newValue.value, type: newType.value, description: newDesc.value })
    drafts.value[key] = newValue.value
    createDialog.value = false
    ElMessage.success('已创建')
  } catch { /* ignore */ }
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.form-label {
  width: 90px;
  font-size: 0.85rem;
  color: var(--text-secondary);
  flex-shrink: 0;
}
</style>
