import { defineConfig, devices } from '@playwright/test'

// Started by scripts/test-browser.sh, which provides the throwaway database.
const BACKEND_PORT = 18080
const WEB_PORT = 5199

export default defineConfig({
  testDir: './e2e',
  // One story on one database: each test builds on the one before.
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    locale: 'en-GB',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: './gradlew --quiet bootRun',
      cwd: '../services/core-service',
      url: `http://localhost:${BACKEND_PORT}/actuator/health`,
      env: { SERVER_PORT: String(BACKEND_PORT) },
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      command: `npx vite --port ${WEB_PORT} --strictPort`,
      url: `http://localhost:${WEB_PORT}`,
      env: { API_TARGET: `http://localhost:${BACKEND_PORT}` },
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
