import AxeBuilder from '@axe-core/playwright';
import { expect, test } from './fixtures';
import { ADMIN } from './accounts';
import { ROUTES } from './routes';

test('greets the logged-in user in German', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('Übersicht · Rechnungen');
  await expect(page.locator('html')).toHaveAttribute('lang', 'de');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(`Guten Tag, ${ADMIN.displayName}`);
});

// The theme switch sits at the bottom of the menu: in the sidebar on the desktop, in the drawer on the phone.
test('switches between light, dark and system theme and keeps the choice', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/');
  const root = page.locator('html');
  const menu = page.getByRole('navigation', { name: 'Hauptmenü' });
  await expect(page.getByRole('banner').getByRole('button')).toHaveCount(0);
  await menu.getByRole('button', { name: 'Dunkel' }).click();
  await expect(root).toHaveAttribute('data-theme', 'dark');
  await expect(menu.getByRole('button', { name: 'Dunkel' })).toHaveAttribute('aria-pressed', 'true');
  await page.reload();
  await expect(root).toHaveAttribute('data-theme', 'dark');
  await menu.getByRole('button', { name: 'Hell' }).click();
  await expect(root).toHaveAttribute('data-theme', 'light');
  await menu.getByRole('button', { name: 'System' }).click();
  await expect(root).not.toHaveAttribute('data-theme');
});

test('switches the theme from the drawer on the phone', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/');
  await page.getByRole('banner').locator('label[for="nav"]').click();
  const menu = page.getByRole('navigation', { name: 'Hauptmenü' });
  await menu.getByRole('button', { name: 'Dunkel' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await menu.getByRole('button', { name: 'System' }).click();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme');
});

test('loads every asset from the app itself', async ({ page, baseURL }) => {
  const own = new URL(baseURL!).origin;
  const foreign: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).origin !== own) foreign.push(request.url());
  });
  await page.goto('/');
  await page.waitForLoadState('networkidle');
  expect(foreign).toEqual([]);
});

for (const route of ROUTES) {
  test(`${route.name} has no accessibility violations`, async ({ browser, adminState }) => {
    const context = await browser.newContext({ storageState: route.anonymous ? { cookies: [], origins: [] } : adminState });
    const page = await context.newPage();
    await page.goto(route.path);
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
    await context.close();
  });
}
