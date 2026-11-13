import { chromium } from '@playwright/test';
import { ADMIN } from './tests/accounts';

// Waits until the application reports UP, then logs in as the admin and keeps the session.
export default async function globalSetup(): Promise<void> {
  const base = process.env.BASE_URL ?? 'http://localhost:8080';
  const deadline = Date.now() + 120_000;
  for (;;) {
    try {
      const response = await fetch(`${base}/actuator/health`);
      if (response.ok && (await response.text()).includes('"UP"')) break;
    } catch {
      // not up yet
    }
    if (Date.now() > deadline) throw new Error(`${base} did not report UP within two minutes`);
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  const browser = await chromium.launch();
  const page = await browser.newPage({ baseURL: base });
  await page.goto('/login');
  await page.getByLabel('Benutzername').fill(ADMIN.username);
  await page.getByLabel('Passwort').fill(ADMIN.password);
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
  await page.waitForURL(`${base}/`);
  await page.context().storageState({ path: '.auth/admin.json' });
  await browser.close();
}
