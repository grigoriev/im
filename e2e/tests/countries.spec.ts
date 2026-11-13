import { expect, test } from '@playwright/test';

// Japan is not on the starting list: only this test adds it, and it removes it again.
test('the admin adds a country that the customer form then offers', async ({ page }) => {
  await page.goto('/admin/laender');
  await expect(page.getByRole('tab', { name: /Länder/ })).toHaveAttribute('aria-selected', 'true');
  await page.getByLabel('Land hinzufügen').selectOption('JP');
  await page.getByRole('button', { name: 'Hinzufügen' }).click();
  await expect(page.getByRole('status')).toHaveText('Japan hinzugefügt.');
  await expect(page.locator('#country-list')).toContainText('Japan');

  await page.goto('/kunden/neu');
  await expect(page.getByLabel('Land')).toHaveValue('DE');
  await expect(page.getByLabel('Land').locator('option[value="JP"]')).toHaveText('Japan');

  await page.goto('/admin/laender');
  await page.getByRole('button', { name: 'Japan entfernen' }).click();
  await expect(page.getByRole('status')).toHaveText('Japan entfernt.');
  await expect(page.locator('#country-list')).not.toContainText('Japan');
});
