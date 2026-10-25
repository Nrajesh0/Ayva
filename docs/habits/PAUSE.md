# Habit pause

Habit cards retain paused habits in the list. The overflow menu switches between Pause habit and Resume habit; the schedule line becomes “Paused · reminders off”, and logging controls are disabled. Paused habits are excluded from the habits page's completion denominator.

Ayva Talk accepts “pause habit Reading”, “resume my Reading habit”, and “pause it” when a habit is selected. Edits use existing confirmation, snapshot guards, and undo. Habit details offer a contextual Pause/Resume action.

Pause suspends reminders without changing the saved reminder toggle, schedule, daily target, logs, or rewards. Notification buttons reject paused habits, current notifications and queued nudges are removed, and reminders are recalculated on resume. Completed pause intervals and the ongoing pause bridge streak gaps on local calendar days without recording completions or increasing the streak. The pause/resume dates are excused; actual completions still count.

Database version 15 adds nullable pausedAt and a pause-history JSON column via an additive migration. Existing habits start active. Encrypted backups and assistant snapshots carry both fields; older snapshots default to an active habit.

Validation: all three targeted checks passed: Talk confirmation/undo and stale snapshot rejection, paused logging/reminder suppression and settings preservation, pause-aware streaks without fabricated logs, and the real Room 14→15 upgrade preserving an existing habit and progress row. Production/test compilation, the 109-file chat boundary check and diff formatting passed. Physical-device visual acceptance remains open.

Release: 2.0.15 (115). The full release test run passed 1,154 of 1,156 tests; the two old task migration fixtures needed their synthesized habit schema and migration chain updated for database 15. Both corrected migration checks then passed in a targeted rerun. Production code was unchanged by that fixture correction. Boundary, dictionary, and diff checks passed.
