import { randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { ANONYMOUS } from './accounts';
import { ROUTES, WIDTHS } from './routes';
import { ensureProfile, newCustomer } from './seller';

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

// No field shows a made-up example: the label says what goes in. Only the search of a long list prompts its typing.
for (const route of ROUTES) {
  test(`${route.name} shows no examples in its fields`, async ({ page, browser, adminState }) => {
    const context = route.anonymous ? await browser.newContext({ storageState: ANONYMOUS }) : null;
    const view = context ? await context.newPage() : page;
    await view.goto(route.path);
    const examples = await view.locator('input[placeholder]:not(.listbox-search), textarea[placeholder]')
      .evaluateAll((fields) => fields.map((field) => `${field.id}: ${field.getAttribute('placeholder')}`));
    expect(examples).toEqual([]);
    await context?.close();
  });
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

// Safari on an iPhone sizes a select of the system by its longest option and keeps the picker of the system there, so
// a long customer name pushed the field out of its card. Chromium and the emulated WebKit shrink the select anyway, so
// the test checks the cause: every label of a field may shrink as a flex item, and a select cuts its text.
test('a field shrinks with its card, however long its value', async ({ page }) => {
  await page.goto('/expenses/new');
  const labels = await page.locator('.floating-label').evaluateAll((all) => all.map((label) => getComputedStyle(label).minWidth));
  expect(labels.length).toBeGreaterThan(0);
  expect(new Set(labels)).toEqual(new Set(['0px']));
  const select = await page.locator('#expense-category').evaluate((element) => {
    const style = getComputedStyle(element);
    return [style.minWidth, style.maxWidth, style.textOverflow];
  });
  expect(select).toEqual(['0px', '100%', 'ellipsis']);
});

test('a utility still sets the minimum width of a field', async ({ page }) => {
  await ensureProfile(page);
  const customer = await newCustomer(page, `Breitenkunde ${randomUUID().slice(0, 8)}`);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/invoices');
  await page.getByLabel('Kunde der neuen Rechnung').selectOption({ label: customer });
  await page.getByRole('button', { name: 'Neue Rechnung' }).click();
  const tax = page.locator('label').filter({ has: page.locator('#line-0-category') });
  expect(await tax.evaluate((label) => getComputedStyle(label).minWidth), '@xl:min-w-64 of the tax field').toBe('256px');
});

// The place sets the size of a control (CLAUDE.md, Code Style): M (40px) in the head of a page, a form and a dialog,
// S (32px) in a toolbar, in the actions of a list row and in the head of a dialog. All visible controls of one place
// have that height, and no template picks a size by class. A closed dialog is hidden: only what the page shows counts.
const PLACES = [
  { selector: '.page-head .page-actions', height: 40 },
  { selector: '.form-actions', height: 40 },
  { selector: '.modal-foot', height: 40 },
  { selector: '.toolbar', height: 32 },
  { selector: '.row-actions', height: 32 },
  { selector: '.modal-head', height: 32 },
];
const SIZE_CLASSES = '.btn-xs, .btn-sm, .btn-md, .btn-lg, .btn-xl, .select-xs, .select-sm, .select-md, .select-lg, '
  + '.input-xs, .input-sm, .input-md, .input-lg, .badge-xs, .badge-sm, .badge-md, .badge-lg';

function controlSizeProblems(page: Page) {
  return page.evaluate(({ places, sizeClasses }) => {
    const problems: string[] = [];
    const visible = (el: Element) => el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
    const controls = 'button, a.btn, label.btn, summary.btn, select, input:not([type=hidden], [type=checkbox], [type=radio])';
    for (const place of places) {
      for (const container of Array.from(document.querySelectorAll(place.selector)).filter(visible)) {
        for (const control of Array.from(container.querySelectorAll<HTMLElement>(controls)).filter(visible)) {
          // The height of the layout: a dialog that still scales open does not count.
          const height = control.offsetHeight;
          if (Math.abs(height - place.height) > 1) {
            const name = control.textContent?.trim() || control.getAttribute('aria-label') || control.id;
            problems.push(`${place.selector}: ${control.tagName.toLowerCase()} "${name}" is ${height}px, not ${place.height}px`);
          }
        }
      }
    }
    for (const el of Array.from(document.querySelectorAll(sizeClasses))) {
      problems.push(`size class on ${el.tagName.toLowerCase()}.${el.className}`);
    }
    return problems;
  }, { places: PLACES, sizeClasses: SIZE_CLASSES });
}

for (const route of ROUTES) {
  for (const width of [375, 1440]) {
    test(`${route.name} sizes its controls by their place at ${width}px`, async ({ browser, adminState }) => {
      const context = await browser.newContext({
        viewport: { width, height: 900 },
        storageState: route.anonymous ? ANONYMOUS : adminState,
      });
      const page = await context.newPage();
      await page.goto(route.path);
      expect(await controlSizeProblems(page)).toEqual([]);
      if (!route.anonymous) await expect(page.locator('.page-head .page-title'), 'the head of the page').toHaveCount(1);
      await context.close();
    });
  }
}

test('an open dialog sizes its controls by their place', async ({ page }) => {
  await page.goto('/admin/users');
  await page.locator('#user-list').getByRole('button', { name: 'Passwort setzen' }).first().click();
  await expect(page.locator('dialog[open] .modal-foot')).toBeVisible();
  expect(await controlSizeProblems(page)).toEqual([]);
});
