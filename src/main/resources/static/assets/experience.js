(() => {
  'use strict';

  const menus = [...document.querySelectorAll('.header-menu')];
  menus.forEach(menu => menu.addEventListener('toggle', () => {
    if (menu.open) menus.forEach(other => { if (other !== menu) other.open = false; });
  }));
  document.addEventListener('click', event => {
    menus.forEach(menu => { if (!menu.contains(event.target)) menu.open = false; });
  });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    menus.forEach(menu => {
      if (!menu.open) return;
      const hadFocus = menu.contains(document.activeElement);
      menu.open = false;
      if (hadFocus) menu.querySelector('summary').focus();
    });
  });

  const filters = document.querySelector('.catalog-filter-panel');
  if (filters && matchMedia('(max-width: 767px)').matches && !document.querySelector('.filter-chip')) {
    filters.open = false;
  }

  document.querySelectorAll('[data-character-count]').forEach(input => {
    const output = document.getElementById(input.dataset.characterCount);
    if (!output) return;
    const update = () => { output.textContent = `${input.value.trim().length} / ${input.maxLength} ký tự`; };
    input.addEventListener('input', update);
    update();
  });
})();
