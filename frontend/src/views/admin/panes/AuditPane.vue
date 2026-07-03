<template>
  <div>
    <div class="pane-toolbar">
      <input v-model="userId" class="neo-input" placeholder="按 userId 过滤" style="width:160px" />
      <button class="neo-btn ghost" style="width:auto;padding:8px 16px" @click="load">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        查询
      </button>
      <div class="spacer" />
    </div>

    <el-table :data="rows" v-loading="loading" class="neo-table" max-height="600">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户" width="80" />
      <el-table-column prop="action" label="动作" width="160" />
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <span :class="['result-badge', row.result === 'SUCCESS' ? 'success' : 'failure']">{{ row.result }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="principal" label="主体" min-width="140" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      <el-table-column prop="occurredAt" label="时间" width="180" />
      <el-table-column prop="prevHash" label="前序哈希" min-width="120" show-overflow-tooltip />
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
.pane-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.spacer { flex: 1; }
.result-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 0.75rem;
  font-weight: 600;
  font-family: var(--font-mono);
}
.result-badge.success {
  background: rgba(46, 213, 115, 0.12);
  color: var(--success);
  border: 1px solid rgba(46, 213, 115, 0.2);
}
.result-badge.failure {
  background: rgba(255, 71, 87, 0.12);
  color: var(--danger);
  border: 1px solid rgba(255, 71, 87, 0.2);
}
</style>