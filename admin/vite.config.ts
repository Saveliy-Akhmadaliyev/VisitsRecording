import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Прокси /api -> бекенд, чтобы не было CORS в разработке.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
