import { expect, test } from './fixtures';
import { ensureProfile } from './seller';

// Screenshots of pages with saved data. Other tests add data in parallel, so these capture single
// elements, never a whole list. The viewports are tall: the element fits without scrolling, so the
// sticky header never covers it. Update with scripts/e2e.sh --update.
const VIEWPORTS = [
  { name: 'phone', width: 390, height: 2000 },
  { name: 'desktop', width: 1440, height: 2000 },
];
const LONG_NAME = 'Gemeinnützige Gesellschaft für Sprachförderung und interkulturelle Bildung Musterstadt mbH';

test('a saved customer with a long name', async ({ page, browser, adminState }) => {
  await page.goto('/kunden/neu');
  await page.getByLabel('Name', { exact: true }).fill(LONG_NAME);
  await page.getByLabel('Straße und Hausnummer').fill('Lange Straße 123');
  await page.getByLabel('PLZ').fill('86150');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
  await page.getByLabel('E-Mail für Rechnungen').fill('erechnung@gesellschaft.example');
  await page.getByLabel('E-Rechnungsformat').selectOption('ZUGFERD_XRECHNUNG');
  await page.getByLabel('Käuferreferenz (Leitweg-ID)').fill('09162000-GGSB1-42');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText('angelegt');
  const url = page.url();

  // A template of this customer, for the screenshot of the list of templates.
  await page.getByRole('button', { name: 'Neue Vorlage' }).click();
  await page.getByLabel('Name der Vorlage').fill('Lesung mit Gespräch und Signierstunde');
  await page.locator('#line-0-text').fill('Lesung aus „Der stille Hafen“, 90 Min.');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Vorlage gespeichert.');

  // A draft for this customer with a lump sum and a line with quantity, for the editor screenshots.
  await ensureProfile(page);
  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: LONG_NAME });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Lesung aus „Der stille Hafen“ mit anschließendem Gespräch, 16.03.2027, 90 Min.');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Position hinzufügen' }).click();
  await page.locator('#line-1-text').fill('Signierstunde');
  await page.locator('#line-1-kind').selectOption('false');
  await page.locator('#line-1-quantity').fill('2');
  await page.locator('#line-1-price').fill('12,50');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');
  const draft = page.url();

  // An issued invoice of this customer, alone in its year, so its number and dates stay the same. The times of the
  // history and the issue, the size and the hash differ per run and are masked.
  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: LONG_NAME });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Sprachkurs Deutsch B1, 10 Termine');
  await page.locator('#line-0-amount').fill('850');
  await page.locator('#invoice-date').fill('2031-03-18');
  await page.locator('#invoice-from').fill('2031-03-02');
  await page.locator('#invoice-to').fill('2031-03-16');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');
  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText('Rechnung 2031-01 ausgestellt.');
  const issued = page.url();

  // A second invoice of this customer in 2032, cancelled by a Stornorechnung of the same year: numbers 2032-01 and
  // 2032-02, all dates fixed.
  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: LONG_NAME });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Workshop Kreatives Schreiben, 2 Tage');
  await page.locator('#line-0-amount').fill('640');
  await page.locator('#invoice-date').fill('2032-02-10');
  await page.locator('#invoice-from').fill('2032-02-03');
  await page.locator('#invoice-to').fill('2032-02-04');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText('Rechnung 2032-01 ausgestellt.');
  const cancelledInvoice = page.url();
  await page.getByText('Stornieren…').click();
  await page.getByRole('button', { name: 'Stornorechnung anlegen' }).click();
  await page.locator('#invoice-date').fill('2032-02-20');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');
  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText('Rechnung 2032-02 ausgestellt.');
  const storno = page.url();
  // A third invoice, 2033-01, whose Stornorechnung stays a draft: its lines show read-only.
  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: LONG_NAME });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Lesung mit Gespräch');
  await page.locator('#line-0-amount').fill('380');
  await page.locator('#invoice-date').fill('2033-05-02');
  await page.locator('#invoice-from').fill('2033-04-28');
  await page.locator('#invoice-to').fill('2033-04-28');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText('Rechnung 2033-01 ausgestellt.');
  await page.getByText('Stornieren…').click();
  await page.getByRole('button', { name: 'Stornorechnung anlegen' }).click();
  const stornoDraft = page.url();

  for (const viewport of VIEWPORTS) {
    for (const scheme of ['light', 'dark'] as const) {
      const context = await browser.newContext({
        colorScheme: scheme,
        viewport: { width: viewport.width, height: viewport.height },
        storageState: adminState,
      });
      const view = await context.newPage();
      await view.goto(url);
      await view.evaluate(() => document.fonts.ready);
      await expect(view.locator('a[aria-current="page"]')).toHaveScreenshot(`customer-row-${viewport.name}-${scheme}.png`);
      await expect(view.locator('section').filter({ has: view.locator('form') }).first())
        .toHaveScreenshot(`customer-form-${viewport.name}-${scheme}.png`);
      // The entry of the audit log for this customer; its time differs per run and is masked.
      await view.goto('/admin/protokoll');
      await expect(view.locator('#audit-list li').filter({ hasText: LONG_NAME }).filter({ hasText: 'Kunde angelegt' }))
        .toHaveScreenshot(
        `audit-entry-${viewport.name}-${scheme}.png`,
        { mask: [view.locator('[data-volatile]')] },
      );
      await view.goto(url);
      await expect(view.locator('#customer-templates')).toHaveScreenshot(`customer-templates-${viewport.name}-${scheme}.png`);
      await view.goto(draft);
      await expect(view.locator('#positionen')).toHaveScreenshot(`invoice-lines-${viewport.name}-${scheme}.png`);
      await view.goto(issued);
      const masks = { mask: [view.locator('[data-volatile]')] };
      await expect(view.locator('#invoice-issued')).toHaveScreenshot(`invoice-issued-${viewport.name}-${scheme}.png`, masks);
      await expect(view.locator('#invoice-documents')).toHaveScreenshot(
        `invoice-documents-${viewport.name}-${scheme}.png`,
        masks,
      );
      await expect(view.locator('#invoice-history')).toHaveScreenshot(`invoice-history-${viewport.name}-${scheme}.png`, masks);
      await expect(view.locator('#invoice-checks')).toHaveScreenshot(`invoice-checks-${viewport.name}-${scheme}.png`, masks);
      await expect(view.locator('#invoice-payments')).toHaveScreenshot(`invoice-payments-${viewport.name}-${scheme}.png`);
      await view.locator('#invoice-payment summary').click();
      await expect(view.locator('#invoice-payment')).toHaveScreenshot(`invoice-payment-${viewport.name}-${scheme}.png`, {
        mask: [view.locator('#payment-date')],
      });
      await view.locator('#invoice-mail summary').click();
      await expect(view.locator('#invoice-mail')).toHaveScreenshot(`invoice-mail-${viewport.name}-${scheme}.png`);
      await view.locator('#invoice-dispatch summary').click();
      await expect(view.locator('#invoice-dispatch')).toHaveScreenshot(`invoice-dispatch-${viewport.name}-${scheme}.png`, {
        mask: [view.locator('#dispatch-date')],
      });
      await view.goto(issued);
      await view.locator('#invoice-cancel summary').click();
      await expect(view.locator('#invoice-cancel')).toHaveScreenshot(`invoice-cancel-${viewport.name}-${scheme}.png`);
      await view.goto(cancelledInvoice);
      await expect(view.locator('#cancelled-by')).toHaveScreenshot(`invoice-cancelled-by-${viewport.name}-${scheme}.png`);
      await view.goto(storno);
      await expect(view.locator('#cancels')).toHaveScreenshot(`invoice-cancels-${viewport.name}-${scheme}.png`);
      await view.goto(stornoDraft);
      await expect(view.locator('#cancels')).toHaveScreenshot(`invoice-cancel-draft-${viewport.name}-${scheme}.png`);
      await expect(view.locator('#cancel-lines')).toHaveScreenshot(`invoice-cancel-lines-${viewport.name}-${scheme}.png`);
      await view.goto('/rechnungen?suche=2032-02');
      await expect(view.locator('#invoice-list li')).toHaveScreenshot(`invoice-list-storno-${viewport.name}-${scheme}.png`);
      await view.goto('/rechnungen');
      await expect(view.locator('#invoice-filter')).toHaveScreenshot(`invoice-filter-${viewport.name}-${scheme}.png`);
      await context.close();
    }
  }
});
