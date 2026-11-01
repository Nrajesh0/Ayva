# Settings organization audit and rearrangement proposal

> Historical audit captured before implementation. See [Settings reorganization](../implementation/SETTINGS_REORGANIZATION.md) for the resulting menus, fixes and validation.


Date: 1 November 2026. Scope: the current working tree based on commit `25802c7`, including the pending menu styling and smaller footer. This is a source and navigation audit, not a claim that every Android/device state has been exercised. No application behavior or menu layout was changed during this audit.

The main problem is fragmented ownership: settings for the same user goal live in different screens, several shortcuts have different names for the same destination, and some duplicated controls offer different choices. A rearrangement should establish one canonical owner for each preference or operation. Related pages can link to that owner; they should not create another independently implemented control or backup flow.

## What was examined

- Main Settings hub, App Settings, Bubble Settings, Security, Account and Subscription.
- Settings search: all 48 indexed entries, actual navigation routes, scroll targets, conditional visibility and collapsed-group behavior.
- Setup Guide and its four permission actions; Android permission helpers and readiness calculations.
- Appearance pickers: app/overlay themes, accents, heatmaps, streak source, launcher icons and the ten transcription styles with previews.
- Task-widget configuration, note-widget configuration and exam-countdown configuration, including defaults versus individual widget instances.
- Private notes vault setup, unlock, recovery phrase export, emergency recovery, change PIN and disable-PIN flows; entry points from Security and Notes.
- Encrypted backup export/restore dialogs; merge versus clean restore; restored-vault PIN setup; snapshot listing/create/restore; saved drafts; backup verification; storage and temporary-export cleanup.
- Diagnostics record/export/clear controls and Account's separate Logs tab.
- Related Account theme/blocking/vault/backup shortcuts and assistant preference editing. Runtime consumers were checked where a displayed setting could be misleading or disconnected.

## Recommended root structure

Use one compact profile card at the top as the Account entry. Keep identity, avatar, achievements and Pro license in Profile. The whole card already opens Account, so the extra Profile & Rank button is optional redundant navigation. Remove or repurpose the Security Vault button: it currently opens the same generic Security page as the main Security & Permissions row.

Under the card, use a single list of seven clear destinations. Retain the current titles-only visual treatment and smaller animated footer. This proposes more descriptive categories than today's five rows, but avoids a long mixed App Settings page and gives reminders a discoverable home without another navigation layer.

| Root destination | Contents / next level |
|---|---|
| Appearance | App theme, accent color, block-screen theme, heatmap colors, launcher icon, transcription style, Widgets |
| Focus & behavior | Blocking master switch, soft-block wait, temporary unlock duration, launch screen, displayed streak source; links to existing practice/vacation controls |
| Notifications & reminders | Task popup behavior, persistent-task repeat interval, routine alerts, habit notification-bar alerts; links into existing daily-summary/practice sections |
| Security & permissions | Device permissions, uninstall protection, screenshot protection, one Private notes vault manager |
| Data & recovery | Create encrypted backup, restore backup, verify backup, safety snapshots, saved drafts, storage and safe temporary-file cleanup |
| Bubble Settings | Preserve its current visual design, layout and existing controls |
| Help & about | Existing permission-setup guide, diagnostics, app version/build and the existing GitHub destination |

Do not retain an App Settings child page also titled Settings alongside the actual Settings root. If a transitional App Settings landing page is needed to preserve the old route, give it a distinct title and forward old links to the new owners.

For Bubble Settings, respect the request to leave its UI alone. Notifications & reminders can contain a clearly named link to its existing Daily summaries & practice section. The existing Bubble page remains the owner of those controls in the first pass. Moving or rebuilding that section is optional later work, not required for this proposal. Morning/evening summaries currently have time pickers, not independent enable switches; adding enable switches would be a separate feature.

## Confirmed overlaps and the right treatment

| Overlap | Current implementation | Recommendation |
|---|---|---|
| Security navigation | Profile-card Security Vault button and Security & Permissions row both navigate to `security`; Account adds more differently named shortcuts to the same page. | One root destination. If retaining a Vault shortcut, target the actual Private notes vault manager and label it accurately. |
| Profile navigation | Whole profile card and Profile & Rank button open Account. | A tappable profile card can serve both; this also reduces the oversized header. |
| App theme | Account's Theme mode and App Settings' App Background Theme call the same manager. | One complete theme picker. Account may display/link the current choice rather than implement a reduced second selector. |
| Encrypted backup and restore | Account and Security separately maintain export/restore state, launchers, dialogs and completion handling, while calling the same backup backend. | One Data & recovery flow. Account can provide a shortcut to that flow. Reuse authentication, validation, restored-vault setup and result handling. |
| Safety snapshots | A direct Security row and Data & recovery > Recovery > Safety snapshots open the same sheet. | One canonical snapshot destination inside Data & recovery; a useful related shortcut may link directly to it. Avoid sending users through a second generic Recovery menu. |
| Permission setup | Setup Guide and Security expose the same underlying Android permission actions with different terminology. | Keep onboarding as a guided entry, backed by the same permission/status definitions and actions as the regular permissions page. |
| Private vault settings | Security has setup/status, phrase export and recovery. Notes > Archive > Vault Options additionally exposes lock, change PIN and disable PIN. | One vault manager reused from Settings and Notes. Preserve PIN verification and existing encryption/recovery guards. |

These are not all deletions. Repeated navigation can be useful when its label, destination and state are consistent. Duplicate implementations and inconsistent subsets are the problem.

## Issues to resolve before a major rearrangement

### 1. Recovery language understates a destructive choice — high priority

The restore checkbox says Clean Restore (Replace IDs). The backend does more than replace identifiers: it prunes current notes absent from the backup and replaces other restored datasets/settings when clean restore is selected. The data-replacement behavior is real even though the restore machinery has transactions and recovery protection.

Call the choice Replace current data with this backup. Explain that newer/current data outside the backup can be removed, show the restore scope, and require a separate explicit confirmation before committing a replacement. Keep the non-replacement default and preserve existing preflight, rollback, vault-PIN setup and archive-authentication behavior. An information-architecture change must not silently change the restore mode.

Safety snapshots, full encrypted backups, saved editor drafts and a vault recovery phrase are different recovery tools. A snapshot does not contain deleted attachments; the existing snapshot UI already says so. Carry that explanation into the new page. A 12-word vault phrase is not the password for an encrypted full backup.

### 2. Permission status and permission controls disagree — high priority

Security initializes and refreshes notification permission state but does not render a Notifications permission row. The root Setup/Active badge includes notification permission, so a user can see Setup without a matching control in that destination.

The hub and setup guide assess four permissions. `PermissionUtils.areAllPermissionsGranted()` additionally includes exact alarms. The manifest declares exact-alarm permissions and scheduling helpers check capability, but there is no regular settings control exposing that capability. This is a consistency gap, not evidence that every phone currently lacks it.

Use one capability list: Usage access, Display over other apps, Battery/background access, Notifications, and Alarms & reminders where applicable. Distinguish essential blocking permissions from reminder capabilities rather than treating every optional permission as a global failure. Share this model with onboarding, search, assistant diagnostics and Settings. The existing root Active badge measures permissions, not whether the blocking service or Account's master blocking switch is active; Ready / Needs setup would describe it more accurately.

### 3. Task notification choices are not three distinct behaviors — high priority

`TaskReminderReceiver` always posts a system notification. Both Floating Bar and Both also request the floating overlay. Priority tasks request a popup even when Notification Only is selected.

The least disruptive menu fix is to offer labels matching the two existing behaviors: Notification bar, and Notification bar + floating popup, or one Floating task reminders switch with the always-retained notification backup explained in its detail page. Existing saved Floating Bar and Both values can map to the same enabled behavior. Explain the priority-task override. Do not alter the sequential popup queue merely to reorganize these controls. If floating-only delivery is actually desired, treat it as a separate behavior change with its own tests.

### 4. The duplicate Account theme selector can show the wrong choice

App Settings supports ten theme modes. Account offers only Sys / Light / Dark and treats every value except system and light as Dark. That includes light variants such as Paper, Warm and Ivory. Selecting Dark there also overwrites the specific variant previously chosen elsewhere.

Replace this second editor with the common full picker or a link showing the actual theme display name. This is a concrete reason to centralize ownership rather than just rename the rows.

### 5. Some displayed security/account claims do not represent the action

- Account's Biometric login & vaults row displays a literal ACTIVE label. Its handler opens Security; it does not configure biometric login. Rename it to its actual destination, or implement/derive a real supported biometric setting before presenting that status.
- Lock app immediately calls `ArchiveVaultSecurity.lockVault()`. It does not establish an application-wide lock. Rename it Lock private notes vault unless broader locking is actually implemented.
- Account's Logs tab constructs a fixed list of times and security-audit messages in code. It is not the diagnostics recorder and is not a verified event trail. Remove that presentation or replace it with real sourced events; do not relabel those fixed messages as real activity history.
- Subscription is a locally verified lifetime Pro-license activation screen. Search and Account copy mention cloud sync / Sync Hub, but the audited destinations do not provide cloud-sync controls. `VaultSyncManager` is an unreferenced local JSON export/import helper, not evidence of a working cloud-sync destination. Use Pro license and Settings labels that describe the implemented flows.

### 6. Smart Regex Parsing appears disconnected

A full main-source scan finds the stored key in Bubble Settings' read/write and in the assistant preference specification. No parsing-path consumer of that switch was found. Treat this as an apparently ineffective control requiring runtime confirmation or wiring, rather than simply moving it into a new category. Preserve the approved Bubble UI while investigating the behavior separately.

The Bubble landscape/video label also overstates the specific condition found in its consumer: the flag is checked against landscape screen bounds. Label it Hide in landscape unless an independent video-mode condition is added and tested. This is a labeling proposal, not a redesign of the Bubble page.

### 7. Multiple settings editors have different validation ranges

| Value | Menu UI | Assistant / runtime |
|---|---|---|
| Bubble auto-hide delay | 1–15 seconds through the stepper | Assistant accepts 1–300 seconds; service uses the saved duration. |
| Persistent reminder interval | Discrete values from 5 minutes to 6 hours | Assistant accepts 1–1440 minutes; reminder scheduling uses the stored value. |
| Soft-block wait | Menu and assistant use 5–60 seconds | Block overlay reads/clamps 5–120 seconds. A legacy 120-second value can therefore display as 60 while being used as 120 until the setting is changed. |

Use shared setting definitions for supported values, units, defaults and side effects. Decide which ranges are intended; do not silently truncate saved values while reorganizing menus. Assistant writes currently mirror established legacy preference stores deliberately. That is compatibility behavior, not a reason to delete one store or rename keys during a UI cleanup.

### 8. Widget naming and instance selection need clearer ownership

Home Screen Widget opens the task-widget configurator, not a general widget selector. Its helper selects the first installed task-widget ID or a default configuration. Notes has its own widget configurator reached through Android's widget setup/its settings button. Exam countdown has another configuration flow and can add a widget to the launcher.

Use Appearance > Widgets with Tasks, Notes and Exam countdown. Select an existing widget instance when several exist and label defaults separately. Preserve each widget's configuration keys and per-instance ID behavior. Do not merge their palettes, text sizes or content selections into one global preference just because the controls look similar.

### 9. Search coverage is narrower than the whole settings hierarchy

The 48-entry catalog covers the primary App Settings, Bubble and Security rows plus three general destinations. It does not individually index child actions such as Verify backup, saved drafts, widget settings and Account's extra controls. Export Recovery Phrase and Practice Alert Time can appear in search while their controls are not currently rendered. Privacy / permissions / safety terms are appended to every Security-route entry, which can make results overly broad.

Search should use stable setting IDs, canonical destinations, relevant aliases and availability/precondition information. It currently matches the visible title exactly to scroll/highlight the row; renaming only the label can break that link. Make nested targets open the correct page/section and explain disabled prerequisites. Keep old names as aliases so users can still find familiar options after the rearrangement.

## Things that should stay separate

- App theme versus app accent versus block-screen theme: background mode, accent palette and blocker appearance are different settings.
- Profile avatar versus launcher icon: identity within the app and Android's launcher icon are different scopes.
- Heatmap colors and individual widget styles versus the overall app accent: these are component-specific preferences.
- Bubble auto-hide, hide in landscape, docked opacity, tuck depth and glow: different timing, visibility and appearance controls.
- Task, habit, routine and practice alerts: different channels/features. Unify terminology and grouping, not their independent behavior.
- Private-vault PIN, vault recovery phrase and full-backup password: different credentials and recovery purposes.
- Full backups, local snapshots, saved drafts and diagnostic bundles: different contents and different restore/share semantics.

## Child-page arrangement

- Appearance: Theme; Accent color; Block screen theme; Heatmap palette; Launcher icon; Transcription style; Widgets. Widgets opens Tasks / Notes / Exam countdown, then the selected instance's existing editor.
- Focus & behavior: Blocking enabled; Soft-block delay; Temporary unlock duration; Default launch screen; Displayed streak source. Related Practice / Vacation links open the current Bubble controls without cloning them.
- Notifications & reminders: Task delivery; Repeat interval for persistent tasks; Routine alerts; Habit notification-bar alerts; Daily summaries & practice link; Device notification/alarm permission links to the canonical permissions owner. Times and random-versus-fixed practice behavior remain distinct.
- Security & permissions: Device permissions; Privacy protections; Private notes vault. The vault subpage provides the existing setup, lock, change/disable PIN, phrase backup and emergency recovery flows subject to their existing prerequisites.
- Data & recovery: Create backup; Restore backup; Verify backup; Safety snapshots; Saved drafts; Storage. Safe cleanup belongs inside Storage. Diagnostics does not belong here simply because it exports a ZIP.
- Bubble Settings: Keep current layout and local groupings. Use canonical IDs/links underneath if shared navigation is introduced.
- Help & about: Permission setup; Diagnostics > Record / Export / Clear; Version/build; GitHub. About/version would be an addition: no version/build entry was found in the audited settings menus.
- Profile: Identity/role/bio/avatar, achievements and the existing economy information; Pro license. A convenient backup link may open Data & recovery, but Profile should not keep another full backup implementation.

## Conservative implementation sequence

1. Agree the ownership map and accurate labels. Resolve misleading status/recovery copy and decide intended notification/range behavior explicitly. Keep the current minimal typography and resized animations.
2. Introduce a shared setting/action registry with stable IDs, labels, aliases, storage owner, validation, side effects, destination, current value and availability. Adapt existing menu and assistant editors to it; preserve legacy storage compatibility.
3. Consolidate backup and private-vault navigation into common guarded flows. Replace duplicate implementations with links, preserving the existing credential checks, media handling, preflight and recovery journal behavior.
4. Move menu entries and update search from the same registry. Keep old navigation routes as forwarders and old labels as search aliases where needed. Do not migrate or delete user preferences just because labels moved.
5. Verify navigation and state continuity from every entry point before release. Only then consider optional changes to Bubble's contents or new controls.

The UI reorganization does not require replacing the core WindowManager blocking mechanism or rewriting the popup queue. Keep both intact.

## Verification required for an implementation

- Every old indexed entry maps to a reachable new destination; nested and conditional entries open the correct screen or explain their prerequisites. Back returns to the originating screen without Account ↔ Settings loops.
- Existing stored preferences, restored settings, theme variants and per-widget instance configurations survive the upgrade. UI and assistant edits agree on values, ranges and effective behavior.
- Permission denied/granted/returned-from-Android-settings states refresh consistently on Android 12–16, including notification and exact-alarm availability. Readiness wording reflects the capability actually being measured.
- Notification mode names match delivery; priority overrides are explicit; concurrent task/habit FIFO reminders still pass the existing regressions and an actual-device background test.
- Vault actions retain required authentication and sensitive-state handling. Full backup export/verify/merge/replace and PIN setup after restore use the same flow from every entry point. Replacement and clearing actions show their actual scope before execution.
- Bubble enable/snooze/resume, auto-hide timing, landscape visibility, summaries and practice/vacation behavior remain intact. Keep the approved Bubble presentation.
- Narrow screens, large text, dark/light/OLED modes, RTL, keyboard, scrolling and selected-value accessibility remain usable. Keep titles-only rows, but show necessary explanations in details, dialogs and search; provide current values and states without relying on color alone.

This audit supports a concrete reorganization plan; it is not a guarantee of bug-free operation. The observed behavior mismatches need focused verification when fixed. No new application code or tests were changed as part of this report.

## Full indexed inventory and proposed ownership

The search catalog currently indexes 48 entries, including navigation destinations and a collapsible group; these are not 48 independent stored preferences.

| Current page | Existing label | Proposed owner |
|---|---|---|
| Bubble Settings | Hidden Bubble Docking & Glow | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Bubble Accent Color | Bubble Settings (keep existing UI); related sections link here |
| Account | Profile & Rank | Profile card > Profile & achievements |
| Account | Subscription | Profile > Pro license |
| Assistance | Setup Guide | Help & about > Permission setup |
| App Settings | App Background Theme | Appearance |
| App Settings | Locked Screen Overlay | Appearance |
| App Settings | Accent Theme | Appearance |
| App Settings | Heatmap Palette | Appearance |
| App Settings | Streak Source | Focus & behavior |
| App Settings | App Launcher Icon | Appearance |
| App Settings | Exam countdown widget | Appearance > Widgets |
| App Settings | Home Screen Widget | Appearance > Widgets |
| App Settings | Soft Mode Wait Timer | Focus & behavior |
| App Settings | Soft Mode Relief Duration | Focus & behavior |
| App Settings | Persistent Reminder Interval | Notifications & reminders |
| App Settings | Routine Notifications | Notifications & reminders |
| App Settings | Task Notification Style | Notifications & reminders |
| App Settings | Habit Top Notifications | Notifications & reminders |
| App Settings | Default Launch Tab | Focus & behavior |
| Bubble Settings | Enable Chat Bubble | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Auto-hide Bubble | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Smart Regex Parsing | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Hide in Landscape / Video Mode | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Hidden Opacity | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Amount Hidden (Tuck Depth) | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Glow Ring Intensity | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Morning Briefing | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Evening Wrap-up | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Vacation Mode | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Streak & Practice Alerts | Bubble Settings (keep existing UI); related sections link here |
| Bubble Settings | Practice Alert Time (conditional) | Bubble Settings (keep existing UI); related sections link here |
| Security & Permissions | Usage Access | Security & permissions > Device permissions |
| Security & Permissions | Display Over Apps | Security & permissions > Device permissions |
| Security & Permissions | Battery Restrictions | Security & permissions > Device permissions |
| Security & Permissions | Uninstall Protection | Security & permissions > Privacy / Private notes vault |
| Security & Permissions | Anti-Screenshot Protection | Security & permissions > Privacy / Private notes vault |
| Security & Permissions | Secret Vault Passcode | Security & permissions > Privacy / Private notes vault |
| Security & Permissions | Export Recovery Phrase (conditional) | Security & permissions > Privacy / Private notes vault |
| Security & Permissions | Emergency Phrase Recovery | Security & permissions > Privacy / Private notes vault |
| Security & Permissions | Export Encrypted Backup | Data & recovery |
| Security & Permissions | Restore from Backup | Data & recovery |
| Security & Permissions | Safety Snapshots (Auto-Recovery) | Data & recovery |
| Security & Permissions | Data & recovery | Data & recovery |
| Security & Permissions | Record Diagnostics | Help & about > Diagnostics |
| Security & Permissions | Export Diagnostic Logs | Help & about > Diagnostics |
| Security & Permissions | Clear Recorded Logs | Help & about > Diagnostics |
| App Settings | Live transcription style | Appearance |

## Source evidence

- `duplicate_security_shortcut`: [PreferencesHubScreen.kt:322](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/PreferencesHubScreen.kt:322)
- `duplicate_theme_setter`: [AccountScreen.kt:357](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:357)
- `account_theme_subset`: [AccountScreen.kt:3684](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:3684)
- `account_theme_misclassification`: [AccountScreen.kt:3692](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:3692)
- `duplicate_backup_account`: [AccountScreen.kt:196](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:196)
- `duplicate_backup_security`: [SecurityScreen.kt:122](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/SecurityScreen.kt:122)
- `duplicate_snapshots_nested`: [DataRecoveryTools.kt:54](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/components/DataRecoveryTools.kt:54)
- `missing_notifications_control_state`: [SecurityScreen.kt:84](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/SecurityScreen.kt:84)
- `readiness_four`: [PreferencesHubScreen.kt:88](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/PreferencesHubScreen.kt:88)
- `readiness_five`: [PermissionUtils.kt:197](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/util/PermissionUtils.kt:197)
- `notification_always_system`: [TaskReminderReceiver.kt:97](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/service/TaskReminderReceiver.kt:97)
- `notification_priority_override`: [TaskReminderReceiver.kt:98](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/service/TaskReminderReceiver.kt:98)
- `theme_modes`: [AppThemeManager.kt:152](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/util/AppThemeManager.kt:152)
- `account_biometric_label`: [AccountScreen.kt:3613](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:3613)
- `account_biometric_active_literal`: [AccountScreen.kt:3627](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:3627)
- `account_biometric_navigation`: [AccountScreen.kt:359](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:359)
- `account_lock_app_action`: [AccountScreen.kt:376](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:376)
- `account_static_audit_logs`: [AccountScreen.kt:3868](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/AccountScreen.kt:3868)
- `clean_restore_label`: [BackupDialogs.kt:235](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/security/BackupDialogs.kt:235)
- `clean_restore_prune`: [BackupRestoreManager.kt:965](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/util/backup/BackupRestoreManager.kt:965)
- `smart_regex_ui`: [BubbleSettingsScreen.kt:74](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/BubbleSettingsScreen.kt:74)
- `chat_repeat_range`: [AssistantPreferences.kt:21](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/util/assistant/AssistantPreferences.kt:21)
- `ui_repeat_range`: [SettingsScreenHelper.kt:34](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/SettingsScreenHelper.kt:34)
- `chat_bubble_range`: [AssistantPreferences.kt:22](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/util/assistant/AssistantPreferences.kt:22)
- `ui_bubble_range`: [BubbleSettingsScreen.kt:235](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/BubbleSettingsScreen.kt:235)
- `ui_wait_range`: [SettingsScreenHelper.kt:61](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/SettingsScreenHelper.kt:61)
- `runtime_wait_range`: [BlockOverlayManager.kt:136](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/service/BlockOverlayManager.kt:136)
- `widget_first_instance`: [SettingsScreenHelper.kt:25](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/SettingsScreenHelper.kt:25)
- `notes_widget_config`: [NoteWidgetConfigureActivity.kt:97](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/widget/NoteWidgetConfigureActivity.kt:97)
- `search_title_identity`: [SettingsSearch.kt:65](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/components/SettingsSearch.kt:65)
- `cloud_copy`: [SettingsSearchCatalog.kt:9](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/components/SettingsSearchCatalog.kt:9)
- `vault_settings_notes`: [ArchiveVaultDialogs.kt:1555](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/notes/ArchiveVaultDialogs.kt:1555)
- `vault_security_status_only`: [SecurityScreen.kt:319](/workspace/Ayva/app/src/main/java/com/focusbyrj/app/ui/screens/SecurityScreen.kt:319)
