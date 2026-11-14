import { expect, test } from './fixtures';
import { ensureProfile, newCustomer } from './seller';
import { randomUUID } from 'node:crypto';

// Teaching under § 4 Nr. 21 with a tax number of its own: a teaching invoice carries it, a mixed draft cannot be
// issued, and "Berichte" shows one Anlage EÜR per tax number. 2021 belongs to this test alone. The profile is shared,
// so the test removes the second number at the end.
test('teaching with its own tax number gets its own invoices and its own Anlage EÜR', async ({ page }) => {
  await ensureProfile(page);
  await page.goto('/einstellungen?teil=steuer');
  await page.getByLabel('Steuernummer für Unterricht (§ 4 Nr. 21)').fill('98/765/43210');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toContainText('Steuer gespeichert.');
  try {
    const customer = await newCustomer(page, `Kursteilnehmer ${randomUUID().slice(0, 8)}`);
    await page.goto('/rechnungen');
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
    await expect(page.getByText('Unterricht nach § 4 Nr. 21 hat eine eigene Steuernummer.', { exact: false })).toBeVisible();

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

    await page.goto('/berichte?jahr=2021');
    await expect(page.locator('#report-euer-unterricht')).toContainText('98/765/43210');
    await expect(page.locator('#euer-111-unterricht')).toHaveText('300,00 €');
    await expect(page.locator('#euer-119-unterricht')).toHaveText('300,00 €');
    await expect(page.locator('#euer-111-uebrige')).toHaveText('0,00 €');
    await expect(page.locator('#report-euer-unterricht')).toHaveScreenshot('report-euer-unterricht.png');
  } finally {
    await page.goto('/einstellungen?teil=steuer');
    await page.getByLabel('Steuernummer für Unterricht (§ 4 Nr. 21)').fill('');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('status')).toContainText('Steuer gespeichert.');
  }
});
