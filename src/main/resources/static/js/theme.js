// Light, dark or system theme. The choice stays in this browser only.
(() => {
  const root = document.documentElement;
  const key = 'im-theme';
  const read = () => {
    try { return localStorage.getItem(key) || 'system'; } catch { return 'system'; }
  };
  const apply = (mode) => {
    if (mode === 'system') delete root.dataset.theme;
    else root.dataset.theme = mode;
    document.querySelectorAll('[data-theme-set]').forEach((button) => {
      button.setAttribute('aria-pressed', String(button.dataset.themeSet === mode));
    });
  };
  apply(read());
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-theme-set]');
    if (!button) return;
    try { localStorage.setItem(key, button.dataset.themeSet); } catch { /* storage unavailable */ }
    apply(button.dataset.themeSet);
  });
})();
