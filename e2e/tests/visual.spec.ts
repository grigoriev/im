import { expect, test } from './fixtures';
import type { Page } from '@playwright/test';
import { ADMIN, ANONYMOUS } from './accounts';
import { ROUTES } from './routes';

// Screenshot of every page, light and dark, phone and desktop. Update with scripts/e2e.sh --update.
// The project "visual" runs before every test that adds data (playwright.config.ts).
// Elements marked data-volatile depend on the date, for example the current year: the screenshot masks them.
const VIEWPORTS = [
  { name: 'phone', width: 390, height: 844 },
  { name: 'desktop', width: 1440, height: 900 },
];

// "Mein Konto" asks for the password once the login is 15 minutes old. Confirmed, it always shows the same state.
async function confirmPassword(page: Page): Promise<void> {
  const confirm = page.locator('#passkey-confirm');
  if (await confirm.isVisible()) {
    await confirm.getByLabel('Passwort').fill(ADMIN.password);
    await confirm.getByRole('button', { name: 'Bestätigen' }).click();
  }
  await expect(page.getByRole('button', { name: 'Passkey hinzufügen' })).toBeVisible();
}

for (const route of ROUTES.filter((item) => item.screenshot !== false)) {
  for (const viewport of VIEWPORTS) {
    for (const scheme of ['light', 'dark'] as const) {
      test(`${route.name} ${viewport.name} ${scheme}`, async ({ browser, adminState }) => {
        const context = await browser.newContext({
          colorScheme: scheme,
          viewport: { width: viewport.width, height: viewport.height },
          storageState: route.anonymous ? ANONYMOUS : adminState,
        });
        const page = await context.newPage();
        await page.goto(route.path);
        if (route.name === 'account') await confirmPassword(page);
        await page.evaluate(() => document.fonts.ready);
        await expect(page).toHaveScreenshot(`${route.name}-${viewport.name}-${scheme}.png`, {
          fullPage: true,
          mask: [page.locator('[data-volatile]')],
        });
        await context.close();
      });
    }
  }
}

// A touch screen shows what a mouse never does, such as the camera button for a receipt. The viewports above keep a
// mouse, so this one emulates a phone with touch.
for (const scheme of ['light', 'dark'] as const) {
  test(`expense-new touch ${scheme}`, async ({ browser, adminState }) => {
    const context = await browser.newContext({
      colorScheme: scheme,
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
      storageState: adminState,
    });
    const page = await context.newPage();
    await page.goto('/expenses/new');
    await page.evaluate(() => document.fonts.ready);
    await expect(page.locator('#expense-camera')).toBeVisible();
    await expect(page).toHaveScreenshot(`expense-new-touch-${scheme}.png`, {
      fullPage: true,
      mask: [page.locator('[data-volatile]')],
    });
    await context.close();
  });
}

// The account after a login by "Angemeldet bleiben": a new passkey needs the password first.
for (const scheme of ['light', 'dark'] as const) {
  test(`account-confirm ${scheme}`, async ({ browser }) => {
    const context = await browser.newContext({
      colorScheme: scheme,
      viewport: { width: 1440, height: 900 },
      storageState: ANONYMOUS,
    });
    const page = await context.newPage();
    await page.goto('/login');
    await page.getByLabel('Benutzername').fill(ADMIN.username);
    await page.getByLabel('Passwort').fill(ADMIN.password);
    await page.getByLabel('Angemeldet bleiben').check();
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Guten Tag');
    await context.clearCookies({ name: 'JSESSIONID' });
    await page.goto('/account');
    await expect(page.locator('#passkey-confirm')).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    await expect(page).toHaveScreenshot(`account-confirm-${scheme}.png`, {
      fullPage: true,
      mask: [page.locator('[data-volatile]')],
    });
    await context.close();
  });
}
