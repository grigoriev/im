import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';
import { ensureProfile, newCustomer } from './seller';

test('a template of a customer starts a draft that keeps its copy', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Vorlagenkunde ${randomUUID().slice(0, 8)}`);
  const customerPage = page.url();

  await page.getByRole('button', { name: 'Neue Vorlage' }).click();
  await page.getByLabel('Name der Vorlage').fill('Lesung mit Gespräch');
  await page.locator('#line-0-text').fill('Lesung, 90 Min.');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Position hinzufügen' }).click();
  await page.locator('#line-1-text').fill('Reisekosten');
  await page.locator('#line-1-amount').fill('80');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Vorlage gespeichert.');
  const templatePage = page.url();

  await page.goto(customerPage);
  const row = page.locator('#customer-templates > div').filter({ hasText: 'Lesung mit Gespräch' });
  await expect(row).toContainText('Lesung, 90 Min. · Reisekosten');
  await row.getByRole('button', { name: 'Rechnung erstellen' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(customer);
  await expect(page.locator('#line-1-text')).toHaveValue('Reisekosten');
  await expect(page.locator('#invoice-totals')).toContainText('480,00 €');
  const draft = page.url();

  await page.goto(templatePage);
  await page.getByRole('button', { name: 'Position 2 entfernen' }).click();
  await page.goto(draft);
  await expect(page.locator('#line-1-text'), 'the draft keeps its copy').toHaveValue('Reisekosten');

  await page.goto(templatePage);
  await page.getByText('Vorlage löschen', { exact: true }).first().click();
  await page.getByRole('button', { name: 'Vorlage löschen' }).click();
  await expect(page.getByRole('status')).toHaveText('Vorlage „Lesung mit Gespräch“ gelöscht.');
  await expect(page.locator('#customer-templates')).toContainText('Noch keine Vorlage.');
});
