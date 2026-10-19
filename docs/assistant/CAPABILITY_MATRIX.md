# Current continuation

See [OFFLINE_ASSISTANT_IMPLEMENTATION.md](OFFLINE_ASSISTANT_IMPLEMENTATION.md) for B3–B11 additions and remaining acceptance. The table and B2 vertical-migration record below document the earlier checkpoint. The live registry now also declares checklist editing, guided preparation drafts and the bundled dictionary/bookmark capability. Native/device and independent human validation are not implied by those declarations.

# B2 capability inventory and migration

The runtime source is `util/assistant/AssistantCapabilities.kt`. Stable IDs describe reads, writes, native navigation, conversation and unavailable operations. Every `AssistantAction` has an exhaustive contract mapping. Domain owners still perform the actual entity, permission, strict-mode, timestamp and side-effect validation. A capability description is never execution authority.

## Existing feature coverage

| Domain | Reads / explanations | Changes / native behavior | Owner and current limit |
| --- | --- | --- | --- |
| Tasks | Lists, filters, stable selections, dates/details/recurrence | Create, edit, complete/reopen, guarded Trash/undo | AssistantTaskLanguage, AssistantDialogue, AssistantActionExecutor; groups reviewed, exact row guards, task-only recovery |
| Checklist subtasks | Native task checklist | Conversational checklist editing unavailable | Native task editor; B5P adds typed edits later |
| Planning | Real saved-task ordering, skips, priorities, suggested work blocks | Does not silently reschedule or create a project plan | AssistantPlanning; trip/exam draft generation/save is B5P |
| Habits | Goals, schedules, reminders, today's saved progress | Create, log counts, edit schedule/goals/reminders, archive/restore | AssistantHabitConversation/Editing and executor; same-day guarded progress undo and reward caps |
| Routines | Named schedules, enabled state, app sets and draft inspection | Create/edit, enable/disable with review | AssistantRoutineConversation, AssistantFeatureActions/executor; strict/focus protections remain |
| Apps | Installed eligible app catalogue, saved rules, blocking explanations | Lock/unlock named apps/groups/categories/exceptions; daily limits | AssistantAppControl, AppCatalog, AssistantFeatureActions; manual unlock does not remove active routine rules, no temporary timed-lock expiry |
| Focus | Actual countdown/status | Start a supported countdown, review stop | FocusSessionManager; cannot replace a running session, no implicit change to app rules |
| Learning | Actual recorded results and mistakes | Open existing native Drill/Blitz/vocabulary tests | AssistantLearningFeatures/Language; no fabricated scores/timing, no embedded replacement test UI |
| Vocabulary | Existing bundled idioms and one-word substitutions | Reviewed bookmark changes and native practice | AssistantVocabularyFeatures/FeatureActions; expanded dictionary and arbitrary sentence explanation unavailable |
| Settings | Actual owner/current value/default/type/range/enum | Validated preview, current-state guard, save and guarded undo | AssistantPreferences is the source schema; generated read/write contracts and setting capability adapter |
| Permissions / usage | Actual permission state, safe diagnostics, foreground-event estimates | Open Android/native settings | AssistantDeviceFeatures/Usage; missing permission/data is unknown/unavailable, not zero or proof of delivery |
| Rewards / streaks | Existing stored balances, progress and receipts | Established native screens; no automatic chat claim inferred from a status request | AssistantDeviceFeatures, native owners |
| Backups | Recorded success timestamps only | Open native Backup & Restore | No chat archive creation, export, inspection or restore; no Notes counts/contents |
| Transcription | User's live/final speech within current capture | Open existing transcription UI; explicit reviewed task extraction | OnDeviceTranscriber/AssistantTranscriptFeatures; no saved-media access, online fallback or model download; partial captions cannot execute |
| Conversation / preferences | Existing authored dialogue, repairs, explicit allowlisted memory | Show/change/forget supported assistant preferences | AssistantDialogue, ConversationSupport, Personalization; wider wit/empathy packs remain B9 |
| Calculations | Bounded arithmetic and time conversions | No code execution | AssistantCalculator |
| Private Notes / vault | Generic packaged help only | Explicit native-screen navigation only | No chat data capability, Notes CRUD/index/search/metadata or consent bypass |

## First complete vertical migration: settings

`AssistantPreferenceSpec` owns the settings group as well as its existing key, storage owner, default, type, range and choices. Settings-group help is generated from that schema. `AssistantSettingCapability` exposes current reads, validated actions, a canonical preview query and common help hints. The setting response renderer resolves keys through that same adapter. No help-only setting key grants write access.

The newer response controls, older `TalkActionChips`, and direct `TalkAction.DirectPrefUpdate`/`RoutineToggle` calls all normalize legacy mutation chips to canonical query requests. Their execution opens/dispatches a preview; a successful navigation is not a successful write. Type mismatches, unknown keys, invalid values and invalid routine IDs are rejected. The existing expected-value guard still rejects outside edits at confirmation.

The coordinator checks declared review policy before applying actions selected through clarification choices. Selecting an ambiguous routine no longer skips the required review. Clear low-risk task creation and existing single-task edit flows retain their behavior. Domain validation remains mandatory at the executor even after confirmation.

## Incremental boundary

This batch does not replace all language parsers or move all domain reads into one universal dispatcher. Existing domain handlers remain behind the typed action contracts; settings has the complete shared read/preview path. B3 addresses competing interpretations and B4 unifies context. Future writes must add a deliberate typed action mapping rather than gain permission from matching help text.

Use `/talk capabilities` or `/talk capabilities tasks` (also planning, habits, routines, apps, focus, learning, settings, device, backup, conversation) to inspect supported operations and limits. These are informational replies and do not execute their displayed example sentences. Help explicitly labels planned/unavailable operations. Usage Access and on-device speech prerequisites are checked live; an unavailable speech service does not prevent opening the transcription UI to see its explanation. These checks do not assert language-pack readiness or OS delivery. Opening this help invalidates old pending confirmations.

## Validation

Verified on 2026-10-18: all 861 tests passed (zero failures/errors/skips), followed by successful debug and optimized unsigned release APK assembly. Both APKs contain capability-help and selected-task recovery markers. See STATUS.md for commands, logs, hashes and pending hosted/device checks. Regression coverage includes generated settings groups, live owner disagreement, published preview choices, old direct chip execution, invalid keys/types/values, routine identity and ambiguous choices, stale confirmations, truthful future-feature help and unchanged natural task creation. Existing Notes-isolation tests and the dependency guard remain required. The guard now includes the legacy chip renderer and rejects direct preference/executor writes from preview-only chip paths. No UI layout, cats, task receipts, briefs, picker design or native test-window changes are part of B2.
