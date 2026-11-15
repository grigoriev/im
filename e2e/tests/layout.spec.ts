import { expect, test } from './fixtures';
import { ANONYMOUS } from './accounts';
import { ROUTES, WIDTHS } from './routes';

// No element scrolls sideways and the page never gets wider than the window.
for (const route of ROUTES) {
  for (const width of WIDTHS) {
    test(`${route.name} has no horizontal scroll at ${width}px`, async ({ browser, adminState }) => {
      const context = await browser.newContext({
        viewport: { width, height: 900 },
        storageState: route.anonymous ? ANONYMOUS : adminState,
      });
      const page = await context.newPage();
      await page.goto(route.path);
      const problems = await page.evaluate(() => {
        const found: string[] = [];
        const doc = document.scrollingElement!;
        if (doc.scrollWidth > window.innerWidth + 1) found.push(`page ${doc.scrollWidth} > ${window.innerWidth}`);
        for (const el of Array.from(document.querySelectorAll<HTMLElement>('body *'))) {
          const style = getComputedStyle(el);
          if (['auto', 'scroll'].includes(style.overflowX) && el.scrollWidth > el.clientWidth + 1) {
            found.push(`scrolls sideways: ${el.tagName.toLowerCase()}.${el.className}`);
          }
        }
        // A floating label stays on one line (app.css): it must fit, or the user reads half of it.
        for (const label of Array.from(document.querySelectorAll<HTMLElement>('.floating-label > span'))) {
          if (label.scrollWidth > label.clientWidth + 1) found.push(`label cut off: ${label.textContent?.trim()}`);
        }
        return found;
      });
      expect(problems).toEqual([]);
      await context.close();
    });
  }
}

// A label stays gray on focus and on a wrong value: only the frame changes color.
test('a field label keeps its gray on focus and on error', async ({ page }) => {
  await page.goto('/settings');
  const field = page.getByLabel('Zusatz');
  const label = page.locator('.floating-label', { has: field }).locator('> span');
  const colorOf = () => label.evaluate(span => getComputedStyle(span).color);
  const gray = await colorOf();
  await field.focus();
  expect(await colorOf()).toBe(gray);
  await field.evaluate(input => input.setAttribute('aria-invalid', 'true'));
  await field.blur();
  expect(await colorOf()).toBe(gray);
});
