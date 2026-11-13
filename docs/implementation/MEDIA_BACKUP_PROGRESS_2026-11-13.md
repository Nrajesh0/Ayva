# Authenticated media reads and backup progress

## Scope

Reduce redundant media decryption without changing media quality, encryption formats,
keys, backup passwords, password derivation, or archive validation. Show short live
operation labels during manual backup, automatic backup, and restore.

Image byte reads now authenticate one bounded ciphertext snapshot in one GCM
operation. The existing 50 MiB encrypted-file bound remains. Streams for files up to
8 MiB use an authenticated memory snapshot, wiped on close. Larger streams retain
the original full verification before returning a stream over the same descriptor.
No plaintext disk cache is introduced. Existing encrypted files are not rewritten.

Progress follows the operation's coroutine context across IO dispatches. It contains
operation labels and attachment counts, never note contents, credentials, or paths.
Observers cannot change the outcome of a backup. Manual flows reject late progress
from cancelled requests. Unknown durations use an indeterminate indicator rather
than invented percentages. The existing automatic-backup settings screen receives
the same detailed progress; the background notification remains a generic notice.

Examples: Checking backup password; Checking attachment 2 of 8; Saving recovery
point; Restoring notes; Saving backup file; Verifying saved file. File readbacks,
authenticated archive checks, restore recovery points, and safe retention remain.

## Validation

The focused regression selection executed 245 tests. Initially 244 passed, with a
database-key test setup failure: it cleared the cache before first accessing the
test Application, whose initialization could refill the cache. Initializing the
Application first fixes the fixture; its original missing-credentials and file
preservation assertions remain. All 11 tests in that class pass on rerun. The
other 234 selected tests passed in the original run.

The selection covers encrypted media tampering/truncation, authenticated snapshots,
large streams, data preservation, portable automatic backups, backup publication,
manual/restore cancellation, coroutine progress isolation, and backup/navigation UI.
The updated native Android test fixture compiles; compilation is not execution on
a device. Source security contracts, chat boundary, dictionary integrity, shell
syntax, and whitespace checks pass. Debug and release lint both pass with no
errors and no findings in changed files. Debug retains 244 warnings and release
retains 242 warnings; each has 57 informational findings elsewhere in the app.
This is not a claim of zero warnings.

No device timing was collected. Removing a decryption pass reduces work; no speedup
factor or completion time is claimed. A small local archive can still take time
for password derivation, complete authentication, durable writes, and restore
snapshots. These safeguards were retained.

## Background behavior (unchanged)

WorkManager checks eligibility approximately every 15 minutes; daily/weekly/custom
due dates control actual backups. Battery, storage, charging settings, Doze, and
manufacturer restrictions can delay execution. The app need not be open once
setup and private-vault authorization are complete.

Clearing Recents normally allows work to continue, subject to manufacturer process
management. Force stop prevents background work until the app is opened again.
Clearing app storage or uninstalling deletes local configuration and keys; external
backup files normally remain but need their exported recovery key for restore.

Interrupted publication never records an unverified file as successful. A killed
process may leave an incomplete file. Older verified backups are retained; a
complete verified local stage may be reused on retry. There is no promise of
byte-by-byte resumption or exact daily execution time.

## Follow-up archive investigation

Source inspection found two independent issues, outside the performance changes:

1. With vault protection enabled and locked, `VaultPayloadEncryptor` correctly
   refuses plaintext archiving. `NoteRepository.setArchived` catches that failure
   and only logs it. List/editor archive actions do not request inline vault
   authorization, so the operation can appear to do nothing. Unlocking in Archive
   and then leaving it locks the vault again.
2. `NotesViewModel.archiveCurrentNote` cancels autosave, clears the editor, and
   archives only a previously saved ID. It does not flush the current draft or
   wait for success. A new note without a saved ID does not get archived; recent
   edits can remain unsaved. Unarchive uses a similar editor-close pattern.

The archive follow-up now fixes these paths with inline authorization, explicit
mutation results, draft preservation and successful-save navigation. Settings also
supports PIN-free archiving while keeping viewing/unarchiving PIN-protected. See
[Archive flow and encryption](ARCHIVE_FLOW_2026-11-13.md) for the implementation
and current verification. Unsupported older vault credentials remain unchanged.

The completed archive follow-up expands coverage to 389 selected regression
executions, all passing across the latest per-class results. Both APK variants
build and full lint passes with no errors; see the archive review for exact scope,
existing warnings, and the final dependency-catalog-only verification.

The completed changes are included in version 2.0.46 (146) for the requested GitHub push.
