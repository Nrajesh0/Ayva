# Settings ownership and search

Each editable feature has one canonical settings owner, reused by Settings,
Account shortcuts and assistant navigation. Stable setting IDs, existing saved
keys/mirrors and old search aliases are retained. A navigation success is not a
preference write or evidence that Android granted access.

| Page | Owns |
| --- | --- |
| Appearance | App/block-screen theme, accent, heatmap, launcher icon, transcription style and Widgets entry |
| Focus & behavior | App-boundary shortcut, soft delay, temporary relief, launch screen, displayed streak, practice/vacation shortcut |
| Notifications & reminders | Delivery style, persistent repeat, routine/habit alerts, summary/practice controls and Android channel/alarm links |
| Security & permissions | Usage, overlays, optional battery help, notifications, exact alarms, uninstall friction, screen protection and private vault |
| Data & recovery | Verified portable export/restore, safety snapshots, saved drafts, storage, temporary-export cleanup and pending recovery |
| Bubble Settings | Existing bubble layout and controls; related pages link here |
| Help & about | Setup help, replayable app tours, diagnostics, app version/build and GitHub |

Restore defaults to snapshot replacement after review. Import extra copies is a
separate advanced choice; see [backup/restore](../backups/BACKUP_AND_RESTORE.md).
Account opens the same pages, uses the actual theme name and labels vault locking
accurately. No synthetic audit-log/biometric status or unused master-blocking bypass
is presented. Pro activation uses the existing signed lifetime-license mechanism;
its [unresolved signing-key finding](../../security/OPEN_FINDINGS.md#licence-signing-key) remains open.

Search includes aliases, nested pages and paused controls without silently
enabling prerequisites. App boundaries support identity-aware search/selection and
reviewed removal; a routine-derived rule is not silently turned into a manual rule.
Settings search/current values observe changes from chat/native controls while open.
Only allowlisted typed settings can be changed by assistant action previews.

Preserve these existing UI/chat ranges: soft delay 5–120 seconds, temporary relief
1–60 minutes, bubble delay 1–300 seconds and persistent repeat 1–1440 minutes.
Stepper bounds also preserve non-aligned saved upper values. Legacy notification
delivery choices remain readable with their notification/priority overrides.

Basic blocker readiness needs usage and overlay access. Notifications, precision
and recommended battery access are represented separately. Android's global/channel
notification controls remain reachable after permission is granted. Optional admin
consent/removal refreshes actual OS state; the app does not silently disable a grant.

Settings and Notes share vault-management safeguards. PIN change/disable and phrase
viewing require authentication; sensitive entry clears on background. Optional
PIN-free archiving still requires authorization for viewing/unarchiving. Disabling
general screenshot protection must not clear a stronger sensitive-window flag.
Sensitive-window protection remains active while a private screen is visible,
including its exit animation.
Live date preview and landscape hiding use the existing preference semantics.

Widget configuration distinguishes Tasks, Notes and Exam instances; ID 0 is the
explicit default style. Avatar, launcher, accent, blocker and per-widget appearance
are separate scopes. Keep the compact design language, actual artwork, keyboard/
Back behavior, scroll state and large-text access when reorganizing these pages.

## Launch and editor entry points

The dock and launch-screen picker share Focus, Tasks, Notes, Time and Account.
The old blank-tab route redirects to Time; unknown external destinations fall
back to Focus. Shortcut extras are consumed once, after startup recovery allows
normal entry, and navigation waits until its graph is ready. Habit and task
creation requests are scoped to their own screen and consumed when shown.

A note shortcut or profile pin saves the current editor before switching. Profile
links retain their return destination when the requested note is already open. A new draft can close safely before its autosave debounce has assigned an ID. A failed
save retains the draft; an active recording is preserved. Voice shortcuts use the
Notes microphone-permission flow and do not replace an open editor/recording.
Delayed dock picker/speech/drawing results cannot replace a newer editor. A delayed
microphone permission grant is checked against its requesting editor and storage
generation. Deleted notes cannot reopen from a stale presentation cache. During maintenance,
shortcuts ask the user to retry after the operation; tours wait for recovery notices.
Task and routine editors retain an encrypted original row alongside their draft.
Live database updates do not reset typed text. Explicit template copies have separate encrypted sessions, so choosing a template does not replace an unrelated new-task draft. A changed or deleted record requires
an explicit reload or save-as-copy choice, and checked updates cannot recreate a
deleted record. Forms close only after the database confirms a save. Save is guarded
before the coroutine starts; reminders/widget failures report that data was saved.
Restored task, routine, habit, quick-add and chat drafts have a discard control. Existing current-format task drafts are handed over to stable identifiers only after the new encrypted write verifies. A temporary Soft unlock never overrides an applicable Hard rule.

Task Undo belongs to the Activity's ViewModel, with a separate four-second deadline
for each deletion. Tab changes and rotation do not commit it early. A process death
before the deferred delete conservatively leaves the original task in storage.
Snapshot replacement cancels an earlier queued deletion, even when the restored
task has the same ID and timestamp. Backup reads retain the original deadline.
Subtask taps use transactions against the latest stored task, independently of the
editor save guard; rapid taps do not overwrite other steps or task fields.

Habit log rewards and goal bonuses have durable receipts. Undoing and repeating a
log does not earn it twice; manually entered historical counts do not earn extra
credit. Pending rewards survive interruption and retry safely after storage failures. Delivered habit steps compact to one earned high-water mark per day; history is retained without a receipt for every past tap. Routine rewards depend on a recorded
run reaching its scheduled end, independent of notification settings. Editing,
disabling or deleting a running routine cancels that run's reward; changing it back
does not reset its same-day receipt. Changes after the recorded end preserve an already-earned reward even if the background completion check ran late. The database upgrade preserves balances and
settles existing log days because earlier releases did not track reward receipts.

Habit editors close and discard their saved draft only after storage succeeds;
failed saves remain retryable, and queued duplicate save taps are ignored.

## First-use tours

New installations can choose a short spotlight introduction after permission setup.
Existing installations are not opted in automatically. Help & about → App tours
provides feature-introduction replays and a switch for contextual first-use tips. Skipping a
tour persists its dismissal; replay does not enable other automatic tips.

Guides cover navigation, tasks and natural-language dates, app blocking, notes and
Archive, profile rewards, habits, routines, and backup choices. Highlights follow
measured controls, account for their window offset, and avoid system insets. Short
cards can scroll with large text. A missing or fully clipped target is never highlighted
at a guessed position. Explicit replay and Next scroll registered targets into view
when necessary; an off-screen step is not silently counted as completed. Guides pause for the keyboard, backgrounding, edited or restored drafts,
authorization dialogs and backup operations; they do not forward taps to controls.
Tour state is kept in separate local preferences, outside the portable backup
allowlist. Tours never fill forms, save records, claim rewards, request permissions,
change vault protection or start data operations. Ayva placement is unchanged.

Tour tests live in `ui/tours` under the JVM test source set. Device acceptance still
needs TalkBack focus/readout, rotation and split-screen positioning, keyboard/
picker returns, and a clean-install run followed by an update with saved data.
Use Help's replay controls on an existing installation; clearing user data is not
needed to test the guides.
