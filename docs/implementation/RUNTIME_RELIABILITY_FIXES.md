# Runtime reliability follow-up

Implements the ten accepted findings from the [runtime audit](../audits/PERFORMANCE_BATTERY_NOTIFICATIONS_BUBBLE_2026-11-02.md). Automatic DND control is also removed. Included in version **2.0.36 / 136**; the verification below records the local checkpoints before the release version bump.

## Changes and user-visible effects

| Area | Change | Consequence |
|---|---|---|
| Drill progress (A01) | Complete the statistics preference transaction. | Future drill XP, counts, streaks and wager fields survive a profile reload. This does not automatically reconstruct results discarded by older builds. |
| Notifications (A02) | Task notifications have a tag containing the full task ID. Actions also have distinct URI identities. Cancellation retires only identifiable legacy task notifications. | Task reminders cannot replace or cancel focus/bubble service notifications, including IDs 1001/2001/4001. Large IDs remain distinct. |
| Snooze recovery (A04) | Restore the remaining local timer after service recreation; distinguish automatic expiry from manual Resume. Persist elapsed-time and boot information with a wall-clock fallback. | Normal dismissal still returns after ten minutes. Clock edits do not extend a same-boot snooze. Reboot/update retains the saved remaining interval where possible. |
| Disabled bubble (A05) | Disabling persists the off setting and cancels current/legacy resume and restart alarms. Stale starts stop immediately. Overlay-permission revocation also stops the service; app resume retries an enabled bubble after permission is granted again. | Turning the feature off means no scheduled return and no invisible bubble service. Enabling it again starts a fresh permitted service. |
| Temporary hiding (A06) | Fullscreen owners have independent hide tokens; all attachment, preview and recovery paths honor the same visibility guards. | Recovery checks and new alerts cannot undo a temporary hide or cut short a snooze. Returning from fullscreen does not cancel snooze. |
| Screen-off startup (A07) | Read actual interactive state before starting app monitoring. Stop polling on screen-off and resume on screen-on. | No foreground-app polling merely because the blocker was recreated while the screen was already off. Interactive blocking remains responsive. |
| Chat persistence (A08) | Serialize initial DB loading and writes; lock memory mutations; replay startup edits; apply UI deltas against the editor's observed snapshot. Badge increments are atomic. | Simultaneous alerts and UI updates preserve each other. The UI does not treat an uninitialized cache as an empty database. Explicit clearing/deleting still works. |
| Restore interaction (A08) | Reload the restored transcript before maintenance ends, invalidate the old editor generation and keep queued persistence suspended during maintenance. | An open chat and previously queued writes cannot replace restored history with their old cache. Restored history becomes the new source of truth. |
| Clock/timezone changes (A09) | Register a local-calendar reconciliation receiver and retryable worker. Read current habit completion state when scheduling. | Habit windows, daily summaries and drills follow current local time. Saved absolute task/work-session timestamps are not shifted. |
| Boot recovery (A10) | Isolate optional service starts and durably enqueue reminder restoration. Retry failed reminder families while allowing independent families to proceed. | A denied bubble start cannot prevent tasks/habits being rearmed. Recovery work is separate from clock-change work so those events cannot cancel each other. |
| Hidden chat (A11) | Use lifecycle-aware state collection and suspend projection/cleanup loops outside STARTED. Refresh immediately on return. | Less unnecessary hidden-screen work; session and transcript expiry catch up when the chat returns. Real focus/game deadlines are not paused. |
| Focus DND | Remove the permission request, helper, and start/stop/expiry/service-cleanup calls. | Focus starts without DND access and leaves the user's phone-wide DND choice unchanged. |

## Preserved behavior

- Landscape hiding for video remains intentional and unchanged. Audit A03 is deferred optional position hardening when that hiding setting is disabled; it is not part of these repairs.
- Dismiss/snooze retains its normal ten-minute return. Explicit Resume can return sooner; disabling the feature cancels the return.
- Snooze backup alarms are non-wakeup. Exact timing is requested only when Android permits it; otherwise the existing inexact fallback remains. A sleeping phone is not awakened just to display a bubble.
- Foreground-service sticky recovery remains; the redundant one-second restart alarm is removed. OEM force-stop and background-start restrictions still apply.
- Chat retains its existing 50-message limit and ten-minute viewed-message policy. Unviewed alerts and active questions retain their existing protection. Saved tasks and Notes are independent.
- Task planning/parsing, Notes encryption, media quality, backup formats and the conversation archive are unchanged. The restore change concerns chat-cache synchronization, not the archive format or cryptographic rules.
- Blocking continues through direct WindowManager overlays. No blocker Activity launch mechanism was introduced.
- The release remains one universal Android 12+ APK. No runtime dependency was added.

## Verification

Local verification completed:

- Full regression suite: **1,434 tests passed**, zero failures/errors/skips.
- After the final app-resume permission-recovery hook and its two additional test variants, **21 focused checks passed** (bubble recovery, DND behavior and the real chat UI/Room integration). The entire suite was not rerun after that small hook.
- Debug and optimized release builds both passed on the final source.
- Release packaging produced **one universal Android 12+ APK**, 62,833,496 bytes (59.92 MiB), target API 35. This local unsigned validation artifact retains version 2.0.35/135; no new version or publication was made.
- Packaged permissions contain neither `ACCESS_NOTIFICATION_POLICY` nor `INTERNET`.
- All **262 conversation/dictionary asset files** match the pre-size-review source hashes. No artwork/resource changes are part of this fix.
- `git diff --check` passed. Detailed machine-readable results are retained outside the repository at `/workspace/artifacts/runtime-fixes/verification.json`.

Build logs: `/workspace/artifacts/audit-fixes-final-validation.log` and `/workspace/artifacts/audit-fixes-final-followup.log`. The full-suite XML snapshot is in `/workspace/artifacts/runtime-fixes/full-test-results/`.

Regression checks cover progress reload, cross-feature notification posting/cancellation and action identity, normal/recreated snooze expiry, stale starts after disabling, independent fullscreen hides, early timer/alert deliveries, landscape hiding/restoration, permission revocation, screen-off service startup, rejected bubble startup during boot, and lifecycle suspension.

Chat checks use real Room databases and controlled executors to hold initial loading while edits arrive. They also cover concurrent alerts/badges, stale editors, clear followed by append, and restore invalidation under the maintenance gate. An actual ChatInterface integration check renders stored history, receives an alert during initialization, restores identical history, then sends a new message and verifies the persisted transcript.

The new projection UI test uses native rendering. New domain checks target Android 12, and bubble recovery also targets Android 14 foreground-service behavior; the existing suite retains Android 15/16 matrices. Redundant API 35 variants of the new domain tests triggered a Robolectric font-loader initialization failure before app code ran and were removed from that extra matrix. Local validation uses a temporary C1-only test-JVM option because the installed Oracle JDK's C2 compiler crashed while optimizing instrumented test bytecode. The local flags are `-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m`; they are outside the repository, do not affect Android/ART, and are not added to CI's Temurin configuration.

No physical-device battery percentage or OEM process-kill guarantee is claimed. The optional profiling candidates in the audit (large reminder ledgers, startup/backup coalescing, widget rendering) remain profiling work rather than blanket rewrites.
