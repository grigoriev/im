import type { Browser, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { ANONYMOUS, uniqueUsername } from './accounts';
import { addVirtualAuthenticator } from './authenticator';

const PASSWORD = 'passkey-passwort-123';

// A fresh user, so parallel tests never share passkeys. Returns a page logged in with the password.
async function newUser(page: Page, browser: Browser, name: string): Promise<{ user: Page; username: string }> {
  const username = uniqueUsername('pk');
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.locator('#new-password').fill(PASSWORD);
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

  await user.goto('/konto');
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

  await user.goto('/konto');
  await expect(user.locator('#passkey-list')).toContainText('zuletzt');
  await user.locator('#label-0').fill('iPhone');
  await user.getByRole('button', { name: 'Umbenennen' }).click();
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
  await user.goto('/konto');
  await user.getByLabel('Name des Geräts').fill('Telefon');
  await user.getByRole('button', { name: 'Passkey hinzufügen' }).click();
  await expect(user.getByRole('status')).toHaveText('Passkey hinzugefügt.');

  await page.goto('/admin/benutzer');
  const row = page.locator('#user-list > div', { hasText: username });
  await expect(row).toContainText('1 Passkey(s)');
  await row.getByRole('button', { name: 'Passkeys entfernen' }).click();
  await expect(page.getByRole('status')).toHaveText(`1 Passkey(s) von „${username}“ entfernt.`);
  await expect(page.locator('#user-list > div', { hasText: username })).toContainText('0 Passkey(s)');
  await user.context().close();
});

test('changes the own password', async ({ page, browser }) => {
  const { user, username } = await newUser(page, browser, 'Neues Passwort');
  await user.goto('/konto');
  await user.getByLabel('Aktuelles Passwort').fill('falsch-falsch-falsch');
  await user.getByLabel('Neues Passwort').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Passwort ändern' }).click();
  await expect(user.getByRole('alert')).toHaveText('Das aktuelle Passwort stimmt nicht.');
  await user.getByLabel('Aktuelles Passwort').fill(PASSWORD);
  await user.getByLabel('Neues Passwort').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Passwort ändern' }).click();
  await expect(user.getByRole('status')).toHaveText('Passwort geändert.');
  await user.getByRole('button', { name: 'Abmelden' }).click();
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('ganz-neues-passwort');
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await expect(user.getByRole('heading', { level: 1 })).toHaveText('Guten Tag, Neues Passwort');
  await user.context().close();
});
