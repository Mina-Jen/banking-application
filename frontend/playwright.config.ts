import { defineConfig, devices } from '@playwright/test'

/**
 * Browser-level tests against a REAL backend and a REAL database.
 *
 * The backend runs on its own port and its own schema (`banking_e2e`), so a
 * test run never touches the accounts in `banking_db` and can start from a
 * known-empty schema the first time. Each test registers fresh users and opens
 * fresh accounts, so runs stay independent even though the database persists
 * between runs.
 *
 * Both servers are started by Playwright and stopped when the run ends. The
 * Vite proxy points at this run's backend, not at whatever is running on 18080.
 */
const BACKEND_PORT = 18081
const FRONTEND_PORT = 5273

const backendUrl = `http://localhost:${BACKEND_PORT}`
const frontendUrl = `http://localhost:${FRONTEND_PORT}`

export default defineConfig({
  testDir: './e2e',
  // One worker: these tests each start sessions and one of them spends a whole
  // minute's request budget, so the shared database is easier to reason about
  // when runs do not overlap.
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  timeout: 45_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: frontendUrl,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: [
    {
      // Flyway creates the schema (V1..V4) on first start, because the database
      // itself is created empty by createDatabaseIfNotExist.
      command: './mvnw -q spring-boot:run',
      cwd: '../backend',
      url: `${backendUrl}/actuator/health`,
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
      env: {
        SERVER_PORT: String(BACKEND_PORT),
        BANKING_DB_URL: 'jdbc:mariadb://localhost:3306/banking_e2e?createDatabaseIfNotExist=true',
        JWT_SECRET: 'e2e-only-jwt-secret-that-is-long-enough-for-hs256-0123456789',
        ADMIN_PASSWORD: 'e2e-admin-password',
      },
    },
    {
      command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
      url: frontendUrl,
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
      env: {
        BACKEND_URL: backendUrl,
      },
    },
  ],
})
