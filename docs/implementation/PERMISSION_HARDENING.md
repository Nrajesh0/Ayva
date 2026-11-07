# Permission hardening — 2026-11-06

Scope: permission reductions and reliability fixes following the permissions review.
Speech-provider selection, speech privacy disclosures, and recognizer behavior are
explicitly deferred by the user. This change does not change vault encryption,
backup credentials, database schemas, or attachment formats.

## Decisions

| Area | Implementation | Compatibility and user impact |
| --- | --- | --- |
| Camera | Remove the app's `CAMERA` request. Keep capture delegated to the selected camera app through the existing preview contract. Handle unavailable camera apps, device-policy denial, and unavailable launchers. | Existing photo result and save handling stay intact. Failure shows an explanation and suggests the gallery. Camera hardware remains optional. |
| Private broadcasts | Register all four custom receiver registrations through `InternalBroadcasts`, requiring the app's signature permission on every supported Android version, plus `RECEIVER_NOT_EXPORTED`. | Android 12/12L now authenticate senders. Package targeting alone did not authenticate them. System screen/user/package receivers retain their system-event handling. |
| Exact alarms | Remove `USE_EXACT_ALARM`; retain user-controlled `SCHEDULE_EXACT_ALARM` and existing approximate fallbacks. | Android controls whether precise delivery is available. Saved reminder deadlines are not rewritten. Approximate delivery can be delayed by Android. |
| Alarm recovery | Observe actual alarm access on activity resume and the protected permission-grant broadcast. Queue full durable reconciliation on first observation in each process and after access changes. Restore the active focus timer too. | Revocation kills the process and cancels exact alarms. The next foreground restores approximate alarms; grant restores precise scheduling. A successful enqueue is remembered only in memory; enqueue failures retry on a later observation and worker failures retain existing backoff. Concurrent resume/broadcast observations share a mutex. Clock/timezone events keep their separate calendar reconciliation job. |
| Full-screen intents | Remove unused `USE_FULL_SCREEN_INTENT`. | Current notifications and overlay reminders do not use notification full-screen intents. |
| Optional device admin | Request no device-control policies. Keep the optional active-admin role for uninstall friction. Refresh actual state after system consent and removal; handle unsupported devices and failures. | Existing grants may retain old screen-lock authority. Settings explain explicit disable/re-enable migration when Android reports that authority. The app does not silently deactivate protection. Saved user data is retained. |
| Battery exemption | Describe it as optional and useful for background reliability; remove promises that Android cannot stop the app. | Existing skip and settings actions remain available. Manufacturer restrictions can still delay or stop background work. |
| Optional voice hardware | Mark microphone hardware optional. | Devices without a microphone can use other features. Speech handling and microphone consent are unchanged. |
| Installed-app visibility | Retain `QUERY_ALL_PACKAGES`. | Existing blocker/assistant discovery includes installed apps without launcher entries. Removing it requires a separate product decision and a narrower discovery design. Store eligibility still needs distribution-specific review. |

Other necessary notification, foreground-service, overlay, usage, boot, wake-lock,
vibration, and overlay-hiding permissions remain. No Internet or broad storage
permission is added. The AndroidX private-receiver signature permission already
existed in the merged APK; its declaration is now explicit in the app manifest.
The verified requested-permission count falls from 18 to 15.

## Retained permission inventory

Android permission names below omit the `android.permission.` prefix except for
the app-defined signature permission. Special access is read from Android at use
time; a manifest declaration alone does not establish that access was granted.

| Permission | Purpose | User/platform control |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | Installed-app discovery for blocking and assistant actions | Manifest visibility; distribution eligibility needs separate review |
| `PACKAGE_USAGE_STATS` | Recognize foreground apps for blocking | Android usage-access settings |
| `SYSTEM_ALERT_WINDOW` | Blocking screens, floating assistant and reminder overlays | Android overlay settings |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional improvement to background reliability | Explicit Android battery-exemption settings |
| `FOREGROUND_SERVICE` | Run supported visible background services | Android foreground-service restrictions and ongoing notification |
| `FOREGROUND_SERVICE_SPECIAL_USE` | App blocking and floating assistant service types | Declared service subtypes and Android service-start rules |
| `FOREGROUND_SERVICE_DATA_SYNC` | WorkManager foreground execution for encrypted automatic backup | Declared data-sync type and Android execution limits |
| `POST_NOTIFICATIONS` | Reminder and service notifications | Runtime consent on Android 13+, notification/channel settings |
| `SCHEDULE_EXACT_ALARM` | Precise reminder and focus-timer scheduling | User-controlled alarm access; approximate fallbacks remain |
| `RECEIVE_BOOT_COMPLETED` | Restore scheduled work and reminders after restart | Android boot delivery and force-stop restrictions |
| `HIDE_OVERLAY_WINDOWS` | Hide third-party overlays over protected app screens | App-controlled protected-window state |
| `VIBRATE` | Reminder haptics | Android notification/channel and device settings |
| `RECORD_AUDIO` | Existing voice features | Runtime microphone consent; speech privacy is deferred |
| `WAKE_LOCK` | Brief reminder delivery and WorkManager execution | Bounded app/library acquisition and Android power restrictions |
| `com.focusbyrj.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Authenticate app-private broadcast senders | Signature permission granted at installation; not a runtime prompt |

## Verification

- Wider regression run: **323 tests; 322 passed, one setup timeout** in
  `ReminderRecoveryTest.prepare` while waiting five seconds for WorkManager
  cancellation. The same fixture timeout was recorded before these changes.
- Isolated settings/reminder rerun: **29 tests passed**, including all three
  `ReminderRecoveryTest` cases and device-admin, permission-model and settings
  navigation tests. Existing assertions and setup deadlines were retained.
- After removing an unnecessary static context reference from the recovery
  coordinator, all **18 alarm-recovery tests passed**. The existing reminder
  setup timeout recurred under concurrent release-build load; the final sequential
  verification passed all **three reminder-recovery tests** on the finished source.
  Run this fixture separately from heavy parallel build work when reproducing results.
- All new permission, camera, device-admin and alarm-recovery tests passed in the
  wider run. All **102 manual/automatic/portable backup checks** passed too.
- Packaged debug and optimized release APK inspection: **15 requested permissions**,
  optional camera/microphone hardware, and no requested device-control policies.
  Release policy XML was resolved through its optimized resource-table path.
- Chat data-boundary guard, dictionary verification, and `git diff --check` passed.
- Final debug and optimized release APK builds passed. Both rebuilt artifacts
  passed the permission, signature-sender gate, optional hardware and empty
  device-admin-policy checks. The release artifact is unsigned in this environment.
- Final full debug lint: **0 errors, 365 warnings, 56 informational findings**.
  Comparing issue IDs, messages and files with the pre-change report found
  **no added findings**. The static-context warning was resolved by retaining only
  the process-local permission-state boolean, without suppressing the detector.

The new tests cover merged permissions and optional hardware, private-receiver
permission/flag configuration and own-command delivery on Android 12, 12L, 14 and
16, empty device-admin policies, delegated-camera failure paths, device-admin
migration UI and opt-in behavior, and exact-alarm recovery coordination including
concurrent observations, permission changes during enqueue, failure and cancellation.
The shared observation has an eight-second budget covering lock contention and
cancellable durable enqueue, keeping the permission broadcast's async work bounded.
Startup's old parallel reminder-scheduling calls now use this single durable
restoration path, avoiding a second simultaneous delivery of overdue reminders.

Before distribution, verify on physical Android devices: capture through the
installed camera app; device-admin activation with no requested policies and
migration from an older active grant; two-app private-broadcast spoof rejection;
alarm-access grant/revocation and background delivery under manufacturer battery
restrictions. Local JVM/Compose tests do not certify those platform behaviors.

## Limits

Recovery cannot run while Android keeps the app force-stopped or before the next
allowed startup after revocation. Permission grant and app foreground are recovery
opportunities, not guarantees of exact delivery without access. Existing reminder
worker retries can be delayed by WorkManager and device restrictions. Speech
privacy remains deferred; this permission cleanup does not make a new privacy
claim about external speech or document providers.
