# Conversation capabilities — batch 3

This batch adds actions to the existing offline coordinator. It introduces no model, network service, dependency or database migration. The chat layout, cats, composer, briefs and native learning windows are unchanged.

## Task editing

After selecting a task, say “change it,” then choose date, title, priority, details, repeat schedule or persistent reminders. The additional fields use the existing typed task executor and guarded undo:

```
Change it
Details
Bring workbook and pen
Yes
Undo
```

“Clear details” removes details explicitly. Quoted text is literal, including words normally interpreted as commands. Repeat schedules support daily, weekly, monthly, yearly or never; arbitrary intervals require clarification. Persistent reminders accept on/off or yes/no, then ask for separate confirmation. An explicitly selected group can use the same workflow. Target versions are checked before preview/confirmation, and unrelated task fields are preserved.

## Habit editing and inspection

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

## Focus on a task

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

## Boundaries and verification

The grammar remains deterministic and bounded. Advanced habit scheduling stays in the tracker. Notes and vault contents remain outside chat. Compound habit edits are not added; send each request separately and review its preview. Habit history is not deleted through these actions.

`AssistantConversationCapabilitiesTest` adds 53 checks of the new workflows against Room, including undo, newer external edits, similarly named tasks/habits, immutable daily targets, archive/restore collisions, literal payloads, group task edits, focus selection, stale targets, old buttons, quoted names and old action compatibility. A preliminary targeted run passed 220 tests across five suites. The final full suite passed 691 tests across 48 suites, including all 53 new checks. Both runs had zero failures, errors or skips.

The final combined Gradle run also passed debug and optimized release APK assembly, including R8 and resource shrinking. The release APK is unsigned; metadata remains 2.0.7 (107). The completed log is `/workspace/scratch/ayva-capabilities-verified.log`. `git diff --check` passed. No Android device or emulator was connected.

Cloud validation uses `/workspace/toolchains/ayva-env.sh` and the external test JVM init script documented in `AYVA_CHAT_DESIGN.md`. On-device checks remain necessary for Android notifications, keyboard/touch interaction and OEM alarm delivery.
