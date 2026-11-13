import { expect, test } from './fixtures';
import { ANONYMOUS, uniqueUsername } from './accounts';
import { ROUTES } from './routes';

const ADMIN_ROUTES = ROUTES.filter((route) => route.path.startsWith('/admin/'));

// A user without the admin role sees no admin menu and gets "Kein Zugriff" on every admin page.
test('a user gets no access to the admin pages', async ({ page, browser }) => {
  expect(ADMIN_ROUTES.length).toBeGreaterThan(0);
  const username = uniqueUsername('ac');
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill('Zugriff Nutzerin');
  await page.locator('#new-password').fill('zugriff-passwort-123');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText(username);

  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('zugriff-passwort-123');
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await expect(user.getByRole('navigation', { name: 'Hauptmenü' }).getByText('Verwaltung')).toHaveCount(0);

  for (const route of ADMIN_ROUTES) {
    const response = await user.goto(route.path);
    expect(response?.status(), route.path).toBe(403);
    await expect(user.getByRole('heading', { level: 1 }), route.path).toHaveText('Kein Zugriff');
  }
  await context.close();
});

// Without login every page except the login page leads to the login page.
test('an anonymous visitor reaches no page without login', async ({ browser }) => {
  const context = await browser.newContext({ storageState: ANONYMOUS });
  const page = await context.newPage();
  for (const route of ROUTES.filter((item) => !item.anonymous)) {
    await page.goto(route.path);
    await expect(page, route.path).toHaveURL(/\/login$/);
  }
  await context.close();
});
