import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：所有 /api 请求转发到 API 网关（P1-1 起；网关再路由到单体 8081），避免跨域
// 如需直连单体调试，把 target 临时改回 http://localhost:8081
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
})
