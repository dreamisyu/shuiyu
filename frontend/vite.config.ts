import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    allowedHosts: true, // 允许所有域名
        //  加上这个代理
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088', // 转发到后端
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '') // 去掉 /api 前缀
      }
    }
  }
})