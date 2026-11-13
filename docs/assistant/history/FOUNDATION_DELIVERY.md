# Assistant foundation and delivery history

Original foundation/batch delivery records, preserved with their verification
limits. Version, test-count and pending-work statements describe the recorded
checkpoint; they do not establish current CI, publication or device acceptance.
See [assistant status](../STATUS.md) and the [build guide](../../development/BUILD_AND_TEST.md).

## Contents

- [Ayva: offline conversation and action batches](#original-offline-batches)
- [B0/B1: reproducible verification and Notes isolation](#b0-b1-foundation)
- [Offline assistant continuation: B3–B11](#b3-b11-continuation)
- [Remaining core implementation pass](#core-completion)

<a id="original-offline-batches"></a>

<a id="original-offline-batches-ayva-offline-conversation-and-action-batches"></a>

## Ayva: offline conversation and action batches

> Completed historical implementation record. The active queue is [the conversation-quality plan](../CONVERSATION_QUALITY_PLAN.md); current status is consolidated in [delivery summary](../STATUS.md).

This implementation extends the existing deterministic assistant. It adds no language model, network service, model download, or new dependency. Natural chat, slash aliases, clarification choices and action chips share the same action grammar and validated task execution. Existing arithmetic, vocabulary, profile, economy and diagnostic cards remain available.

<a id="original-offline-batches-what-changed"></a>

### What changed

| Batch | Result |
| --- | --- |
| 1: reliable actions | Typed task creation, title/details/date/priority/persistence/recurrence edits, completion, reopening and Trash. Shared completion handles task cards, notification actions and existing task helper callers, including recurring tasks, history, reminders, widgets and reward receipts. |
| 2: conversation continuity | Encrypted local conversation state survives activity/process recreation and expires after 15 minutes of inactivity. It retains task/habit identities, displayed candidates, missing fields, composer flags, scoped confirmations and one undo operation. |
| 3: useful app capabilities | Habit creation and capped daily logging, routine lookup/enabling/disabling, 18 validated settings, and a real persisted focus countdown shared with the dashboard. |
| 4: language and context | Natural follow-ups, duplicate resolution, ordinals, corrections, conservative typo matching, compound previews, next-task planning, small talk, local personalization, packaged-help follow-ups, arithmetic and time-unit conversion. |
| 5: integration and regression | Room-backed action/conversation tests, existing regression tests, release compilation and this implementation record. See validation below. |

<a id="original-offline-batches-try-these-conversations"></a>

### Try these conversations

No slash command is required for these examples:

```
Buy milk tomorrow at 6pm
Move it to Friday
Make it priority
Rename it to Buy oat milk
Remind me about it tomorrow
Show its details
Complete it
Undo
```

Date-only rescheduling preserves the task's existing hour/minute; an explicit time replaces it. Removing a due date cancels its reminder without deleting the task. Recurring completion creates the next occurrence, and guarded undo removes that generated occurrence if it has not changed elsewhere.

```
Remind me tomorrow at 6pm
→ What should I remind you about ...?
Call the dentist
```

```
Change reminder interval
→ What should Reminder interval be?
30 minutes
→ Set Reminder interval to 30 minutes?
Yes
Change it to 45 minutes
Yes
```

Settings validate known keys, types, signed numbers, units, ranges, enums and clock times. Conflicting alternatives require clarification. Direct preference chips and older confirmation cards use the same registry and side effects. Theme refresh, bubble service changes, vacation state and reminder scheduling use the existing owners. A confirmation is rejected if its setting or routine changed elsewhere.

```
Add habit reading 2 times a day
Yes
Log habit reading
Log habit reading 2 times
My habits
Undo
```

Habit logging caps at the target recorded for that day, including when the tracker target is edited mid-day. Partial progress is not described as a completed daily goal. Undo/relogging cannot repeatedly earn the same daily completion reward. Rich interval/reminder configuration remains in the habit tracker/card.

```
My routines
Enable morning routine
Yes
Disable routine Study
```

Names resolve to stored identities; ambiguity produces choices. The reply distinguishes enabled schedules from currently active windows. Active strict routines and routines during an active focus session cannot be disabled through this action path. Routine creation/editing remains in Schedules.

```
Start focus for 25 minutes
Time remaining
What should I do next?
Stop focus
Yes
```

The timer uses a persisted elapsed-time deadline in the same boot, a wall-clock deadline after reboot, an app-owned ticker and an alarm fallback. Dashboard and chat observe the same countdown; closing the dashboard does not end it. Starting a second timer cannot replace a running session. Old active-only session flags migrate to a bounded 25-minute session. Inexact-alarm delivery can be delayed by Android when exact alarms are unavailable; current state reconciles against the deadline on restore/status checks.

```
Buy bread; call Mum tomorrow
→ Preview both actions
Yes
Undo
```

Up to five explicitly separated actions are previewed before mutation. Ordinary “bread and butter” stays one task. A modifier such as “buy milk tomorrow and make it priority” applies to that proposed task. Unsupported or ambiguous segments reject the preview without making changes. Execution is sequential and stops/report failures; a cross-store batch is not an atomic transaction. Task-only batch undo restores the applied task changes together; mixed batches retain the last reversible change.

```
Call me Raj
Hi
I feel overwhelmed
Plan my day
What were we talking about?
Forget my preferences
```

Greetings and plans use live tasks, priorities, due dates and the running focus timer. Planning suggests one manageable step rather than changing dates or inventing durations. Name and reply-detail preferences are stored locally until forgotten; the six-input conversational buffer expires with the session. “Forget my preferences” clears conversational memory and personalization while preserving tasks, habits and notes.

```
What is 18 * (4 + 2)?
Convert 90 minutes to hours
Start math drill hard 20 questions
Teach me a word
```

Arithmetic uses a bounded parser rather than code execution. Natural learning requests open existing interactive exercises.

<a id="original-offline-batches-execution-and-data-design"></a>

### Execution and data design

```
Input → offline coordinator → typed action / missing field / choice / confirmation
                         ↘ existing router → FAQ and specialized feature cards
Typed actions → common executor → Room/preferences → reminders, widgets, history, rewards
                      ↘ guarded undo and expiring encrypted session
```

- Confirmations carry a random nonce and stored targets, rather than allowing an unqualified “yes” to run a newly selected operation.
- Bulk task previews snapshot target IDs and update versions. New tasks cannot silently join an old preview. Changed targets require a fresh review.
- Candidate numbers refer to the previously displayed identities, not a new database sort order.
- Confirmations and choices are consumed before mutation. Input receipts prevent repeated processing within the retained session. This is not a cross-process database outbox or a guarantee of exactly-once side effects across crashes.
- Task undo checks complete expected rows before restoring anything, and refuses to overwrite newer edits. Creation undo moves the created task to Trash. Completion rewards remain earned, with receipts preventing repeat rewards on the same identity.
- Trash actions first require the existing encrypted recovery snapshot, and fail without deleting if it cannot be written. Task group writes use Room transactions. Reminder/reward/widget updates happen afterwards. A crash or system failure between data commit and external side effects can still need recovery; failures are not represented as success.
- The encrypted session is separate from the existing short-lived chat transcript. Clearing chat also clears its pending conversation/undo state.
- Screen-usage context now uses Android usage data when permission exists and represents unavailable usage as unknown. The existing focus-activity metric is reported separately; it includes the app's historical activity scoring semantics.
- Note bodies and vault content remain outside chat. Asking to read/search/edit notes opens the existing privacy boundary.
- The WindowManager app-blocking overlay mechanism is unchanged.

<a id="original-offline-batches-architecture-map"></a>

### Architecture map

- `util/assistant/OfflineAssistant.kt`: serialized turn coordination, slots, choices, confirmation guards, action receipts, compound previews and undo selection.
- `AssistantAction.kt` / `AssistantActionExecutor.kt`: typed operations, bounded validation, database changes and domain side effects.
- `AssistantSession.kt`: encrypted, expiring state with atomic file replacement.
- `AssistantTaskLanguage.kt` / `AssistantCompoundParser.kt`: task targeting, modifiers, conservative language rules and safe compound splitting.
- `AssistantPreferences.kt`: shared preference schema, conversion, validation and side effects.
- `AssistantDialogue.kt` / `AssistantCalculator.kt`: live planning, small talk, task views, packaged help and bounded calculations.
- `util/FocusSessionManager.kt`: shared persistent focus countdown; `FocusSessionEndReceiver` and boot restore reconcile background expiry.
- `BubbleChatActivity.kt`: input/card integration; `TalkActionChips.kt` awaits database actions; `TaskReminderHelper.kt` uses shared task mutations.
- `AyvaIntentRouter`, `OfflineNluEngine`, `AyvaChatCommands`, `ChatTaskDraft`: existing routing and task parsing, retained as compatible fallbacks.
- `AyvaTalkEngine`: packaged knowledge, diagnostics and remaining specialized app features. Duplicate imperative task CRUD was removed from this legacy path.

<a id="original-offline-batches-remaining-boundaries"></a>

### Remaining boundaries

This is a bounded English rules assistant. It cannot match a language model's open-ended reasoning, arbitrary paraphrases, long-term semantic memory or general knowledge. Unknown/ambiguous wording should lead to help or a clarification; it should not imply successful action. Some legacy specialized features (app restriction configuration, economy and exercise interactions) still use their established handlers and cards rather than the new executor. Timed chat app-block requests remain unsupported and explain that limitation before changing restrictions. Voice transcription depends on the device's recognition services; the new text assistant itself requires no model or network.

Physical-device checks are still needed for Compose interactions, speech, rotation/process death, notification permissions, exact/inexact alarms, reboot and OEM background restrictions. Unit/Robolectric tests do not establish those device behaviors.

<a id="original-offline-batches-validation"></a>

### Validation

The final `:app:testDebugUnitTest --offline` run passed **496 tests, 0 failures, 0 errors, 0 skipped**. This includes 59 new Room-backed assistant tests, the preceding 35 chat tests and the existing app/security regression suite. Bulk-completion tests now confirm the scoped preview before checking saved rows and absent deletion tombstones; bulk-Trash tests retain their recovery-snapshot assertions and use the displayed nonce rather than an unscoped static command.

Coverage includes task corrections and recurrence, missing fields, duplicate decisions, displayed ordinals, changed targets, stale/replayed confirmations, unrelated-topic invalidation, preference correction and validation, capped habits and reward replay, routine protections, real countdown expiry/restore, encrypted/expired memory, task/habit/notes topic handoffs, compound previews/undo, personalization clearing, safe arithmetic and the notes privacy boundary.

The final implementation's `:app:assembleRelease --offline` build passed, including R8 optimization and resource shrinking. It produced `app/build/outputs/apk/release/app-release-unsigned.apk` at version 2.0.3 (code 103). The build is unsigned in this environment. `git diff --check` passed. Release metadata was subsequently bumped to 2.0.4 (code 104); the local APK above is the pre-bump validation artifact.

---

<a id="b0-b1-foundation"></a>

<a id="b0-b1-foundation-b0b1-reproducible-verification-and-notes-isolation"></a>

## B0/B1: reproducible verification and Notes isolation

Implemented and locally validated 2026-10-18: 849 tests passed, debug and optimized unsigned release assembly passed. See STATUS.md for exact commands, first-run failures, evidence and pending CI/device checks. This record describes changes made after the roadmap; earlier response-control/conversation work remains present.

<a id="b0-b1-foundation-b0-changes"></a>

### B0 changes

- Preserved all pre-existing modified/untracked files (223 files, including design assets) in `/workspace/scratch/ayva-before-assistant-b0/working-tree.tar.gz`, with per-file hashes in `manifest.json` and the original tracked diff in `tracked.patch`. Base commit: `01c692116526e0f78bea043403b6539914f93146`. This is a local recovery artifact, not a GitHub checkpoint or permission to overwrite newer edits.
- Pinned the wrapper distribution to verified Gradle 8.9, matching the AGP 8.7.3 build used successfully by this project. Java/Kotlin app bytecode still targets 17; Gradle/test runtime is JDK 21.
- Added `.github/workflows/validate.yml`: tests plus debug and optimized release assembly for PRs/main/master pushes, read-only repository permissions, no publication and no signing-secret requirement. It retains reports on failures and runs the chat dependency guard.
- Release workflow now uses the same wrapper/JDK and requires unit-test success before publishing. Existing release triggers remain unchanged.
- Debug builds reuse an existing local key or fall back to AGP's standard generated debug signing configuration on fresh checkouts.
- Added portable commands and environment limitations in BUILD_AND_TEST.md. The optional cloud C2 workaround is now a checked-in test-only init script; normal CI does not use it.

<a id="b0-b1-foundation-b1-changes"></a>

### B1 changes

The chat executor previously called the all-data recovery helper, which read Notes during task Trash. It now writes encrypted selected-task snapshots through `TaskRecoveryStore`; this store depends on no Notes DAO, vault state, media attachment helper or all-data backup manager.

A reviewed Trash group executes as one task transaction. It builds exact before/after rows and durably writes/verifies their recovery snapshot before changing task rows. A storage/encryption/verification failure prevents the task write. The snapshot has an explicit `selected_tasks_v1` scope, count/version, stable identities and no Notes fields. A dedicated directory and bounded retention isolate it from all-data backup rotation.

Task snapshots are discoverable in native recovery. Their recovery is dispatched before any all-data safety snapshot or Notes operation. Recovery validates scope/encryption/path and every current task against its expected post-operation row; a missing or externally edited task rejects the entire group. A task-only rollback snapshot precedes recovery's database writes. Repeated recovery refuses to overwrite the restored row. Reminders/widgets refresh afterwards; an error there reports that tasks were restored but refresh failed. Chat offers no snapshot-file restore capability.

Shared encrypted file IO was extracted to a codec with no database access. Native daily/pre-operation all-data backup behavior still uses that codec and retains its own Notes access outside chat. Existing all-data formats are not reinterpreted as empty Notes collections.

Chat now uses a storage-independent `OnDeviceTranscriber`. The native Notes audio manager delegates transcription to that same component and retains its saved-media playback/copy features separately. Existing native speech tests therefore continue exercising the shared recognizer behavior. Chat no longer holds an audio manager that can open saved attachments. The existing voice dialog is shared presentation only: supplied captions, callbacks and a link to Android Voice settings.

Assistant session persistence no longer depends on the saved-media encryption API. It uses the storage-only snapshot codec and authenticated encryption. Old media-format sessions intentionally expire on upgrade, clearing temporary context/confirmations without touching tasks, preferences or saved Notes/media. Current-format sessions retain the existing 15-minute lifetime and size bound.

A legacy status branch no longer constructs an empty `NoteEntity` list. Its Notes navigation label omits the fabricated zero count; brief layout and cards remain unchanged.

<a id="b0-b1-foundation-boundary-audit-and-validation-coverage"></a>

### Boundary audit and validation coverage

Inspected conversational core/domain helpers, legacy Talk engine, router/actions, chat UI callbacks, task reminders, backup status receipts and speech paths. Backup status reads only existing success timestamps; generic Notes help/navigation does not fetch private data. Task state, native learning launches, settings and app controls continue through existing owners. No Note database/content capability is introduced into those paths. Wider capability contracts remain B2 work.

`tools/check_chat_boundary.py` rejects direct Notes/vault/all-data/media capabilities in the inspected core, routing, chat UI, speech and task-recovery sources. It is an architectural regression guard, not a complete transitive static analyzer or an OS security sandbox.

`AssistantNotesIsolationTest` seeds a synthetic private Notes row and instruments SQL touching its table. It exercises chat task Trash/undo, privacy/help/status and cross-topic replies, storage failure, native task-only recovery through a Notes DAO that rejects every call, externally edited groups, corrupt/foreign snapshots, repeated recovery, on-device speech and expiring old encrypted sessions. A separate explicit native-backup smoke test confirms independent all-data backup still includes Notes. Existing bulk-deletion security tests now assert the task-only recovery location.

The first focused run caught per-task bulk snapshots and delayed transaction-bookkeeping observations in the query instrument. The implementation now groups Trash execution; the query monitor observes actual private-table statements rather than unrelated transaction completion. These were corrected before final validation.

Physical Android checks, fresh hosted CI execution and future adversarial language/context work remain separate from local unit/build results. No model, network inference or dictionary data was added in these batches. No version bump or publication is part of this work.

<a id="b0-b1-foundation-final-local-verification"></a>

### Final local verification

All 849 unit tests passed in a test-only invocation (zero failures/errors/skips), followed by successful debug and optimized release assembly. A combined tests/R8 run earlier exposed a timing-sensitive native Notes deletion test; its 13-test class and the complete suite passed separately afterwards. CI and documentation now use separate test/build invocations. The assertion was not disabled or changed. Dependency boundary checks, workflow YAML, documentation links and whitespace checks passed. Hosted Actions and physical Android checks remain unrun. See STATUS.md for logs and APK hashes.

---

<a id="b3-b11-continuation"></a>

<a id="b3-b11-continuation-offline-assistant-continuation-b3b11"></a>

## Offline assistant continuation: B3–B11

Continuation base: `1c8618e540d066366115b6f65fcaf39cbe6309d3` (the verified B2 push). Version remains 2.0.8 / 108. The user superseded the stop-after-B2 instruction and requested the remaining implementation. Consult [STATUS.md](../STATUS.md) for current validation/publication state. This is bounded deterministic English conversation, not a language model or a promise of perfect understanding.

<a id="b3-b11-continuation-interpretation-and-context-b3b4"></a>

### Interpretation and context (B3/B4)

`AssistantInterpretation` supplies a common decision before the coordinator, router and legacy NLU can dispatch. Negation, hypothetical/reported/quoted examples and uncertain command typos cannot grant execution authority. Recognized commands remain explicit; unanchored action mentions ask for clarification. Typos in command words can suggest a correction but do not execute a fuzzy write. Explicit capture and quoted answers to live task/habit title fields preserve literal contents.

Existing domain parsers remain responsible for their grammar. This is an incremental shared arbitration layer, not a universal semantic parser. `/talk` retains app information/settings semantics and never implicitly creates tasks.

Legacy write classification also requires an anchored request; loose mentions of a routine starting, an app allowing something, or a report being done grant no write authority. Clearing history requires an exact chat-target request. An otherwise unrecognized dated message offers Add task / Ask Ayva instead of silently creating a task; clear imperative task captures such as `buy milk tomorrow` stay direct. Unknown app-help replies clear the implicit task target.

References and reviews have their own absolute 15-minute limits. Unrelated activity cannot refresh them. Persisted process identity invalidates confirmation tokens and pauses plan saves after process recreation. Stored lists and suspended questions can be inspected/resumed under their expiry/entity checks. A restored guided draft needs fresh review. Other active pending questions are cleared after process recreation; the user can restate them. Old confirmation authority is never restored.

Task and routine previews capture full records, including changes that don't advance timestamps. Reviewed task/routine writes recheck those snapshots inside their Room transaction. Multi-field task actions advance the expected snapshot using their own undo receipts, rather than adopting an intervening native edit. Settings also recheck the owner value when executing. An exception clears pending confirmation authority and implicit mutation references, including an earlier preview interrupted by a failed read. Oversized messages and unavailable quoted-reply targets explicitly revoke old task/plan controls too.

<a id="b3-b11-continuation-task-conversations-and-guided-planning-b5b5p"></a>

### Task conversations and guided planning (B5/B5P)

Extended task views combine supported status/priority/date/recurrence/undated/title filters and retain displayed identities. Examples:

- `Show overdue priority tasks`
- `Show priority unscheduled tasks about read`
- `Show recurring tasks this week`
- `Show tasks without a due date`

Existing list selection, saved-task receipts, reschedule cards, recurrence and undo behavior remain in place. Clock-only repairs preserve the date in the current preview or selected saved task. `Yes but at eight` asks AM/PM and invalidates the earlier confirmation; a qualified yes is not permission to apply that earlier preview.

Start a guided unsaved draft with `help me plan my trip`, `can we plan things for a trip`, `plan my exam` or `plan my day from scratch`. Collaborative/polite requests share the same grammar as direct requests, including holiday/vacation preparation. A generic planning request asks for scope; a following answer such as `a trip to Goa` selects the domain and retains the supplied goal. Explicit Vacation Mode questions remain setting help; generic trip/holiday words are not aliases for that setting. Existing quick `plan my day` continues to order saved tasks and suggest work blocks.

`Help me plan my trip to Goa next Friday` retains the supplied goal as context and asks for concrete steps and an explicit deadline; it does not infer bookings or treat the destination phrase as a task. `Goal: Visit Kochi!` changes that context, and `what are we planning?` recaps it. Goal text is included in saved task descriptions. Explicit `goal:`, `name:`, `steps:` and `topics:` fields preserve literal text. Unknown planning domains ask which supported planning flow to use. `Help me plan my day for 2 hours` orders real saved tasks within a tentative budget; it does not create a task from the request.

The guided draft contains stable item IDs, revision, domain, timestamps, timezone, supplied constraints, provenance and current question/review. Questions collect steps/topics, an optional shared deadline and optional whole-plan time budget. They have inline equivalents and a user-opened themed sheet. Closing the sheet never saves or discards the draft. Sheet answers and typed answers use the same coordinator; controls bind draft ID/revision and save authority. No sheet calls a DAO.

Useful edits:

- `Rename 2 to Arrange transport`, `remove 2`, `move 2 before 1`
- `Deadline: Friday at 6pm`, `deadline 2: tomorrow at 4pm`, `deadline 2: none`, `deadline 2: shared`
- `Budget: 60 minutes`, `effort: 90 minutes` (explicit total estimated effort)
- `Name: Goa trip preparation`, `one checklist`, `separate tasks`
- `Back to my plan`, `cancel planning`

Stated effort exceeding the available budget blocks save until revised. Unknown effort remains unknown. Deadlines are not scheduled work intervals or estimates of completion. Independently dated entries require separate tasks; checklist children share the parent's deadline and have no separate reminders. Dated work blocks, calendar availability, duration/dependency solving, nested checklists and arbitrary free-form replanning are not implemented.

Exam drafts use user-supplied topics. `Suggest study steps` optionally expands up to six topics into editable learn/practise/revise entries; it invents neither syllabus nor effort. Trip drafts optionally offer a generic preparation checklist. Every suggested entry is labelled optional and can be removed. There is no lookup or invention of current travel requirements, weather, prices or bookings.

<a id="b3-b11-continuation-save-recovery-and-undo"></a>

#### Save, recovery and undo

Only the current **Add tasks** control saves a plan. Its nonce binds the current revision and review; corrections, interruptions, expiry, process recreation and timezone changes require fresh review. A casual yes never saves a plan. A timezone change is shown on renewed review. Existing active duplicate titles block the save rather than overwriting the original.

`AssistantPlanStore` writes tasks and a `AssistantPlanReceipt` in one Room transaction. The receipt stores the draft/revision/concrete-task-payload hash and resulting task identities, so retrying after a commit/session failure returns the existing batch, including after fresh review increments its UI revision. The hash excludes generated row IDs/timestamps and review metadata; changed task contents still fail against an existing receipt. A simulated failure before receipt insertion rolls back the tasks. Receipt pruning only removes completed entries older than a day, beyond draft authority's lifetime.

Reminder/widget effects run after commit. Their pending marker remains until reconciliation succeeds. `Retry plan reminders` retries pending effects without creating tasks. Reconciliation skips rows changed since this save; it cannot resurrect, overwrite or schedule a newly changed row through an old snapshot. A successful scheduling request does not prove Android delivered a notification. The journal is task-only and never calls a Notes/all-data snapshot helper.

Plan undo uses the established guarded task receipts. Native edits prevent an overwrite. After saving, displayed task IDs are recorded in draft order, so `the second one` resolves to the saved plan's second task. Completing a checklist child does not complete its parent.

`AssistantChecklist` supports reading, adding, completing/unchecking, renaming and removing numbered checklist entries of one resolved parent. Every edit is reviewed, verifies original checklist contents and preserves stable IDs. Legacy children missing IDs get deterministic identities on read and persist them on their next edit. Duplicate child IDs require repair in the native editor.

Database migration: Focus database 12→13 adds only `assistant_plan_receipts`; the existing task/checklist schema stays intact.

<a id="b3-b11-continuation-app-controls-learning-and-settings-b6b7b8"></a>

### App controls, learning and settings (B6/B7/B8)

Existing full-inventory lock/unlock, categories, exceptions, routine editing, habit schedules/logging/undo and focus behavior were reused rather than recreated. New bulk reviews bind the visible installed inventory. Installing/removing/renaming an eligible app invalidates an old review. `Also leave Reader unlocked` and `only social apps instead` refine the current selection and preserve applicable exceptions. Picker layouts and WindowManager blocking are unchanged. Manual unlock cannot bypass an active strict routine/focus protection. Routine toggles now have a full-row guarded undo receipt.

`How did I do last time` reads the latest valid stored test result; it never invents scores or question timings. `Quiz me on saved words` filters native vocabulary practice to real bookmarks, including unlearned bookmarked words without falsely marking them learned. A dictionary's `quiz me on it` targets that saved OWS entry in the separate native test window. Native Drill/Blitz/vocabulary test UI is retained.

Native vocabulary quiz choices exclude the target and normalized duplicate distractors, including unlearned bookmarked targets. Questions without three distinct alternative answers are skipped with an honest empty-result state rather than becoming one-option automatic successes. Distractor pools are fetched once per quiz, and candidate questions are bounded before construction. This changes question data, not the test window's layout.

Settings read/change/help still use the 24 allowlisted actual preference schemas. Contextual `what does that do`, `what is it set to now`, `why didn't it work` and `change it back` use the current owner or matching guarded undo. A setting undo cannot undo an unrelated task. Permission diagnostics distinguish saved configuration, observed prerequisites and unknown Android/OEM delivery. Generic catalogue help adds guided-planning/dictionary topics and accurately describes on-device-only speech.

<a id="b3-b11-continuation-dictionary-b7d"></a>

### Dictionary (B7D)

See [DICTIONARY_DATA.md](../DICTIONARY_DATA.md) for the pinned source, notices, reproducible importer, measured data size and hardware limitations.

The dictionary supports exact word/phrase meanings, multiple stable source senses, source labels/examples where present, selected plain-English alternatives, inflection suggestions and contextual follow-ups. Existing idiom/OWS `look up` behavior is preserved; those native lookups now use indexed exact queries instead of loading every entry for a match. Unknown words remain unknown and never become tasks. Historical dictionary controls bind the selected word/sense and cannot save whichever unrelated word happens to be current.

`Save this word` previews a source-sense bookmark. Its atomic vocabulary transaction links the source sense to an existing matching OWS row or creates one; it doesn't mark the word learned. Undo removes the bookmark, preserving the imported library entry for reuse and its source linkage. The new vocabulary 2→3 migration adds only a source-link table, retaining the original 1,773 idioms and 2,027 OWS entries. The bundled v1 database migrates through both existing/new migrations on fresh install.

Whole-sentence explanations support explicitly named patterns: `I would [not] have gone had I known`, `I used to …`, `I am used to …` and `I am [not] feeling under the weather`. Negation and past modality remain explicit. Unsupported constructions, sarcasm and arbitrary sentence paraphrasing are not claimed. Dictionary definitions/examples are inert text, including words such as delete, lock and complete.

<a id="b3-b11-continuation-conversation-and-preferences-b9b10"></a>

### Conversation and preferences (B9/B10)

`AssistantSocial` contains finite conversation packs for check-ins, good/bad news, light banter, boredom, empathy/listening, eight rotating jokes with explanations, four riddles, a bounded word-chain game, two branching story openings and six hobby/interest topics. Follow-ups resolve within the active pack. Game/story controls bind the current conversation frame; old controls cannot resume an unrelated branch or authorize a mutation. Games end, can be stopped, and never grant productivity rewards.

`Just listen`, `no advice` and `be serious` affect the current exchange, without saving an emotional profile or immediately forcing planning. A casual exam/trip disclosure offers planning separately; only an explicit choice/request enters it. Past-tense `I finally finished it` after general conversation asks what was finished rather than completing an old task. Existing task-aware greeting content remains; a new greeting clears old mutation/reference authority. General conversation is bounded, can be repetitive outside the authored branches, and doesn't provide arbitrary knowledge or human-level understanding.

Durable tone, quiet conversation and task-suggestion settings require explicit allowlisted preference requests:

- `Remember that I prefer a direct tone` (warm/playful/direct)
- `Remember quiet conversation`
- `Remember task suggestions` / `remember no suggestions`
- `What do you remember about me`, `forget my reply preferences`, `forget my preferences`

Suggestions are off by default, appear only in chat, use actual saved task facts, and have a 30-minute cooldown. Dismissal quiets them for a day. They never change a task or send a notification. Quiet conversation suppresses them. Forgetting preferences clears the suggestion receipt/context without deleting tasks, habits or private data.

<a id="b3-b11-continuation-validation-and-remaining-gates-b11"></a>

### Validation and remaining gates (B11)

Use the exact runs/artifacts in STATUS.md. Relevant regression groups cover interpretation boundaries, Notes sentinels/query monitoring, source-backed dictionary lookup/credits/bookmarks/native quiz filtering, atomic plan rollback/retry, stale controls, process/expiry, native edits, scope changes, task/date corrections, settings owners/undo and protected existing flows. Test counts are evidence of executed coverage, not a quality score or proof for arbitrary language.

No physical Android device is connected in this environment. P6/B11 physical UI, large text/TalkBack, rotation/keyboard, real speech availability, airplane mode, OEM overlay/reminder delivery, device latency/memory/battery and representative-device dictionary budgets remain unverified. There has been no independent human conversational-quality review or genuinely held-out human-authored language evaluation. These gates remain open; do not label the complete roadmap fully accepted from local tests alone. [DEVICE_ACCEPTANCE.md](../DEVICE_ACCEPTANCE.md) provides the concrete checklist.

---

<a id="core-completion"></a>

<a id="core-completion-remaining-core-implementation-pass"></a>

## Remaining core implementation pass

Status: implementation and necessary checks complete for this bounded delivery. Broader audits and device/release acceptance are deferred at the user's request. This record covers the remaining main implementation items identified in the preceding status update. It does not claim unrestricted language understanding, measured conversational quality, or completion of the roadmap's open research frontier.

<a id="core-completion-delivered-behavior"></a>

### Delivered behavior

- **Work sessions and reminders:** a task can hold several stable-ID intervals independently of its deadline and estimated effort. Chat can add, move and remove sessions, split work, enable/disable work-start reminders, and preserve protected work. Separate alarm identities, live database validation at delivery, encrypted registration/delivery bookkeeping, and idempotent notifications prevent stale sessions from becoming task completions or deadline alerts. Boot, app startup, restore, edits, completion, trash and undo reconcile reminders; WorkManager retries failures. Notification/exact-alarm availability is reported from Android's actual state.
- **Scheduling and replanning:** allowed weekdays and excluded dates, buffers, per-day work budgets, maximum session lengths, earliest starts, prerequisite gaps and estimate ranges feed the interval scheduler. New automatic schedules reserve the upper estimate. Split allocation is published only when the whole step fits. Unschedulable work stays open with a reason. A move can replan its dependent closure while preserving other steps; global flexible replanning preserves protected commitments. Scheduling covers commitments recorded in Ayva. It does not inspect an external calendar or claim a globally optimal result.
- **Native integration:** `/plan use existing task Research` adopts canonical work and its parent/prerequisite closure without duplicating rows. A fresh single-task reference can also hand off with “plan it” or bare `/plan`; stale references cannot. Completed tasks and occasions stay read-only context. Saved-task undo skips unchanged context, including context edited elsewhere. Task cards expose parent/child navigation. The editor changes the canonical parent, prerequisites, estimate, work intervals and reminder setting; it can clear a work interval while preserving the deadline. Native saves reject stale versions, graph cycles, overlapping work and known prerequisite timing violations. Failed saves keep the draft open. Picker times use the saved timezone, and invalid or ambiguous local times require another choice.
- **Context and conversation:** explicit today/tomorrow agenda views include intersecting work intervals and exclude distant work/occasions; deadline-specific views retain deadline semantics. Users can explicitly save/resume named discussion summaries and inspect/delete them. Resumption restores assertions and options while clearing old questions and action authority. Listening mode retains first-person context without creating tasks. Cost/time comparisons accept ranges; overlapping ranges do not produce a certain numeric winner, and a possible hard-limit violation blocks choice handoff. Talk's unknown-response path asks for context instead of presenting an app-only feature menu. Plan commands accept polite prefixes and bounded spelling corrections without rewriting titles.
- **Planning breadth:** reusable user-created templates retain structure, prerequisites, estimates and scheduling policy while dropping task links, dates and authority. Occasion context is excluded and edges to it are removed, preventing a copied birthday/anniversary from becoming ordinary work. Saved defaults and daily planning preferences are explicitly opt-in, inspectable and forgettable. Optional authored suggestions cover trip, study, project, event, errands and routine goals. A step can propose an authored child breakdown, which requires an explicit acceptance before children are added. “What changed?” and “what are you assuming?” derive answers from state.
- **History and recovery:** removing the last linked work step can still be reviewed and moved to Trash, then undone; no replacement task is required. Draft edits have bounded multi-level undo/redo. Saved-task history survives ordinary session expiry and supports version-aware undo/redo across commits. Native writes and their operation receipt share a Room transaction; a retry after the canonical write but before history-file completion reuses that receipt. History rebases only rows changed by its own operation. Outside edits and newly conflicting work stop an inverse operation. Rolling/pre-operation snapshots now include workspaces and the planning library; restore remaps links, assigns fresh workspace IDs and clears old confirmations/history authority. Damaged planning bytes are preserved in a local safety snapshot so replacement restore can proceed, while ordinary restore refuses to interpret those bytes as valid plans.
- **Clock and recurrence:** an explicit plan timezone can differ from the device timezone after review. Changing the device timezone invalidates an existing review. Displays identify the plan timezone; scheduled instants stay fixed when the plan's display zone changes. Gap/fold window boundaries are not guessed. Recurring tasks shift work by local calendar days and clear the previous occurrence's graph links. An invalid/ambiguous or deadline-breaking future occurrence remains unscheduled.

<a id="core-completion-example-commands"></a>

### Example commands

Use `/plan` to enter or resume planning. Ordinary replies in Plan use the same composer.

| Intent | Example |
| --- | --- |
| Adopt native work | `/plan use existing task Research` or `plan it` after selecting one task |
| Separate sessions | `add session for Research on tomorrow at 9am for 30 minutes` |
| Session correction | `move session 2 of Research to Friday at 10am` |
| Remove one interval | `remove session 2 of Research` |
| Split automatic work | `schedule in 30 minute sessions` then give an availability window |
| Constraints | `leave 10 minutes buffer`, `weekdays only`, `keep Friday free`, `maximum 90 minutes per day` |
| Estimates | `estimate Research is 30 to 45 minutes` |
| Protection | `protect Booking`, `unlock Booking` |
| Dependent replan | `move Research to tomorrow at 11am and replan its dependents` |
| Prerequisite gap | `wait 15 minutes after Research before Booking` |
| Structure | `make Packing a subtask of Preparation`, `break down Research`, `use that breakdown` |
| Timezone | `use time zone Asia/Kolkata` |
| Draft history | `undo`, `redo`, `what changed` |
| Canonical history | `saved history`, `undo saved changes`, `redo saved changes` |
| Reuse | `save plan as template Weekend trip`, `/plan use template Weekend trip` |
| Explicit memory | `save discussion as Travel`, `resume discussion Travel`, `delete discussion Travel` |
| Preferences | `remember planning preferences`, `my usual task duration is 30 minutes`, `what do you remember`, `forget planning preferences` |

Commands describe supported constructions, not every possible paraphrase. Unsupported references and constraints require clarification; a generic acknowledgement is not save authority. A changed plan must still be reviewed and explicitly applied.

<a id="core-completion-storage-and-compatibility"></a>

### Storage and compatibility

Room remains schema 14: the extension uses the existing canonical metadata column. New task planning values, workspace documents and numeric comparison state use **format 2**, with format 1 readers retained. Currency clarification preserves both supplied range bounds through reload. Ranged hard limits ask for one maximum rather than silently selecting a bound. New workspace transport versions do not alter the legacy content hash by themselves. Default legacy fields are normalized out of receipt hashing; meaningful new reminder/session/constraint choices remain part of the intent. Older planning metadata without a work-reminder flag reads as disabled, preventing retroactive alarm opt-in.

Retention is bounded: ten workspaces, 100 steps and 500 sessions per workspace, 100 sessions per task; up to twenty draft undo/redo checkpoints with byte limits and global workspace trimming; twenty local saved commits per workspace with an eight-megabyte global history limit; twenty templates and ten saved discussions within a two-megabyte library. Local operation history is not imported as archive authority. The library and valid workspaces participate in portable and local snapshot restore. Clearing chat removes workspaces and transient authority while preserving explicitly saved templates, discussions and preferences; the UI explains this. `forget planning library` deletes that saved memory explicitly. The existing lightweight checklist remains distinct from native child-task relationships.

The assistant uses no models, new inference services or new dependencies. Native Reminder/WorkManager adapters remain outside the chat's Notes/vault data boundary. `BlockOverlayManager.kt` and `FocusBlockerService.kt` retain their direct WindowManager mechanism.

<a id="core-completion-verification-and-deferred-gates"></a>

### Verification and deferred gates

The necessary checks cover data mutation, reminder identity and stale/duplicate delivery, native graph/version/conflict validation, exact undo/redo and crash retry, legacy receipt identity, archive remapping, rolling workspace/library recovery, temporal/constraint boundaries and discussion authority. Existing affected planner and discussion cases are included; unrelated full-suite, device, visual, performance and release runs are excluded.

The preceding integration check passed **174 focused JVM cases** (41 completion essentials, 39 discussion, 67 planner, 14 native planning and 13 archive), zero failures/errors/skips, plus a debug build in **3m 34s** (`/workspace/.setup/completion-handoff-checks.log`). Results are preserved in `/workspace/.setup/completion-handoff-results.*`. That compiled source is identified separately by `completion-handoff-source-compiled.json`; the last Talk-prefixed native handoff fix and expanded regression followed compilation. The final affected-only run passed **80 cases** (41 essentials and 39 discussion), zero failures/errors/skips, and a fresh debug build in **3m 13s** (`/workspace/.setup/completion-final-validation.log`). The source fingerprints matched before/after; XML/JSON and hashes are preserved under `/workspace/.setup/completion-final-*`. The chat boundary guard passed for 87 files and `git diff --check` passed. See CURRENT.md for the exact source identity and APK size. Earlier 158-, 95- and other case counts belong to their own checkpoints and are not added to these counts.

Deferred at the user's request: independent held-out conversation audits, performance measurement on named devices, accessibility/visual/motion review, remaining predictive-Back/OEM/lifecycle scenarios, optimized-release verification, and production rollout. Future breadth tracks—graded/weighted reasoning, richer knowledge methods and languages, deeper dialogue arbitration, and more advanced optimization—remain subject to the roadmap's discovery and promotion process. Current bounded coverage and passing essential checks do not establish those outcomes.
