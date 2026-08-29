import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // 监听所有本机回环地址，兼容浏览器将 localhost 解析为 IPv4 或 IPv6 的情况
  server: { host: '0.0.0.0', port: 5173, strictPort: true },
})
