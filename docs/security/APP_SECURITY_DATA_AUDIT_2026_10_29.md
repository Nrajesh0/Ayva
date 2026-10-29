# Run — security and data integrity audit, 29 October 2026

Follow-up: [remaining findings repaired](APP_SECURITY_REMEDIATION_2026_10_29.md). The findings below retain their discovery-time wording; current implementation and verification status is in the follow-up report.

## Verdict and evidence boundary

**Source review completed across all 12 planned batches. This is not a clean release sign-off.** Focused repairs were made for concrete data-preservation defects. Full restore still lacks crash-safe coordination across databases, files and preferences; concurrent editors can still overwrite each other's changes. These are the main remaining engineering risks, not reasons to replace working cryptography or rewrite ordinary features.

Baseline: `d3bfbd4f3f20fc814227de8f7b80b84610aaae2c`, version 2.0.22 (122), plus the existing unpublished transcription-popup changes. Audit repairs are local, uncommitted changes on top. Existing `docs/mockups/` was not audited or changed. No version bump, publication, user-data reset or key reset was performed. Test fixtures use disposable Robolectric application storage.

Evidence types: source/call-path review, focused tests of production Kotlin using Robolectric, actual backup/restore API calls with synthetic records/files, merged-manifest inspection, boundary checks, dependency advisory queries and a debug build. Robolectric substitutes software keys and can substitute PBKDF2 for native Argon2id and in-memory Room for SQLCipher. Its results **do not prove Android Keystore, native KDF interoperability, actual encrypted database migration, or physical-device behavior**. No physical device or forced process-death/low-storage device experiment was used in this audit.

## Inventory and contracts

| Data | Owner/storage | Protection and recovery |
|---|---|---|
| Notes/checklists/labels/trash | NoteDatabase v7 / NoteDao / NoteRepository / NotesViewModel | SQLCipher database key wrapped by Android Keystore; legacy migration reader; archived payloads have additional vault encryption |
| Vault credentials and recovery | ArchiveVaultSecurity, VaultPayloadEncryptor, VaultCryptoEngine | Stable vault data key, PIN-derived wrapping key, recovery envelope; memory key cleared on lock; compatibility adapters retained |
| Images/files/audio | `files/keep_images`, `files/keep_audio` | AES-GCM local media; explicit legacy plaintext support; temporary plaintext sharing/import staging requires separate consideration |
| Manual backup | BackupRestoreManager / CryptoBackupEngine | Manifest v2; current FBCK crypto v4, readers v1–v4; Argon2id BACKUP parameters + AES-GCM, media SHA-256/size inventory; password-portable |
| Automatic/pre-operation snapshots | DataSafetyManager / EncryptedSnapshotFile / SnapshotCryptoEngine | Snapshot record format v3, device-key encryption; **metadata only, no media bytes**; not fresh-device recovery |
| Selected-task recovery | TaskRecoveryStore | Encrypted bounded before/after records; checks current rows against expected state inside a transaction |
| Tasks/habits/schedules/restrictions | FocusDatabase v15 | SQLCipher using DatabaseKeyProvider, transactional actions and task recovery where provided; no need to impose vault unlock friction |
| Drill/vocabulary/chat | DrillDatabase v1, VocabDatabase v3, AyvaChatDatabase v1, assistant stores | Separate stores; backup includes selected data; assistant session/planning storage has its own recovery boundary |
| Public-content rotation/preferences | App-private preferences | Nonpersonal content IDs intentionally survive chat clear; not a transcript leak |
| Widgets/share/speech | Explicit Android entry points | Widgets exclude archived notes; exported files have narrow URI grants; phone speech provider can process online despite app having no INTERNET permission |

## Batch ledger

| Batch | Reviewed paths and result | Limits / follow-up |
|---|---|---|
| 0 Baseline/inventory | Above inventory; historical findings reconciled below; no changes to unrelated work | Exact device behavior not inferred from old reports |
| 1 Crypto/keys | DatabaseKeyProvider, Argon2idKdf, VaultPayloadEncryptor, ArchiveVaultSecurity, CryptoBackupEngine, SnapshotCryptoEngine, EncryptedMediaStorage, recovery methods in VaultCryptoEngine. Fixed missing-key initialization and truncated-media classification; explicit payload drain added | Native KDF fallback concern Q1; hardware invalidation/interoperability pending |
| 2 Notes/save/migration | NoteDatabase migrations, NoteDatabaseMigrationHelper, NoteDao, NoteRepository and editor save/lock/refresh paths. Fixed corrupt-envelope editing and nontransactional snapshot rollback | O2 concurrent edits; Q2 lock/save failure combinations; supported real SQLCipher upgrade matrix pending |
| 3 Media/import/export | NoteImageHelper bounds/orientation, NotesViewModel image/file/camera imports, NoteMediaManager reference checks, EncryptedMediaFetcher, AudioMemoManager, NotesnookBlockWidgets/ArticleExporter export. Fixed atomic byte writes, import ownership and failed-decrypt sharing | O3 interrupted plaintext staging/export residue; large/provider-specific streams not device-stressed |
| 4 Backup | BackupRestoreManager collection, inventory/write/readback; AutoBackupWorker/Scheduler. Existing media inventory verification retained; final GCM drain added | O1/O2 consistency and crash boundaries; provider overwrite/low-space behavior pending |
| 5 Restore | Preflight, ZIP paths/sizes, manifest, vault setup, prepared media, store mutations and rollback. Fixed validation gaps and preservation of old attachments | O1 remains high priority; not claimed crash-atomic |
| 6 Snapshots/trash | DataSafetyManager, EncryptedSnapshotFile, TaskRecoveryStore, repository and editor deletion callers. Fixed failed-snapshot purge, concurrent snapshot I/O/migration and atomic Notes rollback; clarified metadata-only UI | Historical snapshots cannot recover physically deleted media; device-bound keys remain a recovery prerequisite |
| 7 Vault/sync | Vault credential changes, stable-key migration journal, disable/recovery, lock lifecycle, widgets and sync call sites | No active remote VaultSyncManager caller found; dormant helper is not an active cloud vulnerability. Q1/Q2 remain |
| 8 Android/privacy | Source + merged manifest, FileProvider paths, MainActivity sensitive routes, widget/action receivers, share cache, speech disclosure, dependency versions and CI | No app INTERNET permission, allowBackup=false, no broad FileProvider root found. OEM transfer/grants/recents and native dependency audit pending |
| 9 Cross-feature | Save/import session ownership, per-editor persist mutex versus restore/assistant lock, widget writes, restore rollback ordering, transcription late callbacks | Fixed import crossing and cancellation rollback; O1/O2 and Q2 are not hidden by passing unit checks |
| 10 Ordinary features | Habit DAO atomic progress, pause/reminder guards, task recovery stale-state protection, task/habit action receiver visibility, boot action filtering, chat authority boundary and direct overlays | Portable habit pause-state omission fixed (A15); dictionary bookmark omission documented (O4). Other sampled controls retained. No changes to accepted planning semantics or blocking architecture. Not an exhaustive UI walkthrough |
| 11 Verification/report | Focused regression checks and final debug build; this report separates fixed/open/pending evidence | Device sign-off requirements below remain outstanding |

## Confirmed findings and repairs

All source locations below are under `app/src/main/java/com/focusbyrj/app/`; revision is the baseline plus local changes described above. Severity describes the original reachable failure, not a claim that every user encountered it.

### A01 — Medium: media replacement deleted the previous file before successful installation — fixed

`util/crypto/EncryptedMediaStorage.kt: writeEncryptedBytes`. Overwriting an existing file used delete → rename → copying fallback. Failed replacement could destroy the old valid bytes or leave partial output. Replaced this with synchronized AndroidX AtomicFile write/finalization/rollback. Existing encryption format is unchanged. Focused overwrite and plaintext-compatibility round trip passed; device power-loss/storage exhaustion is pending.

### A02 — Medium: truncated encrypted media could become “legacy plaintext” — fixed

`EncryptedMediaStorage.kt: isEncrypted/openDecryptedInputStream`; backup manifest builder uses this classification. A file containing the encryption header but shorter than header + IV + tag was classified as plaintext, allowing corrupt ciphertext to be backed up as if it were valid attachment bytes. Header recognition now identifies claimed encryption independently of completeness, and streaming reads reject truncated containers. Regression uses a header-plus-IV fixture and checks both readers return failure. Valid legacy plaintext remains readable.

### A03 — High data integrity: expired-trash purge ignored snapshot failure — fixed

`util/backup/DataSafetyManager.kt: purgeExpiredTrash`. A failed pre-operation snapshot returned null, but deletion proceeded. Purge now retains expired rows when that snapshot fails. Test blocks creation of the snapshot directory and verifies the complete synthetic note remains unchanged. The snapshot remains metadata-only; this does not add media recovery.

### A04 — Medium: asynchronous imports could attach to a different editor session — fixed

`ui/screens/notes/NotesViewModel.kt: addImageUriToEditor/addAttachmentToEditor/addDrawingToEditor/addPhotoToEditor/addCapturedPhoto`. The operation previously read whichever editor was active after I/O. Switching notes during import could therefore attach the file to the wrong note. Capture the originating session, verify it before mutation, and discard the newly imported file when ownership changed. Source-reviewed for all five paths; real delayed-provider switching is pending. The change does not alter saved attachment formats.

### A05 — High data integrity: malformed/unsupported manifests were accepted too permissively — fixed

`util/backup/BackupRestoreManager.kt: validateRestoreManifest/extraction`. Unknown manifest versions, wrong JSON array types and duplicate note/habit/schedule IDs could be interpreted as absent data or overwrite records. Reject these before live mutation. Duplicate ZIP entry names, unsupported entry categories and excessive entry counts are also rejected. Existing v1/v2 formats retained; A14 also repairs mixed assigned/stable legacy IDs. Tests verify future version, wrong note-array type and colliding note IDs leave the original live record unchanged. This is not a complete schema validator for every optional field.

### A06 — Medium, provider-dependent: ZIP EOF did not explicitly require cipher EOF — repaired

`BackupRestoreManager.kt: createEncryptedBackupLocked/restoreEncryptedBackupLocked`. ZIP termination is not an authentication contract for the enclosing cipher. The ZIP reader now does not close the underlying cipher prematurely; the cipher is drained to EOF before verification/restore succeeds. A damaged final-tag test verifies no live mutation. JVM providers may already buffer/authenticate before yielding ZIP data; that test alone does not demonstrate the old defect on every Android provider. No format change.

### A07 — High data integrity: late restore failure could roll back rows but lose original attachment bytes — fixed for caught failures

`BackupRestoreManager.kt: media destination allocation/installPreparedMedia`. Clean restore reused live filenames and installation deleted their replacement backups before later assistant-store work completed. A later exception could restore old rows pointing at new bytes. All restored media now receives unique imported paths, including clean restore; old files remain available for rollback. Empty attachments are no longer silently skipped, and the current preparation temp is registered for failure cleanup before writing. Actual API round-trip verifies restored note association/content and preservation of the previous live attachment bytes. O1 still applies to process death before all new stores are installed.

### A08 — Medium: missing database credentials allowed new key initialization over an existing database — fixed

`data/note/DatabaseKeyProvider.kt: getOrCreatePassphrase`. Missing passphrase preferences were treated as first installation even when a nonempty encrypted Notes or Focus database existed. Now initialization fails without generating a replacement key; original bytes are retained. Test confirms both file preservation and no new credential record. This cannot recover an already-lost key; a portable backup or original credentials are still needed.

### A09 — Medium: corrupted vault envelopes could be opened as editable note content — fixed

`NotesViewModel.kt: openExistingNote` and its fresh-record refresh. The Corrupted branch returned the encrypted envelope to the editor, where later edits could overwrite the stored payload. It now refuses editing with a preservation message; a refresh also refuses unreadable envelopes. The new regression failed against the previously compiled editor and is included in final verification of the repair.

### A10 — High data integrity under interruption: rollback could itself be canceled or commit an empty Notes table — fixed for caught cancellation/errors

`BackupRestoreManager.kt` rollback and `DataSafetyManager.kt` snapshot rollback. Rollback now runs in NonCancellable. Snapshot Notes replacement uses `NoteDao.replaceAllNotes` under a Room transaction instead of separate delete and insert commits; preference rollback errors are not silently ignored. Existing snapshot/restore tests rerun. OS process death is outside coroutine cancellation protection: O1 remains.

### A11 — Medium recovery expectations: snapshot UI did not disclose missing media — fixed disclosure; capability unchanged

`ui/screens/SecurityScreen.kt` snapshot list/confirmation and `DataSafetyManager.kt` documentation. The JSON already said METADATA_ONLY, but the recovery UI called these backups without explaining the limitation. UI now states that snapshots contain records and require a full encrypted backup for missing attachments. No attempt to retrofit old snapshots with nonexistent bytes. Full media-bearing snapshots remain future work if automatic full recovery is required.

### A12 — Medium: attachment sharing fell back to raw bytes on decryption failure — fixed

`ui/screens/notes/NotesnookBlockWidgets.kt` attachment click/export. Failed decryption previously copied the original encrypted/corrupt file to an export URI. Export now requires an allowed note-media/cache path, uses the decrypting stream, and removes partial export output on failure. Supported plaintext legacy files still work through the reader. Source-reviewed and compile-checked; external viewer/grant behavior remains a device check.

### A13 — Medium: concurrent snapshot I/O and migration lacked serialization — fixed

`util/backup/EncryptedSnapshotFile.kt` and `DataSafetyManager.migrateLegacyPlaintextSnapshots`. Catch-up backup, scheduled backup and pre-operation migration can access the same snapshot. AtomicFile alone is not a multi-writer/read synchronization mechanism. Read, write and the whole detection/read/migrate/verify sequence now share a synchronized codec lock. Migration validates legacy JSON before rewriting it. Verification exposed startup migration competing with a legacy fixture; that fixture now uses a separate files directory while preserving its strict assertions. Source review establishes the race; no claim of a reproduced real-device corrupt snapshot.

### A14 — Medium data integrity: mixed legacy note IDs lost rows on clean restore — fixed

`BackupRestoreManager.restoreEncryptedBackupLocked`. Pruning used IDs from the manifest instead of IDs returned by insert. A legacy note without an ID was inserted then deleted whenever other restored notes had stable IDs. Insert stable-ID records first, retain generated IDs, then prune against the complete inserted set. The regression deliberately places the ID-less note first and verifies both titles survive; it failed against the previously compiled implementation before the final rebuilt fix.

### A15 — Medium data integrity: portable backup dropped habit pause state — fixed

`BackupRestoreManager` habit serialization/deserialization. `pausedAt` and `pauseHistoryJson` were present in native entities and snapshots but missing in manual backup. Recovery could silently resume a paused habit and lose its pause history. Both fields now round-trip, with defaults for older backups. The attachment round-trip test also verifies the entire restored paused Habit equals its source.

## Outstanding findings and specific verification gaps

### O1 — High: full restore is not durable across process death — confirmed source-level design gap

`BackupRestoreManager.restoreEncryptedBackupLocked` commits Notes, Focus, Drill, vocabulary/chat, preferences, media and assistant state separately. `RestoreRollbackState` exists only in heap memory. A kill/reboot after a DB commit can leave a mixed dataset or rows pointing at not-yet-installed media. Snapshot restore likewise spans Notes and Focus stores. Pre-operation snapshots do not include all app stores or attachment bytes and cannot stand in for a complete durable rollback journal.

**Required remedy:** a versioned durable restore operation record, complete recoverable before-state (including files/preferences/key-dependent state), staged install with explicit phases, and restart reconciliation before normal writers run. Exercise kills before/after each phase and verify complete contents/hashes. This is a coordinated storage change; the small caught-error fixes in this audit must not be presented as solving it. No destructive kill test against user data was run.

### O2 — High data integrity: independent writers can overwrite concurrent changes — confirmed source-level gap

`NotesViewModel` has a per-instance persist mutex, but widget/QuickEdit writes use NoteDao independently. `NoteRepository.doSaveEntity` performs a whole-row update without checking the loaded revision. Restore's AssistantStateLock does not cover normal Notes editors or every habit/widget writer. Backup collection reads multiple tables/stores independently. A widget edit followed by a stale editor save can lose a checklist change; a restore rollback can overwrite edits made after it captured state.

**Required remedy:** revision-aware saves/conflict handling for Notes, and a maintenance/write coordination contract shared by all affected restore writers. Preserve unsaved drafts when a conflict is detected. Do not add one arbitrary mutex only to backup and claim the race is fixed. Needs deterministic two-editor/widget and save-during-restore tests; not device-exercised here.

### O3 — Medium confidentiality: interrupted plaintext staging can remain in private cache — confirmed source-level gap

Restore extracts media into `cache/restore_media_stage_*` and deletes it in finally. A process kill bypasses finally; there is no startup reconciliation for those directories. Explicit share exports are plaintext by design; pruning runs on later exports, so “one hour” is not a guaranteed wall-clock deletion deadline. App-private cache and narrow FileProvider reduce exposure, but they do not meet an unconditional “all Notes bytes encrypted at rest” claim.

**Required remedy:** encrypt restore staging or clean abandoned stages through the durable restore lifecycle without deleting an active operation; clean expired share exports at safe lifecycle points while respecting live grants. Include kill/restart and viewer handoff evidence. Do not imply unlink/zero-writing guarantees physical erasure on flash.

### O4 — Medium: portable backups omit unlearned vocabulary bookmarks and dictionary identity links — confirmed source-level gap

Backup collection calls `getAllLearnedIdioms/getAllLearnedOws`, whose queries require non-null learned_at. Saving a dictionary sense intentionally does not mark it learned, so these bookmarks are absent. Even a learned custom OWS row is exported only as a numeric ID plus flags; restore only updates existing IDs, while `dictionary_bookmarks` and custom term/definition rows are not serialized. Fresh installs can therefore lose saved senses, or numeric-ID collisions can apply flags to unrelated imported words.

**Required remedy:** export bookmarked as well as learned records, persist stable source/sense identity and custom word contents, and restore by identity with remapped local IDs under a vocabulary transaction. Define merge/clean behavior and include this store in rollback. Verify fresh-install recovery plus two-device numeric-ID collisions. Existing archive files cannot recreate data they omitted. This is an ordinary data-portability issue, not a reason to add vault-style protection to vocabulary.

### Q1 — Needs native verification: KDF fallback and key transitions

Argon2idKdf blocks missing native support for BACKUP but can use PBKDF2 for LOGIN while some callers persist an argon2id label. ArchiveVaultSecurity contains compatibility migration paths, so an unconditional lockout claim is not justified without testing the stable-VDK and recovery combinations. Verify native-load failure → PIN setup/change → native availability restored, and old fallback accounts. Tests here use JVM substitutes and cannot settle this. Prefer explicit algorithm/version provenance and fail-closed new writes where a format promises Argon2id; retain deliberate legacy readers.

### Q2 — Needs failure/lifecycle verification: vault lock and editor durability

Vault lock preserves a device-encrypted pending draft after DB failure, but encryption plus pending-draft write failure is logged and the visible editor is cleared. Its synchronous lock/save path also intersects a persist mutex whose other users dispatch UI updates. Verify simultaneous close/autosave/background-lock with storage full, crypto failure and cancellation; preserve recoverable draft state without keeping private content visible. Also verify editor switch during an in-flight new-note insert and rapid repeated Close. No invented clean verdict for these unexecuted cases.

### Other bounded limitations

- Physical-device SQLCipher migration/WAL recovery, Keystore invalidation/lock changes, true Argon2id old-format interoperability, SAF permission loss/low space, large archive memory use, API 35/36 keyboard/Back/widget/font/lifecycle behavior and OEM device transfer remain unverified.
- Resource limits exist for media entries, manifest size, import size and image dimensions. They are not proof that every provider/native decoder or encrypted input allocation is bounded under every malformed input.
- This review did not fuzz vendored native ChatScript, Android media codecs or every transitive dependency. No report of a hypothetical exploit is substituted for evidence.

## Healthy controls retained and historical reconciliation

- F01: production Keystore failures no longer deliberately use the old static write keys; legacy readers remain necessary. New missing-DB-key guard improves this. Hardware-backed status is device-dependent, not established by comments.
- F02/F10: locked-vault saves fail closed; normal save failures have typed results; lock clears editor/cache and has encrypted pending-draft recovery. A09 fixed corrupt editing; Q2 remains.
- F03: current stable VDK wrapping and migration journal reduce PIN-change loss; old-format/native and disable/recovery interruptions still need device evidence.
- F04: legacy migration uses a transaction and preserves source on detected read/persistence failure; fallback databases are quarantined. Existing migration tests pass in their stated environment. ID-presence verification is weaker than full cross-version content proof; no claim of an exhaustive real SQLCipher migration matrix.
- F05/F06: automatic snapshots are encrypted; obsolete/unavailable vault keys are rejected during restore rather than blindly replacing readable protected notes. Media completeness limits are now exposed.
- F07: merge restore remaps imported IDs and media rather than overwriting local Notes by numeric collision. Keep this behavior.
- F08: preflight and caught-exception rollback improve restore, but O1/O2 mean the historical concern is only partially resolved.
- F09: editor removal defers physical deletion; shared-reference checks retain media used by other live Notes; cleanup aborts for unreadable/locked vault reference sets. Snapshot-only references are not a full media archive.
- F11: sensitive routes set secure-window protection; FileProvider exposes only exports/diagnostics; random export names and explicit grants retained. O3 and device checks remain.
- F12: atomic SQL habit increments/decrements and transactional habit deletion retained; focused tests exercise these without imposing vault requirements.
- F13: validation workflow now has boundary checks, tests and debug/release assembly. The present local audit builds debug; it does not claim GitHub release or signed APK verification.
- F14: archive limits, path checks and preference allowlist retained; A05 strengthens preflight. Not an unrestricted parser/fuzzer certification.
- No active remote VaultSyncManager entry point found. Do not market dormant helper defects as current cloud compromise.
- Chat data-boundary guard passes across 117 source files. Accepted task-planning behavior and direct WindowManager blocking remain unchanged.

## Verification record

Initial focused run: **53 tests passed**, 0 failures/errors/skips, across DataPreservationAuditTest (8 at that point), BackupRestoreHardeningTest and WorkPackage2–7SecurityTest. Log: `/tmp/security-audit-tests.log`.

Final targeted run: **60 tests passed**, 0 failures/errors/skips; **debug APK assembled successfully**. See final evidence file `APP_SECURITY_DATA_AUDIT_RESULTS.json`. Final tests include later regressions for corrupt editor rejection, mixed legacy IDs, actual attachment backup/restore and habit pause-state preservation, affected snapshot/restore checks, no-network manifest checks, habits and transcription corrections. Intermediate runs added regression tests after compilation had started and exposed the old corrupt-editor/mixed-ID behaviors; a separate migration check exposed a fixture competing with the startup worker. Source changes were then rebuilt for final verification. Only the final rebuilt results constitute fix verification.

`tools/check_chat_boundary.py`: passed (117 source files). `tools/check_dictionary.py`: passed (107,945 headwords / 163,274 senses). `git diff --check`: passed at final review.

GitHub global advisory API queried for Maven package/version pairs on audit date: `net.zetetic:android-database-sqlcipher@4.5.4`, `com.lambdapioneer.argon2kt:argon2kt@1.4.0`, `androidx.room:room-runtime@2.6.1`, `io.coil-kt:coil@2.7.0`; each returned an empty list. This is a limited registry check, **not** proof of no native/transitive CVEs. No blanket dependency upgrades made.

## Release recommendation and next execution order

Keep these focused repairs and the existing protections. **Do not claim full crash-safe recovery yet.** Address O1 first, O2 second, then O3/O4; resolve Q1/Q2 with targeted native/device fixtures. Only those affected contracts need new tests. Use synthetic notes/media with expected hashes and verified portable backups for destructive/device experiments. A batch with no actionable finding stays unchanged; no cosmetic hardening or broad redesign is required to justify this audit.
