// Every page of the app. The layout and visual tests run on each of them.
export const ROUTES = [
  { name: 'login', path: '/login', anonymous: true },
  { name: 'start', path: '/', anonymous: false },
  { name: 'users', path: '/admin/benutzer', anonymous: false },
  { name: 'account', path: '/konto', anonymous: false },
  { name: 'invoices', path: '/rechnungen', anonymous: false },
  { name: 'customers', path: '/kunden', anonymous: false },
  // A past year without payments: the page of this year changes with the date.
  { name: 'reports', path: '/berichte?jahr=2020', anonymous: false },
  { name: 'customer-new', path: '/kunden/neu', anonymous: false },
  { name: 'settings', path: '/einstellungen', anonymous: false },
  { name: 'tax-notes', path: '/admin/steuerhinweise', anonymous: false },
  { name: 'numbers', path: '/admin/nummernkreis', anonymous: false },
  { name: 'mail', path: '/admin/e-mail', anonymous: false },
  { name: 'baseline', path: '/admin/vorjahre', anonymous: false },
  { name: 'countries', path: '/admin/laender', anonymous: false },
  // The entries of the log depend on the run: visual-data.spec.ts takes a screenshot of one entry instead.
  { name: 'audit', path: '/admin/protokoll', anonymous: false, screenshot: false },
];

// Widths of the no-horizontal-scroll rule (docs/PLAN.md, section 10).
export const WIDTHS = [375, 768, 1024, 1440, 1920];
