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
        return found;
      });
      expect(problems).toEqual([]);
      await context.close();
    });
  }
}
