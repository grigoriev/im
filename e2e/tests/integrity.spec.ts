import { randomUUID } from 'node:crypto';
import pg from 'pg';
import { expect, test } from './fixtures';
import { ensureProfile, newCustomer } from './seller';

// Plays someone with the password of the database owner: switches the trigger off and changes the XML of an issued
// invoice together with its SHA-256. Only the seal, whose key is not in the database, gives the change away. The test
// runs alone and last, and puts the original bytes back, since the overview of every other test would show the change.

async function owner<T>(work: (client: pg.Client) => Promise<T>): Promise<T> {
  const client = new pg.Client({ connectionString: process.env.E2E_DB_URL });
  await client.connect();
  try {
    return await work(client);
  } finally {
    await client.end();
  }
}

async function setXml(invoiceId: string, content: Buffer): Promise<void> {
  await owner(async (client) => {
    await client.query('ALTER TABLE invoice_document DISABLE TRIGGER invoice_document_frozen');
    try {
      await client.query("UPDATE invoice_document SET content = $1, sha256 = encode(sha256($1), 'hex') "
        + "WHERE invoice_id = $2 AND kind = 'XML'", [content, invoiceId]);
    } finally {
      await client.query('ALTER TABLE invoice_document ENABLE TRIGGER invoice_document_frozen');
    }
  });
}

test('a document changed in the database shows on the invoice and the overview, and stays in', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Prüfkunde ${randomUUID().slice(0, 8)}`);
  await page.goto('/invoices');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Lesung');
  await page.locator('#line-0-amount').fill('300');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.locator('#invoice-integrity'), 'an invoice as issued is in order').toHaveCount(0);
  const invoiceId = page.url().split('/').pop()!;

  const original = await owner(async (client) => (await client.query(
    "SELECT content FROM invoice_document WHERE invoice_id = $1 AND kind = 'XML'", [invoiceId])).rows[0].content as Buffer);
  await setXml(invoiceId, Buffer.from(original.toString('utf8').replaceAll('>300.00<', '>3000.00<'), 'utf8'));
  try {
    await page.reload();
    const alert = page.locator('#invoice-integrity');
    await expect(alert).toContainText('Prüfung fehlgeschlagen');
    await expect(alert).toContainText('Das Siegel passt nicht');
    await expect(alert).toContainText('Die XML-Datei weicht vom Stand der Ausstellung ab.');
    await expect(page.locator('#invoice-differences tbody tr')).toHaveText([/Gesamtbetrag\s*300,00 €\s*3\.000,00 €/]);
    await expect(alert).toHaveScreenshot('invoice-integrity.png', { mask: [alert.locator('li').first()] });

    const download = await page.request.get(`/invoices/${invoiceId}/documents/xml`, { maxRedirects: 0 });
    expect(download.status(), 'the changed XML stays in').toBe(303);
    expect(download.headers()['location']).toBe(`/invoices/${invoiceId}`);
    const pdf = await page.request.get(`/invoices/${invoiceId}/documents/pdf`, { maxRedirects: 0 });
    expect(pdf.status(), 'the PDF itself is intact').toBe(200);

    await page.goto('/admin/backup');
    await page.getByRole('button', { name: 'Jetzt prüfen' }).click();
    await expect(page.locator('#backup-error')).toHaveText(
      'Prüfung fehlgeschlagen: 1 Rechnung oder Dokument passt nicht zu Prüfsumme oder Siegel.');
    await page.goto('/');
    await expect(page.locator('#integrity-alert a')).toHaveAttribute('href', `/invoices/${invoiceId}`);
    await page.setViewportSize({ width: 375, height: 800 });
    await page.goto(`/invoices/${invoiceId}`);
    expect(await page.evaluate(() => document.documentElement.scrollWidth), 'the check fits a phone')
      .toBeLessThanOrEqual(375);
  } finally {
    await setXml(invoiceId, original);
  }
  await page.goto('/admin/backup');
  await page.getByRole('button', { name: 'Jetzt prüfen' }).click();
  await expect(page.locator('#backup-success')).toHaveText(
    'Prüfung abgeschlossen: Alle Dateien passen zu Prüfsumme und Siegel.');
  await page.goto('/');
  await expect(page.locator('#integrity-alert')).toHaveCount(0);
});
