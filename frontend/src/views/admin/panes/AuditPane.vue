<template>
  <div>
    <div class="toolbar">
      <el-input v-model.number="userId" placeholder="按 userId 过滤" style="width:160px" clearable />
      <el-button @click="load">查询</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border max-height="600">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户" width="80" />
      <el-table-column prop="action" label="动作" width="160" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="principal" label="主体" />
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column prop="detail" label="详情" show-overflow-tooltip />
      <el-table-column prop="occurredAt" label="时间" width="200" />
      <el-table-column prop="prevHash" label="前序哈希" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi, type AuditRow } from '../../../api/admin'

const rows = ref<AuditRow[]>([])
const loading = ref(false)
const userId = ref<number | undefined>(undefined)

async function load(): Promise<void> {
  loading.value = true
  try {
    const r = await adminApi.listAudit(1, 100, userId.value || undefined)
    rows.value = r.rows
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
