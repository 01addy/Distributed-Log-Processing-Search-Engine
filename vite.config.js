import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api/search': { target: 'http://localhost:8083', changeOrigin: true },
      '/api/ingest': { target: 'http://localhost:8081', changeOrigin: true }
    }
  }
})
