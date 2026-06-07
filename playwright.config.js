// @ts-check
const { defineConfig, devices } = require('@playwright/test');

const storageState = 'e2e/.auth/user.json';

/**
 * @see https://playwright.dev/docs/test-configuration
 */
module.exports = defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /.*\.setup\.js/ },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState,
      },
      dependencies: ['setup'],
      testIgnore: [/auth\.spec\.js/, /ux-mobile-shell\.spec\.js/],
    },
    {
      name: 'chromium-mobile',
      use: {
        ...devices['Pixel 5'],
        storageState,
      },
      dependencies: ['setup'],
      testMatch: /ux-mobile-shell\.spec\.js/,
    },
    {
      name: 'auth',
      use: { ...devices['Desktop Chrome'] },
      testMatch: /auth\.spec\.js/,
    },
  ],
});
