# RUN performance, battery, notifications and bubble audit

Audited revision: `0be9ca27e06ac4ada46fab22b6fd688acf4c0c88` — version **2.0.35 / 135**, 2 November 2026. This is an audit report; the findings below have not been fixed in this release.

The release is published at https://github.com/Nrajesh0/Ayva/releases/tag/v2.0.35. Both release and validation Actions succeeded. The release has one signed universal APK, `RUN-2.0.35.apk`, **62,823,244 bytes**, minimum Android 12 / API 31. The conversation archive and dictionary remain unchanged.

## Scope and evidence

A repository-wide scan covered all **391 production Kotlin files**, the manifest and scheduling configuration. Follow-up source review traced startup, services, notification identities and actions, alarm scheduling, bubble visibility and lifecycle, chat persistence, UI collectors, widgets, media decoding, animations, backups and maintenance coordination. This is a broad static audit with focused executable checks, rather than a claim that every possible device interaction has been exercised.

- Existing application suite: **1,401 tests passed** before publication; both GitHub Actions passed on the published revision.
- Android Lint completed successfully. It reported 6 error-level diagnostics, 322 warnings and 58 informational items; `abortOnError=false` means successful execution does not mean an empty report. Findings were manually triaged rather than treating every warning as a defect.
- Six isolated Android 12 / Robolectric diagnostic probes passed by **reproducing the current defects**. These are evidence probes, not regression tests asserting correct behavior. They live outside the app's source tree and are excluded from CI.
- No physical POCO/HyperOS battery measurement, OEM process-kill trace or hardware frame-time profile was available. There is no battery-percentage claim here. Device-specific killing remains distinct from the code defects below.

Diagnostic source and results are retained in `/workspace/artifacts/runtime-audit/`. Scan inventories and execution logs are in `/workspace/artifacts/performance-*-scan.txt`, `/workspace/artifacts/performance-battery-lint.log` and `/workspace/artifacts/runtime-audit-diagnostics-final.log`.

## Confirmed findings

| ID | Priority | Finding | Evidence |
|---|---|---|---|
| A01 | P1 | Completed drill statistics are not persisted | Executable reproduction |
| A02 | P1 | Task notifications collide with service notification IDs | Executable reproduction |
| A03 | P2 | Bubble can stay outside the screen after rotation/resizing | Executable reproduction |
| A04 | P2 | Recreated snoozed bubble loses its local expiry callback | Executable reproduction |
| A05 | P2 | Stale starts keep a disabled bubble service running | Executable reproduction |
| A06 | P2 | Bubble verification overrides an explicit fullscreen hide | Executable reproduction |
| A07 | P2 | A blocker restart while screen is off starts foreground-app polling | Source/control-flow confirmation |
| A08 | P2 | Chat initialization and concurrent mutations can overwrite transcript state | Source/interleaving confirmation |
| A09 | P2 | Daily local-time alarms are not reconciled on timezone change | Manifest/scheduler confirmation |
| A10 | P2 | A bubble-start exception can abort boot reminder restoration | Source/control-flow confirmation |
| A11 | P2 | Chat refresh work and collectors continue while the activity is stopped | Source/lifecycle confirmation |

P1 means fix first because of lost progress or cross-feature interference. P2 means a concrete defect under the stated trigger; impact and frequency depend on usage. No finding establishes loss of saved Notes or planning tasks.

### A01 — Completed drill results disappear on reload

Location: `app/src/main/java/com/focusbyrj/app/util/AptitudeManager.kt:529` (`recordDrillResult`).

`prefs?.edit()?.apply { ... }` invokes Kotlin's scope function. The returned editor never receives `SharedPreferences.Editor.apply()` or `commit()`. The live profile is updated separately, which makes the result look successful until a reload. XP, question/drill totals, streak/date, weekly XP and wager updates in that editor are discarded.

The diagnostic recorded 30 XP, observed 30 in the live profile, found persisted XP and drill totals still zero, then called `refreshProfile()` and observed XP return to zero. This can also make streak-dependent reminders use stale progress.

Fix direction: complete the editor transaction and add a reload/reinitialization regression test covering statistics, daily counts and streak state. Preserve the existing maintenance barrier.

### A02 — Task reminders overwrite foreground-service notifications

Locations: `service/TaskReminderReceiver.kt:235`, `util/TaskReminderHelper.kt:153,206,244,262`, `service/FocusBlockerService.kt:709`, `service/BubbleService.kt:1349` under `app/src/main/java/com/focusbyrj/app/`.

Tasks use an untagged notification ID equal to `taskId.toInt()`. Focus protection uses untagged ID **1001**; the bubble uses **2001** and routine alerts use **4001**. Those are valid task IDs. Posting, completing, ignoring or rescheduling those tasks can replace or cancel another feature's notification. The integer conversion also loses uniqueness for very large Long IDs.

The diagnostic posted a protection notification at 1001, delivered the actual task-reminder receiver for task 1001, and observed the protection notification replaced by the task notification. This confirms notification interference; it does not prove that the OS terminates the bubble as a consequence.

Fix direction: use a separate notification tag namespace with the full Long task ID; update every post, cancel, grouping and legacy-cleanup path together. Do not cancel a legacy task ID if it is currently a service notification. Keep existing alarm/PendingIntent identities stable unless deliberately migrated.

### A03 — Bubble position is not clamped after dimensions change

Locations: `service/BubbleService.kt:523` (`updateLandscapeVisibility`), `:649` (`FLAG_LAYOUT_NO_LIMITS`), `:1194` (`snapToEdge`).

Dragging clamps Y to the current screen, but configuration recovery only snaps X. With landscape hiding disabled, a bubble placed near the bottom of a tall portrait screen keeps that Y coordinate after landscape rotation. `FLAG_LAYOUT_NO_LIMITS` allows it to remain entirely below the visible display. Similar failures are possible when the usable display shrinks.

The diagnostic changed actual Robolectric display qualifiers from portrait to landscape with hiding disabled. The new screen was shorter, but the bubble's Y remained below its height.

Fix direction: normalize both coordinates against current window bounds/insets on dimension changes and reattachment, preserving the chosen edge and a reachable portion of the bubble. Test rotation in both directions, split-screen, display resizing and peek mode. Intentional landscape hiding should remain intact.

### A04 — Snooze expiry is not restored with the service

Locations: `service/BubbleService.kt:81,313,478`.

`snoozeBubble()` persists a deadline and posts a local handler callback. A recreated service reads the persisted snooze when deciding visibility but never rearms that callback for the remaining duration. The backup inexact, non-wakeup alarm is then the only timed recovery mechanism; it can be deferred. Starting a foreground service through an alarm also has platform restrictions and is not an unconditional recovery guarantee.

The diagnostic created a service with a future persisted snooze deadline and verified both a hidden bubble and no scheduled local expiry callback. Screen/unlock events or an explicit Resume can recover it; the evidence does not imply permanent disappearance in every case.

Fix direction: reconstruct a single local expiry callback from the saved deadline during service creation; reconcile expired snoozes immediately. Keep an appropriate fallback without adding frequent wakeup alarms.

### A05 — Disabled bubble can retain a foreground service

Locations: `service/BubbleService.kt:277,313,1272`, `ui/screens/BubbleSettingsScreen.kt:150`.

Settings normally stop the service when disabled, but the service itself does not reject stale restart/resume deliveries when `bubble_enabled=false`. `onCreate()` promotes it to foreground, registers listeners and starts task observation even though `addBubbleToWindowManager()` refuses to add the disabled view. Previously scheduled restart or resume PendingIntents can reach this state after the toggle is switched off.

The diagnostic directly reproduced a stale start with the preference disabled: no bubble view, but `isRunning=true` and a foreground notification present.

Fix direction: validate enablement/overlay permission at service entry, retire stale restart/snooze alarms when disabled and stop cleanly. Foreground-service startup obligations still need to be satisfied on supported Android versions. Permission revocation should not leave an invisible long-lived service.

### A06 — Verification defeats a deliberate temporary hide

Locations: `service/BubbleService.kt:225,243`, `service/FocusBlockerService.kt:555`, `ui/screens/FullscreenSolutionsView.kt:58`.

`HIDE_BUBBLE` removes the view without remembering why it was hidden. The blocker's periodic verification sees a missing view and reattaches it; screen-on and other restore paths can do the same. Fullscreen solutions use this hide command, so the launcher can return over content that explicitly hid it. `SHOW_BUBBLE` also clears snooze, mixing independent visibility reasons.

The diagnostic sent HIDE, verified removal, then sent the real verification action and observed reattachment without a matching SHOW.

Fix direction: model chat-open, permission dialog, fullscreen suppression, snooze, landscape and disabled states independently; derive visibility from all of them. Repair operations should repair attachment only when that derived state permits it. Keep the WindowManager overlay architecture.

### A07 — Screen-off restart still launches the 350 ms polling loop

Locations: `service/FocusBlockerService.kt:84,90,160,208`.

The normal screen-off broadcast cancels monitoring, which is good. However, `isScreenOn` starts as `true`, and `onCreate()` unconditionally starts the loop. If the service is created/restarted after the screen has already switched off, it missed the broadcast and polls UsageStats approximately three times per second whenever the process can execute until another screen transition corrects it.

Fix direction: initialize from the actual interactive/display state and gate loop creation on that state. Preserve prompt blocking while interactive. Coroutine delays do not themselves hold a wake lock; no claim is made that this loop alone prevents deep sleep.

### A08 — Chat snapshot mutations have overwrite races

Locations: `util/BubbleChatManager.kt:139,204,323,351,378`, `ui/screens/BubbleChatActivity.kt:442`.

Database writes use a serialized coroutine dispatcher, but the preceding `_messagesFlow.value` read/modify/write operations do not. Two background alerts, or an alert and a UI save, can read the same snapshot and publish competing replacements. Badge increment is also a separate read followed by a write. Initialization can finish a suspended database read after the UI has already supplied a welcome/new message, then replace that newer in-memory state with the older database snapshot. A later whole-list synchronization can persist the incomplete snapshot.

These are source-confirmed interleavings; no deterministic concurrent-write stress reproduction was claimed. The affected data is transcript/badge state, not the independent saved-task or Notes databases.

Fix direction: establish one serialized mutation boundary covering initialization, transcript updates, pruning/clear and persistence ordering. Let the UI await or observe initial loading rather than interpreting an uninitialized empty list as an empty database. Add concurrent alert, cold-start and clear-versus-append tests.

### A09 — Local-time reminders keep an old timezone calculation

Locations: `AndroidManifest.xml:262` (BootReceiver registration), `service/BootReceiver.kt:21`, `service/DailySummaryReceiver.kt:37`, `util/HabitAlarmScheduler.kt:132`.

Habit windows and daily summaries compute absolute RTC timestamps from the current local timezone. Their scheduling flows handle boot/update and ordinary delivery, but there is no corresponding time/timezone-change receiver to recompute them. The countdown widget handles these broadcasts for its own display only. Moving to another timezone can therefore deliver the next locally configured reminder at the old timezone's equivalent time until a later scheduling pass corrects it.

Fix direction: add a bounded, centralized time/timezone reconciliation path for local-calendar schedules. Preserve absolute task/work-session timestamps rather than shifting every saved date indiscriminately. Include DST transitions and overnight habit windows.

### A10 — Bubble-start failure can interrupt boot reminder restoration

Location: `service/BootReceiver.kt:24` and `service/BubbleService.kt:185`.

Boot restoration acquires `goAsync()`, then calls bubble startup and several schedulers before entering the coroutine's `try/finally`. Unlike the blocker startup helper, bubble startup can throw. If foreground-service startup is rejected, execution can leave before habits/tasks are rearmed and before the pending broadcast result is finished.

Fix direction: put the entire boot restore under a reliable completion boundary; isolate optional bubble startup from each reminder family. Log and continue independent restoration work. Do not use tighter retry loops or Activity launches as a workaround for Android background restrictions.

### A11 — Background chat UI retains refresh work

Locations: `ui/screens/BubbleChatActivity.kt:470,712,812,818,833`.

The reference-state refresh and 15-second transcript cleanup loops are ordinary `LaunchedEffect` jobs, while repository flows use `collectAsState()`. They have no STARTED/RESUMED lifecycle boundary. An activity remaining stopped in its task can therefore continue refresh reads, encrypted session loading and flow collection despite no visible chat. Recomposition frame-clock suspension is not equivalent to suspending coroutine delays and database collectors.

Fix direction: gate UI projection/collectors with lifecycle-aware collection and `repeatOnLifecycle`; catch up expiry on resume. Keep durable sessions, alerts and countdown deadlines independent of UI visibility. Do not pause a real task/focus deadline merely because its display is hidden.

## Reviewed behavior that should be retained

- App blocking uses direct `WindowManager` overlays; no conversion to background Activity launching is proposed.
- Existing reminder queues serialize task/habit overlays, reject stale callbacks with presentation IDs and use a bounded watchdog. These mechanisms are useful and should be preserved.
- Reminder delivery rechecks whether tasks were completed/trashed/rescheduled and whether habits were paused, archived or already satisfied.
- Task/habit exact-alarm scheduling includes an inexact fallback when exact alarms are unavailable. Expected Android delay is not automatically an application bug.
- Reminder wake locks have short explicit timeouts and normal release paths. The scan found no unbounded acquired wake lock in these flows.
- Bubble destruction removes its handlers, animations, listeners and views. The confirmed lifecycle problems concern recovery/entry state rather than an obvious missing general teardown.
- Voice capture stops on pause and has bounded retries/timeouts. Its running amplitude loop belongs to an active session.
- Widget RemoteViews factory `runBlocking` calls occur in service-factory callbacks; their mere presence does not establish a UI-thread ANR.
- Settings scenery decoding uses IO, and playback is tied to visibility/lifecycle/reduced motion. No always-running invisible scenery loop was established.
- Note image import has dimension/file checks and sampled decoding; editor imports run on IO. There is no justification here for reducing image quality further.
- Backup/restore maintenance gates, atomic files, encryption and startup recovery are intentional data-safety measures. They should not be removed to make a microbenchmark faster.
- The apparent continuous 1 ms chat refresh loop was **ruled out**: `AssistantSessionStore.load()` resets expired/absent state to a fresh timestamp. A near-expiry iteration is not evidence of a persistent hot loop.

## Profiling candidates, not required rewrites

1. **Work-reminder reconciliation:** `WorkSessionReminders.reconcileAll()` repeatedly reads and writes the entire encrypted ledger for each task; a batch is potentially quadratic as task/session count grows. Measure at realistic large sizes, then batch safely while retaining crash-cancellable alarm registrations and delivery receipts.
2. **Startup backup catch-up:** the periodic worker requires battery-not-low, while `AutoBackupScheduler` launches catch-up directly on IO after 24 hours. These paths can also overlap. This is a policy/performance tradeoff, not proof that backing up on app open is wrong. A single-flight constrained catch-up worker could reduce duplication while keeping manual backups immediate and preserving recovery guarantees.
3. **Background-process startup:** even housekeeping workers initialize the Application's managers, Rive and chat warm-up. Export cleanup is registered every 15 minutes. Trace cold worker startup before changing it; expiry-based one-shot cleanup may avoid repeated startup without extending plaintext export leases.
4. **Widget rendering and launch work:** widgets regenerate per-row drawable bitmaps, and initial Activity startup plus `onResume()` can both request refreshes. `CompletedTaskHistoryManager.purgeExpiredCompletedTasks()` also reparses and serializes history and calls synchronous `commit()` from both Activity callbacks, even when nothing needs pruning. The platform may skip an unchanged disk write, so this is not a claim that every resume writes a file. Profile large histories/lists and coalesce redundant work if meaningful; existing typography behavior should remain unchanged.

Some Application-initialized managers receive `MaintenancePreferences`, whose `apply()` deliberately uses a synchronous commit. Activity-owned preferences do not automatically pass through that Application override; the bubble sliders must not be blamed for synchronous commits on that assumption. Preserve the maintenance barrier and measure actual UI I/O call paths before changing this durability tradeoff.

Lint's receiver-flag errors were checked against the actual SDK guards and are not asserted as Android 12+ registration crashes. Its `produceState` diagnostic is contradicted by the producer assigning `value`. Global application-context references and managed overlay lifetimes were not automatically relabeled as Activity leaks. Play policy/API-age, artwork location and formatting diagnostics are not substituted for runtime defects.

## Suggested implementation order

1. **Persistence and notification identities:** fix A01/A02 with reload and cross-feature post/cancel regression coverage.
2. **Bubble state and recovery:** address A03–A06 together with one explicit visibility-state model; validate screen lock/unlock, rotation, permission dialogs, snooze/process recreation, fullscreen views and disabling.
3. **Background/lifecycle work:** fix A07/A11 while retaining real deadline accounting and maintenance durability.
4. **Recovery and concurrency:** fix A08–A10 with controlled concurrent-delivery and alarm-reconciliation tests. Profile the optional scaling candidates separately.

Final device verification should use the POCO X6 Pro / Android 16: inspect actual screen-off wakeups and CPU, repeated chat open/close, rotation near the bottom edge, snooze across process recreation, notification permission/channel changes and recent-task removal. Distinguish a deliberately hidden/snoozed bubble, a detached/offscreen view, a stopped service and an OEM force-stop. An OEM force-stop cannot be guaranteed away by adding restart alarms.
