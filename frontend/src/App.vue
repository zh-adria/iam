<template>
  <div class="app-container">
    <!-- Animated orbs for background depth -->
    <div class="orb orb-1" />
    <div class="orb orb-2" />
    <router-view v-slot="{ Component, route }">
      <transition :name="transitionName" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const transitionName = ref('fade-slide')
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

/* ── Animated gradient orbs ── */
.orb {
  position: fixed;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
  pointer-events: none;
  z-index: 0;
}
.orb-1 {
  width: 500px; height: 500px;
  top: -150px; left: -100px;
  background: radial-gradient(circle, rgba(0,212,255,0.25) 0%, transparent 70%);
  animation: orbFloat1 20s ease-in-out infinite;
}
.orb-2 {
  width: 400px; height: 400px;
  bottom: -100px; right: -80px;
  background: radial-gradient(circle, rgba(108,92,231,0.2) 0%, transparent 70%);
  animation: orbFloat2 25s ease-in-out infinite;
}

@keyframes orbFloat1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(100px, 60px) scale(1.1); }
  50% { transform: translate(50px, -30px) scale(0.95); }
  75% { transform: translate(-40px, 40px) scale(1.05); }
}
@keyframes orbFloat2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-80px, -50px) scale(1.08); }
  66% { transform: translate(30px, 30px) scale(0.92); }
}

/* ── Page transition ── */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.98);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.99);
}
</style>
