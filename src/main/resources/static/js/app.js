// Draws the Lucide icons of the page. The app loads no script from a CDN.
document.addEventListener('DOMContentLoaded', () => {
  if (globalThis.lucide) globalThis.lucide.createIcons();
});

// A form with data-once posts once, for example an e-mail to a customer: a second click while the first request runs
// does nothing, and the submit buttons show as busy.
document.addEventListener('submit', (event) => {
  const form = event.target;
  if (!(form instanceof HTMLFormElement) || form.dataset.once === undefined) return;
  // A button that opens a new tab, such as a preview, sends nothing: it never blocks the form.
  if (event.submitter?.hasAttribute('formtarget')) return;
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
// click picks, Escape cancels, Tab picks and moves on. With more than eight entries, typed letters filter the list.
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
    search.readOnly = true;
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
  // The value the select had when the list opened, for Escape.
  let before = select.selectedIndex;
  // The active entry is also the value of the select, so a screen reader announces it as the arrows move.
  const activate = (item, follow = true) => {
    options.querySelectorAll('[data-active]').forEach((other) => delete other.dataset.active);
    if (!item) return;
    item.dataset.active = '';
    item.scrollIntoView({ block: 'nearest' });
    if (follow && !list.hidden && item.getAttribute('aria-disabled') !== 'true') select.selectedIndex = Number(item.dataset.index);
  };
  const filter = () => {
    const term = search.value.trim().toLocaleLowerCase('de');
    options.querySelectorAll('.listbox-option').forEach((item) => {
      item.hidden = term !== '' && !item.textContent.toLocaleLowerCase('de').includes(term);
    });
    activate(items()[0]);
  };
  const open = (show) => {
    if (show) {
      before = select.selectedIndex;
      render();
    }
    list.hidden = !show;
    select.setAttribute('aria-expanded', String(show));
    if (!show) return;
    if (search) {
      search.value = '';
      filter();
    }
    activate(options.querySelector('[aria-selected="true"]') ?? items()[0]);
  };
  const choose = (item) => {
    if (item && item.getAttribute('aria-disabled') !== 'true') select.selectedIndex = Number(item.dataset.index);
    else select.selectedIndex = before;
    if (select.selectedIndex !== before) {
      select.dispatchEvent(new Event('input', { bubbles: true }));
      select.dispatchEvent(new Event('change', { bubbles: true }));
    }
    open(false);
    select.focus();
  };
  const cancel = () => {
    select.selectedIndex = before;
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
    } else if (search && event.key === 'Backspace') {
      event.preventDefault();
      search.value = search.value.slice(0, -1);
      filter();
    } else if (search && event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
      // The focus stays on the select; typed letters filter the list.
      event.preventDefault();
      search.value += event.key;
      filter();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      cancel();
    } else if (event.key === 'Tab') {
      choose(options.querySelector('[data-active]'));
    }
  };

  select.addEventListener('mousedown', (event) => {
    event.preventDefault();
    select.focus();
    if (list.hidden) open(true);
    else cancel();
  });
  select.addEventListener('keydown', keys);
  search?.addEventListener('mousedown', (event) => {
    event.preventDefault();
    select.focus();
  });
  options.addEventListener('mousedown', (event) => event.preventDefault());
  options.addEventListener('click', (event) => choose(event.target.closest('.listbox-option')));
  options.addEventListener('mousemove', (event) => {
    const item = event.target.closest('.listbox-option');
    if (item && item.dataset.active === undefined) activate(item, false);
  });
  select.setAttribute('aria-expanded', 'false');
  document.addEventListener('mousedown', (event) => {
    if (!list.hidden && !box.contains(event.target)) cancel();
  });
}

// Phones and tablets keep the picker of the system: it brings its own search and fits the touch screen.
document.addEventListener('DOMContentLoaded', () => {
  if (!globalThis.matchMedia('(pointer: fine)').matches) return;
  document.querySelectorAll('select.select').forEach(enhanceSelect);
});

// A button with data-capture="<id>" opens the camera for the file field with that ID, and the photo becomes its file.
// Only a phone or tablet shows the button. The field itself still offers the photo library and the files: the camera
// mode ends once a photo is taken or the camera is closed. Android opens the camera only for a field that takes images
// alone, so the field drops its other types meanwhile.
document.addEventListener('click', (event) => {
  const button = event.target.closest('[data-capture]');
  const input = button && document.getElementById(button.dataset.capture);
  if (!input) return;
  // A second tap while the camera mode is on only opens the camera again: it must not take the images-only types for
  // the ones to restore.
  if (!input.hasAttribute('capture')) {
    const accept = input.getAttribute('accept');
    const done = () => {
      input.removeEventListener('change', done);
      input.removeEventListener('cancel', done);
      input.removeAttribute('capture');
      if (accept === null) input.removeAttribute('accept');
      else input.setAttribute('accept', accept);
    };
    input.addEventListener('change', done);
    input.addEventListener('cancel', done);
    input.setAttribute('accept', 'image/jpeg,image/png');
    input.setAttribute('capture', 'environment');
  }
  input.click();
});

// The shortcut "Neue Rechnung" of the installed app opens the invoices with the customer of a new one focused. A query,
// not a fragment: Chrome drops the focus when it scrolls to a fragment after the load.
document.addEventListener('DOMContentLoaded', () => {
  if (!new URLSearchParams(location.search).has('new')) return;
  document.querySelector('#new-invoice select')?.focus();
});
