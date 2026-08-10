import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Proxies /api/* to the Spring Boot backend during local dev, so the
    // frontend can always call relative paths ("/api/v1/...") without
    // needing VITE_API_BASE_URL set - CORS is still configured
    // server-side (crm.cors.allowed-origins) as a second line of defense
    // for anyone who runs the frontend dev server without this proxy.
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
