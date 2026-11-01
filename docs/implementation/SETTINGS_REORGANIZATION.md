# Settings reorganization

The settings audit is implemented in the existing compact menu style. Bubble Settings retains its layout; its date-preview label now describes the actual control. Settings and Account shortcuts use one canonical owner for each editable feature.

## Menu structure

| Menu | Contents |
| --- | --- |
| Appearance | Transcription style, app and block-screen themes, accent, heatmap, launcher icon, Widgets |
| Focus & behavior | App boundaries shortcut, soft-block delay, temporary unlock duration, launch screen, displayed streak, existing practice/vacation shortcut |
| Notifications & reminders | Task delivery, persistent interval, routine/habit alerts, existing summary/practice controls, Android notification/alarm links |
| Security & permissions | Usage, overlay, battery/background, notifications, exact alarms, uninstall protection, screenshot protection, Private notes vault |
| Data & recovery | Encrypted export/restore, verification, safety snapshots, saved drafts, storage and safe temporary-export cleanup; interrupted-restore repair when applicable |
| Bubble Settings | Existing layout and controls; related menus link here |
| Help & about | Permission setup, diagnostics, version/build, GitHub |

The profile card opens Profile & achievements. Pro license opens the existing signed lifetime-license activation; it does not advertise a recurring subscription or cloud sync.

## Resolved audit issues

- [x] Stable setting IDs and canonical destinations; all 48 old indexed labels remain aliases.
- [x] Appearance, Focus, Notifications and Widgets have separate pages using the shared compact row/page components.
- [x] Readiness, onboarding and Security use the same five-capability permission model, with exact alarms conditional on Android support. Notification access includes Android's global notification switch. The notification row opens channel settings even after permission is granted.
- [x] Settings and Notes use the same private-vault management panel and existing NotesViewModel safeguards. Change/disable PIN still require authentication; phrase viewing requires unlock; sensitive dialog state is cleared on background.
- [x] One full encrypted-backup UI controller, reused through navigation. Restore defaults to adding records; replacement mode explains removal and requires a separate confirmation. Existing authenticated backup format, restore journal and transaction guards remain unchanged.
- [x] Recovery tools are flat. Removed the redundant nested Recovery landing and duplicate snapshots entry.
- [x] Account opens the common theme/security/backup pages, shows all theme names accurately, labels its vault-lock action accurately, and no longer displays a synthetic audit-log tab or fake biometric ACTIVE status. Removed its unused blocking master switch rather than adding a new bypass to runtime enforcement.
- [x] Full theme palette retained. Avatar, launcher icon, accent, block-screen theme and individual widget styles remain separate scopes.
- [x] Widget manager distinguishes Tasks, Notes and Exam countdown; users select the installed instance instead of editing the first one implicitly. Default style uses ID 0 explicitly.
- [x] UI/chat constraints agree: soft delay 5–120 seconds, relief 1–60 minutes, bubble delay 1–300 seconds, persistent repeat 1–1440 minutes. Stepper arithmetic preserves the upper bound for non-aligned saved values.
- [x] Task delivery presents two distinct behaviors. Old Floating Bar/Both values remain readable and keep notification backups and priority-task overrides.
- [x] Live date preview reads the existing preference; disabling it hides the preview while explicit task/date commands still work. Landscape hiding is named accurately.
- [x] Nested and paused controls are searchable without silently enabling prerequisites. Existing Bubble summary/practice controls reflect chat edits while open.
- [x] Help routes, instructions, ranges and notification-choice wording match the new menus.
- [x] Disabling general screenshot protection cannot clear stronger protection on sensitive settings pages.
- [x] Final regression suite, debug build and screenshot review recorded below.

## Preservation

No database schema, original animation assets, app-blocking WindowManager overlays, or reminder FIFO implementation changed. The previously approved 288×93 dp animation display size remains; original frames, timing, resolution and lossless assets are unchanged. Existing saved preference keys and legacy storage mirrors are retained.

## Validation

- Full recompilation and debug build passed: `./gradlew :app:testDebugUnitTest :app:assembleDebug -Pkotlin.incremental=false --offline --console=plain`.
- All **1,398 tests in 92 suites passed**, with no failures, errors or skipped tests. This includes 91 reminder-queue/recovery checks, authenticated backup/restore and repair tests, settings ownership/navigation, reactive preferences, permissions across APIs 28/34/35/36, widget instance selection, private-vault prerequisites and replacement confirmation.
- The catalog has 72 indexed destinations/controls; the fixture verifies all 48 old labels still resolve.
- Chat data-boundary guard, bundled dictionary verification and `git diff --check` passed.
- [Rendered UI gallery](/workspace/artifacts/settings-reorganization/index.html) and [validation record](/workspace/artifacts/settings-reorganization/validation.json).
- [Debug APK](/workspace/artifacts/Ayva-reorganized-settings-debug.apk), version 2.0.34 (134).

Screenshots are renders of production composables under Robolectric, not physical-device captures. No phone is connected; manufacturer-specific permission panels, launcher widget pinning and visual animation behavior still need an on-device smoke test.

Release metadata: 2.0.34 (134). The GitHub release workflow builds and signs the APK after the version-bump commit is pushed.
