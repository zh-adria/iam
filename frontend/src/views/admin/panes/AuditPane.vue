<template>
  <div>
    <PaneToolbar
      v-model:query="userFilter"
      search-placeholder="按 userId 过滤..."
      :show-search="true"
    />
    <el-table :data="rows" v-loading="loading" style="width:100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="userId" label="用户" width="70" />
      <el-table-column prop="tenant" label="租户" width="100" />
      <el-table-column prop="action" label="动作" min-width="140" />
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <span :class="['neo-tag', row.result === 'SUCCESS' ? 'success' : 'danger']">{{ row.result }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="principal" label="主体" min-width="120" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
      <el-table-column prop="occurredAt" label="时间" width="170" />
      <el-table-column prop="prevHash" label="前序哈希" min-width="120" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '../../../api/admin'
import PaneToolbar from '../../../components/PaneToolbar.vue'

const rows = ref<any[]>([])
const loading = ref(false)
const userFilter = ref('')

async function load(): Promise<void> {
  loading.value = true
  try {
    const userId = userFilter.value ? Number(userFilter.value) : undefined
    rows.value = (await adminApi.listAudit(1, 50, userId)).rows
  } finally { loading.value = false }
}
onMounted(load)
</script>
