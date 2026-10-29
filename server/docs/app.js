// HSO Technical Docs App Scripts
document.addEventListener('DOMContentLoaded', () => {
  // Theme Toggle
  const themeBtn = document.getElementById('theme-toggle-btn');
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const savedTheme = localStorage.getItem('hso_docs_theme') || (prefersDark ? 'dark' : 'light');
  
  document.documentElement.setAttribute('data-theme', savedTheme);
  updateThemeBtnText(savedTheme);

  if (themeBtn) {
    themeBtn.addEventListener('click', () => {
      const currentTheme = document.documentElement.getAttribute('data-theme');
      const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', newTheme);
      localStorage.setItem('hso_docs_theme', newTheme);
      updateThemeBtnText(newTheme);
    });
  }

  function updateThemeBtnText(theme) {
    if (themeBtn) {
      themeBtn.innerHTML = theme === 'dark' ? '☀️ Sáng' : '🌙 Tối';
    }
  }

  // Quick Search Filter on Current Page
  const searchInput = document.getElementById('doc-search-input');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const term = e.target.value.toLowerCase().trim();
      const searchTargets = document.querySelectorAll('.feature-card, tr, .callout, p, h2, h3');
      
      if (!term) {
        searchTargets.forEach(el => {
          if (el.tagName === 'TR') el.style.display = '';
          else el.style.opacity = '1';
        });
        return;
      }

      searchTargets.forEach(el => {
        const text = el.textContent.toLowerCase();
        if (el.classList.contains('feature-card')) {
          el.style.display = text.includes(term) ? 'block' : 'none';
        } else if (el.tagName === 'TR' && el.parentElement.tagName === 'TBODY') {
          el.style.display = text.includes(term) ? '' : 'none';
        }
      });
    });
  }
});
