<template>
  <div class="callback-view">
    <div class="callback-container glass-card animate-in">
      <div class="spinner" aria-hidden="true">
        <span style="--i:0" />
        <span style="--i:1" />
        <span style="--i:2" />
        <span style="--i:3" />
        <span style="--i:4" />
        <span style="--i:5" />
        <span style="--i:6" />
        <span style="--i:7" />
      </div>
      <p class="callback-text">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ msg: string }>()
</script>

<style scoped>
.callback-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
}
.callback-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 44px 56px;
}

/* ── modern dot spinner (8 points around a circle) ── */
.spinner {
  position: relative;
  width: 36px;
  height: 36px;
  animation: spin 1s linear infinite;
}
.spinner span {
  position: absolute;
  top: 50%; left: 50%;
  width: 6px; height: 6px;
  margin: -3px 0 0 -3px;
  background: var(--accent);
  border-radius: 50%;
  transform: rotate(calc(var(--i) * 45deg)) translateY(-14px);
  opacity: calc(0.35 + 0.1 * (8 - var(--i)));
}
@keyframes spin { to { transform: rotate(-360deg); } }

.callback-text {
  font-family: var(--font-heading);
  font-weight: 600;
  color: var(--text-muted);
  font-size: 0.95rem;
  letter-spacing: -0.01em;
}
</style>
