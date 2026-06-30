import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // ponytail: admin endpoints live on a separate service (port 8081) after the
      // backend split. Order matters — this rule must come before the /iam catch-all.
      '/iam/admin': { target: 'http://localhost:8081', changeOrigin: true },
      '/iam': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
