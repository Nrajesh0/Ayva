# Ayva: offline conversation and action batches

> Completed historical implementation record. The active queue is [the conversation-quality plan](assistant/CONVERSATION_QUALITY_PLAN.md); current status is consolidated in [delivery summary](assistant/DELIVERY_SUMMARY.md).

This implementation extends the existing deterministic assistant. It adds no language model, network service, model download, or new dependency. Natural chat, slash aliases, clarification choices and action chips share the same action grammar and validated task execution. Existing arithmetic, vocabulary, profile, economy and diagnostic cards remain available.

## What changed

| Batch | Result |
| --- | --- |
| 1: reliable actions | Typed task creation, title/details/date/priority/persistence/recurrence edits, completion, reopening and Trash. Shared completion handles task cards, notification actions and existing task helper callers, including recurring tasks, history, reminders, widgets and reward receipts. |
| 2: conversation continuity | Encrypted local conversation state survives activity/process recreation and expires after 15 minutes of inactivity. It retains task/habit identities, displayed candidates, missing fields, composer flags, scoped confirmations and one undo operation. |
| 3: useful app capabilities | Habit creation and capped daily logging, routine lookup/enabling/disabling, 18 validated settings, and a real persisted focus countdown shared with the dashboard. |
| 4: language and context | Natural follow-ups, duplicate resolution, ordinals, corrections, conservative typo matching, compound previews, next-task planning, small talk, local personalization, packaged-help follow-ups, arithmetic and time-unit conversion. |
| 5: integration and regression | Room-backed action/conversation tests, existing regression tests, release compilation and this implementation record. See validation below. |

## Try these conversations

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

## Execution and data design

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

## Architecture map

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

## Remaining boundaries

This is a bounded English rules assistant. It cannot match a language model's open-ended reasoning, arbitrary paraphrases, long-term semantic memory or general knowledge. Unknown/ambiguous wording should lead to help or a clarification; it should not imply successful action. Some legacy specialized features (app restriction configuration, economy and exercise interactions) still use their established handlers and cards rather than the new executor. Timed chat app-block requests remain unsupported and explain that limitation before changing restrictions. Voice transcription depends on the device's recognition services; the new text assistant itself requires no model or network.

Physical-device checks are still needed for Compose interactions, speech, rotation/process death, notification permissions, exact/inexact alarms, reboot and OEM background restrictions. Unit/Robolectric tests do not establish those device behaviors.

## Validation

The final `:app:testDebugUnitTest --offline` run passed **496 tests, 0 failures, 0 errors, 0 skipped**. This includes 59 new Room-backed assistant tests, the preceding 35 chat tests and the existing app/security regression suite. Bulk-completion tests now confirm the scoped preview before checking saved rows and absent deletion tombstones; bulk-Trash tests retain their recovery-snapshot assertions and use the displayed nonce rather than an unscoped static command.

Coverage includes task corrections and recurrence, missing fields, duplicate decisions, displayed ordinals, changed targets, stale/replayed confirmations, unrelated-topic invalidation, preference correction and validation, capped habits and reward replay, routine protections, real countdown expiry/restore, encrypted/expired memory, task/habit/notes topic handoffs, compound previews/undo, personalization clearing, safe arithmetic and the notes privacy boundary.

The final implementation's `:app:assembleRelease --offline` build passed, including R8 optimization and resource shrinking. It produced `app/build/outputs/apk/release/app-release-unsigned.apk` at version 2.0.3 (code 103). The build is unsigned in this environment. `git diff --check` passed. Release metadata was subsequently bumped to 2.0.4 (code 104); the local APK above is the pre-bump validation artifact.
