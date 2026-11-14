import { expect, test } from './fixtures';

// The pattern is one setting for the whole app: only this test changes it, in its own project after all others
// (playwright.config.ts), and it restores the default.
test('the admin sets the number pattern and the start of next year', async ({ page }) => {
  const next = new Date().getFullYear() + 1;
  await page.goto('/einstellungen');
  await page.getByRole('tab', { name: /Nummernkreis/ }).click();
  await expect(page.locator(`#next-${next}`)).toHaveText(`${next}-01`);

  await page.getByLabel('Muster').fill('{NN}');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('alert')).toContainText('Das Muster braucht das Jahr');

  await page.getByLabel('Muster').fill('RE-{YYYY}-{NNN}');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Muster gespeichert.');
  await expect(page.locator(`#next-${next}`)).toHaveText(`RE-${next}-001`);

  await page.getByLabel(`Startnummer ${next}`).fill('5');
  await page.locator(`#start-${next}`).locator('xpath=ancestor::form').getByRole('button', { name: 'Setzen' }).click();
  await expect(page.getByRole('status')).toHaveText(`Startnummer für ${next} gespeichert.`);
  await expect(page.locator(`#next-${next}`)).toHaveText(`RE-${next}-005`);

  await page.getByLabel('Muster').fill('{YYYY}-{NN}');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator(`#next-${next}`)).toHaveText(`${next}-05`);
});
