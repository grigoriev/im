import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';

const receipt = (name: string) => ({ name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4 ${name}`) });

test('enters an expense with its receipt, changes and cancels one, and sums them for WISO', async ({ page }) => {
  const vendor = `Buchhandlung ${randomUUID().slice(0, 8)}`;
  const year = new Date().getFullYear();

  await page.goto('/expenses');
  await page.locator('#expense-new').click();
  await page.locator('#expense-upload').setInputFiles(receipt('Quittung.pdf'));
  await page.locator('#expense-amount').fill('49,9');
  await page.locator('#expense-category').selectOption({ label: 'Fachliteratur' });
  await page.locator('#expense-vendor').fill(vendor);
  await page.locator('#expense-description').fill('Wörterbuch');
  await page.locator('#expense-save').click();
  await expect(page.getByRole('status')).toHaveText('Ausgabe erfasst.');
  await expect(page.locator('#expense-amount')).toHaveValue('49,90');
  const book = page.url();
  const download = await page.request.get(`${book}/file`);
  expect(download.status()).toBe(200);
  expect((await download.body()).toString()).toBe('%PDF-1.4 Quittung.pdf');

  await page.locator('#expense-description').fill('Wörterbuch Deutsch-Russisch');
  await page.locator('#expense-save').click();
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');

  await page.goto('/expenses/new');
  await page.locator('#expense-upload').setInputFiles(receipt('Laptop.pdf'));
  await page.locator('#expense-amount').fill('1.299,00');
  await page.locator('#expense-category').selectOption({ label: 'Arbeitsmittel' });
  await page.locator('#expense-vendor').fill(vendor);
  await page.locator('#expense-description').fill('Laptop');
  await page.locator('#expense-asset').check();
  await page.locator('#expense-save').click();
  await expect(page.getByRole('status')).toHaveText('Ausgabe erfasst.');

  await page.goto('/expenses/new');
  await page.locator('#expense-upload').setInputFiles(receipt('Doppelt.pdf'));
  await page.locator('#expense-amount').fill('49,90');
  await page.locator('#expense-category').selectOption({ label: 'Fachliteratur' });
  await page.locator('#expense-vendor').fill(vendor);
  await page.locator('#expense-description').fill('doppelt');
  await page.locator('#expense-save').click();
  await page.getByText('Stornieren…').click();
  await page.locator('#expense-cancel-reason').fill('Doppelt erfasst');
  await page.locator('#expense-cancel-confirm').click();
  await expect(page.getByRole('status')).toHaveText('Ausgabe storniert. Sie zählt nicht mehr.');
  await expect(page.locator('#expense-cancelled')).toContainText('Doppelt erfasst');
  await expect(page.locator('#expense-save'), 'a cancelled expense never changes').toHaveCount(0);

  await page.goto('/expenses');
  const mine = page.locator('#expense-list li').filter({ hasText: vendor });
  await expect(mine).toHaveCount(3);
  await expect(mine.filter({ hasText: 'storniert' })).toHaveCount(1);
  await expect(mine.filter({ hasText: 'Anlagegut' })).toHaveCount(1);

  await page.goto(`/reports?year=${year}`);
  await expect(page.locator('#report-expenses')).toContainText('Fachliteratur');
  await expect(page.locator('#report-assets')).toContainText(`${vendor}: Laptop`);
  const csv = await page.request.get(`/reports/expenses.csv?year=${year}`);
  expect(csv.headers()['content-type']).toBe('text/csv;charset=UTF-8');
  const lines = (await csv.text()).split('\r\n').filter((line) => line.includes(vendor));
  expect(lines.map((line) => line.split(';')[1]).sort(), 'the cancelled expense stays out').toEqual(['1299,00', '49,90']);
  const zip = await (await page.request.get(`/reports/archive.zip?year=${year}`)).body();
  expect(zip.includes('Ausgaben/Inhalt.csv'), 'the archive holds the receipts').toBe(true);
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/expenses');
  expect(await page.evaluate(() => document.documentElement.scrollWidth), 'the list fits a phone').toBeLessThanOrEqual(375);
});

test('refuses an expense without receipt and amount', async ({ page }) => {
  await page.goto('/expenses/new');
  await page.locator('#expense-form').evaluate((form: HTMLFormElement) => form.setAttribute('novalidate', ''));
  await page.locator('#expense-save').click();
  await expect(page.locator('#expense-problems')).toContainText('Bitte eine Datei wählen.');
  await expect(page.locator('#expense-problems')).toContainText('Bitte den Betrag angeben.');
});
