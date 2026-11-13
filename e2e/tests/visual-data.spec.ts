import { expect, test } from './fixtures';

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
      await context.close();
    }
  }
});
