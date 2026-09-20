import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：所有 /api 请求转发到 Spring Boot 后端（端口 8081），避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
