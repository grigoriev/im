// Draws the Lucide icons of the page. The app loads no script from a CDN.
document.addEventListener('DOMContentLoaded', () => {
  if (globalThis.lucide) globalThis.lucide.createIcons();
});
