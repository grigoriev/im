import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';
import { ensureProfile, newCustomer } from './seller';

test('creates a draft invoice, edits its lines and deletes it', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Rechnungskunde ${randomUUID().slice(0, 8)}`);

  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(customer);
  await expect(page.locator('#invoice-checks')).toContainText('Position 1: Bitte die Leistung beschreiben.');

  await page.locator('#line-0-text').fill('Lesung aus „Der stille Hafen“ am 16.03.2027, 90 Min.');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Position hinzufügen' }).click();
  await expect(page.locator('#line-1-text')).toBeVisible();
  await expect(page.locator('#line-0-amount')).toHaveValue('400,00');
  await page.locator('#line-1-text').fill('Signierstunde');
  await expect(page.locator('#line-1-quantity'), 'a lump sum hides the quantity').toBeHidden();
  await page.locator('#line-1-kind').selectOption('false');
  await expect(page.locator('#line-1-quantity')).toBeVisible();
  await page.locator('#line-1-quantity').fill('2');
  await page.locator('#line-1-price').fill('12,50');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');
  await expect(page.locator('#invoice-totals')).toContainText('425,00 €');
  await expect(page.locator('#invoice-checks')).toContainText('Vollständig.');
  const preview = await page.request.get((await page.locator('#invoice-preview').getAttribute('href'))!);
  expect(preview.headers()['content-type']).toBe('application/pdf');
  expect((await preview.body()).subarray(0, 5).toString()).toBe('%PDF-');

  await page.locator('#line-1-price').fill('zwölf');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('alert')).toContainText('Position 2: Der Preis ist keine Zahl');
  await expect(page.locator('#line-1-price')).toHaveValue('zwölf');

  // Back to a valid price, then the switch to a lump sum shows the total, and a typed amount wins.
  await page.locator('#line-1-price').fill('12,50');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.locator('#line-1-kind').selectOption('true');
  await expect(page.locator('#line-1-amount')).toHaveValue('25,00');
  await page.locator('#line-1-amount').fill('30');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('#invoice-totals')).toContainText('430,00 €');

  // A quantity changed right before the switch counts, when no new amount was typed.
  await page.locator('#line-1-kind').selectOption('false');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.locator('#line-1-quantity').fill('4');
  await page.locator('#line-1-price').fill('12,50');
  await page.locator('#line-1-kind').selectOption('true');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('#invoice-totals')).toContainText('450,00 €');

  await page.getByRole('button', { name: 'Position 2 entfernen' }).click();
  await expect(page.locator('#line-1-text')).toHaveCount(0);
  await expect(page.locator('#invoice-totals')).toContainText('400,00 €');

  await page.goto('/rechnungen');
  const row = page.locator('#invoice-list li').filter({ hasText: customer });
  await expect(row).toContainText('Entwurf');
  await expect(row).toContainText('400,00 €');
  await row.getByRole('link').click();
  await page.getByText('Entwurf löschen', { exact: true }).first().click();
  await page.getByRole('button', { name: 'Entwurf löschen' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gelöscht.');
  await expect(page.locator('#invoice-list li').filter({ hasText: customer })).toHaveCount(0);
});

test('issues an invoice with its number and documents', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Ausstellkunde ${randomUUID().slice(0, 8)}`);

  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await expect(page.locator('#invoice-issue'), 'an incomplete draft cannot be issued').toHaveCount(0);
  await page.locator('#line-0-text').fill('Lesung am {Leistungszeitraum}');
  await page.locator('#line-0-amount').fill('400');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gespeichert.');

  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText(/^Rechnung \d{4}-\d{2,} ausgestellt\.$/);
  const number = (await page.locator('#invoice-number').textContent())!.trim().replace(/^Rechnung /, '');
  await expect(page.getByRole('status')).toHaveText(`Rechnung ${number} ausgestellt.`);
  await expect(page.locator('#invoice-status')).toHaveText('Ausgestellt');
  await expect(page.locator('#invoice-summary')).toContainText(`${customer} · 400,00 € · fällig am`);
  await expect(page.locator('#invoice-checks')).toContainText('Unveränderbar seit');
  await expect(page.locator('#invoice-history li')).toHaveText([/Entwurf angelegt/, /Rechnung ausgestellt/]);
  await expect(page.locator('#invoice-total')).toHaveText('400,00 €');
  await expect(page.getByText('Entwurf löschen')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Speichern' })).toHaveCount(0);

  const pdf = await page.request.get((await page.locator('#download-pdf').getAttribute('href'))!);
  expect(pdf.headers()['content-type']).toBe('application/pdf');
  expect(pdf.headers()['content-disposition']).toBe(`attachment; filename="Rechnung_${number}.pdf"`);
  expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-');
  const xml = await page.request.get((await page.locator('#download-xml').getAttribute('href'))!);
  expect(xml.headers()['content-type']).toContain('application/xml');
  expect((await xml.body()).toString()).toContain(number);
  await expect(page.locator('#invoice-documents')).toContainText(`Rechnung_${number}.pdf`);
  await expect(page.locator('#invoice-documents')).toContainText(/SHA-256 [0-9a-f]{64}/);

  const address = `rechnung-${randomUUID().slice(0, 8)}@kunde.example`;
  await page.getByText('Per E-Mail senden…').click();
  await expect(page.locator('#mail-subject')).toHaveValue(`Rechnung ${number}`);
  await expect(page.locator('#mail-pdf')).toBeChecked();
  await page.locator('#mail-to').fill(address);
  // The form posts once: after the first submit it refuses the next and shows its button as busy. A listener on the
  // window, which runs after the app's own, keeps the page here, so nothing is sent yet.
  const guard = await page.evaluate(() => {
    const button = document.getElementById('mail-send') as HTMLButtonElement;
    const form = button.form as HTMLFormElement;
    const stay = (event: Event) => event.preventDefault();
    window.addEventListener('submit', stay);
    const first = new SubmitEvent('submit', { bubbles: true, cancelable: true, submitter: button });
    form.dispatchEvent(first);
    const busy = button.getAttribute('aria-disabled');
    const second = new SubmitEvent('submit', { bubbles: true, cancelable: true, submitter: button });
    window.removeEventListener('submit', stay);
    form.dispatchEvent(second);
    delete form.dataset.sent;
    button.removeAttribute('aria-disabled');
    return { busy, refused: second.defaultPrevented };
  });
  expect(guard).toEqual({ busy: 'true', refused: true });
  await page.getByRole('button', { name: 'Senden', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText(`E-Mail an ${address} gesendet.`);
  await expect(page.locator('#invoice-status')).toHaveText('Versendet');
  await expect(page.locator('#invoice-history li').last()).toContainText(`E-Mail aus der App, ${address}, Notiz: PDF`);
  // GreenMail got it; its API answers on the compose network.
  const inbox = await (await page.request.get(`http://mail:8080/api/user/${address}/messages/INBOX`)).json();
  expect(inbox).toHaveLength(1);
  expect(inbox[0].subject).toBe(`Rechnung ${number}`);
  expect(inbox[0].mimeMessage).toContain(`filename=Rechnung_${number}.pdf`);

  await page.getByText('Als versendet markieren…').click();
  await page.getByLabel('Weg').selectOption({ label: 'Post' });
  await page.getByLabel('Empfänger (optional)').fill('Buchhaltung');
  await page.locator('#invoice-dispatch').getByLabel('Notiz (optional)').fill('mit Begleitbrief');
  await page.getByRole('button', { name: 'Vermerken' }).click();
  await expect(page.getByRole('status')).toHaveText('Versand vermerkt.');
  await expect(page.locator('#invoice-status')).toHaveText('Versendet');
  await expect(page.locator('#invoice-history li').last()).toContainText('Versand vermerkt');
  await expect(page.locator('#invoice-history li').last()).toContainText(', Post, Buchhaltung, Notiz: mit Begleitbrief');

  await expect(page.locator('#payments-open')).toHaveText('400,00 €');
  await page.getByText('Zahlung erfassen…').click();
  await page.getByLabel('Betrag in €').fill('150');
  await page.getByRole('button', { name: 'Erfassen' }).click();
  await expect(page.getByRole('status')).toHaveText('Zahlung erfasst.');
  await expect(page.locator('#invoice-status')).toHaveText('Versendet');
  await expect(page.locator('#payments-open')).toHaveText('250,00 €');
  await page.getByText('Zahlung erfassen…').click();
  await expect(page.getByLabel('Betrag in €'), 'the open amount is the default').toHaveValue('250,00');
  await page.getByRole('button', { name: 'Erfassen' }).click();
  await expect(page.getByRole('status')).toHaveText('Zahlung erfasst. Die Rechnung ist bezahlt.');
  await expect(page.locator('#invoice-status')).toHaveText('Bezahlt');
  await expect(page.locator('#payments-paid')).toHaveText('400,00 €');
  await expect(page.locator('#payments-open')).toHaveText('0,00 €');
  await expect(page.locator('#invoice-history li').last()).toContainText('Zahlung erfasst');

  await page.goto('/rechnungen');
  const row = page.locator('#invoice-list li').filter({ hasText: customer });
  await expect(row).toContainText(number);
  await expect(row).toContainText('Bezahlt');
  await expect(row).toContainText('400,00 €');

  await page.getByLabel('Kunde oder Nummer').fill(number);
  await page.getByRole('button', { name: 'Filtern' }).click();
  await expect(page.locator('#invoice-list li')).toHaveCount(1);
  await expect(page.locator('#invoice-list li')).toContainText(customer);
  await page.getByLabel('Status').selectOption({ label: 'Entwurf' });
  await page.getByRole('button', { name: 'Filtern' }).click();
  await expect(page.locator('#invoice-none')).toHaveText('Keine Rechnungen gefunden.');
});

test('cancels an invoice with a Stornorechnung', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Stornokunde ${randomUUID().slice(0, 8)}`);
  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Lesung');
  await page.locator('#line-0-amount').fill('300');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  const number = (await page.locator('#invoice-number').textContent())!.trim().replace(/^Rechnung /, '');
  const original = page.url();

  await page.getByText('Stornieren…').click();
  await page.getByRole('button', { name: 'Stornorechnung anlegen' }).click();
  await expect(page.getByRole('status')).toHaveText('Stornorechnung als Entwurf angelegt. Die Nummer folgt beim Ausstellen.');
  await expect(page.locator('#cancels')).toContainText(`Rechnung ${number}`);
  await expect(page.locator('#cancel-lines')).toContainText('Lesung');
  await expect(page.locator('#cancel-total')).toHaveText('300,00 €');
  await expect(page.getByRole('button', { name: 'Position hinzufügen' })).toHaveCount(0);

  await page.getByText('Rechnung ausstellen…').click();
  await page.getByRole('button', { name: 'Jetzt ausstellen' }).click();
  await expect(page.getByRole('status')).toHaveText(/^Rechnung \S+ ausgestellt\.$/);
  const storno = (await page.locator('#invoice-number').textContent())!.trim().replace(/^Rechnung /, '');
  await expect(page.locator('#cancels')).toContainText(`Rechnung ${number}`);
  await expect(page.locator('#invoice-payments')).toHaveCount(0);
  await expect(page.getByText('Stornieren…')).toHaveCount(0);
  const pdf = await page.request.get((await page.locator('#download-xml').getAttribute('href'))!);
  expect((await pdf.body()).toString()).toContain('<ram:TypeCode>381</ram:TypeCode>');

  await page.goto(original);
  await expect(page.locator('#invoice-status')).toHaveText('Storniert');
  await expect(page.locator('#cancelled-by')).toContainText(`Stornorechnung ${storno}`);
  await expect(page.locator('#invoice-history li').last()).toContainText('Rechnung storniert');

  await page.goto(`/rechnungen?suche=${encodeURIComponent(customer)}`);
  await expect(page.locator('#invoice-list li').filter({ hasText: `Stornorechnung · ${storno}` })).toHaveCount(1);
});

test('warns when a draft would cross the §19 limit', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Grenzkunde ${randomUUID().slice(0, 8)}`);
  await page.goto('/rechnungen');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  await page.locator('#line-0-text').fill('Großauftrag');
  await page.locator('#line-0-amount').fill('100.001,00');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('#over-limit')).toContainText('über 100.000 €');
  await expect(page.locator('#over-limit')).toHaveScreenshot('over-limit.png');

  await page.locator('#line-0-amount').fill('400,00');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('#over-limit')).toHaveCount(0);
  await page.getByText('Entwurf löschen', { exact: true }).first().click();
  await page.getByRole('button', { name: 'Entwurf löschen' }).click();
  await expect(page.getByRole('status')).toHaveText('Entwurf gelöscht.');
});
