<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
defineSlots<{ action?: () => any }>()
</script>

<template>
  <div class="empty-state">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
      <rect x="3" y="3" width="18" height="18" rx="3"/>
      <line x1="9" y1="9" x2="15" y2="15"/>
      <line x1="15" y1="9" x2="9" y2="15"/>
    </svg>
    <div class="empty-state-text">
      <div style="font-weight:600;color:var(--text-primary)">{{ title }}</div>
      <div v-if="description" style="font-size:0.85rem;margin-top:2px;color:var(--text-muted)">{{ description }}</div>
    </div>
    <slot name="action" />
  </div>
</template>
