import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';
import { ensureProfile, newCustomer } from './seller';

test('creates a draft invoice, edits its lines and deletes it', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Rechnungskunde ${randomUUID().slice(0, 8)}`);

  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(customer);
  await expect(page.locator('#invoice-checks')).toContainText('Position 1: Bitte die Leistung beschreiben.');

  await page.locator('#line-0-text').fill('Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Position hinzufügen' }).click();
  await expect(page.locator('#line-1-text')).toBeVisible();
  await expect(page.locator('#line-0-amount')).toHaveValue('400,00');
  await page.locator('#line-1-text').fill('Signierstunde');
  await expect(page.locator('#line-1-quantity'), 'a lump sum hides the quantity').toBeHidden();
  await page.locator('#line-1-kind').selectOption('false');
  await expect(page.locator('#line-1-quantity')).toBeVisible();
  await page.locator('#line-1-quantity').fill('2');
  await page.locator('#line-1-price').fill('12,50');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');
  await expect(page.locator('#invoice-totals')).toContainText('425,00 €');
  await expect(page.locator('#invoice-checks')).toContainText('Vollständig.');
  const preview = await page.request.get((await page.locator('#invoice-preview').getAttribute('href'))!);
  expect(preview.headers()['content-type']).toBe('application/pdf');
  expect((await preview.body()).subarray(0, 5).toString()).toBe('%PDF-');

  await page.locator('#line-1-price').fill('zwölf');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('alert')).toContainText('Position 2: Der Preis ist keine Zahl');
  await expect(page.locator('#line-1-price')).toHaveValue('zwölf');

  // Back to a valid price, then the switch to a lump sum shows the total, and a typed amount wins.
  await page.locator('#line-1-price').fill('12,50');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.locator('#line-1-kind').selectOption('true');
  await expect(page.locator('#line-1-amount')).toHaveValue('25,00');
  await page.locator('#line-1-amount').fill('30');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('#invoice-totals')).toContainText('430,00 €');

  // A quantity changed right before the switch counts, when no new amount was typed.
  await page.locator('#line-1-kind').selectOption('false');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.locator('#line-1-quantity').fill('4');
  await page.locator('#line-1-price').fill('12,50');
  await page.locator('#line-1-kind').selectOption('true');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('#invoice-totals')).toContainText('450,00 €');

  await page.getByRole('button', { name: 'Position 2 entfernen' }).click();
  await expect(page.locator('#line-1-text')).toHaveCount(0);
  await expect(page.locator('#invoice-totals')).toContainText('400,00 €');

  await page.goto('/rechnungen');
  const row = page.locator('#invoice-list li').filter({ hasText: customer });
  await expect(row).toContainText('Entwurf');
  await expect(row).toContainText('400,00 €');
  await row.getByRole('link').click();
  await page.getByText('Entwurf löschen', { exact: true }).first().click();
  await page.getByRole('button', { name: 'Entwurf löschen' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gelöscht.');
  await expect(page.locator('#invoice-list li').filter({ hasText: customer })).toHaveCount(0);
});

test('issues an invoice with its number and documents', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Ausstellkunde ${randomUUID().slice(0, 8)}`);

  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await expect(page.locator('#invoice-issue'), 'an incomplete draft cannot be issued').toHaveCount(0);
  await page.locator('#line-0-text').fill('Lesung am {Leistungszeitraum}');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');

  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText(/^Rechnung \d{4}-\d{2,} ausgestellt\.$/);
  const number = (await page.locator('#invoice-number').textContent())!.trim().replace(/^Rechnung /, '');
  await expect(page.getByRole('status')).toHaveText(`Rechnung ${number} ausgestellt.`);
  await expect(page.locator('#invoice-status')).toHaveText('Ausgestellt');
  await expect(page.locator('#invoice-summary')).toContainText(`${customer} · 400,00 € · fällig am`);
  await expect(page.locator('#invoice-checks')).toContainText('Unveränderbar seit');
  await expect(page.locator('#invoice-history li')).toHaveText([/Entwurf angelegt/, /Rechnung ausgestellt/]);
  await expect(page.locator('#invoice-total')).toHaveText('400,00 €');
  await expect(page.getByText('Entwurf löschen')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Speichern' })).toHaveCount(0);

  const pdf = await page.request.get((await page.locator('#download-pdf').getAttribute('href'))!);
  expect(pdf.headers()['content-type']).toBe('application/pdf');
  expect(pdf.headers()['content-disposition']).toBe(`attachment; filename="Rechnung_${number}.pdf"`);
  expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-');
  const xml = await page.request.get((await page.locator('#download-xml').getAttribute('href'))!);
  expect(xml.headers()['content-type']).toContain('application/xml');
  expect((await xml.body()).toString()).toContain(number);
  await expect(page.locator('#invoice-documents')).toContainText(`Rechnung_${number}.pdf`);
  await expect(page.locator('#invoice-documents')).toContainText(/SHA-256 [0-9a-f]{64}/);

  await page.goto('/rechnungen');
  const row = page.locator('#invoice-list li').filter({ hasText: customer });
  await expect(row).toContainText(number);
  await expect(row).toContainText('Ausgestellt');
  await expect(row).toContainText('400,00 €');

  await page.getByLabel('Kunde oder Nummer').fill(number);
  await page.getByRole('button', { name: 'Filtern' }).click();
  await expect(page.locator('#invoice-list li')).toHaveCount(1);
  await expect(page.locator('#invoice-list li')).toContainText(customer);
  await page.getByLabel('Status').selectOption({ label: 'Entwurf' });
  await page.getByRole('button', { name: 'Filtern' }).click();
  await expect(page.locator('#invoice-none')).toHaveText('Keine Rechnungen gefunden.');
});
