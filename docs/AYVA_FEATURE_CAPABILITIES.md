# Ayva feature capabilities

This implementation extends the existing deterministic, offline conversation coordinator. It adds no model, network service, dependency or Room schema migration. Feature windows remain under their existing owners. Cats, composer controls, chat layout, brief layouts and full-screen test flows are preserved.

## Blocking, device health and usage

- `Why is Reader blocked` resolves an exact installed app name or package and reports the saved manual rule, active routine windows, focus protection and any temporary relief window. It distinguishes configured rules from evidence that an overlay is actually visible. Threshold readings are unknown when Android usage cannot be read.
- `Check device health`, `diagnose reminders` and `diagnose blocking` inspect Usage Access, overlay access, notifications, exact alarm access, battery optimization exemption and Android's service listing. These observations cannot diagnose a past missed notification conclusively.
- `Compare my screen time` reconstructs foreground sessions from Android usage events, comparing today with yesterday up to the same local time. `Show my usage` lists the leading apps. Missing access or empty, unobservable data is unavailable, not zero. Usage does not establish whether an app was a distraction.
- `Set a daily limit for app com.example.reader to 30 minutes` shows a preview before saving a HARD daily usage limit. `Restrict that app` requires a fresh usage-topic reference and previews a manual block with no automatic expiry. Generated actions pin package names. Execution and undo validate saved snapshots and respect focus/active strict protection. System, launcher and Ayva packages are refused.

The WindowManager overlay implementation is unchanged.

## Routines and habits

`Create routine Study` starts a guided conversation for days, time window, installed apps and HARD/SOFT mode. An unnamed routine also asks for its name. `Show apps` preserves the app-selection question. Overnight windows, week-boundary overlap warnings, exact names/package names and explicit local-time previews are supported. Saving requires confirmation.

`Edit routine Study` asks which field should change. Existing active strict routines and focus-protected edits cannot be weakened through chat. The full original snapshot is revalidated inside a transaction; newer edits and duplicate routine names are rejected. Undo also checks the complete saved snapshot and current protections. An active strict routine cannot be undone through chat. Interruptions can be resumed with `back to the routine`; confirmations are never restored by resuming a question.

Advanced habit scheduling uses the existing scheduler and full habit snapshots:

- `Set habit Water interval to 90 minutes` switches to interval mode; accepted intervals are 5–1440 minutes, matching the scheduler's minimum.
- `Set habit Water window to 22:00 to 06:00` switches to interval mode with an overnight window.
- `Set habit Water reminder time to 9am` switches to one fixed daily reminder.

Previews explain mode changes. Reminder enablement stays as saved, existing daily logs retain recorded targets, and undo restores all tracker fields. Missing values use the existing resumable habit question flow.

## Vocabulary and learning

- `Look up break the ice`, `explain idiom break the ice` and `explain word curious` retrieve stored library definitions. No definition is generated when a match is absent.
- `Bookmark that idiom` uses fresh vocabulary context; a native card containing multiple possible words asks the user to choose. Actions carry the selected type and database ID, so an old button cannot silently retarget another word. Bookmark changes require confirmation and support guarded undo. Viewing a word does not change mastery.
- `Review words I missed` lists only persisted quiz mistake flags. Unmastered words alone are not treated as wrong answers. `Practice missed words` opens the existing native vocabulary quiz with that subset. Current flags are not advertised as a complete historical mistake log.
- `Review my mistakes` reports recorded wrong questions, selected/correct answers and stored explanations. `What should I practise after that test` offers a suitable existing practice window. Missing records and missing explanations are reported explicitly.
- `Compare my last two tests` compares valid saved sessions of the same known practice type. Counts, difficulty and missing legacy metadata limit the conclusions. New result JSON stores practice kind and difficulty without a database migration.
- `Where am I slow` reports measured overall elapsed time and explains that per-question times were not measured. Previously synthesized per-question timing/accuracy values are treated as unknown, including old explicit JSON values. New writers no longer invent them. Existing solution views show `Not measured` for these fields.

## Voice task previews

Only explicit final recognizer results enter task-extraction context. Partial captions remain editable in the composer and cannot feed extraction; stopping capture does not promote a partial caption to a final result. The existing transcript UI continues displaying captured text.

After stopping transcription, ask `make tasks from what I just said` or `preview my transcript`. Up to five explicit task requests separated by semicolons, newlines or `and then` become a preview. Unsupported segments and duplicate titles reject the proposal before saving. Changes never execute merely because speech text arrives.

`Change transcript item 2 to remind me to call Dad tomorrow` and `drop transcript item 2` revise the preview. Each valid revision replaces the confirmation token. Saving still requires confirmation, old confirmations cannot replay, and task undo uses the existing reminder/reward-aware path.

Transcript text/drafts and feature references live only in the existing encrypted conversation state. Transcript/word/usage references have their own 15-minute freshness checks. Clear/forget removes feature context. No raw audio or transcript is added to preferences or a separate plaintext file.

## Rewards and backup health

`What can I claim` and `my reward balance` read actual wallet and pending focus balances, today's quest progress, award state and chest eligibility. They do not refresh the quest manager (which can award rewards), claim or spend. Existing quest/chest windows revalidate claims. Learning XP and focus XP are identified separately; chat does not invent a transaction ledger.

`Backup status` reports the latest locally recorded successful daily snapshot and an encrypted-export verification receipt. The export receipt is written only after the native archive's post-write verification succeeds. Receipts do not prove an external file still exists or is currently readable. Backup passwords, note/vault contents, archive selection and restore stay in the native security controls.

## Validation and device follow-up

Automated regression coverage exercises feature routing, previews, stale confirmations, external edits, guarded undo, routine interruption/overlap, habit scheduling, vocabulary identity and missed-word selection, learning provenance, transcript revisions, permission absence and read-only status reports. Existing conversation and feature tests are retained.

Physical-device checks remain necessary for Android speech services/language packs, Usage Access history, OEM background/battery behavior, actual overlay visibility, exact-alarm delivery and document-provider export/restore. No physical device is attached to this environment.

Final cloud validation: **790 tests in 50 suites passed**, with zero failures, errors or skipped tests. `:app:assembleDebug` and `:app:assembleRelease` passed; the release APK is unsigned in this environment. Release mapping contains all seven new feature coordinator/dialogue/executor classes. `git diff --check` passed. An existing asynchronous task-update regression test now awaits the Room emission before asserting its unchanged title/timestamp requirements.
