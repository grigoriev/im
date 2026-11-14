// Draws the Lucide icons of the page. The app loads no script from a CDN.
document.addEventListener('DOMContentLoaded', () => {
  if (globalThis.lucide) globalThis.lucide.createIcons();
});

// A form with data-once posts once, for example an e-mail to a customer: a second click while the first request runs
// does nothing, and the submit buttons show as busy.
document.addEventListener('submit', (event) => {
  const form = event.target;
  if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-once')) return;
  if (form.dataset.sent) {
    event.preventDefault();
    return;
  }
  form.dataset.sent = 'true';
  form.querySelectorAll('button[type="submit"]').forEach((button) => button.setAttribute('aria-disabled', 'true'));
});
