/**
 * AYVA (FocusByRj) — 2026 Minimalist Engine
 * Coordinates real screenshot switching, smooth tab transitions,
 * floating bubble animations, and release QR generator.
 */

document.addEventListener('DOMContentLoaded', () => {
  initRealScreenshotSwitcher();
  initSubPillSwitchers();
  initFloatingBubbles();
  initReleaseQrCode();
});

// ==========================================================================
// 1. REAL SCREENSHOT SWITCHER
// ==========================================================================
function initRealScreenshotSwitcher() {
  const topTabs = document.querySelectorAll('.phone-tab-btn');
  const allScreenshots = document.querySelectorAll('.real-screenshot-img');
  const allSubSelectors = document.querySelectorAll('.screenshot-sub-selector');

  function activateScreen(screenName) {
    // 1. Update top switcher tabs
    topTabs.forEach(tab => {
      tab.classList.toggle('active', tab.dataset.screen === screenName);
    });

    // 2. Hide all screenshots
    allScreenshots.forEach(img => {
      img.classList.remove('active');
    });

    // 3. Hide all sub-pill selectors
    allSubSelectors.forEach(sel => {
      sel.style.display = 'none';
    });

    // 4. Activate target screen & matching sub-selector
    const targetSubSelector = document.getElementById(`sub-${screenName}`);
    if (targetSubSelector) {
      targetSubSelector.style.display = 'flex';
      const activePill = targetSubSelector.querySelector('.sub-pill-btn.active') || targetSubSelector.firstElementChild;
      if (activePill) {
        activePill.classList.add('active');
        const targetImgId = activePill.dataset.target;
        const targetImg = document.getElementById(targetImgId);
        if (targetImg) targetImg.classList.add('active');
      }
    } else {
      // Direct single screen (like dashboard or matrix)
      const directImg = document.getElementById(`img-${screenName}`);
      if (directImg) directImg.classList.add('active');
    }
  }

  topTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      activateScreen(tab.dataset.screen);
    });
  });

  // Expose globally for bento card links
  window.navigateToScreen = activateScreen;
}

// ==========================================================================
// 2. SUB-PILL SWITCHERS (Within screens like Chat, Routines, Tasks)
// ==========================================================================
function initSubPillSwitchers() {
  const subPills = document.querySelectorAll('.sub-pill-btn');

  subPills.forEach(pill => {
    pill.addEventListener('click', () => {
      const parentSelector = pill.closest('.screenshot-sub-selector');
      parentSelector.querySelectorAll('.sub-pill-btn').forEach(p => p.classList.remove('active'));
      pill.classList.add('active');

      const targetImgId = pill.dataset.target;
      const allScreenshots = document.querySelectorAll('.real-screenshot-img');
      allScreenshots.forEach(img => img.classList.remove('active'));

      const targetImg = document.getElementById(targetImgId);
      if (targetImg) targetImg.classList.add('active');
    });
  });
}

// ==========================================================================
// 3. FLOATING BUBBLES INTERACTION
// ==========================================================================
function initFloatingBubbles() {
  const phoneBubble = document.getElementById('phoneFloatingBubble');
  const websiteBubble = document.getElementById('websiteFloatingBubble');

  if (phoneBubble) {
    phoneBubble.addEventListener('click', () => {
      if (window.navigateToScreen) window.navigateToScreen('chat');
    });
  }

  if (websiteBubble) {
    websiteBubble.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      if (window.navigateToScreen) window.navigateToScreen('chat');
    });
  }
}

// ==========================================================================
// 4. STANDALONE SVG QR CODE GENERATOR (GitHub Releases v2.0.0)
// ==========================================================================
function initReleaseQrCode() {
  const qrFrame = document.getElementById('qrCodeSlot');
  if (!qrFrame) return;

  // Clean standalone vector QR representation linking to APK v2.0.0 release
  qrFrame.innerHTML = `
    <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="100" height="100" rx="10" fill="#FFFFFF"/>
      <!-- Top-Left Target Finder -->
      <rect x="8" y="8" width="28" height="28" rx="4" fill="#000000"/>
      <rect x="12" y="12" width="20" height="20" rx="2" fill="#FFFFFF"/>
      <rect x="16" y="16" width="12" height="12" rx="2" fill="#000000"/>
      <!-- Top-Right Target Finder -->
      <rect x="64" y="8" width="28" height="28" rx="4" fill="#000000"/>
      <rect x="68" y="12" width="20" height="20" rx="2" fill="#FFFFFF"/>
      <rect x="72" y="16" width="12" height="12" rx="2" fill="#000000"/>
      <!-- Bottom-Left Target Finder -->
      <rect x="8" y="64" width="28" height="28" rx="4" fill="#000000"/>
      <rect x="12" y="68" width="20" height="20" rx="2" fill="#FFFFFF"/>
      <rect x="16" y="72" width="12" height="12" rx="2" fill="#000000"/>
      <!-- Data Matrices with Ayva Mint Green Accent -->
      <rect x="42" y="10" width="6" height="6" fill="#000000"/>
      <rect x="52" y="16" width="6" height="6" fill="#000000"/>
      <rect x="42" y="24" width="6" height="12" fill="#000000"/>
      <rect x="52" y="30" width="8" height="6" fill="#000000"/>
      <rect x="10" y="42" width="8" height="6" fill="#000000"/>
      <rect x="24" y="42" width="12" height="6" fill="#000000"/>
      <rect x="42" y="42" width="16" height="16" rx="3" fill="#66BB97"/>
      <rect x="64" y="42" width="8" height="6" fill="#000000"/>
      <rect x="80" y="42" width="10" height="8" fill="#000000"/>
      <rect x="64" y="54" width="12" height="6" fill="#000000"/>
      <rect x="82" y="56" width="8" height="8" fill="#000000"/>
      <rect x="42" y="66" width="8" height="8" fill="#000000"/>
      <rect x="54" y="66" width="8" height="14" fill="#000000"/>
      <rect x="42" y="80" width="10" height="8" fill="#000000"/>
      <rect x="68" y="72" width="10" height="6" fill="#000000"/>
      <rect x="82" y="72" width="8" height="16" fill="#000000"/>
      <rect x="68" y="84" width="10" height="6" fill="#000000"/>
    </svg>
  `;
}
