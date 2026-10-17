# Ayva conversation continuity and feature access

This change implements conversation batches 1 and 2. It keeps the current composer, slash button, chat cards, cats, test windows, tracker screens, notification controls and blocking overlays.

## Conversation behavior

- The encrypted `AssistantSession` is the authoritative task/topic reference for the chat. The activity no longer maintains a second task reference with a different expiry. Legacy FAQ follow-ups consult the same session in the real app.
- Explicitly select a task with `let's work on the second task`. `No, the other reading one` can change that reference when exactly one alternative exists; otherwise Ayva asks. Selecting a reference does not mutate the task or replay the last action.
- After changing topics, `back to the task` can restore the most recent still-pending task reference. An ordinary `done` while discussing a habit cannot complete that older task.
- An unfinished title/date/setting question can be interrupted and resumed with `back to that reminder` or `continue that request`. Only one interrupted question is retained. Task versions and the 15-minute session lifetime are checked. Confirmation tokens are never suspended or revived.
- `Actually, Dad instead of Mum, and after lunch` keeps the same task identity. Ayva asks for an exact date/time when it cannot interpret the time, then previews the combined change. The task is not partially renamed while waiting; confirmation applies the edits and undo restores them together.
- Clearing history through the menu clears assistant context as well as the visible conversation. Saved app data survives.

## Existing features through more natural language

- `Can we do a hard drill with twenty questions?` opens the existing fullscreen Drill.
- After a test, `easier, same number of questions`, `give me a harder round`, `another round` and `do it again` reuse the relevant configuration. Difficulty/count modifiers apply to arithmetic tests. Vocabulary and Blitz retain their own modes.
- `practice my vocabulary` opens the retention hub; `teach me more words` opens the existing learn-more flow; `quiz me on vocabulary` opens the existing vocabulary test.
- `How did I do?` uses counts from a real completed-test card. The completion payload includes difficulty and target count, without changing grading, question generation, rewards, summary or solutions. An already-consumed completion does not overwrite a newer topic or restart.
- `I drank two glasses of water`, `had another glass` and a contextual `one more` use the existing habit action executor. Multiple matching habits require selection. Counts remain capped by the habit target. Litres and minutes are not silently converted into event counts.

## Deliberate bounds

There is no model, network lookup or arbitrary text execution. New language patterns produce existing typed actions or existing feature commands. Explicit reminder creation takes precedence over feature words inside a task title. Arithmetic lengths remain 10, 20 or unlimited; unsupported natural requests ask for one of those choices. Test results and context are session-scoped, rather than a claim to remember every past test. Private notes remain outside chat.

Later planning and reply-style batches are separate work; this change does not invent task durations or alter the current UI.

## Verification

`OfflineAssistantBatchTest` exercises real Room tasks/habits and the encrypted session through complete conversations, including interruption, resumption, ambiguous references, combined corrections, confirmations, undo, feature dispatch and recorded test results. Existing router, integration, security and native Drill tests remain part of the full unit-test gate. Device-level visual/permission behavior requires Android device testing.
