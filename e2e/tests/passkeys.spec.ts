import type { Browser, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { ANONYMOUS, uniqueUsername } from './accounts';
import { addVirtualAuthenticator } from './authenticator';

const PASSWORD = 'passkey-passwort-123';

// A fresh user, so parallel tests never share passkeys. Returns a page logged in with the password.
async function newUser(page: Page, browser: Browser, name: string): Promise<{ user: Page; username: string }> {
  const username = uniqueUsername('pk');
  await page.goto('/admin/users');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.locator('#new-password').fill(PASSWORD);
  await page.locator('#new-confirm').fill(PASSWORD);
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText(username);
  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill(PASSWORD);
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await expect(user.getByRole('heading', { level: 1 })).toHaveText(`Guten Tag, ${name}`);
  return { user, username };
}

test('links a passkey to the account, logs in with it, renames and removes it', async ({ page, browser }) => {
  const { user } = await newUser(page, browser, 'Passkey Nutzerin');
  const credentials = await addVirtualAuthenticator(user);

  await user.goto('/account');
  await expect(user.getByText('Noch kein Passkey.')).toBeVisible();
  await user.getByLabel('Name des Geräts').fill('MacBook Air');
  await user.getByRole('button', { name: 'Passkey hinzufügen' }).click();
  await expect(user.getByRole('status')).toHaveText('Passkey hinzugefügt.');
  await expect(user.locator('#passkey-list')).toContainText('MacBook Air');
  expect(await credentials()).toBe(1);

  // Log out, then log in with the passkey only.
  await user.getByRole('button', { name: 'Abmelden' }).click();
  await user.getByRole('button', { name: 'Mit Passkey anmelden' }).click();
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Guten Tag, Passkey Nutzerin');

  await user.goto('/account');
  await expect(user.locator('#passkey-list')).toContainText('zuletzt');
  await user.locator('#passkey-list').getByRole('button', { name: 'Umbenennen' }).click();
  const rename = user.locator('dialog[open]');
  await rename.locator('#label-0').fill('iPhone');
  await rename.getByRole('button', { name: 'Umbenennen' }).click();
  await expect(user.getByRole('status')).toHaveText('Passkey umbenannt.');
  await expect(user.locator('#passkey-list')).toContainText('iPhone');
  await user.getByRole('button', { name: 'Entfernen' }).click();
  await expect(user.getByRole('status')).toHaveText('Passkey entfernt.');
  await expect(user.getByText('Noch kein Passkey.')).toBeVisible();

  // The removed passkey no longer logs in.
  await user.getByRole('button', { name: 'Abmelden' }).click();
  await user.getByRole('button', { name: 'Mit Passkey anmelden' }).click();
  await expect(user.getByRole('alert')).toContainText('Das hat nicht geklappt');
  await user.context().close();
});

test('the admin removes the passkeys of a user', async ({ page, browser }) => {
  const { user, username } = await newUser(page, browser, 'Verlorenes Telefon');
  await addVirtualAuthenticator(user);
  await user.goto('/account');
  await user.getByLabel('Name des Geräts').fill('Telefon');
  await user.getByRole('button', { name: 'Passkey hinzufügen' }).click();
  await expect(user.getByRole('status')).toHaveText('Passkey hinzugefügt.');

  await page.goto('/admin/users');
  const row = page.locator('#user-list > div', { hasText: username });
  await expect(row).toContainText('1 Passkey(s)');
  await row.getByRole('button', { name: 'Passkeys entfernen' }).click();
  await expect(page.getByRole('status')).toHaveText(`1 Passkey(s) von „${username}“ entfernt.`);
  await expect(page.locator('#user-list > div', { hasText: username })).toContainText('0 Passkey(s)');
  await user.context().close();
});

test('changes the own password', async ({ page, browser }) => {
  const { user, username } = await newUser(page, browser, 'Neues Passwort');
  await user.goto('/account');
  await user.getByLabel('Aktuelles Passwort').fill(PASSWORD);
  await user.getByLabel('Neues Passwort', { exact: true }).fill('ganz-neues-passwort');
  await user.getByLabel('Neues Passwort wiederholen').fill('ganz-anderes-passwort');
  await user.getByRole('button', { name: 'Passwort ändern' }).click();
  await expect(user.getByRole('alert')).toHaveText('Die beiden Passwörter stimmen nicht überein.');
  await user.getByLabel('Aktuelles Passwort').fill('falsch-falsch-falsch');
  await user.getByLabel('Neues Passwort', { exact: true }).fill('ganz-neues-passwort');
  await user.getByLabel('Neues Passwort wiederholen').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Passwort ändern' }).click();
  await expect(user.getByRole('alert')).toHaveText('Das aktuelle Passwort stimmt nicht.');
  await user.getByLabel('Aktuelles Passwort').fill(PASSWORD);
  await user.getByLabel('Neues Passwort', { exact: true }).fill('ganz-neues-passwort');
  await user.getByLabel('Neues Passwort wiederholen').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Passwort ändern' }).click();
  await expect(user.getByRole('status')).toHaveText('Passwort geändert.');
  await user.getByRole('button', { name: 'Abmelden' }).click();
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Guten Tag, Neues Passwort');
  await user.context().close();
});

// A second browser of the user, logged in with "Angemeldet bleiben" and without the session of that login.
async function rememberedLogin(browser: Browser, username: string): Promise<Page> {
  const context = await browser.newContext({ storageState: ANONYMOUS });
  const page = await context.newPage();
  await page.goto('/login');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Passwort').fill(PASSWORD);
  await page.getByLabel('Angemeldet bleiben').check();
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Guten Tag');
  await context.clearCookies({ name: 'JSESSIONID' });
  return page;
}

test('a login by "Angemeldet bleiben" confirms the password before a new passkey', async ({ page, browser }) => {
  const { user, username } = await newUser(page, browser, 'Bestätigung');
  await user.context().close();
  const remembered = await rememberedLogin(browser, username);
  await addVirtualAuthenticator(remembered);

  await remembered.goto('/account');
  await expect(remembered.getByText('Bestätigen Sie für einen neuen Passkey oder API-Token zuerst Ihr Passwort.')).toBeVisible();
  await expect(remembered.getByRole('button', { name: 'Passkey hinzufügen' })).toHaveCount(0);
  const confirm = remembered.locator('#passkey-confirm');
  await confirm.getByLabel('Passwort').fill('falsch-falsch-falsch');
  await confirm.getByRole('button', { name: 'Bestätigen' }).click();
  await expect(remembered.getByRole('alert')).toHaveText('Das Passwort stimmt nicht.');
  await confirm.getByLabel('Passwort').fill(PASSWORD);
  await confirm.getByRole('button', { name: 'Bestätigen' }).click();
  await remembered.getByLabel('Name des Geräts').fill('MacBook Air');
  await remembered.getByRole('button', { name: 'Passkey hinzufügen' }).click();
  await expect(remembered.getByRole('status')).toHaveText('Passkey hinzugefügt.');
  await remembered.context().close();
});

test('a new password ends the sessions of the other browsers', async ({ page, browser }) => {
  const { user, username } = await newUser(page, browser, 'Zwei Browser');
  const other = await browser.newContext({ storageState: ANONYMOUS });
  const second = await other.newPage();
  await second.goto('/login');
  await second.getByLabel('Benutzername').fill(username);
  await second.getByLabel('Passwort').fill(PASSWORD);
  await second.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await expect(second.getByRole('heading', { level: 1 })).toHaveText('Guten Tag, Zwei Browser');
  await second.goto('/account');
  await expect(second.getByRole('heading', { level: 1 })).toHaveText('Mein Konto');

  await user.goto('/account');
  await user.getByLabel('Aktuelles Passwort').fill(PASSWORD);
  await user.getByLabel('Neues Passwort', { exact: true }).fill('ganz-neues-passwort');
  await user.getByLabel('Neues Passwort wiederholen').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Passwort ändern' }).click();
  await expect(user.getByRole('status')).toHaveText('Passwort geändert.');
  await user.goto('/account');
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Mein Konto');

  await second.goto('/account');
  await expect(second).toHaveURL(/\/login$/);
  await other.close();
  await user.context().close();
});
