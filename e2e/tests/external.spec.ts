import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';
import type { Page } from '@playwright/test';
import { ensureProfile } from './seller';

// External invoices (docs/PLAN.md, section 7): made outside the app, registered with their file and the next number.
// A minimal Excel workbook: a ZIP that names xl/workbook.xml.
const XLSX = Buffer.from(
  'UEsDBBQAAAAIAGdpdl3HHBc8CgAAAAgAAAATAAAAW0NvbnRlbnRfVHlwZXNdLnhtbLMJqSxILda3AwBQSwMEFAAAAAgAZ2l2Xc6emBMNAAAACwAAAA8A'
    + 'AAB4bC93b3JrYm9vay54bWyzKc8vyk7Kz8/WtwMAUEsBAhQDFAAAAAgAZ2l2XcccFzwKAAAACAAAABMAAAAAAAAAAAAAAIABAAAAAFtDb250ZW50X1R5'
    + 'cGVzXS54bWxQSwECFAMUAAAACABnaXZdzp6YEw0AAAALAAAADwAAAAAAAAAAAAAAgAE7AAAAeGwvd29ya2Jvb2sueG1sUEsFBgAAAAACAAIAfgAAAHUA'
    + 'AAAAAA==',
  'base64',
);
const VIEWPORTS = [
  { name: 'phone', width: 390, height: 2000 },
  { name: 'desktop', width: 1440, height: 2000 },
];

/** A customer who demands invoices in a form of their own. */
async function externalCustomer(page: Page, name: string): Promise<void> {
  await page.goto('/customers/new');
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByLabel('Straße und Hausnummer').fill('Kaiserstraße 7');
  await page.getByLabel('PLZ').fill('60311');
  await page.getByLabel('Ort', { exact: true }).fill('Frankfurt am Main');
  await page.getByLabel('E-Rechnungsformat').selectOption('EXTERNAL');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText('angelegt');
}

/** A saved external draft of the customer: a §19 line and a teaching line, dated in the given year. */
async function externalDraft(page: Page, customer: string, date: string): Promise<string> {
  await page.goto('/invoices');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await expect(page.locator('#external-hint')).toBeVisible();
  await expect(page.locator('#invoice-intro'), 'the file has the texts').toHaveCount(0);
  await page.locator('#invoice-date').fill(date);
  await page.locator('#invoice-from').fill(date);
  await page.locator('#invoice-to').fill(date);
  await page.locator('#line-0-text').fill('Lesung');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');
  await expect(page.locator('#invoice-checks')).toContainText('Vollständig.');
  await expect(page.locator('#invoice-preview'), 'the file is the invoice').toHaveCount(0);
  return page.url();
}

test('registers an external invoice with the next number and its file', async ({ page }) => {
  await ensureProfile(page);
  const customer = `Portalkunde ${randomUUID().slice(0, 8)}`;
  await externalCustomer(page, customer);
  await externalDraft(page, customer, '2003-06-12');

  await page.getByRole('button', { name: 'Rechnung erfassen…' }).click();
  await expect(page.locator('#register-next')).toHaveText('2003-01');
  await page.locator('#register-number').fill('2003-07');
  await page.locator('#register-file').setInputFiles({ name: 'Juni.xlsx', mimeType: 'application/octet-stream', buffer: XLSX });
  await page.locator('#invoice-register-confirm').click();
  await expect(page.getByRole('alert')).toContainText('Die nächste Rechnungsnummer ist 2003-01, angegeben ist 2003-07.');

  await page.getByRole('button', { name: 'Rechnung erfassen…' }).click();
  await page.locator('#register-number').fill('2003-01');
  await page.locator('#register-file').setInputFiles({ name: 'Juni.xlsx', mimeType: 'application/octet-stream', buffer: XLSX });
  await page.locator('#invoice-register-confirm').click();
  await expect(page.getByRole('status')).toHaveText('Externe Rechnung 2003-01 erfasst.');
  await expect(page.locator('#invoice-number')).toHaveText('Rechnung 2003-01');
  await expect(page.locator('#invoice-originals')).toContainText('Juni.xlsx');
  await expect(page.locator('#invoice-mail')).toContainText('Originaldateien anhängen');
  await expect(page.locator('#mail-xml'), 'an external invoice has no XML of the app').toHaveCount(0);
  await expect(page.locator('#invoice-history')).toContainText('Externe Rechnung erfasst');

  const download = await page.request.get((await page.locator('#download-original-1').getAttribute('href'))!);
  expect(download.ok()).toBe(true);
  expect(Buffer.from(await download.body()).equals(XLSX)).toBe(true);

  await page.locator('#original-file').setInputFiles({
    name: 'Juni.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 Kopie'),
  });
  await page.locator('#original-add').click();
  await expect(page.getByRole('status')).toHaveText('Datei „Juni.pdf“ hinzugefügt.');
  await expect(page.locator('#invoice-originals li')).toHaveCount(2);

  const address = `portal-${randomUUID().slice(0, 8)}@kunde.example`;
  await page.getByText('Per E-Mail senden…').click();
  await page.locator('#mail-to').fill(address);
  await page.getByRole('button', { name: 'Senden', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText(`E-Mail an ${address} gesendet.`);
  await expect(page.locator('#invoice-history li').last()).toContainText(`${address}, Notiz: Juni.xlsx, Juni.pdf`);

  await page.locator('#invoice-payment summary').click();
  await page.locator('#payment-date').fill('2003-06-30');
  await page.locator('#payment-amount').fill('400,00');
  await page.locator('#payment-save').click();
  await expect(page.locator('#invoice-status')).toHaveText('Bezahlt');
});

test('external invoice screenshots', async ({ page, browser, adminState }) => {
  test.slow();
  await ensureProfile(page);
  const customer = 'Messe Frankfurt Kunde mit eigenem Rechnungsformular';
  await externalCustomer(page, customer);
  // The only invoices of their year, so the numbers stay the same: the second one is 2036-01, the draft shows 2036-02.
  const draft = await externalDraft(page, customer, '2036-04-02');
  const second = await externalDraft(page, customer, '2036-04-03');
  await page.getByRole('button', { name: 'Rechnung erfassen…' }).click();
  await page.locator('#register-number').fill('2036-01');
  await page.locator('#register-file').setInputFiles({ name: 'April.xlsx', mimeType: 'application/octet-stream', buffer: XLSX });
  await page.locator('#invoice-register-confirm').click();
  await expect(page.getByRole('status')).toHaveText('Externe Rechnung 2036-01 erfasst.');
  const registered = page.url();
  expect(registered).toBe(second);

  for (const viewport of VIEWPORTS) {
    for (const scheme of ['light', 'dark'] as const) {
      const context = await browser.newContext({
        colorScheme: scheme,
        viewport: { width: viewport.width, height: viewport.height },
        storageState: adminState,
      });
      const view = await context.newPage();
      await view.goto(draft);
      await view.evaluate(() => document.fonts.ready);
      await expect(view.locator('#external-hint')).toHaveScreenshot(`external-hint-${viewport.name}-${scheme}.png`);
      await view.getByRole('button', { name: 'Rechnung erfassen…' }).click();
      await expect(view.locator('#invoice-register .modal-box'))
        .toHaveScreenshot(`external-register-${viewport.name}-${scheme}.png`);
      await view.goto(registered);
      const masks = { mask: [view.locator('[data-volatile]')] };
      await expect(view.locator('#invoice-originals')).toHaveScreenshot(`external-originals-${viewport.name}-${scheme}.png`, masks);
      await expect(view.locator('#invoice-checks')).toHaveScreenshot(`external-checks-${viewport.name}-${scheme}.png`, masks);
      await context.close();
    }
  }
});

test('takes the numbers of a year before the app by hand, explains its gaps and closes it', async ({ page, browser, adminState }) => {
  test.slow();
  await ensureProfile(page);
  await page.goto('/admin/numbers');
  await page.locator('#manual-year').fill('2004');
  await page.locator('#manual-on').click();
  await expect(page.getByRole('status')).toHaveText('Die Nummern für 2004 kommen von Hand.');
  await expect(page.locator('#manual-years')).toContainText('Nummernkreis 2004');

  const customer = 'Altkunde aus dem Jahr vor der App';
  await externalCustomer(page, customer);
  for (const [date, number] of [['2004-05-02', '2004-04'], ['2004-01-20', '2004-02']]) {
    await externalDraft(page, customer, date);
    await page.getByRole('button', { name: 'Rechnung erfassen…' }).click();
    await expect(page.locator('#register-by-hand')).toContainText('von Hand erfasst');
    await page.locator('#register-number').fill(number);
    await page.locator('#register-file').setInputFiles({ name: `${number}.xlsx`, mimeType: 'application/octet-stream', buffer: XLSX });
    await page.locator('#invoice-register-confirm').click();
    await expect(page.getByRole('status')).toHaveText(`Externe Rechnung ${number} erfasst.`);
  }
  const byHand = await externalDraft(page, customer, '2004-06-01');

  await page.goto('/numbers/2004');
  await expect(page.locator('#book-summary')).toHaveText('2 Rechnungen · 2 Lücken, davon 2 ohne Grund');
  await page.locator('#gap-reason-1').fill('Nummer verschrieben, Rechnung nie versandt');
  await page.locator('#gap-save-1').click();
  await expect(page.getByRole('status')).toHaveText('Lücke 2004-01 begründet.');
  await expect(page.locator('#book-close')).toHaveCount(0);

  for (const viewport of VIEWPORTS) {
    for (const scheme of ['light', 'dark'] as const) {
      const context = await browser.newContext({
        colorScheme: scheme,
        viewport: { width: viewport.width, height: viewport.height },
        storageState: adminState,
      });
      const view = await context.newPage();
      await view.goto('/numbers/2004');
      await view.evaluate(() => document.fonts.ready);
      await expect(view.locator('#book-entries')).toHaveScreenshot(`book-entries-${viewport.name}-${scheme}.png`);
      await view.goto(byHand);
      await view.getByRole('button', { name: 'Rechnung erfassen…' }).click();
      await expect(view.locator('#invoice-register .modal-box'))
        .toHaveScreenshot(`external-register-by-hand-${viewport.name}-${scheme}.png`);
      await context.close();
    }
  }

  await page.locator('#gap-reason-3').fill('Storniert vor dem Versand');
  await page.locator('#gap-save-3').click();
  await page.getByRole('button', { name: 'Jahr abschließen…' }).click();
  await page.locator('#book-close-confirm').click();
  await expect(page.getByRole('status')).toHaveText('Das Jahr 2004 ist abgeschlossen. Der Zähler macht mit 5 weiter.');
  await expect(page.locator('#book-state')).toHaveText('Abgeschlossen');

  await page.goto(byHand);
  await page.getByRole('button', { name: 'Rechnung erfassen…' }).click();
  await expect(page.locator('#register-next'), 'the counter continues after the year').toHaveText('2004-05');
});
