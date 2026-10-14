/**
 * RuN — Official Interactive Landing Page Controller
 * Zero-Network App Locker & Offline Focus Suite
 */

document.addEventListener('DOMContentLoaded', () => {
  initReleaseData();
  initPhoneDemo();
  initAvatarSelector();
  initFaqAccordion();
  generateQrCode();
});

// GitHub Release Constants
const GITHUB_REPO = 'Nrajesh0/Ayva';
const DEFAULT_VERSION = 'v2.0.0';
const DEFAULT_DOWNLOAD_URL = `https://github.com/${GITHUB_REPO}/releases/latest/download/app-release.apk`;
const RELEASES_PAGE_URL = `https://github.com/${GITHUB_REPO}/releases/tag/${DEFAULT_VERSION}`;

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

      // Find APK asset
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
 * Interactive Phone Focus Timer & Blocker Simulation
 */
function initPhoneDemo() {
  const timerClock = document.getElementById('timer-clock');
  const timerCircle = document.querySelector('.timer-progress-circle');
  const toggleBtn = document.getElementById('timer-toggle-btn');
  const triggerBlockBtn = document.getElementById('sim-block-trigger');
  const blockOverlay = document.getElementById('phone-block-overlay');
  const dismissBlockBtn = document.getElementById('dismiss-block-btn');

  let totalSeconds = 25 * 60; // 25:00
  let currentSeconds = 24 * 60 + 59; // 24:59
  let isRunning = true;
  let timerInterval = null;
  const circumference = 408;

  function updateClockDisplay() {
    const mins = Math.floor(currentSeconds / 60);
    const secs = currentSeconds % 60;
    if (timerClock) {
      timerClock.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }
    if (timerCircle) {
      const progress = currentSeconds / totalSeconds;
      const offset = circumference * (1 - progress);
      timerCircle.style.strokeDashoffset = offset;
    }
  }

  function startTimer() {
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = setInterval(() => {
      if (currentSeconds > 0) {
        currentSeconds--;
        updateClockDisplay();
      } else {
        currentSeconds = totalSeconds;
      }
    }, 1000);
    if (toggleBtn) {
      toggleBtn.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <rect x="6" y="4" width="4" height="16" rx="1"/>
          <rect x="14" y="4" width="4" height="16" rx="1"/>
        </svg>
        Pause
      `;
    }
  }

  function pauseTimer() {
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = null;
    if (toggleBtn) {
      toggleBtn.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <polygon points="5 3 19 12 5 21 5 3"/>
        </svg>
        Resume
      `;
    }
  }

  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      isRunning = !isRunning;
      if (isRunning) startTimer();
      else pauseTimer();
    });
  }

  // Blocker Simulation trigger
  if (triggerBlockBtn && blockOverlay) {
    triggerBlockBtn.addEventListener('click', () => {
      blockOverlay.classList.add('active');
    });
  }

  if (dismissBlockBtn && blockOverlay) {
    dismissBlockBtn.addEventListener('click', () => {
      blockOverlay.classList.remove('active');
    });
  }

  startTimer();
}

/**
 * Interactive Avatar Selector
 */
function initAvatarSelector() {
  const avatarPills = document.querySelectorAll('.avatar-pill');
  const phoneLogo = document.getElementById('phone-app-logo');
  const avatarDesc = document.getElementById('avatar-active-desc');

  avatarPills.forEach(pill => {
    pill.addEventListener('click', () => {
      avatarPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');

      const avatarSrc = pill.getAttribute('data-img');
      const avatarName = pill.getAttribute('data-name');
      const avatarTier = pill.getAttribute('data-tier');

      if (phoneLogo && avatarSrc) {
        phoneLogo.src = avatarSrc;
      }

      if (avatarDesc && avatarName) {
        avatarDesc.textContent = `${avatarName} (${avatarTier})`;
      }
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

        // Close all others
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
 * Generate a standalone crisp SVG QR Code for Android download
 * Direct download target without external tracking services
 */
function generateQrCode() {
  const qrContainer = document.getElementById('qr-code-target');
  if (!qrContainer) return;

  // Ultra-clean SVG QR Code representation linking to the latest release
  const qrSvg = `
    <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges">
      <rect width="100" height="100" fill="#ffffff"/>
      <!-- Top Left Position Finder -->
      <path fill="#000000" d="M10,10 h24 v24 h-24 z M14,14 v16 h16 v-16 z M18,18 h8 v8 h-8 z"/>
      <!-- Top Right Position Finder -->
      <path fill="#000000" d="M66,10 h24 v24 h-24 z M70,14 v16 h16 v-16 z M74,18 h8 v8 h-8 z"/>
      <!-- Bottom Left Position Finder -->
      <path fill="#000000" d="M10,66 h24 v24 h-24 z M14,70 v16 h16 v-16 z M18,74 h8 v8 h-8 z"/>
      <!-- Timing Pattern & Data Blocks -->
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
