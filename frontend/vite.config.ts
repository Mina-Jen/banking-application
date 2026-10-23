import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The Spring Boot backend. Port 18080 because 8080 is busy on this machine.
const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:18080'

export default defineConfig({
  plugins: [react(), tailwindcss()],

  server: {
    port: 5173,
    // The browser talks only to this dev server; Vite forwards /api to Spring
    // Boot. Both look like the same origin to the browser, so no CORS setup is
    // needed in the backend and no credentials ever leave this machine.
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
    },
  },

  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Only the jsdom suites live under src/. The browser tests in e2e/ are
    // Playwright specs (they import @playwright/test, not Vitest) and are run by
    // `npm run e2e`; without this narrowing Vitest would try to collect them and
    // fail on every `npm test`.
    include: ['src/**/*.test.{ts,tsx}'],
    // Keep the generated OpenAPI schema out of coverage numbers.
    coverage: { exclude: ['src/api/schema.d.ts'] },
  },
})
