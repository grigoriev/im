import { expect, test } from '@playwright/test';
import { ADMIN, ANONYMOUS, uniqueUsername } from './accounts';

test.describe('without session', () => {
  test.use({ storageState: ANONYMOUS });

  test('sends a visitor to the login page', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button', { name: 'Anmelden' })).toBeVisible();
  });

  test('names a wrong password', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Benutzername').fill(ADMIN.username);
    await page.getByLabel('Passwort').fill('falsch-falsch-falsch');
    await page.getByRole('button', { name: 'Anmelden' }).click();
    await expect(page.getByRole('alert')).toHaveText('Benutzername oder Passwort ist falsch.');
  });

  test('logs in and out', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Benutzername').fill(ADMIN.username);
    await page.getByLabel('Passwort').fill(ADMIN.password);
    await page.getByRole('button', { name: 'Anmelden' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(`Guten Tag, ${ADMIN.displayName}`);
    await page.getByRole('button', { name: 'Abmelden' }).click();
    await expect(page.getByRole('status')).toHaveText('Sie sind abgemeldet.');
  });
});

test('the admin creates a user, who sees no admin area', async ({ page, browser }) => {
  const username = uniqueUsername('elena');
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill('Elena Sommer');
  await page.locator('#new-password').fill('elena-passwort-123');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Benutzer „${username}“ angelegt.`);

  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('elena-passwort-123');
  await user.getByRole('button', { name: 'Anmelden' }).click();
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Guten Tag, Elena Sommer');
  await expect(user.getByRole('link', { name: 'Benutzer' })).toHaveCount(0);
  await user.goto('/admin/benutzer');
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Kein Zugriff');
  await context.close();
});

test('the admin sets a new password', async ({ page, browser }) => {
  const username = uniqueUsername('gast');
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill('Gast');
  await page.locator('#new-password').fill('erstes-passwort-123');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  const row = page.locator('#user-list > div', { hasText: username });
  await row.getByLabel('Neues Passwort').fill('zweites-passwort-123');
  await row.getByRole('button', { name: 'Passwort setzen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Neues Passwort für „${username}“ gesetzt.`);

  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('zweites-passwort-123');
  await user.getByRole('button', { name: 'Anmelden' }).click();
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Guten Tag, Gast');
  await context.close();
});

test('a long name without spaces stays inside the menu and the page', async ({ page, browser }) => {
  const username = uniqueUsername('lang');
  const name = 'Sehrlangername'.repeat(9).slice(0, 120);
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.locator('#new-password').fill('lang-passwort-123');
  await page.getByRole('button', { name: 'Anlegen' }).click();

  const context = await browser.newContext({ storageState: ANONYMOUS, viewport: { width: 1024, height: 800 } });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('lang-passwort-123');
  await user.getByRole('button', { name: 'Anmelden' }).click();
  await expect(user.getByRole('heading', { level: 1 })).toContainText('Sehrlangername');
  const overflow = await user.evaluate(() => {
    const menu = document.querySelector('nav[aria-label="Hauptmenü"]')!;
    const inside = [...menu.querySelectorAll('*')].every((el) => el.getBoundingClientRect().right <= menu.getBoundingClientRect().right + 1);
    return { inside, page: document.scrollingElement!.scrollWidth <= window.innerWidth + 1 };
  });
  expect(overflow).toEqual({ inside: true, page: true });
  await context.close();
});
