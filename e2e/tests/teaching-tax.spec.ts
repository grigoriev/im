import { expect, test } from './fixtures';
import { ensureProfile, newCustomer } from './seller';
import { randomUUID } from 'node:crypto';

// Teaching under § 4 Nr. 21 with a tax number of its own: a teaching invoice carries it, a mixed draft cannot be
// issued, and "Berichte" shows one Anlage EÜR per tax number. 2021 belongs to this test alone. The profile is shared,
// so the test removes the second number at the end.
test('teaching with its own tax number gets its own invoices and its own Anlage EÜR', async ({ page }) => {
  await ensureProfile(page);
  await page.goto('/settings?part=tax');
  await page.getByLabel('Steuernummer für Unterricht (§ 4 Nr. 21)').fill('98/765/43210');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toContainText('Steuer gespeichert.');
  try {
    const customer = await newCustomer(page, `Kursteilnehmer ${randomUUID().slice(0, 8)}`);
    await page.goto('/invoices');
    await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
    await page.getByRole('button', { name: 'Neue Rechnung' }).click();
    await page.locator('#invoice-date').fill('2021-02-01');
    await page.locator('#line-0-text').fill('Deutschkurs B1, 10 Termine');
    await page.locator('#line-0-amount').fill('300');
    await page.locator('#line-0-category').selectOption('EXEMPT_4_21');
    await page.getByRole('button', { name: 'Position hinzufügen' }).click();
    await page.locator('#line-1-text').fill('Lesung');
    await page.locator('#line-1-kind').selectOption('true');
    await page.locator('#line-1-amount').fill('100');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByText('Unterricht nach § 4 Nr. 21 steht allein auf einer Rechnung.', { exact: false })).toBeVisible();

    await page.getByRole('button', { name: 'Position 2 entfernen' }).click();
    await expect(page.getByRole('button', { name: 'Rechnung ausstellen…' })).toBeVisible();
    await page.getByRole('button', { name: 'Rechnung ausstellen…' }).click();
    await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
    await expect(page.getByRole('status')).toHaveText(/^Rechnung 2021-\d{2,} ausgestellt\.$/);
    const xml = await page.request.get((await page.locator('#download-xml').getAttribute('href'))!);
    expect(await xml.text()).toContain('98/765/43210');

    await page.getByText('Zahlung erfassen…').click();
    await page.locator('#payment-date').fill('2021-02-15');
    await page.getByRole('button', { name: 'Erfassen' }).click();
    await expect(page.getByRole('status')).toHaveText('Zahlung erfasst. Die Rechnung ist bezahlt.');

    // Teaching abroad: not taxable in Germany, yet teaching. The customer suggests the business.
    const abroad = `Goethe-Zentrum ${randomUUID().slice(0, 8)}`;
    await page.goto('/customers/new');
    await page.getByLabel('Name', { exact: true }).fill(abroad);
    await page.getByLabel('Straße und Hausnummer').fill('Amir-Timur-Straße 1');
    await page.getByLabel('PLZ').fill('100000');
    await page.getByLabel('Ort', { exact: true }).fill('Taschkent');
    await page.getByLabel('Tätigkeit').selectOption('TEACHING');
    await page.getByRole('button', { name: 'Anlegen' }).click();
    await expect(page.getByRole('status')).toContainText('angelegt');
    await page.goto('/invoices');
    await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: abroad });
    await page.getByRole('button', { name: 'Neue Rechnung' }).click();
    await expect(page.getByLabel('Tätigkeit')).toHaveValue('TEACHING');
    await page.locator('#invoice-date').fill('2021-03-01');
    await page.locator('#line-0-text').fill('Lehrerfortbildung in Taschkent');
    await page.locator('#line-0-amount').fill('200');
    await page.locator('#line-0-category').selectOption('NOT_TAXABLE_ABROAD');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await page.getByRole('button', { name: 'Rechnung ausstellen…' }).click();
    await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
    const abroadXml = await page.request.get((await page.locator('#download-xml').getAttribute('href'))!);
    expect(await abroadXml.text()).toContain('98/765/43210');
    await page.getByText('Zahlung erfassen…').click();
    await page.locator('#payment-date').fill('2021-03-15');
    await page.getByRole('button', { name: 'Erfassen' }).click();
    await expect(page.getByRole('status')).toHaveText('Zahlung erfasst. Die Rechnung ist bezahlt.');

    await page.goto('/reports?year=2021');
    await expect(page.locator('#report-income-teaching')).toContainText('98/765/43210');
    await expect(page.locator('#income-111-teaching')).toHaveText('500,00 €');
    await expect(page.locator('#income-119-teaching')).toHaveText('500,00 €');
    await expect(page.locator('#income-111-other')).toHaveText('0,00 €');
    await expect(page.locator('#report-income-teaching')).toHaveScreenshot('report-income-teaching.png');
  } finally {
    await page.goto('/settings?part=tax');
    await page.getByLabel('Steuernummer für Unterricht (§ 4 Nr. 21)').fill('');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('status')).toContainText('Steuer gespeichert.');
  }
});
