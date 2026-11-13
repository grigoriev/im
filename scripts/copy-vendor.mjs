// Copies the browser libraries and the web font into the static resources.
// The app serves them itself: no CDN at runtime (docs/PLAN.md, section 11).
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';

const target = 'target/classes/static';
const files = [
  ['node_modules/htmx.org/dist/htmx.min.js', 'vendor/htmx.min.js'],
  ['node_modules/alpinejs/dist/cdn.min.js', 'vendor/alpine.min.js'],
  ['node_modules/lucide/dist/umd/lucide.min.js', 'vendor/lucide.min.js'],
  ['node_modules/@fontsource-variable/jetbrains-mono/files/jetbrains-mono-latin-wght-normal.woff2', 'fonts/jetbrains-mono-latin.woff2'],
  ['node_modules/@fontsource-variable/jetbrains-mono/files/jetbrains-mono-latin-ext-wght-normal.woff2', 'fonts/jetbrains-mono-latin-ext.woff2'],
];

for (const [from, to] of files) {
  const destination = join(target, to);
  mkdirSync(dirname(destination), { recursive: true });
  copyFileSync(from, destination);
}
console.log(`Copied ${files.length} files to ${target}`);
