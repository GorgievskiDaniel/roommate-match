import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  // Another backend, such as a second copy on a different port, can be used with BACKEND_PORT.
  const backend = `localhost:${loadEnv(mode, '.', 'BACKEND_').BACKEND_PORT ?? 8080}`
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': `http://${backend}`,
        '/ws': { target: `ws://${backend}`, ws: true },
      },
    },
  }
})
