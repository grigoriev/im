import { defineConfig, devices } from '@playwright/test';

// Runs only inside the pinned Playwright image (scripts/e2e.sh), so screenshots match everywhere.
export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFilePath}/{arg}{ext}',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  globalSetup: './global-setup.ts',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://web:8080',
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    trace: 'retain-on-failure',
    // Logged in as the admin of docker-compose.yml. Tests of the login page reset it.
    storageState: '.auth/admin.json',
  },
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.001, animations: 'disabled' },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
