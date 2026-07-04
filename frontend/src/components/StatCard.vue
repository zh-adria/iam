<script setup lang="ts">
defineProps<{
  value: string | number
  label: string
  delta?: string
  deltaUp?: boolean
  icon?: string
  accent?: boolean
}>()
</script>

<template>
  <div :class="['stat-card glass-card', { 'accent-edge': accent }]">
    <div class="stat-head">
      <span v-if="$slots.icon || icon" class="stat-icon-wrap">
        <slot name="icon"><span v-html="icon" /></slot>
      </span>
      <span v-if="delta" :class="['stat-delta', deltaUp ? 'up' : 'down']">
        <svg v-if="deltaUp" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="18 15 12 9 6 15"/></svg>
        <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="6 9 12 15 18 9"/></svg>
        {{ delta }}
      </span>
    </div>
    <div class="stat-value">{{ value }}</div>
    <div class="stat-label">{{ label }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-icon-wrap {
  width: 36px; height: 36px;
  border-radius: var(--radius-md);
  background: var(--accent-glow);
  display: flex; align-items: center; justify-content: center;
  color: var(--accent);
  flex-shrink: 0;
}
.stat-icon-wrap :deep(svg) { width: 18px; height: 18px; }
.stat-delta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 0.72rem;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--bg-card);
}
.stat-delta.up { color: var(--success); background: var(--success-glow); }
.stat-delta.down { color: var(--danger); background: var(--danger-glow); }
.stat-value {
  font-family: var(--font-heading);
  font-size: 1.85rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1;
  color: var(--text-primary);
}
.stat-label { font-size: 0.8rem; color: var(--text-muted); font-weight: 500; }
</style>
