import { defineConfig, devices } from '@playwright/test';

/**
 * The staff console in a browser, with its API mocked per test: no backend
 * needed. Desktop size - the console is used at a desk.
 *
 *   npx playwright test -c qa/console-e2e
 */
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  reporter: [['list']],
  use: { ...devices['Desktop Chrome'], locale: 'en-IN', timezoneId: 'Asia/Kolkata' },
});
