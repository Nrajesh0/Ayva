# Sequential floating reminders

## Reproduced fault

The old task presentation path called `hideOverlayDirect(context)` as its first
construction step. That method unconditionally sent `onOverlayDismissed`, including
when there was no old view. The coordinator had already reserved the incoming
reminder, so this scheduled a queue advance 280 ms after opening it. With three
simultaneous tasks, a real WindowManager regression test observed task 3 after
three seconds without any user action. A mixed task/habit test also found both
managers showing windows concurrently.

Additional loss paths were found:

- The queue evicted its oldest waiting item once ten were waiting.
- Task completion, ignore, cancellation and rescheduling could hide an unrelated
  active task. Habit notification actions similarly dismissed any active habit.
- Queue callbacks had no presentation identity, so a late callback could advance a
  newer window. Construction and real dismissal were not distinguished.
- Waiting reminders and the interrupted active reminder existed only in memory.
- A task notification posting/grouping failure could prevent floating dispatch.
  Optional habit notification posting had the same ordering problem.
- Habit artwork loads asynchronously, outside the existing synchronous try/catch.
  Lottie's default asynchronous failure handler could throw on the main thread.

## Resulting behavior

Task and habit floating reminders share a FIFO. The active reminder stays visible
until the user dismisses, ignores, completes or reschedules it. The next reminder
opens after the current dismissal, with a 280 ms transition. There is no automatic
expiry or queue-size eviction. Simultaneous arrivals are serialized by the main
looper; ordering is by arrival at that looper, rather than database coroutine
completion order or task ID. Repeated alarms for an already active/waiting item are
deduplicated. Task and habit identities remain distinct even with equal numeric IDs.

Every production window callback carries a monotonically increasing presentation
ID. Construction cleanup is silent. Duplicate or stale callbacks cannot release a
different window. Actions remove only the matching queued reminder and matching
active window, including while its database validation is pending. Notification
reschedule actions preserve queue position and open that reminder's editor when it
is displayed; an active reminder's editor opens in place.

Before each presentation, current database state is loaded off the main thread.
Completed, trashed, deleted, undated or rescheduled task occurrences are skipped.
Archived, paused, disabled, completed or no-longer-scheduled habits are skipped;
previous-day habit reminders are not replayed. Titles, details and progress are
refreshed rather than displayed from an old snapshot.

An AtomicFile in the private `noBackupFilesDir` stores the active and waiting
identities plus occurrence date/due time and the reschedule-editor flag. It stores
no task titles, details or habit text and is excluded from backups. Application
startup reloads the identities and validates them against the current database.
A dismissed item is removed from disk before the next presentation. Failed
validation or window presentation retains the identity for a later startup retry,
while allowing other reminders to proceed. Unexpected window removal also releases
the slot and preserves the interrupted reminder. Atomic replacement protects the
last complete file against interrupted writes.

Direct WindowManager overlays remain the presentation mechanism. If overlay
permission is missing, the existing system-notification path remains available and
the pending popup is retained for retry. Automatic background Activity launches
were removed from the task overlay fallback: modern Android may reject them, and
releasing the queue before their lifecycle ends cannot guarantee one window at a
time. The separate popup Activity source is otherwise unchanged.

Floating dispatch precedes system notification posting; habit rescheduling and
persistent task rearming also happen independently of that posting. A failed habit
animation falls back to its emoji without disabling the reminder's controls.

## Verification

`ReminderOverlayQueueTest` uses actual WindowManager views and checks attachment,
active identities, dismissal behavior, 25-item bursts, concurrent background
arrivals, duplicate alarms, mixed types, stale callbacks, transition arrivals,
scoped cancellation, reschedule actions, permission failure, unexpected detach,
clearing during presentation, Back handling and slow system animation scales.
Cancellation waits for the actual window-removal callback rather than assuming a
fixed animation duration. It runs on Robolectric API
28, 34, 35 and 36 (Android 9, 14, 15 and 16).

`ReminderOverlayRecoveryTest` uses an in-memory Room database and a real disk store
to check restart order, interruption replay, refreshed task data, stale task/habit
filtering, progress completion, cancellation during validation, permission recovery
and database failure retention. Existing Batch4 security/queue, habit, task option
and canonical task planning tests are included in validation.

The two initial regression tests were run before the implementation and both
failed; they pass with the fix. The debug APK is built from the updated code as version 2.0.33 (versionCode 133). Final validation: 143 tests,
zero failures/errors/skips; debug assembly succeeded. See
`REMINDER_OVERLAY_VALIDATION.json` for suite counts and the APK SHA-256.

## Practical limits and device checks

These are automated Android framework and database tests, not physical-phone
verification. Android controls alarm delivery during force-stop, device shutdown,
Doze and vendor battery restrictions. An in-app queue can serialize reminders once
received; it cannot force Android to deliver a suppressed alarm. A killed process
recovers its pending queue when the app process next starts, not immediately while
it is dead. Overlay permission is still required for floating windows. Storage
write failures are logged and cannot offer the normal restart durability guarantee.

Before release, on an Android 12+ phone, schedule three distinct tasks at the same
minute, then a task and two habits at the same minute. Leave the first popup open,
then exercise Close/Ignore/Done/Reschedule and notification actions for a waiting
item. Repeat while another app is foreground, with notification-bar alerts disabled
but overlay permission enabled, and after killing/restarting the process. Confirm
that exactly one popup appears at a time, each remaining valid reminder follows,
and the system copies and future alarms retain their selected settings.
