import { expect, test } from './fixtures';
import { newCustomer } from './seller';
import { randomUUID } from 'node:crypto';

// The styled list of a select: it opens on a click, picks with a click, moves with the arrows, Escape restores the
// value it had, Tab keeps the new one, and typed letters filter a long list. The native select keeps the value.
test('a select opens a styled list and picks, cancels and filters with mouse and keys', async ({ page }) => {
  await page.goto('/kunden/neu');
  const format = page.getByLabel('E-Rechnungsformat');
  const list = page.locator('.listbox-list').filter({ hasText: 'XRechnung: nur XML' });
  await expect(format).toHaveValue('ZUGFERD_EN16931');

  await format.click();
  await expect(list).toBeVisible();
  await expect(format).toHaveAttribute('aria-expanded', 'true');
  await list.getByText('XRechnung: nur XML').click();
  await expect(list).toBeHidden();
  await expect(format).toHaveValue('XRECHNUNG');

  await format.press('ArrowDown');
  await expect(list).toBeVisible();
  await format.press('ArrowUp');
  await expect(format).toHaveValue('ZUGFERD_XRECHNUNG');
  await format.press('Escape');
  await expect(list).toBeHidden();
  await expect(format).toHaveValue('XRECHNUNG');

  await format.press('Enter');
  await format.press('ArrowUp');
  await format.press('Tab');
  await expect(list).toBeHidden();
  await expect(format).toHaveValue('ZUGFERD_XRECHNUNG');

  await format.click();
  await page.locator('h2').first().click();
  await expect(list).toBeHidden();
  await expect(format).toHaveValue('ZUGFERD_XRECHNUNG');
});

test('typed letters filter a long list and Enter picks the match', async ({ page }) => {
  const token = randomUUID().slice(0, 8);
  const name = await newCustomer(page, `Filterkunde ${token}`);
  for (let i = 0; i < 9; i++) await newCustomer(page, `Füllkunde ${randomUUID().slice(0, 8)}`);
  await page.goto('/rechnungen');
  const customer = page.getByLabel('Kunde der neuen Rechnung');
  await customer.click();
  const list = page.locator('.listbox-list').filter({ has: page.locator('.listbox-search') }).first();
  await expect(list).toBeVisible();
  await customer.press('ArrowDown');
  await page.keyboard.type(token);
  await expect(list.locator('.listbox-search')).toHaveValue(token);
  await expect(list.locator('.listbox-option:not([hidden])')).toHaveText([name]);
  await customer.press('Enter');
  await expect(list).toBeHidden();
  await expect(customer.locator('option:checked')).toHaveText(name);
});
