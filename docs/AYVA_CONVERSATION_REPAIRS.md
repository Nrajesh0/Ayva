# Conversational follow-ups and repairs

This batch extends the existing offline, deterministic coordinator. It adds no
model, remote service, arbitrary personal-fact memory or new database tables.
The chat UI, task/reschedule cards, cats, briefs and native test windows keep
their existing implementation.

## Keep the conversation while clarifying

Questions such as “what do you need from me?”, “why are you asking?”, “what are
my options?” and “what now?” explain the current question without becoming a
task title or discarding its draft. “What will change?” reports typed proposed
actions and actual current setting values, retaining the current confirmation.
“Repeat that” repeats reply text without replaying a mutation or opening a test.

“Where were we?” reports the active feature/item and an available unfinished
question. It does not resume a question or revive an old confirmation itself.
“You misunderstood” cancels the pending request and gives correction guidance
for the actual feature, rather than always suggesting task-title/date edits.

## Routine conversations

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

## Habit conversations

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

## Verification

`AssistantConversationExpansionTest` exercises complete request/reply sequences
against Room, including clarification, corrections, stale buttons, external
edits, reference expiry, cross-feature boundaries and routine protections.
Verified on 2026-10-18: all **839 tests passed**, with zero failures, errors or
skips. This includes 20 new conversation regressions. Debug and optimized release
APK assembly both passed, and both APKs contain the new conversation handlers.
The cloud release APK is unsigned. `git diff --check` also passed. Physical-device
checks remain necessary for speech, OEM background behavior and actual overlays.
