import { expect, test } from './fixtures';
import { randomUUID } from 'node:crypto';
import { ensureProfile } from './seller';

test('creates a public customer with XRechnung, changes and deletes it', async ({ page }) => {
  const name = `Volkshochschule ${randomUUID().slice(0, 8)}`;
  await page.goto('/customers');
  await page.getByRole('link', { name: 'Neuer Kunde' }).click();
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Straße und Hausnummer').fill('Am Markt 1');
  await page.getByLabel('PLZ').fill('86150');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
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

  await page.getByRole('button', { name: 'Kunden löschen' }).click();
  await page.getByRole('button', { name: 'Endgültig löschen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Kunde „${name}“ gelöscht.`);
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});

test('a second contact person of one organization copies its address and stays apart in the lists', async ({ page }) => {
  const name = `Sprachschule am See ${randomUUID().slice(0, 8)}`;
  await page.goto('/customers/new');
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Straße und Hausnummer').fill('Seeweg 5');
  await page.getByLabel('PLZ').fill('86150');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
  await page.getByLabel('Ansprechpartner').fill('Frau Weber');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Kunde „${name}“ angelegt.`);

  await page.getByRole('link', { name: 'Weiterer Ansprechpartner' }).click();
  await expect(page.getByLabel('Straße und Hausnummer')).toHaveValue('Seeweg 5');
  await expect(page.getByLabel('Ansprechpartner')).toHaveValue('');
  await page.getByLabel('Ansprechpartner').fill('Herr Klein');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('heading', { level: 2 })).toHaveText(`${name} · Herr Klein`);

  await page.goto('/invoices');
  const choice = page.getByLabel('Kunde der neuen Rechnung');
  await expect(choice.locator('option', { hasText: `${name} · Frau Weber` })).toHaveCount(1);
  await expect(choice.locator('option', { hasText: `${name} · Herr Klein` })).toHaveCount(1);
});

test('a department and the contact person go into the address of the invoice', async ({ page }) => {
  await ensureProfile(page);
  const name = `Sprachschule am See ${randomUUID().slice(0, 8)}`;
  await page.goto('/customers/new');
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Abteilung / Zusatz').fill('KST 80441000, Bereich 44 DaF-Programme');
  await page.getByLabel('Ansprechpartner').fill('Frau Weber');
  await page.getByLabel('Straße und Hausnummer').fill('Seeweg 5');
  await page.getByLabel('PLZ').fill('86150');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Kunde „${name}“ angelegt.`);
  await expect(page.getByLabel('Abteilung / Zusatz')).toHaveValue('KST 80441000, Bereich 44 DaF-Programme');

  await page.getByRole('link', { name: 'Weiterer Ansprechpartner' }).click();
  await expect(page.getByLabel('Abteilung / Zusatz'), 'the copy keeps the department')
    .toHaveValue('KST 80441000, Bereich 44 DaF-Programme');
  await expect(page.getByLabel('Ansprechpartner')).toHaveValue('');

  await page.goto('/invoices');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: `${name} · Frau Weber` });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Online-Sprachkurs Deutsch B1');
  await page.locator('#line-0-amount').fill('984');
  await page.locator('#line-0-category').selectOption('EXEMPT_4_21');
  await page.getByRole('button', { name: 'Speichern' }).click();
  const preview = await page.request.get((await page.locator('#invoice-preview').getAttribute('href'))!);
  expect((await preview.body()).subarray(0, 5).toString()).toBe('%PDF-');
  await page.getByRole('button', { name: 'Rechnung ausstellen…' }).click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText(/^Rechnung .+ ausgestellt\.$/);
  const xml = await (await page.request.get((await page.locator('#download-xml').getAttribute('href'))!)).text();
  expect(xml, 'BT-51, the address line 2 of the buyer').toContain('<ram:LineTwo>KST 80441000, Bereich 44 DaF-Programme</ram:LineTwo>');
  expect(xml, 'BT-56, the contact point of the buyer').toMatch(/<ram:DefinedTradeContact>\s*<ram:PersonName>Frau Weber<\/ram:PersonName>/);
});
