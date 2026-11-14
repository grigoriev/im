import { expect, type Page } from '@playwright/test';

// The seller profile that invoices need. settings.spec.ts saves the same values, so the two specs never
// disagree when they run in parallel.
export async function ensureProfile(page: Page): Promise<void> {
  await page.goto('/einstellungen');
  if ((await page.getByLabel('Name', { exact: true }).inputValue()) !== '') return;
  await fillProfile(page);
}

// Fills and saves every tab of the profile.
export async function fillProfile(page: Page): Promise<void> {
  await page.goto('/einstellungen?teil=anschrift');
  await page.getByLabel('Name', { exact: true }).fill('Erika Mustermann');
  await page.getByLabel('Zusatz, z. B. der Beruf').fill('Freiberufliche Dozentin');
  await page.getByLabel('Straße und Hausnummer').fill('Musterweg 1');
  await page.getByLabel('PLZ').fill('12345');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toContainText('Anschrift gespeichert.');
  await page.getByRole('tab', { name: /^Kontakt( unvollständig)?$/ }).click();
  await page.getByLabel('E-Mail').fill('erika@example.org');
  await page.getByLabel('Telefon').fill('+49 30 1234567');
  await page.getByLabel('Website, z. B. www.example.de').fill('www.erika.example');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toContainText('Kontakt gespeichert.');
  await page.getByRole('tab', { name: /^Steuer( unvollständig)?$/ }).click();
  await page.getByLabel('Steuernummer').fill('12/345/67890');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toContainText('Steuer gespeichert.');
  await page.getByRole('tab', { name: /^Bankverbindung( unvollständig)?$/ }).click();
  await page.getByLabel('IBAN').fill('de89 3704 0044 0532 0130 00');
  await page.getByLabel('BIC').fill('cobadeffxxx');
  await page.getByLabel('Kreditinstitut').fill('Commerzbank');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Bankverbindung gespeichert.');
}

// A customer with a unique name, ZUGFeRD EN16931. Returns the name.
export async function newCustomer(page: Page, name: string): Promise<string> {
  await page.goto('/kunden/neu');
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Straße und Hausnummer').fill('Hafenstraße 3');
  await page.getByLabel('PLZ').fill('20457');
  await page.getByLabel('Ort', { exact: true }).fill('Hamburg');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText('angelegt');
  return name;
}
