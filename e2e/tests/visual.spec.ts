import { expect, test } from '@playwright/test';
import { ROUTES } from './routes';

// Screenshot of every page, light and dark, phone and desktop. Update with scripts/e2e.sh --update.
const VIEWPORTS = [
  { name: 'phone', width: 390, height: 844 },
  { name: 'desktop', width: 1440, height: 900 },
];

for (const route of ROUTES) {
  for (const viewport of VIEWPORTS) {
    for (const scheme of ['light', 'dark'] as const) {
      test(`${route.name} ${viewport.name} ${scheme}`, async ({ page }) => {
        await page.emulateMedia({ colorScheme: scheme });
        await page.setViewportSize({ width: viewport.width, height: viewport.height });
        await page.goto(route.path);
        await page.evaluate(() => document.fonts.ready);
        await expect(page).toHaveScreenshot(`${route.name}-${viewport.name}-${scheme}.png`, { fullPage: true });
      });
    }
  }
}
