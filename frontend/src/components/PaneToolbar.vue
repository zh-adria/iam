<script setup lang="ts">
defineProps<{
  searchPlaceholder?: string
  searchWidth?: string
  showSearch?: boolean
  showCreate?: boolean
  createLabel?: string
}>()
const emit = defineEmits<{
  create: []
  'update:query': [v: string]
}>()
const query = defineModel<string>('query', { default: '' })
defineSlots<{
  action?: () => any
}>()
</script>

<template>
  <div class="pane-toolbar">
    <el-button v-if="showCreate" type="primary" @click="emit('create')">
      <el-icon><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg></el-icon>
      {{ createLabel || '新建' }}
    </el-button>
    <slot name="action" />
    <span class="spacer" />
    <el-input
      v-if="showSearch"
      v-model="query"
      class="search-input"
      :placeholder="searchPlaceholder"
      :style="searchWidth ? { width: searchWidth } : undefined"
      clearable
    >
      <template #suffix>
        <el-icon style="color: var(--text-muted)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </el-icon>
      </template>
    </el-input>
  </div>
</template>
