(function () {
  'use strict';

  /* Появление блоков при прокрутке */
  var reveals = Array.prototype.slice.call(document.querySelectorAll('.reveal'));
  if ('IntersectionObserver' in window && reveals.length) {
    document.body.classList.add('reveal-ready');
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -30px 0px' });
    reveals.forEach(function (el) { observer.observe(el); });
  }

  /* Интерактивный макет приложения */
  var demo = document.getElementById('demo');
  if (!demo) return;

  var page = document.getElementById('demoPage');
  var nameEl = document.getElementById('demoName');
  var hostEl = document.getElementById('demoHost');
  var emojiEl = document.getElementById('demoEmoji');
  var dishEl = document.getElementById('demoDish');
  var catalog = document.getElementById('demoCatalog');
  var bar = document.getElementById('demoBar');
  var tabs = Array.prototype.slice.call(demo.querySelectorAll('.demo-tab'));

  function activate(button) {
    tabs.forEach(function (tab) { tab.classList.toggle('is-active', tab === button); });
    if (nameEl) nameEl.textContent = button.dataset.name || '';
    if (hostEl) hostEl.textContent = button.dataset.host || '';
    if (emojiEl) emojiEl.textContent = button.dataset.emoji || '🍟';
    if (dishEl) dishEl.textContent = button.dataset.dish || 'Меню магазина';
    if (page) page.style.setProperty('--demo-accent', button.dataset.color || '#D62300');
    hideCatalog();
  }

  function showCatalog() {
    if (catalog) catalog.hidden = false;
    var close = document.getElementById('demoCatalogClose');
    if (close) close.focus();
  }

  function hideCatalog() {
    if (catalog) catalog.hidden = true;
  }

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () { activate(tab); });
  });

  var add = document.getElementById('demoAdd');
  if (add) add.addEventListener('click', showCatalog);

  var close = document.getElementById('demoCatalogClose');
  if (close) close.addEventListener('click', function () {
    hideCatalog();
    if (add) add.focus();
  });

  demo.querySelectorAll('.demo-catalog-item').forEach(function (item) {
    item.addEventListener('click', function () {
      var target = tabs.find(function (tab) { return tab.dataset.host === item.dataset.host; });
      if (!target) {
        target = document.createElement('button');
        target.type = 'button';
        target.className = 'demo-tab';
        ['name', 'host', 'emoji', 'color', 'dish'].forEach(function (key) {
          target.dataset[key] = item.dataset[key];
        });
        target.innerHTML = '<span></span><small></small>';
        target.querySelector('span').textContent = item.dataset.emoji;
        target.querySelector('small').textContent = item.dataset.name;
        target.addEventListener('click', function () { activate(target); });
        if (bar && add) bar.insertBefore(target, add);
        tabs.push(target);
      }
      activate(target);
      if (target.scrollIntoView) target.scrollIntoView({ inline: 'nearest', block: 'nearest' });
    });
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && catalog && !catalog.hidden) {
      hideCatalog();
      if (add) add.focus();
    }
  });
})();
