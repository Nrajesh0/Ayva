/**
 * RuN — Official Interactive Landing Page Controller
 * True-to-App Interface Simulator & Real-time Release Manager
 */

document.addEventListener('DOMContentLoaded', () => {
  initReleaseData();
  initScreenSwitcher();
  initDashboardInteractions();
  initBlockerCountdown();
  initAvatarSelector();
  initFaqAccordion();
  generateQrCode();
});

// GitHub Repo Configuration
const GITHUB_REPO = 'Nrajesh0/Ayva';
const DEFAULT_VERSION = 'v2.0.0';
const DEFAULT_DOWNLOAD_URL = `https://github.com/${GITHUB_REPO}/releases/latest/download/app-release.apk`;

/**
 * Fetch latest release from GitHub API
 */
async function initReleaseData() {
  const versionTags = document.querySelectorAll('.dynamic-version');
  const downloadBtns = document.querySelectorAll('.dynamic-download-link');
  const releaseDateEl = document.getElementById('release-date');
  const apkSizeEl = document.getElementById('apk-size');

  try {
    const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`);
    if (res.ok) {
      const data = await res.json();
      const tagName = data.tag_name || DEFAULT_VERSION;
      versionTags.forEach(el => el.textContent = tagName);

      const apkAsset = data.assets && data.assets.find(a => a.name.endsWith('.apk'));
      const downloadUrl = apkAsset ? apkAsset.browser_download_url : `https://github.com/${GITHUB_REPO}/releases/download/${tagName}/app-release.apk`;

      downloadBtns.forEach(btn => {
        btn.href = downloadUrl;
      });

      if (apkAsset && apkSizeEl) {
        const sizeMb = (apkAsset.size / (1024 * 1024)).toFixed(1);
        apkSizeEl.textContent = `${sizeMb} MB`;
      }

      if (releaseDateEl && data.published_at) {
        const date = new Date(data.published_at);
        releaseDateEl.textContent = date.toLocaleDateString(undefined, {
          year: 'numeric',
          month: 'short',
          day: 'numeric'
        });
      }
    } else {
      fallbackReleaseData(versionTags, downloadBtns);
    }
  } catch (err) {
    fallbackReleaseData(versionTags, downloadBtns);
  }
}

function fallbackReleaseData(versionTags, downloadBtns) {
  versionTags.forEach(el => el.textContent = DEFAULT_VERSION);
  downloadBtns.forEach(btn => btn.href = DEFAULT_DOWNLOAD_URL);
}

/**
 * Real App Screen Switcher (Syncs both the top switcher and phone floating dock)
 */
function initScreenSwitcher() {
  const switcherBtns = document.querySelectorAll('.screen-switch-btn');
  const dockTabs = document.querySelectorAll('.dock-tab-btn');
  const screenViews = document.querySelectorAll('.real-screen-view');

  window.activateScreen = function(targetScreenId) {
    // Hide all screens
    screenViews.forEach(view => {
      view.classList.remove('active');
      if (view.id === targetScreenId) {
        view.classList.add('active');
      }
    });

    // Update top switcher bar
    switcherBtns.forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-screen') === targetScreenId);
    });

    // Update phone floating dock
    dockTabs.forEach(tab => {
      tab.classList.toggle('active', tab.getAttribute('data-screen') === targetScreenId);
    });
  };

  switcherBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const screenId = btn.getAttribute('data-screen');
      if (screenId) window.activateScreen(screenId);
    });
  });

  dockTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const screenId = tab.getAttribute('data-screen');
      if (screenId) window.activateScreen(screenId);
    });
  });
}

/**
 * Dashboard Screen Interactive Toggles & Deep Work Dial
 */
function initDashboardInteractions() {
  // App restriction switch toggles
  const switches = document.querySelectorAll('.material-switch');
  switches.forEach(sw => {
    sw.addEventListener('click', (e) => {
      e.stopPropagation();
      sw.classList.toggle('checked');
    });
  });

  // Deep work start/pause toggle
  const deepWorkBtn = document.getElementById('deep-work-btn');
  const deepWorkTime = document.getElementById('deep-work-time');
  let isWorkActive = false;
  let workSeconds = 25 * 60;
  let workInterval = null;

  if (deepWorkBtn && deepWorkTime) {
    deepWorkBtn.addEventListener('click', () => {
      isWorkActive = !isWorkActive;
      if (isWorkActive) {
        deepWorkBtn.textContent = 'Pause';
        deepWorkBtn.style.background = '#f43f5e';
        deepWorkBtn.style.color = '#fff';
        workInterval = setInterval(() => {
          if (workSeconds > 0) {
            workSeconds--;
            const m = Math.floor(workSeconds / 60);
            const s = workSeconds % 60;
            deepWorkTime.textContent = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
          }
        }, 1000);
      } else {
        deepWorkBtn.textContent = 'Start';
        deepWorkBtn.style.background = 'var(--accent-cyan)';
        deepWorkBtn.style.color = '#030508';
        if (workInterval) clearInterval(workInterval);
      }
    });
  }

  // Interactive habit checkboxes in notes & habits screens
  const checkBoxes = document.querySelectorAll('.note-check-box');
  checkBoxes.forEach(cb => {
    cb.addEventListener('click', (e) => {
      e.stopPropagation();
      cb.classList.toggle('checked');
      cb.textContent = cb.classList.contains('checked') ? '✓' : '';
    });
  });
}

/**
 * Mindful Pause Blocker Screen Countdown (Real BlockOverlayManager logic)
 */
function initBlockerCountdown() {
  const clockEl = document.getElementById('block-countdown');
  const exitBtn = document.getElementById('block-exit-home-btn');
  const progressFill = document.querySelector('.block-progress-fill');

  let remaining = 15;
  let blockInterval = null;

  function runCountdown() {
    if (blockInterval) clearInterval(blockInterval);
    remaining = 15;
    if (clockEl) clockEl.textContent = '00:15';
    if (progressFill) progressFill.style.width = '100%';

    blockInterval = setInterval(() => {
      if (remaining > 0) {
        remaining--;
        const pad = remaining < 10 ? `0${remaining}` : `${remaining}`;
        if (clockEl) clockEl.textContent = `00:${pad}`;
        if (progressFill) progressFill.style.width = `${(remaining / 15) * 100}%`;
      } else {
        clearInterval(blockInterval);
        if (clockEl) clockEl.textContent = '00:00';
        if (progressFill) progressFill.style.width = '0%';
      }
    }, 1000);
  }

  // Restart countdown when user navigates to the blocker screen
  const blockerTabBtn = document.querySelector('[data-screen="screen-blocker"]');
  if (blockerTabBtn) {
    blockerTabBtn.addEventListener('click', runCountdown);
  }

  // "Exit to Home" takes the user back to the Focus Dashboard
  if (exitBtn) {
    exitBtn.addEventListener('click', () => {
      if (window.activateScreen) {
        window.activateScreen('screen-dashboard');
      }
    });
  }

  runCountdown();
}

/**
 * Avatar Selector: Updates Phone Top Bar Ring & Displays Tier Info
 */
function initAvatarSelector() {
  const avatarCards = document.querySelectorAll('.avatar-thumb-card');
  const phoneAvatarImg = document.getElementById('phone-avatar-img');
  const avatarNameBadge = document.getElementById('active-avatar-name');
  const avatarTierBadge = document.getElementById('active-avatar-tier');

  avatarCards.forEach(card => {
    card.addEventListener('click', () => {
      avatarCards.forEach(c => c.classList.remove('active'));
      card.classList.add('active');

      const imgPath = card.getAttribute('data-img');
      const name = card.getAttribute('data-name');
      const tier = card.getAttribute('data-tier');

      if (phoneAvatarImg && imgPath) {
        phoneAvatarImg.src = imgPath;
        phoneAvatarImg.parentElement.style.animation = 'avatarPulse 0.4s ease';
        setTimeout(() => {
          phoneAvatarImg.parentElement.style.animation = '';
        }, 400);
      }

      if (avatarNameBadge && name) avatarNameBadge.textContent = name;
      if (avatarTierBadge && tier) avatarTierBadge.textContent = tier;
    });
  });
}

/**
 * FAQ Accordion
 */
function initFaqAccordion() {
  const faqItems = document.querySelectorAll('.faq-item');

  faqItems.forEach(item => {
    const trigger = item.querySelector('.faq-trigger');
    const content = item.querySelector('.faq-content');

    if (trigger && content) {
      trigger.addEventListener('click', () => {
        const isActive = item.classList.contains('active');

        faqItems.forEach(other => {
          other.classList.remove('active');
          const otherContent = other.querySelector('.faq-content');
          if (otherContent) otherContent.style.maxHeight = null;
        });

        if (!isActive) {
          item.classList.add('active');
          content.style.maxHeight = content.scrollHeight + 'px';
        }
      });
    }
  });
}

/**
 * Crisp Standalone SVG QR Code
 */
function generateQrCode() {
  const qrContainer = document.getElementById('qr-code-target');
  if (!qrContainer) return;

  const qrSvg = `
    <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges">
      <rect width="100" height="100" fill="#ffffff"/>
      <path fill="#000000" d="M10,10 h24 v24 h-24 z M14,14 v16 h16 v-16 z M18,18 h8 v8 h-8 z"/>
      <path fill="#000000" d="M66,10 h24 v24 h-24 z M70,14 v16 h16 v-16 z M74,18 h8 v8 h-8 z"/>
      <path fill="#000000" d="M10,66 h24 v24 h-24 z M14,70 v16 h16 v-16 z M18,74 h8 v8 h-8 z"/>
      <path fill="#000000" d="
        M38,10 h4 v4 h-4 z M46,10 h4 v4 h-4 z M54,10 h4 v4 h-4 z
        M38,18 h8 v4 h-8 z M50,18 h4 v4 h-4 z M58,18 h4 v4 h-4 z
        M38,26 h4 v4 h-4 z M46,26 h8 v4 h-8 z
        M10,38 h4 v4 h-4 z M18,38 h8 v4 h-8 z M30,38 h4 v4 h-4 z M38,38 h12 v4 h-12 z M54,38 h4 v4 h-4 z M62,38 h8 v4 h-8 z M74,38 h4 v4 h-4 z M82,38 h8 v4 h-8 z
        M14,46 h4 v4 h-4 z M22,46 h4 v4 h-4 z M34,46 h8 v4 h-8 z M46,46 h4 v4 h-4 z M54,46 h8 v4 h-8 z M66,46 h4 v4 h-4 z M74,46 h4 v4 h-4 z M86,46 h4 v4 h-4 z
        M10,54 h8 v4 h-8 z M22,54 h4 v4 h-4 z M30,54 h8 v4 h-8 z M42,54 h8 v4 h-8 z M54,54 h4 v4 h-4 z M62,54 h12 v4 h-12 z M78,54 h4 v4 h-4 z M86,54 h4 v4 h-4 z
        M38,62 h4 v4 h-4 z M46,62 h12 v4 h-12 z M62,62 h4 v4 h-4 z M70,62 h8 v4 h-8 z M82,62 h4 v4 h-4 z
        M38,70 h8 v4 h-8 z M50,70 h4 v4 h-4 z M58,70 h8 v4 h-8 z M70,70 h4 v4 h-4 z M78,70 h8 v4 h-8 z
        M38,78 h4 v4 h-4 z M46,78 h4 v4 h-4 z M54,78 h12 v4 h-12 z M70,78 h4 v4 h-4 z M82,78 h8 v4 h-8 z
        M38,86 h12 v4 h-12 z M54,86 h4 v4 h-4 z M62,86 h8 v4 h-8 z M74,86 h12 v4 h-12 z
      "/>
    </svg>
  `;
  qrContainer.innerHTML = qrSvg;
}
