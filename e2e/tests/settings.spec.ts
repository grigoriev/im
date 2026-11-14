import { expect, test } from './fixtures';
import { ANONYMOUS, uniqueUsername } from './accounts';

// The seller profile is one record for the whole app: only this test writes it.
test('saves the seller profile and shows the problems of a wrong entry', async ({ page }) => {
  await page.goto('/einstellungen');
  await expect(page.getByRole('tab', { name: 'Profil' })).toHaveAttribute('aria-selected', 'true');
  await page.getByLabel('Name', { exact: true }).fill('Erika Mustermann');
  await page.getByLabel('Straße und Hausnummer').fill('Musterweg 1');
  await page.getByLabel('PLZ').fill('12345');
  await page.getByLabel('Ort', { exact: true }).fill('Musterstadt');
  await page.getByLabel('E-Mail').fill('erika@example.org');
  await page.getByLabel('Telefon').fill('+49 30 1234567');
  await page.getByLabel('Steuernummer').fill('12/345/67890');
  await page.getByLabel('IBAN').fill('DE89 3704 0044 0532 0130 01');
  await page.getByLabel('BIC').fill('cobadeffxxx');
  await page.getByLabel('Zusatz, z. B. der Beruf').fill('Freiberufliche Dozentin');
  await page.getByLabel('Website, z. B. www.example.de').fill('www.erika.example');
  await page.getByLabel('Kreditinstitut').fill('Commerzbank');
  await page.getByRole('button', { name: 'Speichern' }).click();

  await expect(page.getByRole('alert')).toHaveText('Die IBAN ist ungültig.');
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Erika Mustermann');

  await page.getByLabel('IBAN').fill('de89 3704 0044 0532 0130 00');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Profil gespeichert.');
  await expect(page.getByLabel('IBAN')).toHaveValue('DE89 3704 0044 0532 0130 00');
  await expect(page.getByLabel('BIC')).toHaveValue('COBADEFFXXX');
  await expect(page.getByLabel('Steuerstatus')).toHaveValue('KLEINUNTERNEHMER');
  await expect(page.getByLabel('Zusatz, z. B. der Beruf')).toHaveValue('Freiberufliche Dozentin');
  await expect(page.getByLabel('Kreditinstitut')).toHaveValue('Commerzbank');
  await expect(page.getByLabel('Kontoinhaber (leer: der Name)')).toHaveValue('');
});

test('the admin changes a default tax note and restores it', async ({ page }) => {
  await page.goto('/einstellungen');
  await page.getByRole('tab', { name: /Steuerhinweise/ }).click();
  const note = page.getByLabel('Steuerfreie Bildungsleistung (§ 4 Nr. 21 UStG)');
  await expect(note).toHaveValue('Steuerfrei gemäß § 4 Nr. 21 UStG.');
  await note.fill('Steuerfrei nach § 4 Nr. 21 a UStG.');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Steuerhinweise gespeichert.');
  await expect(note).toHaveValue('Steuerfrei nach § 4 Nr. 21 a UStG.');

  await note.fill('');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(note).toHaveValue('Steuerfrei gemäß § 4 Nr. 21 UStG.');
});

test('the admin changes the default e-mail text and restores it', async ({ page }) => {
  await page.goto('/einstellungen');
  await page.getByRole('tab', { name: /E-Mail/ }).click();
  const subject = page.getByLabel('Betreff');
  await expect(subject).toHaveValue('Rechnung {Rechnungsnummer}');
  await expect(page.getByLabel('Text')).toHaveValue(/meine Rechnung \{Rechnungsnummer\}/);
  await subject.fill('Rechnung {Rechnungsnummer} von Elena Sommer');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('E-Mail-Text gespeichert.');
  await expect(subject).toHaveValue('Rechnung {Rechnungsnummer} von Elena Sommer');

  await subject.fill('');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(subject).toHaveValue('Rechnung {Rechnungsnummer}');
});

test('the admin enters the revenue of last year, and the overview counts it', async ({ page }) => {
  const lastYear = new Date().getFullYear() - 1;
  await page.goto('/einstellungen');
  await page.getByRole('tab', { name: /Vorjahre/ }).click();
  await expect(page.getByLabel('Jahr')).toHaveValue(String(lastYear));
  await page.getByLabel('Umsatz in €').fill('18.250,00');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText(`Vorjahresumsatz ${lastYear} gespeichert.`);
  await expect(page.locator('#baseline-list')).toContainText('18.250,00 €');

  await page.goto('/');
  await expect(page.locator('#threshold')).toContainText('Mit dem Vorjahresumsatz aus den Einstellungen.');
  await expect(page.locator('#threshold-previous')).toContainText('18.250,00 €');

  await page.goto('/admin/vorjahre');
  await page.getByRole('button', { name: `Vorjahresumsatz ${lastYear} entfernen` }).click();
  await expect(page.getByText('Noch kein Vorjahresumsatz erfasst.')).toBeVisible();
});

test('the admin downloads the full export and cannot import it over data', async ({ page }) => {
  await page.goto('/einstellungen');
  await page.getByRole('tab', { name: /Datensicherung/ }).click();
  await expect(page.locator('#backup-overdue')).toHaveText('Es gibt noch keine automatische Sicherung.');
  await expect(page.locator('#backup-last')).toHaveText('noch keine');
  const download = await page.request.get((await page.locator('#backup-download').getAttribute('href'))!);
  expect(download.headers()['content-type']).toBe('application/zip');
  expect(download.headers()['content-disposition']).toMatch(/^attachment; filename="im-datensicherung-\d{4}-\d{2}-\d{2}\.zip"$/);
  const zip = await download.body();
  expect(zip.subarray(0, 2).toString()).toBe('PK');
  expect(zip.includes('manifest.json')).toBe(true);
  expect(zip.includes('data/invoice.json')).toBe(true);

  await page.locator('#backup-file').setInputFiles({ name: 'export.zip', mimeType: 'application/zip', buffer: zip });
  await page.getByRole('button', { name: 'Einspielen' }).click();
  await expect(page.locator('#backup-error')).toContainText('Hier gibt es schon Daten.');
});

test('a user edits the profile but not the tax notes', async ({ page, browser }) => {
  const username = uniqueUsername('st');
  await page.goto('/admin/benutzer');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill('Einstellungen Nutzerin');
  await page.locator('#new-password').fill('settings-passwort-123');
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText(username);

  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill('settings-passwort-123');
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await user.getByRole('link', { name: 'Einstellungen' }).click();
  await expect(user.getByRole('tab', { name: 'Profil' })).toBeVisible();
  await expect(user.getByRole('tab', { name: /Steuerhinweise/ })).toHaveCount(0);
  await expect(user.getByRole('tab', { name: /Nummernkreis/ })).toHaveCount(0);
  await expect(user.getByRole('tab', { name: /Länder/ })).toHaveCount(0);
  await expect(user.getByRole('tab', { name: /E-Mail/ })).toHaveCount(0);
  await expect(user.getByRole('tab', { name: /Vorjahre/ })).toHaveCount(0);
  await expect(user.getByRole('tab', { name: /Datensicherung/ })).toHaveCount(0);
  const response = await user.goto('/admin/steuerhinweise');
  expect(response?.status()).toBe(403);
  await context.close();
});
