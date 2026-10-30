import { defineConfig } from '@playwright/test'

// Start the real Spring Boot backend, MySQL and Redis before running this suite.
export default defineConfig({
  testDir: './integration',
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  outputDir: 'test-results/integration',
  use: {
    baseURL: 'http://127.0.0.1:15174',
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 15174 --strictPort',
    port: 15174,
    env: { API_PROXY_TARGET: process.env.INTEGRATION_API_TARGET || 'http://127.0.0.1:8080' },
  },
})
