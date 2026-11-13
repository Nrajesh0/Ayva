# Conversation implementation history

Preserved implementation records for the bounded offline assistant. These are
original checkpoints, not current test results or a task queue. Consult
[assistant status](../STATUS.md) for the current handoff and limits.

## Contents

- [Conversation continuity — batch 1](#batch-1-continuity)
- [Conversation language — batch 2](#batch-2-language)
- [Conversation capabilities — batch 3](#batch-3-capabilities)
- [Explicit preferences and planning — batch 4](#batch-4-planning)
- [Conversational follow-ups and repairs](#follow-up-repairs)
- [Ayva feature capabilities](#feature-capabilities)
- [Ayva conversation continuity and feature access](#continuity-and-feature-access)

<a id="batch-1-continuity"></a>

<a id="batch-1-continuity-conversation-continuity--batch-1"></a>

## Conversation continuity — batch 1

This batch extends the existing offline assistant and typed action executor. The chat layout, composer, cats, briefs and full-screen learning windows are unchanged.

<a id="batch-1-continuity-supported-conversations"></a>

### Supported conversations

- Show a task list, then refer to the first two, last three, second and fourth, or task numbers. References follow the IDs in the displayed order even if database sorting changes. A task clarification list becomes the latest displayed order; resuming an older question restores its original list frame.
- Select several tasks with “let's work on the first two tasks,” then use “them,” “both” or “those two.” Selection alone changes no tasks. Group mutations require a preview and confirmation.
- Refine a live date preview: “keep their original times,” “actually Monday at 8am,” “not Friday—Monday,” “actually leave the second one tomorrow,” or “except the second one.” Other proposed edits remain intact. Each successful revision creates a new confirmation token; old buttons cannot execute it.
- Return to the previous task or a descriptive reference such as “the reading task.” Return to the last displayed list with “back to that task list”; it shows current details in the original order.
- Interrupt an unfinished reminder or setting question, then say “back to that reminder” or “back to the setting.” Up to three unfinished questions are retained. Returning to one can suspend a different live question.

<a id="batch-1-continuity-state-and-limits"></a>

### State and limits

List snapshots, selections and unfinished questions stay in the existing encrypted session file. New JSON fields are optional when reading older sessions; there is no database migration or additional plaintext store.

The session expires after 15 minutes of inactivity. Lists and suspended questions also have their own 15-minute age checks, so unrelated activity cannot keep an old list/question usable indefinitely. Natural-language lists retain only their displayed first 20 items. Native task-list snapshots retain up to 100 IDs and record whether the list was truncated; a truncated list cannot silently resolve “last” or an entire-list group.

Implicit task references cannot cross into a habit, setting, timer or unrelated topic, including unfinished feature questions. Oversized group selections cannot silently turn into smaller selections after the session is reloaded. Missing, completed or trashed targets produce clarification instead of silently selecting remaining items. A count such as “both” must match an identified selection. Ordinals without a displayed list do not sort and select from the database.

Date previews validate task versions before revision and confirmation. A failed clarification invalidates the old confirmation while retaining the editable draft; an externally changed target discards the stale preview. Suspended questions never retain confirmation tokens. Their targets and versions are validated before resuming, and a resumed answer creates a fresh preview where confirmation is needed.

Cancelling, clearing history, forgetting preferences or explicitly releasing the active reference clears suspended questions. Clearing/forgetting also erases list and group memory. Existing mutation checks, encrypted storage, receipts and undo remain in use. Notes and vault contents remain outside chat.

<a id="batch-1-continuity-verification"></a>

### Verification

`AssistantConversationContinuityTest` exercises whole conversations against a Room database, including preview revisions/undo, list reordering, missing items, expired state, cross-topic references, interrupted questions and old-session compatibility. Existing assistant, presentation, router and UI tests remain part of the full unit suite. The final full suite passed 605 tests across 46 suites, with zero failures, errors or skips, including 41 new continuity tests. An older cancellation integration test now shows its task list before referring to “task 2,” matching the new rule that ordinals must refer to displayed items.

Debug and optimized release APK assembly also passed. The subsequent combined verification, including batch 2, is recorded in `/workspace/scratch/ayva-language-verified.log`; the earlier batch 1 log in `/tmp` was lost during a cloud restart. No Android device or emulator was connected, so on-device chat verification remains.

The cloud test JVM uses the external init script described in `AYVA_CHAT_DESIGN.md` for its known JDK 21 compiler issue; production Gradle settings and test assertions are unchanged.

<a id="batch-1-continuity-following-batches"></a>

### Following batches

All four conversation-improvement batches are implemented. Batch 2 adds shared request interpretation, focused clarification, guided edits and reply preferences; see `AYVA_CONVERSATION_LANGUAGE.md`. Batch 3 adds more task fields, habit edits and focus sessions linked to tasks; see `AYVA_CONVERSATION_CAPABILITIES.md`. Batch 4 adds explicit preference review/reset and time-budget planning with live context; see `AYVA_CONVERSATION_PLANNING.md`. This batch establishes the conversation state those features use.

---

<a id="batch-2-language"></a>

<a id="batch-2-language-conversation-language--batch-2"></a>

## Conversation language — batch 2

This batch builds on the continuity work in `AYVA_CONVERSATION_CONTINUITY.md`. It extends the deterministic offline coordinator; no model, server, dependency or database migration is introduced. The chat layout, composer, cats, briefs, Drill and other native learning windows are unchanged.

<a id="batch-2-language-supported-conversations"></a>

### Supported conversations

- Polite requests share an anchored normalization path across chat, the router and NLU: “Could you please add task buy milk tomorrow at 6pm?”, “I'd like you to remind me to…”, and “Would you mind moving it to Friday?”
- Negated actions such as “don't delete it” and tentative actions such as “maybe delete it” do not execute. They invalidate an unrelated pending confirmation. Guidance requests such as “help me delete it” remain questions.
- “Thanks”, “got it” and similar acknowledgements do not become reminder titles, task edits, setting values or confirmation. Ayva reminds the user what answer is still needed. “Yes, thanks” is an explicit affirmative answer; “yes but move it…” is a new request. “Not yet” keeps a preview available without applying it.
- “Change it” asks which field to edit: date, title or priority. The workflow retains the selected task IDs and versions. Title and priority changes produce a fresh confirmation; dates use the existing date workflow. It also supports an explicitly selected group. An unclear date such as “after lunch” asks for a day and time instead of guessing.
- Edit questions can be interrupted and resumed with “back to that reminder.” Changes made outside the conversation invalidate the edit rather than silently overwriting newer data.
- A quoted title is literal, including words that normally count as acknowledgement or commands. Explicit creation such as `/create thanks` remains available. User task titles and emoji are preserved.
- Unrecognized requests get one focused question and at most two suggestions, including resuming a valid unfinished question. Repeated unrecognized input offers concrete examples. A fallback response uses the conversation topic rather than pretending to be a feature explanation.

<a id="batch-2-language-reply-preferences-and-boundaries"></a>

### Reply preferences and boundaries

“Be concise”/“more detail” and “no emoji”/“use emojis” are independent preferences in the existing personalization store. They control the new help, suggestions and confirmation buttons. They never truncate task results or confirmation facts. Existing native cards and legacy feature explanations retain their own formatting. “Forget my preferences” clears these preferences through the existing path.

The grammar supports explicit patterns, not arbitrary English understanding. Ambiguous targets and unsupported values require clarification. Notes and vault contents remain outside chat. Previews still use typed actions, token expiry, version checks, receipts and undo.

<a id="batch-2-language-verification"></a>

### Verification

`AssistantConversationLanguageTest` adds 33 complete conversation checks against Room, including polite requests, negation, guidance, courtesy during unfinished requests, independent reply preferences, unknown-input recovery, guided edits, interruption, literal titles and stale targets. The targeted run passed all 190 tests across five suites. The full suite passed 638 tests across 47 suites. Both runs had zero failures, errors or skips.

Debug and optimized release APK assembly passed in the same final Gradle run. The release APK is unsigned; app metadata remains 2.0.7 (107). The final log is `/workspace/scratch/ayva-language-verified.log`. An earlier cloud restart interrupted validation; the recorded final run completed successfully. `git diff --check` also passed.

Cloud commands use `/workspace/toolchains/ayva-env.sh` and the external test JVM init script documented in `AYVA_CHAT_DESIGN.md`. No Android device is attached, so verification here covers automated conversations and builds, not on-device interaction.

---

<a id="batch-3-capabilities"></a>

<a id="batch-3-capabilities-conversation-capabilities--batch-3"></a>

## Conversation capabilities — batch 3

This batch adds actions to the existing offline coordinator. It introduces no model, network service, dependency or database migration. The chat layout, cats, composer, briefs and native learning windows are unchanged.

<a id="batch-3-capabilities-task-editing"></a>

### Task editing

After selecting a task, say “change it,” then choose date, title, priority, details, repeat schedule or persistent reminders. The additional fields use the existing typed task executor and guarded undo:

```
Change it
Details
Bring workbook and pen
Yes
Undo
```

“Clear details” removes details explicitly. Quoted text is literal, including words normally interpreted as commands. Repeat schedules support daily, weekly, monthly, yearly or never; arbitrary intervals require clarification. Persistent reminders accept on/off or yes/no, then ask for separate confirmation. An explicitly selected group can use the same workflow. Target versions are checked before preview/confirmation, and unrelated task fields are preserved.

<a id="batch-3-capabilities-habit-editing-and-inspection"></a>

### Habit editing and inspection

```
Show habit Reading
Change habit Reading goal
3 times a day
Yes
Rename it to Fiction
Yes
Turn off reminders for habit Fiction
Yes
Archive habit Fiction
Yes
Archived habits
Restore habit Fiction
Yes
Undo
```

Habit inspection shows live progress, current/best streaks, the saved goal and reminder state. Rename, daily goal, reminder toggle, archive and restore all require confirmation. Ambiguous names produce choices before a fresh preview. Explicit habit requests cannot edit a similarly named task. Habit pronouns require the habit topic.

A missing title or goal becomes a resumable question. Courtesy replies cannot fill it. Interrupt it and say “back to the habit” to continue. The selected habit's full snapshot is validated before answering, confirming, executing and undoing; execution and undo check snapshots inside Room transactions. Duplicate active names are rejected, including restoration/undo that would cause a collision.

Daily goals are bounded to 1–100 repetitions. Changing a saved goal preserves every existing daily log's target, progress and history. For example, today's recorded 2/3 remains 2/3 after changing the saved goal to 5; a future new log uses 5. Archive preserves logs and cancels reminders. Restore and undo use existing reminder scheduling and respect the current day's completion state. Other tracker settings—including interval windows, sounds, descriptions and icons—are preserved.

<a id="batch-3-capabilities-focus-on-a-task"></a>

### Focus on a task

```
Show my tasks
Focus on the first one for 15 minutes
What am I focusing on
Complete focus task
Timer status
Stop focus
Yes
```

A focus session can retain one task ID in the existing focus preferences. Duration defaults to 25 minutes when omitted, and explicit durations use the existing 1–240-minute validation. Names containing “for” and quoted names are supported; a quoted pronoun is a literal title. Ambiguous or duplicate task names require selection. A running timer cannot be replaced by another request.

Status resolves the current task title by ID, including after renaming. “Complete focus task” uses the existing completion, recurring-task, history, reward and undo path; it does not stop the countdown. Status-message completion buttons retain the original task ID, so old buttons cannot retarget a newer session. Missing, trashed and completed tasks are reported without silently selecting another task. The association is cleared when the session ends or stops. Old focus actions without an association remain compatible, and ordinary dashboard/chat timers remain unlinked.

This adds optional task metadata to the existing timer. It does not change the WindowManager app-blocking mechanism or introduce timer pausing/duration changes.

<a id="batch-3-capabilities-boundaries-and-verification"></a>

### Boundaries and verification

The grammar remains deterministic and bounded. Advanced habit scheduling stays in the tracker. Notes and vault contents remain outside chat. Compound habit edits are not added; send each request separately and review its preview. Habit history is not deleted through these actions.

`AssistantConversationCapabilitiesTest` adds 53 checks of the new workflows against Room, including undo, newer external edits, similarly named tasks/habits, immutable daily targets, archive/restore collisions, literal payloads, group task edits, focus selection, stale targets, old buttons, quoted names and old action compatibility. A preliminary targeted run passed 220 tests across five suites. The final full suite passed 691 tests across 48 suites, including all 53 new checks. Both runs had zero failures, errors or skips.

The final combined Gradle run also passed debug and optimized release APK assembly, including R8 and resource shrinking. The release APK is unsigned; metadata remains 2.0.7 (107). The completed log is `/workspace/scratch/ayva-capabilities-verified.log`. `git diff --check` passed. No Android device or emulator was connected.

Cloud validation uses `/workspace/toolchains/ayva-env.sh` and the external test JVM init script documented in `AYVA_CHAT_DESIGN.md`. On-device checks remain necessary for Android notifications, keyboard/touch interaction and OEM alarm delivery.

---

<a id="batch-4-planning"></a>

<a id="batch-4-planning-explicit-preferences-and-planning--batch-4"></a>

## Explicit preferences and planning — batch 4

This batch completes the four conversation-improvement batches. It adds deterministic planning and explicit preference controls without a model, network service, dependency or database migration. Chat layout, composer, cats, briefs and native learning windows are unchanged.

<a id="batch-4-planning-preferences-you-can-review-and-remove"></a>

### Preferences you can review and remove

```
Remember that I prefer 15-minute focus sessions
Remember that I prefer 2 tasks in my plan
Remember that I prefer due dates first
What do you remember about me
Forget my focus preference
Forget my planning preferences
Forget my preferences
```

The allowlist contains name, reply detail, help/suggestion emoji, default focus minutes, plan size and planning order. Focus defaults are bounded to 1–240 whole minutes, plan size to 1–5 tasks, and ordering to priorities first or due dates first. Existing name/reply preferences remain compatible. Profile review lists only explicit settings; individual reset commands restore defaults. “Forget my preferences” also clears the conversational references, unfinished questions, undo and plan context without deleting saved app items. Clearing chat removes the conversation/plan while retaining explicitly saved preferences.

These controls use the existing local personalization preferences. New stored values are bounded numbers/enums; no arbitrary facts, passwords, inferred traits or task content are added to the profile. A rejected “remember…” request does not enter profile storage. “Remember to call Mum” still creates a task, and explicit `/create` text remains literal task creation. Existing encrypted short-lived conversation/history behavior is separate from profile storage.

An explicit duration overrides the default. Changing a preference does not resize a running timer. Both ordinary focus starts and focus sessions linked to tasks use the saved default when their duration is omitted.

<a id="batch-4-planning-plan-with-available-time"></a>

### Plan with available time

```
I have 30 minutes
Why this order
Skip that one
Only today
Include future tasks
Include skipped tasks
Show my plan
```

Plans read current pending tasks, exclude completed/trashed/deleted items, and retain only displayed task IDs for ordinal follow-ups. Default priority ordering highlights overdue tasks before priorities and nearest due dates. Due-date ordering sorts nearest dates first and uses priority to break ties. “Plan my day” excludes future-dated tasks while including undated tasks. Plan size uses the saved preference. “What should I do next” selects one task.

A time budget allocates work blocks across the displayed tasks, capped by the preferred focus duration. Blocks never exceed the requested budget; any remaining time is explicitly unallocated. These are work blocks, not estimates of task completion. Plans do not change task dates, mark tasks done, create calendar events, or start a timer automatically. If a focus timer is already running, the reply reports it and offers its status instead of replacing it.

“Due dates first” or “priorities first” during planning changes that plan only. “Skip that one” temporarily excludes the selected task from the plan without postponing or completing it. “Include skipped tasks” restores those candidates. An explanation or refreshed plan re-reads live priorities, dates and completion state. Task action buttons retain explicit task IDs; old buttons cannot silently switch to the new first task.

```
Plan my time
→ How much time should the plan cover?
Half an hour
```

Planning budgets support 1–480 whole minutes, including numeric hours converted to whole minutes. Invalid or missing budgets produce a typed question. Courtesy cannot fill it. The question can be interrupted and resumed with “back to planning.” A reminder interrupted by a planning request can still resume with “back to that reminder.”

<a id="batch-4-planning-context-and-limits"></a>

### Context and limits

Plan context lives in the existing encrypted conversation file. Its JSON fields are optional for older sessions. Plans and planning questions have their own 15-minute expiry as well as the existing conversation expiry; unrelated activity cannot keep an old plan alive indefinitely. Plans retain at most five displayed tasks and 100 temporary exclusions. Clear/forget/cancel paths remove the relevant unfinished plan. Preference changes are explicit; planning and emotional statements never infer durable preferences.

The grammar is bounded English, not open-ended reasoning. Task duration estimates, freeform long-term personal facts, dependencies and calendar availability are not inferred. Notes and vault contents remain outside chat. The app-blocking WindowManager mechanism is unchanged.

<a id="batch-4-planning-verification"></a>

### Verification

`AssistantConversationPlanningTest` adds 51 checks for explicit persistence/reset, rejected profile data, defaults versus explicit durations, budget validation, live sorting and task filtering, immutable task data, scoped skipping, stale buttons, running focus sessions, interruption, expiry and old-session compatibility. The targeted run passed 278 tests across six suites. The full suite passed 742 tests across 49 suites. Both runs had zero failures, errors or skips.

The final combined Gradle run passed debug and optimized release APK assembly, including R8 and resource shrinking. The release APK is unsigned; app metadata remains 2.0.7 (107). The completed log is `/workspace/scratch/ayva-planning-verified.log`. `git diff --check` passed. No Android device or emulator was connected.

Cloud validation uses the existing offline Android toolchain and external test JVM init script described in `AYVA_CHAT_DESIGN.md`. On-device UI, notification and OEM alarm checks remain separate.

---

<a id="follow-up-repairs"></a>

<a id="follow-up-repairs-conversational-follow-ups-and-repairs"></a>

## Conversational follow-ups and repairs

This batch extends the existing offline, deterministic coordinator. It adds no
model, remote service, arbitrary personal-fact memory or new database tables.
The chat UI, task/reschedule cards, cats, briefs and native test windows keep
their existing implementation.

<a id="follow-up-repairs-keep-the-conversation-while-clarifying"></a>

### Keep the conversation while clarifying

Questions such as “what do you need from me?”, “why are you asking?”, “what are
my options?” and “what now?” explain the current question without becoming a
task title or discarding its draft. “What will change?” reports typed proposed
actions and actual current setting values, retaining the current confirmation.
“Repeat that” repeats reply text without replaying a mutation or opening a test.

“Where were we?” reports the active feature/item and an available unfinished
question. It does not resume a question or revive an old confirmation itself.
“You misunderstood” cancels the pending request and gives correction guidance
for the actual feature, rather than always suggesting task-title/date edits.

<a id="follow-up-repairs-routine-conversations"></a>

### Routine conversations

- `show routine Study` → `change its days to weekends` → preview → confirm.
- While reviewing that preview, `change its time to 7pm to 9pm` or `make it soft`
  revises it while preserving earlier proposed fields and the original snapshot.
- During a guided new routine, `actually weekends` can correct its earlier days
  while keeping its unanswered time/app/mode questions. Invalid corrections
  never cause required fields to be skipped.
- `when does it run?`, `what apps does it block?` and `is it enabled?` distinguish
  an unsaved draft from the currently saved schedule. Scheduled activity does
  not prove an overlay is visible.
- `edit it` starts the existing field chooser; `pause it` and `resume it` preview
  changes to the selected routine's saved enablement.

Only an explicitly identified single routine supplies a pronoun reference. The
reference lives in the existing encrypted session and has its own 15-minute
age check. New feature topics and routine lists release it. Revisions validate
the original full routine snapshot; external edits, missing targets and active
strict/focus protections reject the edit. All successful revisions issue a new
confirmation token through the existing coordinator. No blocking-service or
WindowManager code changes are needed.

<a id="follow-up-repairs-habit-conversations"></a>

### Habit conversations

After selecting a habit, ask `what is its schedule?`, `what is its goal?` or
`are its reminders on?`. These read the saved habit, distinguish fixed daily
and interval schedules, and make no guarantee about Android delivery.

`change its interval to every 90 minutes`, `change its reminder time to 7pm`,
`change its window to 22:00 to 06:00` and `turn its reminders off` use the same
validated previews as explicit habit commands. A pending edit accepts
`actually 8pm` or `actually 60 minutes` for its current field. Invalid values
keep the original snapshot, ask for a replacement, and invalidate the old
confirmation. A changed habit cannot be overwritten using a stale correction.

These references stay within their feature; they never infer a habit from a
similarly named task or change the global reminder interval. Lock/unlock verbs
also participate in negation and qualified-request handling: “don't lock…” does
not apply a lock, and “yes but make them soft” revises rather than confirms.

<a id="follow-up-repairs-verification"></a>

### Verification

`AssistantConversationExpansionTest` exercises complete request/reply sequences
against Room, including clarification, corrections, stale buttons, external
edits, reference expiry, cross-feature boundaries and routine protections.
Verified on 2026-10-18: all **839 tests passed**, with zero failures, errors or
skips. This includes 20 new conversation regressions. Debug and optimized release
APK assembly both passed, and both APKs contain the new conversation handlers.
The cloud release APK is unsigned. `git diff --check` also passed. Physical-device
checks remain necessary for speech, OEM background behavior and actual overlays.

---

<a id="feature-capabilities"></a>

<a id="feature-capabilities-ayva-feature-capabilities"></a>

## Ayva feature capabilities

This implementation extends the existing deterministic, offline conversation coordinator. It adds no model, network service, dependency or Room schema migration. Feature windows remain under their existing owners. Cats, composer controls, chat layout, brief layouts and full-screen test flows are preserved.

<a id="feature-capabilities-blocking-device-health-and-usage"></a>

### Blocking, device health and usage

- `Why is Reader blocked` resolves an exact installed app name or package and reports the saved manual rule, active routine windows, focus protection and any temporary relief window. It distinguishes configured rules from evidence that an overlay is actually visible. Threshold readings are unknown when Android usage cannot be read.
- `Check device health`, `diagnose reminders` and `diagnose blocking` inspect Usage Access, overlay access, notifications, exact alarm access, battery optimization exemption and Android's service listing. These observations cannot diagnose a past missed notification conclusively.
- `Compare my screen time` reconstructs foreground sessions from Android usage events, comparing today with yesterday up to the same local time. `Show my usage` lists the leading apps. Missing access or empty, unobservable data is unavailable, not zero. Usage does not establish whether an app was a distraction.
- `Set a daily limit for app com.example.reader to 30 minutes` shows a preview before saving a HARD daily usage limit. `Restrict that app` requires a fresh usage-topic reference and previews a manual block with no automatic expiry. Generated actions pin package names. Execution and undo validate saved snapshots and respect focus/active strict protection. System, launcher and Ayva packages are refused.

The WindowManager overlay implementation is unchanged.

<a id="feature-capabilities-routines-and-habits"></a>

### Routines and habits

`Create routine Study` starts a guided conversation for days, time window, installed apps and HARD/SOFT mode. An unnamed routine also asks for its name. `Show apps` preserves the app-selection question. Overnight windows, week-boundary overlap warnings, exact names/package names and explicit local-time previews are supported. Saving requires confirmation.

`Edit routine Study` asks which field should change. Existing active strict routines and focus-protected edits cannot be weakened through chat. The full original snapshot is revalidated inside a transaction; newer edits and duplicate routine names are rejected. Undo also checks the complete saved snapshot and current protections. An active strict routine cannot be undone through chat. Interruptions can be resumed with `back to the routine`; confirmations are never restored by resuming a question.

Advanced habit scheduling uses the existing scheduler and full habit snapshots:

- `Set habit Water interval to 90 minutes` switches to interval mode; accepted intervals are 5–1440 minutes, matching the scheduler's minimum.
- `Set habit Water window to 22:00 to 06:00` switches to interval mode with an overnight window.
- `Set habit Water reminder time to 9am` switches to one fixed daily reminder.

Previews explain mode changes. Reminder enablement stays as saved, existing daily logs retain recorded targets, and undo restores all tracker fields. Missing values use the existing resumable habit question flow.

<a id="feature-capabilities-vocabulary-and-learning"></a>

### Vocabulary and learning

- `Look up break the ice`, `explain idiom break the ice` and `explain word curious` retrieve stored library definitions. No definition is generated when a match is absent.
- `Bookmark that idiom` uses fresh vocabulary context; a native card containing multiple possible words asks the user to choose. Actions carry the selected type and database ID, so an old button cannot silently retarget another word. Bookmark changes require confirmation and support guarded undo. Viewing a word does not change mastery.
- `Review words I missed` lists only persisted quiz mistake flags. Unmastered words alone are not treated as wrong answers. `Practice missed words` opens the existing native vocabulary quiz with that subset. Current flags are not advertised as a complete historical mistake log.
- `Review my mistakes` reports recorded wrong questions, selected/correct answers and stored explanations. `What should I practise after that test` offers a suitable existing practice window. Missing records and missing explanations are reported explicitly.
- `Compare my last two tests` compares valid saved sessions of the same known practice type. Counts, difficulty and missing legacy metadata limit the conclusions. New result JSON stores practice kind and difficulty without a database migration.
- `Where am I slow` reports measured overall elapsed time and explains that per-question times were not measured. Previously synthesized per-question timing/accuracy values are treated as unknown, including old explicit JSON values. New writers no longer invent them. Existing solution views show `Not measured` for these fields.

<a id="feature-capabilities-voice-task-previews"></a>

### Voice task previews

Only explicit final recognizer results enter task-extraction context. Partial captions remain editable in the composer and cannot feed extraction; stopping capture does not promote a partial caption to a final result. The existing transcript UI continues displaying captured text.

After stopping transcription, ask `make tasks from what I just said` or `preview my transcript`. Up to five explicit task requests separated by semicolons, newlines or `and then` become a preview. Unsupported segments and duplicate titles reject the proposal before saving. Changes never execute merely because speech text arrives.

`Change transcript item 2 to remind me to call Dad tomorrow` and `drop transcript item 2` revise the preview. Each valid revision replaces the confirmation token. Saving still requires confirmation, old confirmations cannot replay, and task undo uses the existing reminder/reward-aware path.

Transcript text/drafts and feature references live only in the existing encrypted conversation state. Transcript/word/usage references have their own 15-minute freshness checks. Clear/forget removes feature context. No raw audio or transcript is added to preferences or a separate plaintext file.

<a id="feature-capabilities-rewards-and-backup-health"></a>

### Rewards and backup health

`What can I claim` and `my reward balance` read actual wallet and pending focus balances, today's quest progress, award state and chest eligibility. They do not refresh the quest manager (which can award rewards), claim or spend. Existing quest/chest windows revalidate claims. Learning XP and focus XP are identified separately; chat does not invent a transaction ledger.

`Backup status` reports the latest locally recorded successful daily snapshot and an encrypted-export verification receipt. The export receipt is written only after the native archive's post-write verification succeeds. Receipts do not prove an external file still exists or is currently readable. Backup passwords, note/vault contents, archive selection and restore stay in the native security controls.

<a id="feature-capabilities-validation-and-device-follow-up"></a>

### Validation and device follow-up

Automated regression coverage exercises feature routing, previews, stale confirmations, external edits, guarded undo, routine interruption/overlap, habit scheduling, vocabulary identity and missed-word selection, learning provenance, transcript revisions, permission absence and read-only status reports. Existing conversation and feature tests are retained.

Physical-device checks remain necessary for Android speech services/language packs, Usage Access history, OEM background/battery behavior, actual overlay visibility, exact-alarm delivery and document-provider export/restore. No physical device is attached to this environment.

Final cloud validation: **790 tests in 50 suites passed**, with zero failures, errors or skipped tests. `:app:assembleDebug` and `:app:assembleRelease` passed; the release APK is unsigned in this environment. Release mapping contains all seven new feature coordinator/dialogue/executor classes. `git diff --check` passed. An existing asynchronous task-update regression test now awaits the Room emission before asserting its unchanged title/timestamp requirements.

---

<a id="continuity-and-feature-access"></a>

<a id="continuity-and-feature-access-ayva-conversation-continuity-and-feature-access"></a>

## Ayva conversation continuity and feature access

This change implements conversation batches 1 and 2. It keeps the current composer, slash button, chat cards, cats, test windows, tracker screens, notification controls and blocking overlays.

<a id="continuity-and-feature-access-conversation-behavior"></a>

### Conversation behavior

- The encrypted `AssistantSession` is the authoritative task/topic reference for the chat. The activity no longer maintains a second task reference with a different expiry. Legacy FAQ follow-ups consult the same session in the real app.
- Explicitly select a task with `let's work on the second task`. `No, the other reading one` can change that reference when exactly one alternative exists; otherwise Ayva asks. Selecting a reference does not mutate the task or replay the last action.
- After changing topics, `back to the task` can restore the most recent still-pending task reference. An ordinary `done` while discussing a habit cannot complete that older task.
- An unfinished title/date/setting question can be interrupted and resumed with `back to that reminder` or `continue that request`. Only one interrupted question is retained. Task versions and the 15-minute session lifetime are checked. Confirmation tokens are never suspended or revived.
- `Actually, Dad instead of Mum, and after lunch` keeps the same task identity. Ayva asks for an exact date/time when it cannot interpret the time, then previews the combined change. The task is not partially renamed while waiting; confirmation applies the edits and undo restores them together.
- Clearing history through the menu clears assistant context as well as the visible conversation. Saved app data survives.

<a id="continuity-and-feature-access-existing-features-through-more-natural-language"></a>

### Existing features through more natural language

- `Can we do a hard drill with twenty questions?` opens the existing fullscreen Drill.
- After a test, `easier, same number of questions`, `give me a harder round`, `another round` and `do it again` reuse the relevant configuration. Difficulty/count modifiers apply to arithmetic tests. Vocabulary and Blitz retain their own modes.
- `practice my vocabulary` opens the retention hub; `teach me more words` opens the existing learn-more flow; `quiz me on vocabulary` opens the existing vocabulary test.
- `How did I do?` uses counts from a real completed-test card. The completion payload includes difficulty and target count, without changing grading, question generation, rewards, summary or solutions. An already-consumed completion does not overwrite a newer topic or restart.
- `I drank two glasses of water`, `had another glass` and a contextual `one more` use the existing habit action executor. Multiple matching habits require selection. Counts remain capped by the habit target. Litres and minutes are not silently converted into event counts.

<a id="continuity-and-feature-access-deliberate-bounds"></a>

### Deliberate bounds

There is no model, network lookup or arbitrary text execution. New language patterns produce existing typed actions or existing feature commands. Explicit reminder creation takes precedence over feature words inside a task title. Arithmetic lengths remain 10, 20 or unlimited; unsupported natural requests ask for one of those choices. Test results and context are session-scoped, rather than a claim to remember every past test. Private notes remain outside chat.

Later planning and reply-style batches are separate work; this change does not invent task durations or alter the current UI.

<a id="continuity-and-feature-access-verification"></a>

### Verification

`OfflineAssistantBatchTest` exercises real Room tasks/habits and the encrypted session through complete conversations, including interruption, resumption, ambiguous references, combined corrections, confirmations, undo, feature dispatch and recorded test results. Existing router, integration, security and native Drill tests remain part of the full unit-test gate. Device-level visual/permission behavior requires Android device testing.
