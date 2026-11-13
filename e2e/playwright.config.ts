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
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    trace: 'retain-on-failure',
  },
  expect: {
    // Strict on purpose: the default threshold 0.2 misses dark theme changes, and a ratio misses one new menu item.
    toHaveScreenshot: { threshold: 0.05, maxDiffPixels: 20, animations: 'disabled' },
  },
  // The screenshots run first, while the database holds only the admin: tests that add data come after.
  projects: [
    { name: 'visual', testMatch: /visual\.spec\.ts/, use: { ...devices['Desktop Chrome'] } },
    { name: 'chromium', testIgnore: /visual\.spec\.ts/, dependencies: ['visual'], use: { ...devices['Desktop Chrome'] } },
  ],
});
