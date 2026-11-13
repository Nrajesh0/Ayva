> Historical review of commit `53e0e005`. Implementation and validation status is tracked in
> [Pre-release hardening](../features/system/PRE_RELEASE_HARDENING.md). Findings below describe the pre-change source.

# Ayva remaining-work review — 7 November 2026

Reviewed `Nrajesh0/Ayva`, version 2.0.44/build 144, commit
`53e0e005ba28ae5808b96021e941e6447e15f04c`. The fetched `origin/main` matches
this commit. This is a risk-focused source review and synthetic reproduction
exercise, not a claim that every path in the 417 Kotlin source files was executed.
Application source, user data, version and repository configuration were not changed.

Speech privacy remains explicitly deferred. Existing permission and backup
repairs are accounted for below rather than listed again as unfinished features.

## Priorities

| ID | Priority | Remaining work | Evidence |
| --- | --- | --- | --- |
| R01 | Fix first: data loss | Stop identifying disposable starter habits by their titles | Reproduced with real repository/Room operations on API 31 and 34 |
| R02 | Fix first: data loss | Make destructive media cleanup safe against saves, recovery and unreadable references | Two deletion cases reproduced on API 31 and 34 |
| R03 | Next: privacy | Apply complete diagnostic redaction before every output | Two redaction gaps reproduced on API 31 and 34 |
| R04 | Next: privacy | Redact task/habit notifications on the lock screen by default | Current notification/channel construction inspected |
| R05 | Planned security migration | Encrypt auxiliary user stores consistently | Current database factories and preference writes inspected |
| R06 | Planned security migration | Inventory and migrate legacy static-key/plaintext media | Compatibility readers still present; modern writes remain fail-closed |
| R07 | Security design decision | Clarify/strengthen vault isolation against app-identity execution | Recoverable PIN wrapping key and unauthenticated Keystore access inspected |
| R08 | Next: functionality | Give habit notifications identities containing the full habit ID | Notification replacement reproduced for IDs 1 and 400001 |
| R09 | Next: durability | Report and recover failed drill-summary/claim persistence | Memory-first writes and silent exception handlers inspected |
| R10 | Release quality | Enforce lint errors and exercise device/native recovery paths in CI | Gradle lint settings and both workflows inspected |
| R11 | UX improvement | Separate essential permissions from optional reliability recommendations | Shared readiness model and UI conditions inspected |
| R12 | Maintenance improvement | Triage warnings, split large screens, refresh old audit tools and profile hot paths | Retained lint report, file sizes and existing review scripts inspected |

## Confirmed findings and recommended implementation

### R01 — Title-based cleanup can delete authored habits and their logs

[HabitRepository.kt:325](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/data/HabitRepository.kt#L325)
checks `cleaned_placeholder_habits_v1`, then deletes every habit titled
`Hydration Protocol`, `Deep Reading` or `Mindful Focus`, including its logs.
It does not establish that those rows were generated demo data. It is invoked
from [application startup](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/FocusApplication.kt#L82)
and AccountScreen. A missing flag can occur on an older installation; asynchronous
first-run initialization also makes relying on cleanup having already run unsafe.

Reproduction: start without the flag, insert a user-authored `Deep Reading`
habit with a custom description, target and progress log, then run cleanup.
The habit and log disappear. A control confirms the existing flag protects
matching habits created after cleanup. The existing Batch9 test covers that
post-flag control, not the missing-flag case.

Recommendation: preserve records unless seed provenance can be proved. Do not
infer ownership from a user-editable title. Unknown legacy records should remain
available for explicit review/removal. Any removal of proven seeds should be
transactional and preserve routine/reference integrity.

### R02 — Media cleanup can destroy attachments that should be retained

[NoteMediaManager.kt:187](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/data/note/NoteMediaManager.kt#L187)
takes a note-reference snapshot, then later wipes files outside that snapshot
once their modification time is more than five minutes old
([sweep](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/data/note/NoteMediaManager.kt#L246)).
The scan and sweep do not share an ownership transaction with note writers.
The maintenance barrier excludes restore operations; ordinary writers can run
concurrently and it does not make this reference snapshot authoritative.

Reproduction A: obtain the cleanup snapshot, complete a note insertion referencing
an older image, then finish the cleanup sweep. The note remains in Room but its
image is gone. The fixture deterministically inserts the save between the real
DAO read and sweep; it does not depend on probabilistic thread timing. A fresh-file
control remains intact under the current grace period.

There is a relevant production ordering: after successful vault unlock,
[NotesViewModel.kt:142](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/ui/screens/notes/NotesViewModel.kt#L142)
starts pending-draft recovery and orphan cleanup as separate IO coroutines.
This can put recovering attachments into the same unsafe ordering. The full
vault-recovery race was traced in source; the executable fixture tests the shared
cleanup/save interleaving, not the full encrypted unlock UI.

Reproduction B: a note contains a malformed image-reference array retaining the
image path text. `getImageUris()` catches the parsing error and returns an empty
list; cleanup deletes the older file instead of aborting on uncertain references.
Unreadable metadata should not authorize permanent media deletion.

Recommendation: coordinate cleanup with saves, restores, active editors, undo and
recoverable drafts; reject incomplete/unreadable reference inventories. Use live
ownership or an equivalent verified lifecycle, with reference revalidation before
destruction. Prefer a recoverable quarantine while ownership is uncertain. Merely
extending the five-minute grace period does not solve either issue.

### R03 — Diagnostic redaction does not cover every advertised field or output

[AppLogger.kt:145](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/util/diagnostics/AppLogger.kt#L145)
runs its credential regex only when the text contains `passcode`, `pin`, `secret`
or `password`. The regex supports more labels, but standalone `api_key`, `apikey`,
`access_token`, `auth_token`, `token`, `passphrase` and `mnemonic` messages never
reach it. Synthetic examples with these labels survive sanitization unchanged.

[AppLogger.kt:103](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/util/diagnostics/AppLogger.kt#L103)
also calls `Log.e` with the raw message/throwable before sanitizing the in-memory
buffer. A synthetic password was redacted in the buffer but present in Logcat.
Release rules strip verbose/debug/info Android logs, but retain warning/error
calls, so this is not solely a debug-logging concern.

Diagnostic export calls the same sanitizer on captured Logcat. The standalone-label
gap can therefore affect an exported diagnostic bundle if such content is logged.
This review did not observe an actual user's credential in a log or demonstrate
that another ordinary Android app can read these logs.

Recommendation: keep credentials and authored content out of messages; apply
structured redaction before every sink, including warning/error throwables.
Exercise every supported label, multiline values and exception causes. Do not
treat a regex over arbitrary text as a complete personal-data filter.

### R04 — Task and habit lock-screen privacy remains unfinished

The [task channel](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/service/TaskReminderReceiver.kt#L131)
and [habit channel](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/service/HabitReceiver.kt#L59)
are created with public lock-screen visibility. Their notifications include
titles/details or custom views without a redacted public version. Actual exposure
depends on Android, channel and device settings; this is not a cipher failure.

Recommendation: private defaults, a generic public notification and a clearly
explained opt-in for showing content. Existing channels may retain user-managed
settings, so an upgrade needs honest guidance rather than assuming a new default
overwrites prior system choices. Work-session notifications already use a private
visibility setting and offer a useful consistency reference.

### R05–R07 — Remaining security work needs migration/recovery design

- **R05, application-level encryption coverage:** chat, vocabulary/bookmarks and
  drill history use ordinary Room factories. Completed task titles/details are
  serialized into ordinary `focus_completed_tasks_history` preferences
  ([write](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/util/CompletedTaskHistoryManager.kt#L175)).
  The primary task and note databases being encrypted does not encrypt these
  secondary copies. Android sandboxing and device file-based encryption still
  apply. Plan resumable, verified migrations retaining IDs, bookmarks, history
  and rollback; do not create empty replacements when decryption/opening fails.
- **R06, legacy files:** [EncryptedMediaStorage.kt:207](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/util/crypto/EncryptedMediaStorage.kt#L207)
  still accepts a source-derived static compatibility key and legacy plaintext.
  New production writes do not use that fallback. This review did not measure
  how many real installations still contain legacy files. Add an inventory and
  verified atomic rewrite before retiring old readers or copies.
- **R07, vault threat model:** the [PIN-derived wrapping key](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L258)
  is itself recoverable through the app's Keystore master key, whose
  [creation](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/data/note/ArchiveVaultSecurity.kt#L1254)
  does not require OS authentication. This is an app-process/root compromise
  boundary, not proof that an unrelated normal app bypasses the PIN. Stronger
  credential isolation needs an explicit design for recovery and authorized
  unattended private backups. Replacing envelopes or removing keys as a quick
  cleanup could make existing data permanently unreadable.

These reconfirm the corresponding open findings in the earlier independent
security review. They are not newly introduced by the permission patch.

### R08 — Large habit IDs can replace/cancel another habit's notification

[HabitReceiver.kt:40](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/service/HabitReceiver.kt#L40)
reduces IDs modulo 400000. IDs `1` and `400001` both become notification `500001`.
The receiver posts without a distinguishing tag, and the action receiver cancels
using the same reduced ID. A synthetic platform check posting both identities
leaves only the second notification. This does not demonstrate lost habit records
or incorrectly applied progress; the reproduced effect is notification replacement.

Recommendation: use a tag containing the full Long habit ID consistently for
post, update and cancel, as task notifications already do. Retire identifiable
legacy notifications without cancelling other feature notifications.

### R09 — Drill persistence failures are silent

[DrillDatabase.kt:93](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/src/main/java/com/focusbyrj/app/data/drill/DrillDatabase.kt#L93)
updates memory before an asynchronous insert and catches database exceptions with
an empty body. Claim persistence has the same pattern at line 135. If storage or
database access fails, a summary/claim can appear current in memory without a
durable record, and the caller receives no failure result or retry state.

This is a source-confirmed error-path gap; no failed real-device disk was tested.
Return durable save outcomes, surface recoverable failure, and coordinate summary
and reward/claim settlement so retry/process death does not lose history or double
settle rewards. Preserve the original result until verified persistence succeeds.

### R10 — CI needs stronger release gates

[app/build.gradle.kts:42](https://github.com/Nrajesh0/Ayva/blob/53e0e005ba28ae5808b96021e941e6447e15f04c/app/build.gradle.kts#L42) disables lint abort-on-error
and release checks. Neither workflow currently invokes lint. Tests/builds alone
will not prevent future lint errors from reaching release. Add explicit lint error
enforcement while baselining existing warnings for gradual cleanup.

Instrumentation tests exist, including native crypto and interrupted-restore
recovery, but the workflows run JVM tests/builds only. Add dedicated device/native
verification for the supported API levels, upgrade/restore, alarm revocation,
background recovery and platform privacy behavior. Use test installations rather
than real user data. The restore process-termination fixture needs separate
instrumentation invocations, as its source documents.

The WorkManager fixture's short setup timeout is already known to fail under load
and pass on isolated retry. Diagnose its initialization/scheduling deterministically
instead of removing behavioral assertions. Validate the CI time budget: the
validation job has a 40-minute cap for tests plus both builds, while a previous
local full run exceeded that before build time. Different hardware can change
timings; this review did not establish that the current GitHub run timed out.

### R11–R12 — UX and maintenance improvements

- **R11:** battery exemption is described as optional, but `blockingReady` still
  requires it; the overall badge then shows Setup. Onboarding likewise calls the
  action Skip Configuration unless all optional/reliability grants are present.
  Skip is enabled, so this is confusing readiness messaging rather than a proven
  blocking failure. Distinguish essential capability, available approximate
  reminders and optional reliability improvements.
- **R12:** the latest retained lint report has zero errors, 365 warnings and 56
  informational findings. Major warning groups include HardcodedText (91),
  ObsoleteSdkInt (88), IconLocation (53), SetTextI18n (30), DefaultLocale (17),
  StaticFieldLeak (12), ApplySharedPref (7) and ContentDescription (7). Prioritize
  real lifecycle, locale, persistence and accessibility problems. A warning is
  not automatically a functional defect; application-context references can be
  intentional and widget/resource aliases need care before deletion.
- AccountScreen is 4627 lines, KeepNoteEditor 3768, NotesViewModel 2255 and
  BubbleChatActivity 2161. Extract responsibilities incrementally with behavior
  guards. File length alone does not establish poor runtime performance. Profile
  startup, rendering, repository queries and background work before optimizing.
- Some old review scripts still assert pre-remediation behavior; for example
  `docs/security/ayva_review_checks.py` assumes old fallback/save/migration paths.
  Refresh or explicitly archive them so historical evidence is not treated as a
  current vulnerability test. Export auxiliary database schemas and add versioned
  migration tests before their planned encryption/schema changes.

## What is already implemented or intentionally deferred

The recent private-receiver guards, permission reductions, device-admin policy
reduction, bounded alarm recovery, manual-backup live-stage ownership and repeated
confirmation protection are present. Production main/note database initialization
fails closed; the destructive Room fallbacks inspected are in test-environment
branches, not ordinary production fallback paths. Atomic habit increments and chat
load/write synchronization also exist; historical reports should not relist them
as untouched defects.

No obvious production TODO/NotImplementedError feature stub was found in the
marker scan. That is not proof of complete feature coverage. Previously excluded
features and deliberately removed menu entries are not automatically backlog
items. Speech-provider privacy remains deferred under the user's instruction.

## Verification and scope

Fresh execution: **16 synthetic audit checks** across Android API 31 and 34.
Twelve cover cleanup, controls and notification collisions; four cover log
redaction. They deliberately assert the current undesired behavior where described:
their successful execution confirms the findings, not product correctness.
They use real in-memory Room operations and the production helpers under
Robolectric, with synthetic records/bytes/strings. They do not validate native
SQLCipher at-rest protection, hardware Keystore behavior or real lock-screen UI.

Fixtures, init script and JUnit XML were retained outside the repository in
`/workspace/artifacts/remaining-audit-2026-11-07/`. The init script temporarily adds
the source directory under `/tmp/ayva-remaining-audit/`; it does not modify normal
project source sets. Chat-boundary and dictionary-integrity guards passed again.
The lint counts above come from the retained prior full report; full lint, full
regression suite and release APK builds were not repeated for this read-only scan.
No real user data or real credentials were used. No dependency/CVE database scan,
physical-device pentest or comprehensive exported-component fuzzing was performed.

Recommended implementation order: **R01/R02 data preservation**, then **R03/R04
privacy and R08/R09 durability**, then **R05–R07 planned security migrations**,
with **R10 automated release protection** and **R11/R12 measured UX/maintenance**.
