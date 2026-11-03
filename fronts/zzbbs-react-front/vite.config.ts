import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backend = process.env.PYBBS_BACKEND || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': backend,
      '/common': backend,
      '/static': backend,
      '/upload': backend,
    },
  },
})
