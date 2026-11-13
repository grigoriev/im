import { expect, test } from '@playwright/test';
import { ANONYMOUS, uniqueUsername } from './accounts';

// The seller profile is one record for the whole app: only this test writes it.
test('saves the seller profile and shows the problems of a wrong entry', async ({ page }) => {
  await page.goto('/einstellungen');
  await expect(page.getByRole('tab', { name: 'Profil' })).toHaveAttribute('aria-selected', 'true');
  await page.getByLabel('Name', { exact: true }).fill('Erika Mustermann');
  await page.getByLabel('Straße und Hausnummer').fill('Musterweg 1');
  await page.getByLabel('PLZ').fill('12345');
  await page.getByLabel('Ort').fill('Musterstadt');
  await page.getByLabel('E-Mail').fill('erika@example.org');
  await page.getByLabel('Telefon').fill('+49 30 1234567');
  await page.getByLabel('Steuernummer').fill('12/345/67890');
  await page.getByLabel('IBAN').fill('DE89 3704 0044 0532 0130 01');
  await page.getByLabel('BIC').fill('cobadeffxxx');
  await page.getByRole('button', { name: 'Speichern' }).click();

  await expect(page.getByRole('alert')).toHaveText('Die IBAN ist ungültig.');
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Erika Mustermann');

  await page.getByLabel('IBAN').fill('de89 3704 0044 0532 0130 00');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Profil gespeichert.');
  await expect(page.getByLabel('IBAN')).toHaveValue('DE89 3704 0044 0532 0130 00');
  await expect(page.getByLabel('BIC')).toHaveValue('COBADEFFXXX');
  await expect(page.getByLabel('Steuerstatus')).toHaveValue('KLEINUNTERNEHMER');
});

test('the admin changes a default tax note and restores it', async ({ page }) => {
  await page.goto('/einstellungen');
  await page.getByRole('tab', { name: /Steuerhinweise/ }).click();
  const note = page.getByLabel('Steuerfreie Bildungsleistung (§ 4 Nr. 21 UStG)');
  await expect(note).toHaveValue('Steuerfrei gemäß § 4 Nr. 21 UStG.');
  await note.fill('Steuerfrei nach § 4 Nr. 21 a UStG.');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Steuerhinweise gespeichert.');
  await expect(note).toHaveValue('Steuerfrei nach § 4 Nr. 21 a UStG.');

  await note.fill('');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(note).toHaveValue('Steuerfrei gemäß § 4 Nr. 21 UStG.');
});

test('a user edits the profile but not the tax notes', async ({ page, browser }) => {
  const username = uniqueUsername('st');
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill('Einstellungen Nutzerin');
  await page.locator('#new-password').fill('settings-passwort-123');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText(username);

  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('settings-passwort-123');
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await user.getByRole('link', { name: 'Einstellungen' }).click();
  await expect(user.getByRole('tab', { name: 'Profil' })).toBeVisible();
  await expect(user.getByRole('tab', { name: /Steuerhinweise/ })).toHaveCount(0);
  const response = await user.goto('/admin/steuerhinweise');
  expect(response?.status()).toBe(403);
  await context.close();
});
