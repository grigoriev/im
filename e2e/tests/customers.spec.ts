import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';

test('creates a public customer with XRechnung, changes and deletes it', async ({ page }) => {
  const name = `Volkshochschule ${randomUUID().slice(0, 8)}`;
  await page.goto('/kunden');
  await page.getByRole('link', { name: 'Neuer Kunde' }).click();
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Straße und Hausnummer').fill('Am Markt 1');
  await page.getByLabel('PLZ').fill('86150');
  await page.getByLabel('Ort').fill('Musterstadt');
  await page.getByLabel('E-Rechnungsformat').selectOption('XRECHNUNG');
  await page.getByRole('button', { name: 'Anlegen' }).click();

  await expect(page.getByRole('alert')).toContainText('Die XRechnung braucht eine Käuferreferenz');
  await expect(page.getByRole('alert')).toContainText('Die XRechnung braucht die E-Mail-Adresse des Kunden.');
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue(name);

  await page.getByLabel('Käuferreferenz (Leitweg-ID)').fill('09162000-VHS01-42');
  await page.getByLabel('E-Mail für Rechnungen').fill('erechnung@vhs.example');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Kunde „${name}“ angelegt.`);
  await expect(page.getByRole('navigation', { name: 'Kundenliste' }).getByRole('link', { name })).toHaveAttribute('aria-current', 'page');

  await page.getByLabel('Straße und Hausnummer').fill('Rathausplatz 2');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  await expect(page.getByLabel('Straße und Hausnummer')).toHaveValue('Rathausplatz 2');

  await page.getByText('Kunden löschen').click();
  await page.getByRole('button', { name: 'Endgültig löschen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Kunde „${name}“ gelöscht.`);
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});
