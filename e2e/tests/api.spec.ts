import { expect, test } from './fixtures';
import { ANONYMOUS, uniqueUsername } from './accounts';

// A test login of the fresh user, built from parts.
const LOGIN = ['api', 'token', 'login', '123'].join('-');

// A user creates an API token on "Mein Konto", an assistant uses it, the user deletes it.
test('creates an API token, uses it for the customers and deletes it', async ({ page, browser }) => {
  const username = uniqueUsername('api');
  await page.goto('/admin/users');
  await page.getByLabel('Benutzername').fill(username);
  await page.getByLabel('Name', { exact: true }).fill('Api Nutzerin');
  await page.locator('#new-password').fill(LOGIN);
  await page.locator('#new-confirm').fill(LOGIN);
  await page.getByRole('button', { name: 'Anlegen' }).click();
  await expect(page.getByRole('status')).toContainText(username);

  const context = await browser.newContext({ storageState: ANONYMOUS });
  const user = await context.newPage();
  await user.goto('/login');
  await user.getByLabel('Benutzername').fill(username);
  await user.getByLabel('Passwort').fill(LOGIN);
  await user.getByRole('button', { name: 'Anmelden', exact: true }).click();

  await user.goto('/account');
  await expect(user.getByText('Noch kein API-Token.')).toBeVisible();
  await user.locator('#token-name').fill('Claude Code');
  await user.getByRole('button', { name: 'Token anlegen' }).click();
  await expect(user.getByRole('status')).toHaveText('API-Token „Claude Code“ angelegt.');
  const token = await user.locator('#new-token').inputValue();
  expect(token).toMatch(/^im_[A-Za-z0-9_-]{43}$/);
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await user.getByRole('button', { name: 'Kopieren' }).click();
  await expect(user.locator('[data-copy="new-token"]')).toHaveText('Kopiert');
  expect(await user.evaluate(() => navigator.clipboard.readText())).toBe(token);
  await expect(user.locator('#token-list')).toContainText('Lesen und schreiben');

  const apiContext = await browser.newContext({ storageState: ANONYMOUS });
  const api = apiContext.request;
  const auth = { Authorization: `Bearer ${token}` };
  expect((await api.get('/api/v1/customers')).status()).toBe(401);
  const created = await api.post('/api/v1/customers', {
    headers: auth,
    data: { name: `Sprachschule ${username}`, street: 'Hafenstraße 1', postcode: '12345', city: 'Musterstadt',
      countryCode: 'DE', format: 'ZUGFERD_EN16931' },
  });
  expect(created.status()).toBe(201);
  const customer = await created.json();
  const list = await api.get('/api/v1/customers', { headers: auth });
  expect(list.status()).toBe(200);
  expect((await list.json()).map((c: { id: number }) => c.id)).toContain(customer.id);

  const templates = await api.post(`/api/v1/customers/${customer.id}/templates`, { headers: auth });
  expect(templates.status()).toBe(201);
  const form = await (await api.get(`/api/v1/customers/${customer.id}/templates/${(await templates.json()).id}`,
    { headers: auth })).json();
  const saved = await api.put(`/api/v1/customers/${customer.id}/templates/${(await templates.json()).id}`,
    { headers: auth, data: { ...form, name: 'Tutorierung', purchaseOrder: '4500000001' } });
  expect(saved.status()).toBe(200);
  expect((await saved.json()).purchaseOrder).toBe('4500000001');
  expect((await api.get('/api/v1/mail-texts', { headers: auth })).status()).toBe(200);
  expect((await api.get('/api/v1/admin/tax-notes', { headers: auth })).status()).toBe(403);
  const mcp = await api.post('/mcp', {
    headers: { ...auth, Accept: 'application/json, text/event-stream' },
    data: { jsonrpc: '2.0', id: 1, method: 'tools/call', params: { name: 'get_customer', arguments: { id: customer.id } } },
  });
  expect(mcp.status()).toBe(200);
  expect((await mcp.json()).result.content[0].text).toContain(`Sprachschule ${username}`);

  await user.goto('/customers');
  await expect(user.getByText(`Sprachschule ${username}`)).toBeVisible();

  await user.goto('/account');
  await expect(user.locator('#new-token')).toHaveCount(0);
  await expect(user.locator('#token-list')).toContainText('zuletzt');
  await user.locator('#token-list').getByRole('button', { name: 'Löschen' }).click();
  await expect(user.getByRole('status')).toHaveText('API-Token gelöscht.');
  expect((await api.get('/api/v1/customers', { headers: auth })).status()).toBe(401);
  await apiContext.close();
  await context.close();
});
