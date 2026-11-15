import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';
import { ensureProfile } from './seller';

// The business documents: upload, versions, description and download (docs/PLAN.md, section 7, "Documents").
const PDF = Buffer.from('%PDF-1.4\n% Steuerbescheid\n');
const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]);
const VIEWPORTS = [
  { name: 'phone', width: 390, height: 2000 },
  { name: 'desktop', width: 1440, height: 2000 },
];

test('a document keeps every version and its file', async ({ page, browser, adminState }) => {
  test.slow();
  const title = `Einkommensteuerbescheid ${randomUUID().slice(0, 8)}`;
  await page.goto('/documents');
  await page.getByRole('link', { name: 'Dokument ablegen' }).click();
  await page.locator('#document-upload').setInputFiles({
    name: 'Bescheid.txt', mimeType: 'text/plain', buffer: Buffer.from('kein PDF'),
  });
  await page.getByLabel('Art').selectOption('TAX_ASSESSMENT');
  await page.getByLabel('Titel').fill(title);
  await page.getByLabel('Datum des Dokuments').fill('2027-05-10');
  await page.getByLabel('Betrifft das Jahr').fill('2026');
  await page.getByRole('button', { name: 'Ablegen' }).click();
  await expect(page.locator('#document-problems')).toHaveText('Bitte eine PDF-, JPG- oder PNG-Datei wählen.');
  await expect(page.getByLabel('Titel'), 'the form keeps the entries').toHaveValue(title);

  await page.locator('#document-upload').setInputFiles({
    name: 'Bescheid 2026.pdf', mimeType: 'application/pdf', buffer: PDF,
  });
  await page.getByRole('button', { name: 'Ablegen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Dokument „${title}“ abgelegt.`);
  await expect(page.locator('#document-file')).toContainText('Bescheid 2026.pdf');
  const first = page.url();
  const download = await page.request.get((await page.locator('#document-download').getAttribute('href'))!);
  expect(download.headers()['content-type']).toBe('application/pdf');
  expect(Buffer.from(await download.body())).toEqual(PDF);

  await page.getByLabel('Notiz').first().fill('Ohne Nachzahlung');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  await expect(page.locator('#document-note')).toHaveValue('Ohne Nachzahlung');

  await page.locator('#version-upload').setInputFiles({ name: 'Geändert.png', mimeType: 'image/png', buffer: PNG });
  await page.locator('#version-date').fill('2027-08-01');
  await page.locator('#version-note').fill('Geändert nach Einspruch');
  await page.getByRole('button', { name: 'Neue Fassung ablegen' }).click();
  await expect(page.getByRole('status')).toHaveText('Neue Fassung abgelegt.');
  await expect(page.locator('#document-versions li')).toHaveCount(2);
  await expect(page.locator('#document-versions')).toContainText('aktuell');
  await expect(page.locator('#document-title')).toHaveValue(title);

  await page.goto(first);
  await expect(page.locator('#document-replaced')).toContainText('Eine neuere Fassung ersetzt dieses Dokument.');
  await expect(page.locator('#document-title')).toBeDisabled();
  await expect(page.locator('#document-version')).toHaveCount(0);

  await page.goto('/documents?category=TAX_ASSESSMENT');
  const row = page.locator('#document-list li').filter({ hasText: title });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('Steuerbescheid · 01.08.2027 · für 2026 · neue Fassung');
  await page.goto('/documents?category=CONTRACT');
  await expect(page.locator('#document-list li').filter({ hasText: title })).toHaveCount(0);

  await page.goto('/admin/audit');
  await expect(page.locator('main')).toContainText('Neue Fassung abgelegt');

  // The replaced version alone: its own data only. Times, hashes and the user differ per run and are masked.
  for (const viewport of VIEWPORTS) {
    for (const scheme of ['light', 'dark'] as const) {
      const context = await browser.newContext({ colorScheme: scheme, viewport, storageState: adminState });
      const view = await context.newPage();
      await view.goto(first);
      await view.evaluate(() => document.fonts.ready);
      await view.locator('h1').evaluate((heading) => { heading.textContent = 'Einkommensteuerbescheid 2026'; });
      await view.locator('#document-title').evaluate((input: HTMLInputElement) => { input.value = 'Einkommensteuerbescheid 2026'; });
      await expect(view.locator('main')).toHaveScreenshot(`document-replaced-${viewport.name}-${scheme}.png`, {
        mask: [view.locator('[data-volatile]')],
      });
      await context.close();
    }
  }
});

test('the app writes the Verfahrensdokumentation as versions', async ({ page }) => {
  await ensureProfile(page);
  await page.goto('/documents');
  await page.getByRole('link', { name: 'Erstellen' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verfahrensdokumentation');
  await page.getByLabel('Gültig ab').fill('2027-01-01');
  await page.getByLabel('Programm für Belege und Ausgaben').fill('WISO Steuern');
  await page.getByLabel('Ablageort der Jahresarchive').fill('in einem Dropbox-Ordner');
  await page.getByLabel('Änderung dieser Fassung').fill('Erstfassung');
  const [preview] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Vorschau (PDF)' }).click(),
  ]);
  expect(preview.suggestedFilename()).toBe('Verfahrensdokumentation_Entwurf.pdf');
  await page.getByRole('button', { name: 'Als Fassung ablegen' }).click();
  await expect(page.getByRole('status')).toContainText('Verfahrensdokumentation als neue Fassung abgelegt.');
  await expect(page.locator('#document-file')).toContainText(/Verfahrensdokumentation_\d{4}-\d{2}-\d{2}\.pdf/);
  const pdf = await page.request.get((await page.locator('#document-download').getAttribute('href'))!);
  expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-');

  await page.goto('/documents/procedure');
  await expect(page.getByLabel('Programm für Belege und Ausgaben'), 'the values stay').toHaveValue('WISO Steuern');
  await expect(page.locator('#procedure-current')).toContainText('Verfahrensdokumentation_');
  await page.getByLabel('Änderung dieser Fassung').fill('Neue Version der App');
  await page.getByRole('button', { name: 'Als Fassung ablegen' }).click();
  await expect(page.locator('#document-versions li')).toHaveCount(2);
});
