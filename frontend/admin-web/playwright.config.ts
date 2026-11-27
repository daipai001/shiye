import { defineConfig, devices } from '@playwright/test'

const channel = process.env.PLAYWRIGHT_CHANNEL || (process.env.CI ? undefined : 'chrome')

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:15174',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        channel,
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --host localhost --port 15174',
        url: 'http://localhost:15174',
        reuseExistingServer: true,
        timeout: 120_000,
      },
})
