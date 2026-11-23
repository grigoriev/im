import { expect, test } from './fixtures';

test('every page names the version, and the admin sees what runs', async ({ page }) => {
  await page.goto('/');
  // The E2E image is built from the branch: a snapshot, named with the time of its build.
  await expect(page.locator('#app-version')).toContainText(/Version \d+\.\d+\.\d+-SNAPSHOT/);
  await expect(page.locator('#app-version')).toContainText(/gebaut \d{2}\.\d{2}\.\d{4}, \d{2}:\d{2}/);

  await page.getByRole('navigation', { name: 'Hauptmenü' }).getByRole('link', { name: 'System', exact: true }).click();
  await expect(page.locator('#system-version')).toContainText(/\d+\.\d+\.\d+-SNAPSHOT/);
  await expect(page.locator('#system-schema')).toHaveText(/^V\d+$/);
  await expect(page.locator('#system-database')).toHaveText(/^\d+/);
  await expect(page.locator('#system-java')).toHaveText(/^\d+/);
});
