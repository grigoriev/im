import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('shows the start page in German', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('Rechnungen');
  await expect(page.locator('html')).toHaveAttribute('lang', 'de');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Willkommen');
});

test('switches between light, dark and system theme and keeps the choice', async ({ page }) => {
  await page.goto('/');
  const root = page.locator('html');
  await page.getByRole('button', { name: 'Dunkel' }).click();
  await expect(root).toHaveAttribute('data-theme', 'dark');
  await expect(page.getByRole('button', { name: 'Dunkel' })).toHaveAttribute('aria-pressed', 'true');
  await page.reload();
  await expect(root).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Hell' }).click();
  await expect(root).toHaveAttribute('data-theme', 'light');
  await page.getByRole('button', { name: 'System' }).click();
  await expect(root).not.toHaveAttribute('data-theme');
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

test('has no accessibility violations', async ({ page }) => {
  await page.goto('/');
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});
