# Runtime reliability and permissions

## Blocking and readiness

`BlockOverlayManager` and `FocusBlockerService` must keep direct
`WindowManager.addView` / `TYPE_APPLICATION_OVERLAY` blocking. Background Activity
launches are not a substitute on modern Android; follow the root `AGENTS.md`.
Programmatic blocker view changes belong in the existing overlay implementation.
Explicit user notification taps/native navigation are separate flows.

Usage and overlay access establish basic blocking readiness. Notification access,
exact alarms and optional battery exemption have their own status. Missing data
is not zero usage or proof that an alarm was delivered. No automatic DND control
or broad storage permission is introduced. Camera capture delegates to an installed
camera app, with denial/unavailable-app handling and a gallery alternative; the
app itself requests no CAMERA permission. Camera/microphone hardware is optional.

## Requested permissions

| Permission | Purpose |
| --- | --- |
| QUERY_ALL_PACKAGES | Existing installed-app discovery; store eligibility needs distribution review |
| PACKAGE_USAGE_STATS | OS usage access for foreground-app detection |
| SYSTEM_ALERT_WINDOW | Blocking, assistant and reminder overlays |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Optional background-reliability help; no guarantee against OEM stopping |
| FOREGROUND_SERVICE / SPECIAL_USE / DATA_SYNC | Supported visible blocker/bubble services and WorkManager backup execution |
| POST_NOTIFICATIONS | User/channel-controlled reminder/service notifications |
| SCHEDULE_EXACT_ALARM | User-controlled precision, with approximate fallback |
| RECEIVE_BOOT_COMPLETED | Restore eligible scheduled work after restart/unlock |
| HIDE_OVERLAY_WINDOWS | Hide third-party overlays on protected app windows |
| VIBRATE / WAKE_LOCK | Haptics and bounded reminder/worker delivery |
| RECORD_AUDIO | Existing capture, subject to microphone consent and recognizer policy |
| App-defined DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | Signature-authenticated private broadcasts |

The inventory represents 15 requested permissions at the hardening checkpoint;
confirm the merged APK for a new release. INTERNET, USE_EXACT_ALARM and unused
USE_FULL_SCREEN_INTENT are absent. Manifest declarations alone are not OS grants.
App-private receivers require the signature permission and non-exported flag;
system event receivers keep their appropriate system handling.

Optional device-admin activation requests no device-control policies and provides
uninstall friction. An older grant may retain screen-lock authority; settings
explain explicit disable/re-enable migration without silently removing protection.

## Reminder persistence and scheduling

Task/habit windows share one main-looper FIFO, retaining arrival order without
automatic expiry or size eviction. Active windows remain until a real user action;
duplicate/stale callbacks cannot dismiss a different presentation. Task/habit
identities remain distinct even with equal numeric IDs. Matching reschedule
actions preserve queue position; notification post failures do not discard overlays.

An AtomicFile in `noBackupFilesDir` stores active/waiting identities and occurrence
metadata, not titles/details. Before presentation load current rows off the main
thread and skip obsolete/completed/trashed/rescheduled/paused occurrences. Remove
a dismissed identity durably before advancing. Failed validation/window creation
retains it for retry while allowing other items to proceed. Missing overlay access
keeps the supported notification path and queued popup rather than background
Activity-launch fallback. Asynchronous artwork failures have a safe fallback.

Alarm access is re-observed on resume and the protected grant event. A serialized,
bounded coordinator durably queues reconciliation after first process observation
and changes, including the focus timer. Failures retry; saved deadlines are not
rewritten. Time/timezone events keep calendar reconciliation distinct. Boot, Doze,
force-stop, notification settings and OEM quotas limit actual delivery; neither
WorkManager nor a foreground service guarantees exact timing.

## Speech lifecycle and privacy

Recognizer connections are disposable and generation-bound. A stale callback
cannot overwrite edited text or another session. Keep committed transcript separate
from revisable partials; duplicate final callbacks cannot append/execute twice.
Silence/retry/backoff and unavailable-service states remain visible and bounded.
Backgrounding, rotation, pause/resume and text editing preserve the existing prefix
and explicitly accepted transcript rather than treating partial captions as actions.

The popup uses a stable transcript pane with scrolling, restrained idle movement
and microphone-RMS-driven visualization. Scrolling back suspends auto-follow.
Edit pauses capture; Finish editing returns to the transcript; Use text takes the
corrected text; Cancel discards the capture session. Old connection callbacks may
not overwrite the correction. Do not resize the surface with every partial result.

Speech-provider/privacy review was explicitly deferred. Availability of an on-device
service and the app's lack of INTERNET permission do not establish the behavior of
another installed recognizer. Keep that acceptance item visible rather than claiming
all capture is private/offline solely from a successful compile or setting value.

## Diagnosing failures without deleting data

Obtain crash/logcat evidence for the actual APK/device before guessing at an OEM
startup failure. Retain database/key files and encrypted recovery state. Do not
clear app storage, weaken SQLCipher checks or remove a recovery gate as a diagnosis.
The note-editor non-inline boundaries address a DEX VerifyError that JVM checks
alone missed; preserve them. Distinguish recoverable IO/unsupported-format errors
from a demonstrated key or native dependency failure. Device/provider tests and
their revision are recorded separately from build/source checks.
