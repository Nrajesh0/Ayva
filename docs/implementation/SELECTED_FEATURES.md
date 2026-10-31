# Selected productivity features

Scope follows the user's selection: implement 1–6, 9–11 and 14. Exclude command search,
unified reminder rules, user shortcuts and feature visibility controls. All features work locally;
no model or model download is introduced. Notes remain outside chat's data access.

## Tasks

- Task editor → **More**: **Waiting for** chooses a prerequisite. Advanced planning retains existing
  multiple-prerequisite and parent/work-session controls. Cycles are rejected transactionally.
- Completion validates prerequisites. Native task UI offers an explicit **Complete anyway**
  decision for dependencies, without deleting links. This never bypasses required subtasks.
- Deleting a referenced prerequisite asks before detaching links. Chat deletion rejects references
  outside the selected group. Automatic completed-task cleanup retains referenced tasks.
- Under **More**, **Start showing on** is independent of deadline and reminder time. Future-start tasks are excluded
  from Today, remain in All/Upcoming, and retain metadata through backup/import. Dates after the
  deadline are rejected. Recurrences shift availability with the occurrence.
- **Finish all subtasks before completing** applies to native, assistant, notification and widget
  completion paths. Recurring copies reset checklist completion.
- Tasks **+** opens **Create task**, **Create template**, and **Existing templates**. Template creation uses a dedicated editor and creates no task. Existing templates opens Your templates/Ready-made. The editor's **Save as template** stores title,
  details, priority, checklist structure and its completion requirement. Choosing a template opens
  an independent editable task; dates and live dependency IDs are intentionally not copied.

## Habits

- Habit editor: Daily, Selected days, or Days per week. Weekly targets count distinct days where
  the existing daily target was met, using Monday–Sunday weeks. Weekly history is labeled in weeks.
- Selected rest days bridge streaks and are skipped by reminder scheduling/delivery. Once a weekly target is met, reminder delivery waits until the next Monday. Manual logging
  remains available. Schedule changes take effect from their change date.
- **Create habit routine** on Habits selects and reorders existing habits. Each step logs the normal
  habit progress; Next is offered when its daily target is met. Skip and Stop preserve progress.
  No additional routine streak, app-blocking rule, or automatic completion is created.
- Schema 16 adds `habits.frequencyJson`, defaulting older records to daily. Full backups,
  metadata snapshots and assistant undo snapshots preserve it.

## Data & recovery

Settings → Security → **Data & recovery** uses the existing settings action row and a bottom sheet with Recovery, Verify a backup and Storage destinations:

- Saved editor drafts with read-only review and explicit text copying; original encrypted files remain.
- Existing scoped safety snapshots and their restore confirmation. Metadata snapshots are clearly
  distinguished from full attachment backups.
- Status/retry for the existing interrupted-restore journal. Failure retains recovery files.
- **Verify an encrypted backup**, requiring its password. It authenticates the complete payload,
  bounds decompression, validates structure and media inventory/hashes, and deletes temporary
  encrypted verification staging. It does not restore or overwrite live records. Legacy archives
  without a media inventory are explicitly identified; verification is not a future-version guarantee.
- Storage totals for databases, attachments, recovery snapshots, drafts, temporary exports and other
  cache. External file-picker backups are excluded. Cleanup is limited to expired temporary exports;
  it never guesses which attachments are unused or deletes databases/backups/drafts.

Portable templates and routine definitions are included in encrypted backup preferences. During
merge restore, routines map only to imported habit IDs; missing references never bind to unrelated
local habits. Conflicting library IDs get new IDs. Restore journaling covers the preference stores.

## Exam countdown widget

Native resizable home-screen widget with a configuration activity and preview. Each instance has
its own saved configuration. Options include title/message, exam date/time and IANA timezone,
preparation start, days/weeks/days+hours, app-theme/dark/light/custom colors, opacity, corner radius,
text size/alignment, optional date/progress, and count-up or custom end text. Ambiguous/nonexistent
DST times are rejected. Progress is elapsed preparation time, not an estimate of study completion.

Settings → Exam countdown widget opens configuration and preview; supported launchers can pin it directly. Tap a widget to reconfigure. Small sizes hide secondary details. Updates use system widget refresh
and inexact non-wakeup alarms; Android may defer updates while asleep. No per-second service or
exact-alarm permission is added. Boot, date/time, timezone, package replacement and launcher widget
ID restoration are handled. Countdown definitions are portable backups; launcher bindings remain
local and imported definitions can be selected from configuration.

## Validation

Completed validation:
- 148 focused domain, migration, assistant task, habit and backup-verification checks passed;
  debug APK assembled (`/tmp/ayva-selected-features-tests2.log`).
- Full encrypted backup merge round-trip passed, including imported habit-ID remapping,
  template collision handling, frequency preservation and countdown definitions.
- 11 Notes-isolation checks passed; dependency guard passed across 117 chat source files.
- Final storage-alias/weekly-reminder regressions and the rebuilt debug APK passed
  (`/tmp/ayva-productivity-release-check.log`, 18 checks). Across the batches, 162 distinct
  focused checks passed; repeated checks are not counted twice.
Real-device launcher rendering, resizing, pinning and Android 16 scheduling still require device
verification; no phone/emulator is connected to this workspace.

Existing uncommitted voice stability and screenshot-protection changes predate this feature batch
and are preserved. Excluded features and profile/chat redesigns are not part of this work.

## UI organization follow-up

Tasks + now opens Create task / Create template / Existing templates. Templates have a
separate draft namespace and do not parse dates out of names. Planning, prerequisites,
start dates and completion requirements are grouped under the collapsed More section.
Recovery uses a settings action row and themed bottom sheet with Recovery, Backup
verification and Storage destinations. Habit routines use a labeled grouped card.

Validation: full suite passed (1,248 checks) before integrating upstream CI repairs;
32 focused checks and debug assembly passed after rebasing onto those repairs and
finishing template draft/name handling. Chat dependency boundary passed (117 files).
