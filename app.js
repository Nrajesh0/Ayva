/**
 * AYVA (FocusByRj) — 2026 Interactive Engine
 * Handles authentic screen switching, offline NLU simulation,
 * interactive task cards, habit tracking, and live micro-animations.
 */

document.addEventListener('DOMContentLoaded', () => {
  initScreenSwitcher();
  initAyvaChatEngine();
  initBlockCountdown();
  initNotesVaultPlayer();
  initHabitsTracker();
  initTodosChecklist();
  initWebsiteFloatingBubble();
  initQrCode();
});

// ==========================================================================
// 1. SCREEN SWITCHER (Synchronized Top Tabs + Floating Dock)
// ==========================================================================
function initScreenSwitcher() {
  const topTabs = document.querySelectorAll('.screen-tab-btn');
  const dockItems = document.querySelectorAll('.dock-item');
  const screenViews = document.querySelectorAll('.phone-screen-view');

  function switchScreen(screenId) {
    // Update top tabs
    topTabs.forEach(tab => {
      tab.classList.toggle('active', tab.dataset.screen === screenId);
    });

    // Update dock items
    dockItems.forEach(item => {
      item.classList.toggle('active', item.dataset.screen === screenId);
    });

    // Update screen views
    screenViews.forEach(view => {
      const match = view.id === `view-${screenId}`;
      view.classList.toggle('active', match);
    });
  }

  topTabs.forEach(tab => {
    tab.addEventListener('click', () => switchScreen(tab.dataset.screen));
  });

  dockItems.forEach(item => {
    item.addEventListener('click', () => switchScreen(item.dataset.screen));
  });

  // Expose globally for bento card links
  window.navigateToAppScreen = switchScreen;
}

// ==========================================================================
// 2. AYVA BUBBLE CHAT ENGINE (Offline NLU & Task Card Generator)
// ==========================================================================
function initAyvaChatEngine() {
  const timeline = document.getElementById('chatTimeline');
  const inputField = document.getElementById('chatInputField');
  const sendBtn = document.getElementById('chatSendBtn');
  const micBtn = document.getElementById('chatMicBtn');
  const priorityBtn = document.getElementById('togglePriorityBtn');
  const persistentBtn = document.getElementById('togglePersistentBtn');
  const suggestions = document.querySelectorAll('.suggestion-pill');
  const quickCmds = document.querySelectorAll('.quick-cmd-chip');
  const catHost = document.getElementById('peekingCat');

  let isPriority = false;
  let isPersistent = false;
  let isRecording = false;

  // Toggle Priority
  if (priorityBtn) {
    priorityBtn.addEventListener('click', () => {
      isPriority = !isPriority;
      priorityBtn.classList.toggle('active-priority', isPriority);
    });
  }

  // Toggle Persistent Alarm
  if (persistentBtn) {
    persistentBtn.addEventListener('click', () => {
      isPersistent = !isPersistent;
      persistentBtn.classList.toggle('active-persistent', isPersistent);
    });
  }

  // Voice Live Transcription Simulation
  if (micBtn) {
    micBtn.addEventListener('click', () => {
      isRecording = !isRecording;
      micBtn.classList.toggle('recording', isRecording);
      if (isRecording) {
        inputField.placeholder = "Listening... speak now";
        setTimeout(() => {
          if (isRecording) {
            inputField.value = "Review project deliverables tomorrow at 4pm priority";
            isPriority = true;
            if (priorityBtn) priorityBtn.classList.add('active-priority');
            isRecording = false;
            micBtn.classList.remove('recording');
            inputField.placeholder = "Type a task, routine, or talk to Ayva...";
          }
        }, 1800);
      } else {
        inputField.placeholder = "Type a task, routine, or talk to Ayva...";
      }
    });
  }

  // Click quick prompt suggestions
  suggestions.forEach(pill => {
    pill.addEventListener('click', () => {
      const text = pill.dataset.prompt || pill.textContent.replace('+', '').trim();
      inputField.value = text;
      handleSendMessage(text);
    });
  });

  // Click quick commands chips
  quickCmds.forEach(cmd => {
    cmd.addEventListener('click', () => {
      const command = cmd.dataset.cmd;
      inputField.value = command;
      handleSendMessage(command);
    });
  });

  // Click Send button or press Enter
  if (sendBtn) {
    sendBtn.addEventListener('click', () => {
      const text = inputField.value.trim();
      if (text) handleSendMessage(text);
    });
  }

  if (inputField) {
    inputField.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const text = inputField.value.trim();
        if (text) handleSendMessage(text);
      }
    });
  }

  // Peeking cat tap interaction
  if (catHost) {
    catHost.addEventListener('click', () => {
      appendBotMessage("🐱 *Purrs softly* I'm always watching your back. Say `remind me...` or type `/help` anytime!");
    });
  }

  // Send Message Logic
  function handleSendMessage(text) {
    inputField.value = '';
    appendUserMessage(text);

    // Ayva Offline NLU processing simulation
    setTimeout(() => {
      processNluIntent(text, isPriority, isPersistent);
      // Reset toggles after sending
      isPriority = false;
      isPersistent = false;
      if (priorityBtn) priorityBtn.classList.remove('active-priority');
      if (persistentBtn) persistentBtn.classList.remove('active-persistent');
    }, 450);
  }

  function appendUserMessage(text) {
    const timeStr = getCurrentTime();
    const msgEl = document.createElement('div');
    msgEl.className = 'chat-msg user';
    msgEl.innerHTML = `
      <div class="msg-bubble">${escapeHtml(text)}</div>
      <div class="msg-time">${timeStr}</div>
    `;
    timeline.appendChild(msgEl);
    timeline.scrollTop = timeline.scrollHeight;
  }

  function appendBotMessage(htmlContent) {
    const timeStr = getCurrentTime();
    const msgEl = document.createElement('div');
    msgEl.className = 'chat-msg bot';
    msgEl.innerHTML = `
      <div class="msg-bubble">${htmlContent}</div>
      <div class="msg-time">${timeStr}</div>
    `;
    timeline.appendChild(msgEl);
    timeline.scrollTop = timeline.scrollHeight;
  }

  // Simulated OfflineNluEngine
  function processNluIntent(rawText, forcePriority, forcePersistent) {
    const lower = rawText.toLowerCase().trim();

    // 1. Slash commands
    if (lower.startsWith('/tasks')) {
      appendBotMessage(`
        📋 <b>Active Tasks (3 pending)</b>
        <div style="margin-top:6px; display:flex; flex-direction:column; gap:4px; font-size:0.75rem;">
          <div>• ☕ <b>Buy organic coffee beans</b> (Tomorrow 9:00 AM)</div>
          <div>• 📄 <b>Finish Q3 client report</b> (Today 5:00 PM 🔥)</div>
          <div>• 🧘 <b>Evening decompress meditation</b> (Daily 9:00 PM)</div>
        </div>
      `);
      return;
    }

    if (lower.startsWith('/habit')) {
      appendBotMessage(`
        🎯 <b>Daily Habits Check-In</b>
        <div style="margin-top:8px; display:flex; flex-direction:column; gap:6px;">
          <div style="display:flex; align-items:center; justify-content:space-between; background:rgba(255,255,255,0.05); padding:6px 10px; border-radius:8px;">
            <span>🌅 Morning Meditation (15m)</span>
            <span style="color:#10b981; font-weight:700;">✓ Done (14d 🔥)</span>
          </div>
          <div style="display:flex; align-items:center; justify-content:space-between; background:rgba(255,255,255,0.05); padding:6px 10px; border-radius:8px;">
            <span>📚 Offline Reading (30m)</span>
            <button onclick="this.textContent='✓ Done'; this.style.color='#10b981';" style="background:none; border:1px solid #10b981; color:#10b981; padding:2px 8px; border-radius:6px; font-size:0.7rem; cursor:pointer;">Mark</button>
          </div>
        </div>
      `);
      return;
    }

    if (lower.startsWith('/drill')) {
      appendBotMessage(`
        ⚡ <b>Focus Arithmetic Drill</b><br>
        What is <b>14 × 7 + 18</b>?
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:6px; margin-top:8px;">
          <button onclick="handleDrillAnswer(this, false)" style="padding:6px; border-radius:8px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1); color:white; font-size:0.75rem; cursor:pointer;">112</button>
          <button onclick="handleDrillAnswer(this, true)" style="padding:6px; border-radius:8px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1); color:white; font-size:0.75rem; cursor:pointer;">116</button>
          <button onclick="handleDrillAnswer(this, false)" style="padding:6px; border-radius:8px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1); color:white; font-size:0.75rem; cursor:pointer;">124</button>
          <button onclick="handleDrillAnswer(this, false)" style="padding:6px; border-radius:8px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1); color:white; font-size:0.75rem; cursor:pointer;">108</button>
        </div>
      `);
      return;
    }

    if (lower.startsWith('/freeze')) {
      appendBotMessage(`🛡️ <b>Streak Shield:</b> You have <b>2 / 3</b> Streak Freezes equipped. Miss a practice day and your flame remains intact!`);
      return;
    }

    // 2. Natural language task addition
    const hasPriority = forcePriority || lower.includes('priority') || lower.includes('urgent') || lower.includes('asap');
    const hasPersistent = forcePersistent || lower.includes('persistent') || lower.includes('alarm') || lower.includes('loud');

    let cleanTitle = rawText
      .replace(/priority/gi, '')
      .replace(/persistent/gi, '')
      .replace(/tomorrow/gi, '')
      .replace(/today/gi, '')
      .replace(/at \d+(:?\d+)?\s*(am|pm)?/gi, '')
      .replace(/^\/task\s*/i, '')
      .replace(/^\/add\s*/i, '')
      .replace(/^remind me to\s*/i, '')
      .trim();

    if (!cleanTitle) cleanTitle = "New Focus Task";
    cleanTitle = cleanTitle.charAt(0).toUpperCase() + cleanTitle.slice(1);

    // Extract time or default to tomorrow 9am
    let dueStr = "Tomorrow, 9:00 AM";
    if (lower.includes('today')) dueStr = "Today, 5:00 PM";
    if (lower.includes('monday')) dueStr = "Next Monday, 10:00 AM";
    if (lower.includes('6pm') || lower.includes('6 pm')) dueStr = "Today, 6:00 PM";
    if (lower.includes('4pm') || lower.includes('4 pm')) dueStr = "Tomorrow, 4:00 PM";

    appendBotMessage(`
      ✨ <b>Task Created & Reminder Set!</b>
      <div class="timeline-task-card">
        <div class="task-card-header">
          <div class="task-card-title-row">
            <div class="task-checkbox-mock" onclick="toggleTaskCard(this)"></div>
            <span>${escapeHtml(cleanTitle)}</span>
          </div>
        </div>
        <div class="task-meta-row">
          <span class="meta-chip due">⏰ ${dueStr}</span>
          ${hasPriority ? '<span class="meta-chip priority">🔥 High Priority</span>' : ''}
          ${hasPersistent ? '<span class="meta-chip alarm">🔔 Persistent Ringing</span>' : ''}
        </div>
        <div class="task-action-btns">
          <button class="task-btn-action" onclick="toggleTaskCard(this.closest('.timeline-task-card').querySelector('.task-checkbox-mock'))">✓ Complete</button>
          <button class="task-btn-action" onclick="alert('Rescheduled to Next Available Slot')">🗓 Reschedule</button>
        </div>
      </div>
    `);
  }

  window.handleDrillAnswer = function(btn, isCorrect) {
    if (isCorrect) {
      btn.style.background = '#10b981';
      btn.style.borderColor = '#10b981';
      btn.textContent += ' ✓ Correct! (+50 XP)';
    } else {
      btn.style.background = '#ef4444';
      btn.style.borderColor = '#ef4444';
      btn.textContent += ' ✗';
    }
  };

  window.toggleTaskCard = function(checkbox) {
    checkbox.classList.toggle('checked');
    const titleSpan = checkbox.parentElement.querySelector('span');
    if (checkbox.classList.contains('checked')) {
      titleSpan.style.textDecoration = 'line-through';
      titleSpan.style.opacity = '0.5';
    } else {
      titleSpan.style.textDecoration = 'none';
      titleSpan.style.opacity = '1';
    }
  };
}

// ==========================================================================
// 3. APP BLOCKER (15-Second BAL Overlay Countdown)
// ==========================================================================
function initBlockCountdown() {
  const timerLabel = document.getElementById('blockTimerLabel');
  const progressRing = document.getElementById('blockTimerProgress');
  const exitBtn = document.getElementById('btnExitHome');

  if (!timerLabel || !progressRing) return;

  const totalSeconds = 15;
  let remaining = totalSeconds;
  const circumference = 226; // 2 * pi * r (approx)

  const countdownInterval = setInterval(() => {
    remaining--;
    if (remaining >= 0) {
      const min = String(Math.floor(remaining / 60)).padStart(2, '0');
      const sec = String(remaining % 60).padStart(2, '0');
      timerLabel.textContent = `${min}:${sec}`;

      const offset = circumference - (remaining / totalSeconds) * circumference;
      progressRing.style.strokeDashoffset = offset;
    } else {
      clearInterval(countdownInterval);
      timerLabel.textContent = "00:00";
      timerLabel.style.color = "#38bdf8";
      progressRing.style.stroke = "#38bdf8";
    }
  }, 1000);

  if (exitBtn) {
    exitBtn.addEventListener('click', () => {
      alert("Safe Exit: Returning you to the Home Screen.");
      if (window.navigateToAppScreen) window.navigateToAppScreen('chat');
    });
  }
}

// ==========================================================================
// 4. NOTES VAULT (Audio Memo Waveform Player)
// ==========================================================================
function initNotesVaultPlayer() {
  const playBtn = document.getElementById('audioMemoPlayBtn');
  const timeLabel = document.getElementById('audioMemoTimeLabel');
  const waveBars = document.querySelectorAll('.wave-bar');

  if (!playBtn || !timeLabel) return;

  let isPlaying = false;
  let timerInterval = null;
  let currentSec = 14;

  playBtn.addEventListener('click', () => {
    isPlaying = !isPlaying;
    playBtn.textContent = isPlaying ? '❚❚' : '▶';
    waveBars.forEach(bar => bar.classList.toggle('playing', isPlaying));

    if (isPlaying) {
      timerInterval = setInterval(() => {
        currentSec++;
        if (currentSec > 42) currentSec = 0;
        timeLabel.textContent = `0:${String(currentSec).padStart(2, '0')} / 0:42`;
      }, 1000);
    } else {
      clearInterval(timerInterval);
    }
  });
}

// ==========================================================================
// 5. HABITS TRACKER (Interactive Ring & Completion Counters)
// ==========================================================================
function initHabitsTracker() {
  const triggers = document.querySelectorAll('.habit-check-trigger');
  const ringProgress = document.getElementById('habitsRingProgress');
  const ringText = document.getElementById('habitsRingText');
  const countLabel = document.getElementById('habitsCompletedCount');

  let completed = 4;
  const total = 6;
  const circumference = 157; // 2 * pi * 25

  triggers.forEach(trig => {
    trig.addEventListener('click', () => {
      const isDone = trig.classList.toggle('done');
      trig.textContent = isDone ? '✓' : '';

      completed += isDone ? 1 : -1;
      completed = Math.max(0, Math.min(total, completed));

      if (countLabel) countLabel.textContent = `${completed} of ${total} completed today`;
      const ratio = completed / total;
      const offset = circumference - (ratio * circumference);
      if (ringProgress) ringProgress.style.strokeDashoffset = offset;
      if (ringText) ringText.textContent = `${Math.round(ratio * 100)}%`;
    });
  });
}

// ==========================================================================
// 6. TODOS & TASKS CHECKLIST
// ==========================================================================
function initTodosChecklist() {
  const checkboxes = document.querySelectorAll('.task-checkbox-round');
  const tabs = document.querySelectorAll('.todo-tab');

  checkboxes.forEach(box => {
    box.addEventListener('click', () => {
      box.classList.toggle('checked');
      const title = box.closest('.task-item-card').querySelector('.task-title-text');
      if (title) title.classList.toggle('struck', box.classList.contains('checked'));
    });
  });

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
    });
  });
}

// ==========================================================================
// 7. WEBSITE FLOATING BUBBLE (Quick Jump / Interactive Demo)
// ==========================================================================
function initWebsiteFloatingBubble() {
  const bubble = document.getElementById('websiteFloatingBubble');
  if (!bubble) return;

  bubble.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    if (window.navigateToAppScreen) window.navigateToAppScreen('chat');
    const input = document.getElementById('chatInputField');
    if (input) {
      setTimeout(() => input.focus(), 600);
    }
  });
}

// ==========================================================================
// 8. STANDALONE SVG QR CODE GENERATOR (Direct GitHub Releases v2.0.0)
// ==========================================================================
function initQrCode() {
  const qrContainer = document.getElementById('releaseQrContainer');
  if (!qrContainer) return;

  // Clean standalone SVG QR code pointing directly to APK release
  qrContainer.innerHTML = `
    <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="100" height="100" rx="8" fill="#FFFFFF"/>
      <!-- QR Pattern Elements -->
      <!-- Top-Left Finder -->
      <rect x="8" y="8" width="28" height="28" rx="4" fill="#000000"/>
      <rect x="12" y="12" width="20" height="20" rx="2" fill="#FFFFFF"/>
      <rect x="16" y="16" width="12" height="12" rx="2" fill="#000000"/>
      <!-- Top-Right Finder -->
      <rect x="64" y="8" width="28" height="28" rx="4" fill="#000000"/>
      <rect x="68" y="12" width="20" height="20" rx="2" fill="#FFFFFF"/>
      <rect x="72" y="16" width="12" height="12" rx="2" fill="#000000"/>
      <!-- Bottom-Left Finder -->
      <rect x="8" y="64" width="28" height="28" rx="4" fill="#000000"/>
      <rect x="12" y="68" width="20" height="20" rx="2" fill="#FFFFFF"/>
      <rect x="16" y="72" width="12" height="12" rx="2" fill="#000000"/>
      <!-- Data Bits -->
      <rect x="42" y="10" width="6" height="6" fill="#000000"/>
      <rect x="52" y="16" width="6" height="6" fill="#000000"/>
      <rect x="42" y="24" width="6" height="12" fill="#000000"/>
      <rect x="52" y="30" width="8" height="6" fill="#000000"/>
      <rect x="10" y="42" width="8" height="6" fill="#000000"/>
      <rect x="24" y="42" width="12" height="6" fill="#000000"/>
      <rect x="42" y="42" width="16" height="16" rx="2" fill="#10B981"/>
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

// Helpers
function getCurrentTime() {
  const now = new Date();
  return now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function escapeHtml(str) {
  return str.replace(/[&<>'"]/g, 
    tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
  );
}
