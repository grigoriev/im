import { test as base } from '@playwright/test';
import fs from 'node:fs';
import { ADMIN } from './accounts';

export { expect } from '@playwright/test';

// One admin session per worker. The app keeps flash messages in the session: tests that ran in parallel
// in one shared session took each other's messages, for example "Benutzer ... angelegt.".
export const test = base.extend<object, { adminState: string }>({
  adminState: [
    async ({ browser }, use, workerInfo) => {
      const file = `.auth/admin-${workerInfo.parallelIndex}.json`;
      if (!fs.existsSync(file)) {
        // A worker fixture gets no options of the config: take the base URL from the project.
        const context = await browser.newContext({
          baseURL: workerInfo.project.use.baseURL,
          storageState: { cookies: [], origins: [] },
        });
        const page = await context.newPage();
        await page.goto('/login');
        await page.getByLabel('Benutzername').fill(ADMIN.username);
        await page.getByLabel('Passwort').fill(ADMIN.password);
        await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
        await page.waitForURL(/\/$/);
        await context.storageState({ path: file });
        await context.close();
      }
      await use(file);
    },
    { scope: 'worker' },
  ],
  storageState: ({ adminState }, use) => use(adminState),
});
