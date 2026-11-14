// Draws the Lucide icons of the page. The app loads no script from a CDN.
document.addEventListener('DOMContentLoaded', () => {
  if (globalThis.lucide) globalThis.lucide.createIcons();
});

// A form with data-once posts once, for example an e-mail to a customer: a second click while the first request runs
// does nothing, and the submit buttons show as busy.
document.addEventListener('submit', (event) => {
  const form = event.target;
  if (!(form instanceof HTMLFormElement) || form.dataset.once === undefined) return;
  if (form.dataset.sent) {
    event.preventDefault();
    return;
  }
  form.dataset.sent = 'true';
  form.querySelectorAll('button[type="submit"]').forEach((button) => button.setAttribute('aria-disabled', 'true'));
});

// A dialog opens from a button with data-dialog="<id>" and closes from a button with data-close, with Escape or with a
// click on the backdrop. A dialog the server renders with the attribute open shows as a modal right away, for
// example with the problems of its form.
document.addEventListener('click', (event) => {
  const opener = event.target.closest('[data-dialog]');
  if (opener) {
    document.getElementById(opener.dataset.dialog)?.showModal();
    return;
  }
  const closer = event.target.closest('[data-close]');
  if (closer) closer.closest('dialog')?.close();
});
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('dialog.modal[open]').forEach((dialog) => {
    dialog.close();
    dialog.showModal();
  });
});

// A select opens a styled list instead of the list of the browser. The select itself stays: it keeps its label,
// its value, the focus and the form. A click or Space, Enter or an arrow key opens the list; the arrows move, Enter or a
// click picks, Escape or Tab closes. A list with more than eight entries gets a search field.
function enhanceSelect(select) {
  if (select.multiple || select.dataset.native !== undefined || select.parentElement.classList.contains('listbox')) return;
  const box = document.createElement('div');
  box.className = 'listbox';
  select.parentNode.insertBefore(box, select);
  box.appendChild(select);

  const list = document.createElement('div');
  list.className = 'listbox-list';
  list.hidden = true;
  list.setAttribute('aria-hidden', 'true');
  const search = select.options.length > 8 ? document.createElement('input') : null;
  if (search) {
    search.className = 'listbox-search';
    search.type = 'search';
    search.placeholder = 'Suchen …';
    search.tabIndex = -1;
    list.appendChild(search);
  }
  const options = document.createElement('div');
  list.appendChild(options);
  box.appendChild(list);

  const items = () => [...options.querySelectorAll('.listbox-option:not([hidden])')];
  const render = () => {
    options.replaceChildren(...[...select.options].map((option, index) => {
      const item = document.createElement('div');
      item.className = 'listbox-option';
      item.dataset.index = index;
      item.textContent = option.textContent;
      item.setAttribute('aria-selected', String(option.selected));
      if (option.disabled) item.setAttribute('aria-disabled', 'true');
      return item;
    }));
  };
  const activate = (item) => {
    options.querySelectorAll('[data-active]').forEach((other) => other.removeAttribute('data-active'));
    if (!item) return;
    item.dataset.active = '';
    item.scrollIntoView({ block: 'nearest' });
  };
  const filter = () => {
    const term = search.value.trim().toLocaleLowerCase('de');
    options.querySelectorAll('.listbox-option').forEach((item) => {
      item.hidden = term !== '' && !item.textContent.toLocaleLowerCase('de').includes(term);
    });
    activate(items()[0]);
  };
  const open = (show) => {
    if (show) render();
    list.hidden = !show;
    if (!show) return;
    if (search) {
      search.value = '';
      filter();
      search.focus();
    }
    activate(options.querySelector('[aria-selected="true"]') ?? items()[0]);
  };
  const choose = (item) => {
    if (item && item.getAttribute('aria-disabled') !== 'true') {
      const index = Number(item.dataset.index);
      if (select.selectedIndex !== index) {
        select.selectedIndex = index;
        select.dispatchEvent(new Event('input', { bubbles: true }));
        select.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }
    open(false);
    select.focus();
  };
  const move = (step) => {
    const all = items();
    const current = all.indexOf(options.querySelector('[data-active]'));
    activate(all[Math.min(all.length - 1, Math.max(0, current + step))]);
  };
  const keys = (event) => {
    if (list.hidden) {
      if ([' ', 'Enter', 'ArrowDown', 'ArrowUp'].includes(event.key)) {
        event.preventDefault();
        open(true);
      }
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      move(event.key === 'ArrowDown' ? 1 : -1);
    } else if (event.key === 'Enter' || (event.key === ' ' && event.target === select)) {
      event.preventDefault();
      choose(options.querySelector('[data-active]'));
    } else if (event.key === 'Escape') {
      event.preventDefault();
      open(false);
      select.focus();
    } else if (event.key === 'Tab') {
      open(false);
    }
  };

  select.addEventListener('mousedown', (event) => {
    event.preventDefault();
    select.focus();
    open(list.hidden);
  });
  select.addEventListener('keydown', keys);
  search?.addEventListener('keydown', keys);
  search?.addEventListener('input', filter);
  options.addEventListener('mousedown', (event) => event.preventDefault());
  options.addEventListener('click', (event) => choose(event.target.closest('.listbox-option')));
  options.addEventListener('mousemove', (event) => {
    const item = event.target.closest('.listbox-option');
    if (item && item.dataset.active === undefined) activate(item);
  });
  document.addEventListener('mousedown', (event) => {
    if (!box.contains(event.target)) open(false);
  });
}

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('select.select').forEach(enhanceSelect);
});
