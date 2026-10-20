# Native task hierarchy and work intervals

Status: implemented and verified by 96 focused JVM tests and a debug build. See [CURRENT.md](CURRENT.md) for the source fingerprint, exact suites, preserved results and limitations.

User journey: discuss a goal, add parent/subtasks and prerequisites, schedule work, review and apply. The resulting task rows retain the graph and interval independently of the workspace. Deleting a workspace leaves saved task work occupied; deleting a parent does not delete its children. R04–R08 and R10 gain native persistence coverage; their full roadmap acceptance remains open.

## Storage and compatibility decision

Schema 14 adds `Task.planningJson`, a versioned, bounded optional value with native parent/prerequisite task IDs, duration, work start, original timezone, position and goal. Schema 13 rows default to the empty string. Existing lightweight checklist subtasks remain a separate feature. This additive representation keeps the entire expected task version in existing receipts, encrypted recovery and undo records. A work interval remains separate from `Task.dueDate`.

A normalized multi-session table is deferred until the conversation supports multiple sessions per step. The current planner has exactly one interval per step. This choice must be reconsidered for indexed agenda queries or large task collections; parsing all native metadata is not an unbounded scheduling solver. No automatic foreign-key cascade is introduced: unavailable parent/prerequisite references remain visible and surviving tasks are independently usable.

Canonical saves allocate IDs and apply the graph inside one Room transaction, including when children precede their parents. Full snapshots guard metadata as well as title/deadline edits. Conflicts with native sessions are checked again before writes. Receipts contain the resulting complete rows; retries reuse the receipt, and undo restores the prior full metadata.

Native work is used by conversation conflict checks, including after workspace discard. Completed, trashed and deleted task sessions do not occupy time. Deadlines alone do not occupy time. Linked workspace copies must not reserve stale native work after a native edit. Active unsaved proposals remain draft reservations.

## Restore and native flows

Password-protected backups, local task recovery, rolling/pre-operation task snapshots, raw vault export/import and plaintext-database migration carry the optional metadata. Backup merges and raw imports assign task IDs first, then remap both graph edges and expected workspace snapshots. References absent from an imported task set become explicit missing references and cannot bind a coincident local ID. Unknown versions and cyclic known edges reject restore before mutation. This does not implement restoration of chat workspaces from rolling snapshots.

Task cards and the native editor show saved planning information. Native edits preserve it; clearing work time clears only the interval, leaving duration and deadline. An editor cannot save a deadline earlier than the displayed interval's end without changing the deadline or clearing that interval. “Refresh saved tasks” adopts current native hierarchy, duration and start along with titles/deadlines, and invalidates review. A native graph reference outside the current workspace requires explicit reconciliation rather than guessing a draft step. An explicit unlink makes a separate draft copy and preserves the original task; duplicate-name and occupied-work guards still apply.

The Today/Upcoming filters in tasks and the widget share the earliest explicit work/deadline commitment, with a calendar-derived exclusive next-day boundary. The widget labels work as work. Deadline alarms retain their existing meaning; work times do not send reminders yet, and the chat/editor say so. Newly generated recurring occurrences do not inherit an earlier occurrence's work interval or graph identity.

## Acceptance and next work

Necessary checks cover the schema 13→14 migration, the schema 12 chain, native graph/session save, stale metadata guards, transactional conflict rejection, undo, partial/colliding ID remapping, encrypted backup merges, rolling task snapshots, raw vault merge, agenda classification and unchanged deadline-alarm timing. Existing planner/archive regressions are included because both contracts changed. Routine composable text does not need a new device run for this slice; native predictive Back and motion acceptance remain separate.

Next dependency-ready work: distinct work-start reminders with durable reconciliation, native session rescheduling and richer hierarchy navigation, multiple sessions per step, full rolling-workspace recovery, and independently authored conversational evaluation. A saved work interval is not evidence that these features are implemented.

Validation command: `:app:testDebugUnitTest` filtered to `CanonicalTaskPlanningTest`, `ConversationalPlannerTest`, `PlanningArchiveIntegrationTest`, `AssistantOfflineExpansionTest.taskDatabaseMigrationKeepsTasksAndCreatesPlanJournal` and `Batch5SecurityAuditTest.testFocusDatabaseMigrationHelperPreservesModernTaskColumns`, plus `:app:assembleDebug`. The prepared Java environment, test-runtime init script, Kotlin daemon memory override and two-worker setting from the playbook were used. Exit status 0; 96 tests, no failures/errors/skips; 3m 7s. `python3 tools/check_chat_boundary.py` and `git diff --check` also passed. No new Android-device or release acceptance claim is made.
