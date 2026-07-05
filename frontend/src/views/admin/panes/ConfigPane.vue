<template>
  <div>
    <PaneToolbar
      v-model:query="query"
      search-placeholder="搜索配置 key..."
      :show-search="true"
      :show-create="false"
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
      <el-table-column label="操作" width="110" align="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain :loading="savingKey === row.key" @click="save(row)">保存</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type ConfigItem } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'

const loading = ref(false)
const savingKey = ref('')
const query = ref('')
const page = ref(1)
const size = ref(20)
const items = ref<ConfigItem[]>([])
const drafts = ref<Record<string, string>>({})
const visibleSecrets = ref<Record<string, boolean>>({})

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

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
