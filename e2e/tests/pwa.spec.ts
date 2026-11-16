import { randomUUID } from 'node:crypto';
import { expect, test } from './fixtures';
import { ANONYMOUS } from './accounts';
import { newCustomer } from './seller';

type Icon = { src: string; sizes: string; type: string; purpose: string };

test.describe('without a session', () => {
  test.use({ storageState: ANONYMOUS });

  // The phone fetches the manifest and the icons without the cookies of the app.
  test('the installed app finds its manifest and icons', async ({ page }) => {
    const response = await page.request.get('/manifest.webmanifest');
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('application/manifest+json');
    const manifest = await response.json();
    expect(manifest).toMatchObject({ name: 'Rechnungen', short_name: 'Rechnungen', start_url: '/', scope: '/',
      display: 'standalone', lang: 'de' });
    expect(manifest.icons.map((icon: Icon) => icon.purpose)).toEqual(expect.arrayContaining(['any', 'maskable']));
    expect(manifest.shortcuts.map((shortcut: { name: string }) => shortcut.name))
      .toEqual(['Neue Rechnung', 'Neue Ausgabe']);
    for (const icon of manifest.icons as Icon[]) {
      const file = await page.request.get(icon.src);
      expect(file.status(), icon.src).toBe(200);
      expect(file.headers()['content-type'], icon.src).toContain(icon.type);
    }

    await page.goto('/login');
    const links = {
      manifest: 'link[rel="manifest"]',
      favicon: 'link[rel="icon"][type="image/svg+xml"]',
      ico: 'link[rel="icon"][sizes="32x32"]',
      apple: 'link[rel="apple-touch-icon"]',
    };
    for (const [name, selector] of Object.entries(links)) {
      const href = await page.locator(selector).getAttribute('href');
      expect(href, name).toBeTruthy();
      const file = await page.request.get(href!);
      expect(file.status(), name).toBe(200);
    }
    await expect(page.locator('meta[name="apple-mobile-web-app-title"]')).toHaveAttribute('content', 'Rechnungen');
  });
});

test('the shortcuts of the installed app open a new invoice and a new expense', async ({ page }) => {
  await newCustomer(page, `Verknüpfung ${randomUUID().slice(0, 8)}`);
  await page.goto('/invoices?new');
  // The active element, not toBeFocused: that one also needs the window in focus, which a parallel run cannot promise.
  await expect.poll(() => page.evaluate(() => document.activeElement?.id)).toBe('new-invoice-customer');
  await page.goto('/expenses/new');
  await expect(page.locator('#expense-upload')).toBeVisible();
});

test('a computer shows no camera button', async ({ page }) => {
  await page.goto('/expenses/new');
  await expect(page.locator('#expense-upload')).toBeVisible();
  await expect(page.locator('#expense-camera')).toBeHidden();
});

test.describe('on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });

  test('the camera button takes the receipt with the camera, and the field keeps the files', async ({ page }) => {
    await page.goto('/expenses/new');
    const upload = page.locator('#expense-upload');
    const camera = page.locator('#expense-camera');
    await expect(camera).toBeVisible();
    await expect(upload).not.toHaveAttribute('capture');

    const chooser = page.waitForEvent('filechooser');
    await camera.tap();
    const files = await chooser;
    await expect(upload).toHaveAttribute('capture', 'environment');
    await files.setFiles({ name: 'Foto.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9]) });
    await expect(upload).not.toHaveAttribute('capture');
    expect(await upload.evaluate((input: HTMLInputElement) => input.files?.[0]?.name)).toBe('Foto.jpg');
  });
});
