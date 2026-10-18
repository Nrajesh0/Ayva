# Conversation continuity — batch 1

This batch extends the existing offline assistant and typed action executor. The chat layout, composer, cats, briefs and full-screen learning windows are unchanged.

## Supported conversations

- Show a task list, then refer to the first two, last three, second and fourth, or task numbers. References follow the IDs in the displayed order even if database sorting changes. A task clarification list becomes the latest displayed order; resuming an older question restores its original list frame.
- Select several tasks with “let's work on the first two tasks,” then use “them,” “both” or “those two.” Selection alone changes no tasks. Group mutations require a preview and confirmation.
- Refine a live date preview: “keep their original times,” “actually Monday at 8am,” “not Friday—Monday,” “actually leave the second one tomorrow,” or “except the second one.” Other proposed edits remain intact. Each successful revision creates a new confirmation token; old buttons cannot execute it.
- Return to the previous task or a descriptive reference such as “the reading task.” Return to the last displayed list with “back to that task list”; it shows current details in the original order.
- Interrupt an unfinished reminder or setting question, then say “back to that reminder” or “back to the setting.” Up to three unfinished questions are retained. Returning to one can suspend a different live question.

## State and limits

List snapshots, selections and unfinished questions stay in the existing encrypted session file. New JSON fields are optional when reading older sessions; there is no database migration or additional plaintext store.

The session expires after 15 minutes of inactivity. Lists and suspended questions also have their own 15-minute age checks, so unrelated activity cannot keep an old list/question usable indefinitely. Natural-language lists retain only their displayed first 20 items. Native task-list snapshots retain up to 100 IDs and record whether the list was truncated; a truncated list cannot silently resolve “last” or an entire-list group.

Implicit task references cannot cross into a habit, setting, timer or unrelated topic, including unfinished feature questions. Oversized group selections cannot silently turn into smaller selections after the session is reloaded. Missing, completed or trashed targets produce clarification instead of silently selecting remaining items. A count such as “both” must match an identified selection. Ordinals without a displayed list do not sort and select from the database.

Date previews validate task versions before revision and confirmation. A failed clarification invalidates the old confirmation while retaining the editable draft; an externally changed target discards the stale preview. Suspended questions never retain confirmation tokens. Their targets and versions are validated before resuming, and a resumed answer creates a fresh preview where confirmation is needed.

Cancelling, clearing history, forgetting preferences or explicitly releasing the active reference clears suspended questions. Clearing/forgetting also erases list and group memory. Existing mutation checks, encrypted storage, receipts and undo remain in use. Notes and vault contents remain outside chat.

## Verification

`AssistantConversationContinuityTest` exercises whole conversations against a Room database, including preview revisions/undo, list reordering, missing items, expired state, cross-topic references, interrupted questions and old-session compatibility. Existing assistant, presentation, router and UI tests remain part of the full unit suite. The final full suite passed 605 tests across 46 suites, with zero failures, errors or skips, including 41 new continuity tests. An older cancellation integration test now shows its task list before referring to “task 2,” matching the new rule that ordinals must refer to displayed items.

Debug and optimized release APK assembly also passed. The subsequent combined verification, including batch 2, is recorded in `/workspace/scratch/ayva-language-verified.log`; the earlier batch 1 log in `/tmp` was lost during a cloud restart. No Android device or emulator was connected, so on-device chat verification remains.

The cloud test JVM uses the external init script described in `AYVA_CHAT_DESIGN.md` for its known JDK 21 compiler issue; production Gradle settings and test assertions are unchanged.

## Following batches

All four conversation-improvement batches are implemented. Batch 2 adds shared request interpretation, focused clarification, guided edits and reply preferences; see `AYVA_CONVERSATION_LANGUAGE.md`. Batch 3 adds more task fields, habit edits and focus sessions linked to tasks; see `AYVA_CONVERSATION_CAPABILITIES.md`. Batch 4 adds explicit preference review/reset and time-budget planning with live context; see `AYVA_CONVERSATION_PLANNING.md`. This batch establishes the conversation state those features use.
