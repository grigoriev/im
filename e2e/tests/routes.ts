// Every page of the app. The layout and visual tests run on each of them.
export const ROUTES = [
  { name: 'login', path: '/login', anonymous: true },
  { name: 'start', path: '/', anonymous: false },
  { name: 'users', path: '/admin/benutzer', anonymous: false },
  { name: 'account', path: '/konto', anonymous: false },
  { name: 'settings', path: '/einstellungen', anonymous: false },
  { name: 'tax-notes', path: '/admin/steuerhinweise', anonymous: false },
];

// Widths of the no-horizontal-scroll rule (docs/PLAN.md, section 10).
export const WIDTHS = [375, 768, 1024, 1440, 1920];
