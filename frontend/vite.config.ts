import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The browser only talks to this dev server. Calls to /api are forwarded to the
    // Spring Boot backend, so the backend needs no CORS configuration.
    // Set API_TARGET to use another backend, e.g. API_TARGET=http://localhost:8081 npm run dev
    proxy: {
      '/api': process.env.API_TARGET ?? 'http://localhost:8080',
    },
  },
})
