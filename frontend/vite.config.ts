import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // ponytail: 1) admin 端点 → iam-admin :8081; 2) 其余 /iam/* → auth-server :8080.
      // 顺序不能反。路径按开头匹配 — 同 nginx location。
      // 为方便排查，proxyReq 日志默认开启；随时改 loglevel: 'silent' 关闭。
      '/iam/admin': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: p => p.replace(/^\/iam\/admin/, '/iam/admin'),
        configure: proxy => {
          proxy.on('error', (err, _req, res) => {
            console.error('[proxy /iam/admin] backend error:', err.message)
            // 向客户端送出明确 status code，避免浏览器看到 502 卡住
            if (!res.headersSent) {
              res.writeHead(502, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ proxyError: 'admin backend unavailable', detail: err.message }))
            }
          })
        }
      },
      '/iam': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: proxy => {
          proxy.on('error', (err, _req, res) => {
            console.error('[proxy /iam] backend error:', err.message)
            if (!res.headersSent) {
              res.writeHead(502, { 'Content-Type': 'application/json' })
              res.end(JSON.stringify({ proxyError: 'auth-server backend unavailable', detail: err.message }))
            }
          })
        }
      }
    }
  }
})
