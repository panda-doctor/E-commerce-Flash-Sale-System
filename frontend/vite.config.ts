import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// 后端网关：唯一公网入口（精确 Path 路由）。
// 必须走同源代理：后端会话 Cookie 为 SameSite=Strict，跨端口直连会导致不携带 Cookie。
const GATEWAY = process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8090'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    allowedHosts: true,
    port: 5173,
    proxy: {
      '/api': { target: GATEWAY, changeOrigin: false },
      // 商品图片静态资源同样由 product 应用提供，必须经网关代理
      '/uploads': { target: GATEWAY, changeOrigin: false },
    },
  },
  preview: {
    host: '0.0.0.0',
    allowedHosts: true,
    port: 4173,
    proxy: {
      '/api': { target: GATEWAY, changeOrigin: false },
      '/uploads': { target: GATEWAY, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
