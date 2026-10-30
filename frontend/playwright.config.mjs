import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  use: { baseURL: 'http://127.0.0.1:15173', trace: 'retain-on-failure' },
  webServer: [
    { command: 'node mock/server.mjs', port: 18080, env: { MOCK_PORT: '18080' } },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
      port: 15173,
      env: { API_PROXY_TARGET: 'http://127.0.0.1:18080' },
    },
  ],
})
