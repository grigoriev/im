// Every page of the app. The layout and visual tests run on each of them.
export const ROUTES = [
  { name: 'login', path: '/login', anonymous: true },
  { name: 'start', path: '/', anonymous: false },
  { name: 'users', path: '/admin/users', anonymous: false },
  { name: 'account', path: '/account', anonymous: false },
  { name: 'invoices', path: '/invoices', anonymous: false },
  { name: 'customers', path: '/customers', anonymous: false },
  // A past year without payments: the page of this year changes with the date.
  { name: 'reports', path: '/reports?year=2020', anonymous: false },
  { name: 'customer-new', path: '/customers/new', anonymous: false },
  { name: 'documents', path: '/documents', anonymous: false },
  { name: 'document-new', path: '/documents/new', anonymous: false },
  { name: 'settings', path: '/settings', anonymous: false },
  { name: 'tax-notes', path: '/admin/tax-notes', anonymous: false },
  { name: 'numbers', path: '/admin/numbers', anonymous: false },
  { name: 'mail', path: '/admin/mail', anonymous: false },
  { name: 'baseline', path: '/admin/baselines', anonymous: false },
  { name: 'backup', path: '/admin/backup', anonymous: false },
  { name: 'countries', path: '/admin/countries', anonymous: false },
  // The entries of the log depend on the run: visual-data.spec.ts takes a screenshot of one entry instead.
  { name: 'audit', path: '/admin/audit', anonymous: false, screenshot: false },
];

// Widths of the no-horizontal-scroll rule (docs/PLAN.md, section 10).
export const WIDTHS = [375, 768, 1024, 1440, 1920];
