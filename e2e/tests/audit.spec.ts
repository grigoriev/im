import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';

test('the audit log shows who created a customer', async ({ page }) => {
  const name = `Protokoll Kunde ${randomUUID().slice(0, 8)}`;
  await page.goto('/kunden/neu');
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Straße und Hausnummer').fill('Am Markt 1');
  await page.getByLabel('PLZ').fill('86150');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText('angelegt');

  await page.getByRole('navigation', { name: 'Hauptmenü' }).getByRole('link', { name: 'Protokoll', exact: true }).click();
  const entry = page.locator('#audit-list li').filter({ hasText: name });
  await expect(entry).toHaveCount(1);
  await expect(entry).toContainText('admin');
  await expect(entry).toContainText(`Kunde angelegt: ${name}`);
  await expect(page.locator('#audit-list li').filter({ hasText: 'Angemeldet: Passwort' }).first()).toBeVisible();
});
