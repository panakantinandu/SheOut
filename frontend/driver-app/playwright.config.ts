import { defineConfig, devices } from '@playwright/test';

/**
 * Browser tests for the partner app. The API is mocked per test (see e2e/),
 * so nothing here needs the backend.
 *
 *   npm run test:e2e -w frontend/driver-app
 *
 * Browsers: PLAYWRIGHT_BROWSERS_PATH (D:\DevTools\ms-playwright on the dev
 * machine), or `npx playwright install chromium`.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4191',
    ...devices['Pixel 7'],
    serviceWorkers: 'block',
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
  },
  webServer: {
    command: 'npx vite --port 4191 --strictPort',
    url: 'http://localhost:4191',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
