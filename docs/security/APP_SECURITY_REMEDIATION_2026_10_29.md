# Remaining audit repairs — 29 October 2026

This follows APP_SECURITY_DATA_AUDIT_2026_10_29.md. The original report records discovery; this document records subsequent repairs. Baseline remains d3bfbd4 plus local audit and transcription changes. No release/version bump or push is included.

## Repairs

| Finding | Implementation |
|---|---|
| O1: interrupted multi-store restore | RestoreJournal captures authenticated, encrypted, verified before-state for all five Room databases, application preferences and assistant recovery files. It writes and syncs the journal before mutation, uses an operation-specific durable commit marker, and reconciles pending operations before startup managers/workers. Recovery is repeatable; failed recovery retains the journal and blocks ordinary writes. Original attachment files are retained under the unique-path installation policy. |
| O2: conflicting writes | MaintenanceOpenHelperFactory applies the admission barrier to SQLite mutation/transaction entry points, including background and widget writes. Application preference commits and snapshot writes use the same barrier. Conflicting writes receive a retry failure rather than starving Room's worker pool. The main screen shows maintenance progress. Editor saves compare stored revisions within a transaction; widget checklist edits mutate current rows transactionally. Quick edit/add retain input on conflict. Whole-store replacement invalidates existing editor generations, including equal-timestamp imports. |
| O3: plaintext staging/residue | Restore staging is encrypted as it is extracted, and verification/preparation decrypt streams when needed. Legacy abandoned plaintext stages are encrypted in place with hash verification and atomic replacement, preserving their paths/content. ExportCleanupWorker performs periodic expiry cleanup; startup also prunes expired exports. Fresh share files retain a one-hour lease. Android scheduling is best effort, including when force-stopped. |
| O4: incomplete bookmark backups | VocabularyArchive includes bookmarked, mastered and learned entries, full custom word content, and dictionary sense links. Restore matches word identity and remaps numeric IDs, preserves local state for merges, and resets learning/bookmark flags for clean restores. The complete vocabulary tables are covered by the recovery journal. |
| Q1: mislabeled KDF fallback | Production Argon2id operations fail closed when the native library is unavailable. The implicit PBKDF2 substitute remains restricted to JVM tests. Explicit legacy PBKDF2 readers and compatibility migration paths remain available; no existing key material is reset. |
| Q2: lock/save draft loss and races | Save/acknowledgment is protected against coroutine cancellation, editor IDs/revisions are tracked by session, and UI updates do not require a Main-thread hop while holding the persistence mutex. Close guards preserve newer edits and other editor sessions. Failed vault saves retain hidden draft state; already-vault-encrypted payloads can be stored atomically without a second device-key operation. Recovery creates a preserved copy instead of overwriting a newer live note. |

Encrypted media and snapshot writers also sync and verify installation; AtomicFile's rename failure must not be reported as a successful save. Journal deletion is verified before removing its commit marker. Reminder reconciliation and manager reloads follow rollback.

## Recovery contract

1. Hold the maintenance admission barrier and capture the current complete database state.
2. Sync, encrypt and verify the before-state journal.
3. Run validation/restore, retaining original live media and encrypting staging.
4. After every store commits, sync the commit marker containing the journal operation ID.
5. Remove the journal before removing the commit marker.

A restart without a matching commit marker restores the before-state. A restart with a matching marker retains the committed new state. Recovery never turns an unreadable journal into an empty dataset or generates replacement encryption keys.

The journal is device-bound. A portable backup remains necessary for a lost device or unrecoverable Keystore keys. Automatic historical snapshots remain metadata-only. Previously created backups cannot recover bookmarks or media bytes they omitted.

## Verification

**Passed:** 65 targeted unit checks; the final nine recovery checks rerun after the draft-notification change; final debug APK and Android-test APK builds; chat-boundary guard (117 files) and whitespace checks.

**Installed-APK evidence:** API 29 x86_64 emulator passed native Argon2id/Android Keystore/tamper/staging checks. A second test synced proof of reaching before_commit, killed its own process, and a fresh instrumentation launch passed exact Notes/tasks fingerprint and preference recovery checks. Its synthetic records were cleaned up. This is actual process termination, not only a thrown unit-test exception.

Focused verification is recorded in APP_SECURITY_REMEDIATION_RESULTS.json. It covers actual backup restore interruptions at prepared/Notes/Focus/preferences/media/before-commit phases, post-commit termination, repeat recovery, whole-record contents and attachment bytes, encrypted staging, conflicting editor/widget writes, vocabulary identity collisions, failed atomic installation and failed vault-draft recovery.

A separate installed-APK Android test deliberately terminates its own process before the restore commit marker. A new instrumentation launch verifies startup recovery using fingerprints of the original Notes and task records and the original preference value. It also checks native Argon2id and Android Keystore media authentication. Its fixture is synthetic and cleans up its own records after verification.

Physical-device power loss, vendor-specific hardware key invalidation and every OEM storage-provider behavior are not inferred from unit or emulator checks. No software test can guarantee saving when both storage and every usable encryption key are unavailable; hidden drafts and explicit failure preserve what is recoverable without claiming a durable save.
